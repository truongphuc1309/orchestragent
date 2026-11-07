package com.orchestragent.daemon.provider.claude;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stand-in for the {@code claude} CLI in tests: a separate Java process that the provider starts exactly like the
 * real one. It writes its arguments and the prompt to files in its working folder, then acts out the scenario named
 * in the prompt ({@code scenario:<name>}):
 *
 * <ul>
 *   <li>{@code ok}: a short conversation and a successful result (the default).</li>
 *   <li>{@code error}: an error result, exit code 1.</li>
 *   <li>{@code crash}: a message on standard error and exit code 3, no result.</li>
 *   <li>{@code hang}: starts a child process, then prints nothing and never ends.</li>
 *   <li>{@code chatty}: prints a line every 50 ms and never ends.</li>
 *   <li>{@code garbage}: lines that are not JSON, then a successful result.</li>
 *   <li>{@code orphan}: starts a child that keeps standard output open, waits 1.5 s, answers and exits.</li>
 *   <li>{@code escape}: starts such a child and answers and exits at once.</li>
 * </ul>
 *
 * With {@code --resume missing}, it says the conversation was not found and exits 1, as the real CLI does.
 */
public final class FakeClaude {

    static final String ARGS_FILE = "fake-claude-args.txt";
    static final String PROMPT_FILE = "fake-claude-prompt.txt";
    static final String CHILD_PID_FILE = "fake-claude-child.pid";
    static final String MISSING_SESSION = "missing";

    private static final Pattern SCENARIO = Pattern.compile("scenario:([a-z]+)");

    private FakeClaude() {
    }

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        if (args.length == 1 && args[0].equals("--version")) {
            out.println("9.9.9 (Fake Claude)");
            return;
        }
        if (args.length == 1 && args[0].equals("--child")) {
            Files.writeString(Path.of(CHILD_PID_FILE), Long.toString(ProcessHandle.current().pid()));
            // Long enough for any test, short enough that a test that fails never leaves it running for good.
            Thread.sleep(120_000);
            return;
        }

        Files.write(Path.of(ARGS_FILE), List.of(args), StandardCharsets.UTF_8);
        String prompt = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        Files.writeString(Path.of(PROMPT_FILE), prompt, StandardCharsets.UTF_8);

        String resume = valueOf(args, "--resume");
        if (MISSING_SESSION.equals(resume)) {
            System.err.println("No conversation found with session ID: " + resume);
            System.exit(1);
        }
        String session = resume != null ? resume : "fake-session-1";
        Matcher scenario = SCENARIO.matcher(prompt);
        String name = scenario.find() ? scenario.group(1) : "ok";

        out.println("{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"" + session
                + "\",\"model\":\"" + (valueOf(args, "--model") == null ? "fake-model" : valueOf(args, "--model"))
                + "\",\"permissionMode\":\"" + valueOf(args, "--permission-mode") + "\",\"claude_code_version\":\"9.9.9\"}");
        switch (name) {
            case "error" -> {
                out.println(result(session, true, "Something broke"));
                System.exit(1);
            }
            case "crash" -> {
                System.err.println("boom: the fake CLI crashed");
                System.exit(3);
            }
            case "hang" -> {
                startChild();
                Thread.sleep(Long.MAX_VALUE);
            }
            case "chatty" -> {
                while (true) {
                    out.println("{\"type\":\"rate_limit_event\",\"session_id\":\"" + session + "\"}");
                    Thread.sleep(50);
                }
            }
            case "orphan", "escape" -> {
                startChild();
                if (name.equals("orphan")) {
                    Thread.sleep(1500);
                }
                out.println(result(session, false, "Done, a child is left behind"));
                out.flush();
                // Exit without waiting for the child, which still holds our standard output.
                Runtime.getRuntime().halt(0);
            }
            case "garbage" -> {
                out.println("this is not json");
                out.println("{\"type\":\"something_new\",\"session_id\":\"" + session + "\"}");
                out.println(result(session, false, "Done despite the noise"));
            }
            default -> {
                out.println("{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"Working on it\"},"
                        + "{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"Write\",\"input\":{\"file_path\":\"notes.md\"}}]},"
                        + "\"session_id\":\"" + session + "\"}");
                out.println("{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"t1\","
                        + "\"content\":\"File created\"}]},\"session_id\":\"" + session + "\"}");
                out.println(result(session, false, "All done in " + Path.of("").toAbsolutePath().getFileName()));
            }
        }
    }

    private static String result(String session, boolean error, String text) {
        return "{\"type\":\"result\",\"subtype\":\"success\",\"is_error\":" + error + ",\"result\":\"" + text
                + "\",\"session_id\":\"" + session + "\",\"total_cost_usd\":0.0123,"
                + "\"usage\":{\"input_tokens\":10,\"cache_read_input_tokens\":100,\"output_tokens\":5}}";
    }

    private static String valueOf(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(flag)) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static void startChild() throws IOException {
        String java = ProcessHandle.current().info().command().orElse("java");
        new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), FakeClaude.class.getName(), "--child")
                .inheritIO()
                .start();
    }
}
