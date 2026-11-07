package com.orchestragent.daemon.provider.claude;

import com.orchestragent.daemon.provider.MessageType;
import com.orchestragent.daemon.provider.RunListener;
import com.orchestragent.daemon.provider.Usage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the output of {@code claude -p --output-format stream-json --verbose}, one JSON object per line, and turns
 * it into log lines and a final result.
 *
 * <ul>
 *   <li>{@code system}/{@code init}: the session ID (and a system line saying the run started).</li>
 *   <li>{@code assistant}: text blocks become text lines, tool calls become tool_use lines
 *       ({@code <tool> <main argument>} on the first line, the input as JSON below).</li>
 *   <li>{@code user}: tool results become tool_result lines.</li>
 *   <li>{@code result}: the final answer, session ID, usage, cost and whether it is an error.</li>
 * </ul>
 *
 * Lines that are not JSON, and kinds of messages it does not know, are skipped: newer versions of the CLI add
 * kinds, and they must not break a run. One parser per run, fed from one thread; read from another once the run
 * ended (the methods are synchronized for that).
 */
final class StreamJsonParser {

    /** The {@code result} message. {@code output} is the final answer, or the error when {@code error} is true. */
    record Result(String output, boolean error, String sessionId, Usage usage, String terminalReason) {
    }

    static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** Longest tool result kept in the log; the rest is cut (the web app shows less anyway). */
    static final int MAX_TOOL_RESULT = 10_000;

    /** The arguments that best describe a tool call, in order of preference, for the first line of a tool_use. */
    private static final List<String> MAIN_ARGUMENTS =
            List.of("command", "file_path", "pattern", "path", "url", "query", "description", "prompt");

    private final RunListener listener;
    private final String workDir;
    private String sessionId;
    private Result result;
    private int lines;
    private boolean stopped;

    StreamJsonParser(RunListener listener) {
        this(listener, null);
    }

    /** @param workDir the run's folder: tool calls show paths inside it relative to it (may be null) */
    StreamJsonParser(RunListener listener, Path workDir) {
        this.listener = Objects.requireNonNull(listener, "listener");
        this.workDir = workDir == null ? null : workDir.toAbsolutePath().normalize().toString();
    }

    /** The session ID from the init or the result message, or null before either. */
    synchronized String sessionId() {
        return sessionId;
    }

    /** The result message, or null when none came (the CLI crashed or was stopped). */
    synchronized Result result() {
        return result;
    }

    /** How many JSON messages were read. */
    synchronized int messages() {
        return lines;
    }

    /** Ignores everything from now on: the run is over even if a leftover process still prints. */
    synchronized void stop() {
        stopped = true;
    }

    synchronized void accept(String line) {
        if (stopped || line == null || line.isBlank() || line.charAt(0) != '{') {
            return;
        }
        JsonNode message;
        try {
            message = JSON.readTree(line);
        } catch (JacksonException notJson) {
            return;
        }
        lines++;
        String sid = text(message, "session_id");
        if (sid != null && sessionId == null) {
            sessionId = sid;
        }
        switch (text(message, "type") == null ? "" : text(message, "type")) {
            case "system" -> system(message);
            case "assistant" -> assistant(message);
            case "user" -> user(message);
            case "result" -> result(message);
            default -> {
                // stream_event, rate_limit_event, ... : not shown.
            }
        }
    }

    private void system(JsonNode message) {
        if (!"init".equals(text(message, "subtype"))) {
            return;
        }
        String version = text(message, "claude_code_version");
        String model = text(message, "model");
        String mode = text(message, "permissionMode");
        listener.onMessage(MessageType.SYSTEM, "Claude Code" + (version == null ? "" : " " + version) + " started (session "
                + sessionId + (model == null ? "" : ", model " + model) + (mode == null ? "" : ", permission mode " + mode) + ")");
    }

    private void assistant(JsonNode message) {
        for (JsonNode block : content(message)) {
            switch (text(block, "type") == null ? "" : text(block, "type")) {
                case "text" -> {
                    String text = text(block, "text");
                    if (text != null && !text.isBlank()) {
                        listener.onMessage(MessageType.TEXT, text.strip());
                    }
                }
                case "tool_use" -> listener.onMessage(MessageType.TOOL_USE, describeToolUse(block, workDir));
                default -> {
                    // thinking, redacted_thinking, ...: not shown.
                }
            }
        }
    }

    private void user(JsonNode message) {
        for (JsonNode block : content(message)) {
            if (!"tool_result".equals(text(block, "type"))) {
                continue;
            }
            String body = toolResultText(block.get("content"));
            if (body.length() > MAX_TOOL_RESULT) {
                body = body.substring(0, MAX_TOOL_RESULT) + "\n… (cut)";
            }
            boolean failed = block.path("is_error").asBoolean(false);
            listener.onMessage(MessageType.TOOL_RESULT, failed ? "Error: " + body : body);
        }
    }

    private void result(JsonNode message) {
        String output = text(message, "result");
        boolean error = message.path("is_error").asBoolean(false);
        String terminalReason = text(message, "terminal_reason");
        if ("prompt_too_long".equals(terminalReason)) {
            // The CLI may call this a success; it is not: the conversation no longer fits in the context window.
            error = true;
            output = "The conversation is too long for the model's context window"
                    + (output == null || output.isBlank() ? "" : " (" + output.strip() + ")");
        }
        String sid = text(message, "session_id");
        if (sid != null) {
            sessionId = sid;
        }
        result = new Result(output == null ? "" : output.strip(), error, sessionId, usage(message), terminalReason);
        if (error) {
            listener.onMessage(MessageType.ERROR, result.output().isEmpty() ? "Claude Code reported an error" : result.output());
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private static Iterable<JsonNode> content(JsonNode message) {
        JsonNode content = message.path("message").path("content");
        return content.isArray() ? content : List.of();
    }

    /**
     * {@code Bash npm test} on the first line, then the input as JSON. On the first line a path inside the work
     * folder is shown relative to it ({@code Write README.md}), so the file name is what one sees.
     */
    static String describeToolUse(JsonNode block, String workDir) {
        String name = text(block, "name") == null ? "tool" : text(block, "name");
        JsonNode input = block.get("input");
        String main = null;
        if (input != null && input.isObject()) {
            for (String key : MAIN_ARGUMENTS) {
                String value = text(input, key);
                if (value != null && !value.isBlank()) {
                    main = relative(value.strip(), workDir).replaceAll("\\s+", " ");
                    break;
                }
            }
        }
        String first = main == null ? name : name + " " + (main.length() > 200 ? main.substring(0, 200) + "…" : main);
        return input == null || input.isNull() ? first : first + "\n" + JSON.writeValueAsString(input);
    }

    /** {@code value} relative to {@code folder} when it is a path inside it (case ignored on Windows), else as is. */
    static String relative(String value, String folder) {
        if (folder == null || value.length() <= folder.length() + 1) {
            return value;
        }
        String path = value.replace('\\', '/');
        String base = folder.replace('\\', '/');
        boolean inside = File.separatorChar == '\\'
                ? path.regionMatches(true, 0, base, 0, base.length())
                : path.startsWith(base);
        return inside && path.charAt(base.length()) == '/' ? value.substring(base.length() + 1) : value;
    }

    /** A tool result is a string, or a list of blocks of which the text ones are kept. */
    private static String toolResultText(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isString()) {
            return content.asString();
        }
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode block : content) {
                if ("text".equals(text(block, "type")) && text(block, "text") != null) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(text(block, "text"));
                } else if ("image".equals(text(block, "type"))) {
                    text.append(text.isEmpty() ? "" : "\n").append("(image)");
                }
            }
            return text.toString();
        }
        return content.toString();
    }

    /**
     * Tokens and cost from the result. Input tokens count everything the model read: new input, and input written
     * to and read from the prompt cache.
     */
    private static Usage usage(JsonNode message) {
        JsonNode usage = message.get("usage");
        Long input = null;
        Long output = null;
        if (usage != null && usage.isObject()) {
            input = usage.path("input_tokens").asLong(0)
                    + usage.path("cache_creation_input_tokens").asLong(0)
                    + usage.path("cache_read_input_tokens").asLong(0);
            output = usage.path("output_tokens").asLong(0);
        }
        JsonNode cost = message.get("total_cost_usd");
        BigDecimal costUsd = cost != null && cost.isNumber() ? cost.decimalValue() : null;
        return new Usage(input, output, costUsd);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || !value.isString() ? null : value.asString();
    }
}
