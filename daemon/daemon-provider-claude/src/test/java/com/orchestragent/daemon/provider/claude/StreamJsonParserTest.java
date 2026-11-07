package com.orchestragent.daemon.provider.claude;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Reads recorded-format stream-json fixtures (hand-written in the shape Claude Code 2.1 prints; see
 * {@code src/test/resources/stream-json}). Never runs the real CLI.
 */
class StreamJsonParserTest {

    private final List<String> log = new ArrayList<>();
    private final StreamJsonParser parser = new StreamJsonParser((type, content) -> log.add(type.code() + ": " + content));

    private void feed(String fixture) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/stream-json/" + fixture)) {
            assertThat(in).as(fixture).isNotNull();
            new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().forEach(parser::accept);
        }
    }

    @Test
    void aSuccessfulRunGivesTheAnswerTheSessionAndTheUsage() throws IOException {
        feed("success.jsonl");

        StreamJsonParser.Result result = parser.result();
        assertThat(result.error()).isFalse();
        assertThat(result.output()).isEqualTo("I wrote README.md with a short title.");
        assertThat(result.sessionId()).isEqualTo("5b1f6a52-1111-4a3e-9c1a-0d6c2f7f0a01");
        assertThat(parser.sessionId()).isEqualTo(result.sessionId());
        assertThat(result.usage().inputTokens()).isEqualTo(19L + 2000L + 13000L);
        assertThat(result.usage().outputTokens()).isEqualTo(140L);
        assertThat(result.usage().costUsd()).isEqualByComparingTo(new BigDecimal("0.0421375"));
    }

    @Test
    void turnsTheConversationIntoLogLines() throws IOException {
        feed("success.jsonl");

        assertThat(log).containsExactly(
                "system: Claude Code 2.1.311 started (session 5b1f6a52-1111-4a3e-9c1a-0d6c2f7f0a01, model claude-sonnet-4-5-20250929, permission mode acceptEdits)",
                "text: I will check what is in the folder.",
                "tool_use: Bash ls -la\n{\"command\":\"ls -la\",\"description\":\"List files\"}",
                "tool_result: total 0\ndrwxr-xr-x 2 me me 64 Nov  8 10:00 .",
                "tool_use: Write /work/1-abcd1234/README.md\n{\"file_path\":\"/work/1-abcd1234/README.md\",\"content\":\"# Demo\\n\"}",
                "tool_result: File created successfully at: /work/1-abcd1234/README.md",
                "tool_result: Error: Permission to use Bash has been denied.",
                "text: I wrote README.md with a short title.");
    }

    /** Recorded from Claude Code 2.1.311 (haiku), with paths and account details removed. */
    @Test
    void readsARealRecording() throws IOException {
        feed("real-2.1.311-haiku.jsonl");

        StreamJsonParser.Result result = parser.result();
        assertThat(result.error()).isFalse();
        assertThat(result.output()).isEqualTo("pong");
        assertThat(result.sessionId()).isEqualTo("b5c46591-7c8e-48e4-8940-3d57b884b8a0");
        assertThat(result.usage().inputTokens()).isEqualTo(4L + 21275L + 51371L);
        assertThat(result.usage().outputTokens()).isEqualTo(268L);
        assertThat(result.usage().costUsd()).isEqualByComparingTo(new BigDecimal("0.0049031100000000005"));
        // thinking blocks and thinking_tokens / rate_limit_event messages are not shown
        assertThat(log).containsExactly(
                "system: Claude Code 2.1.311 started (session b5c46591-7c8e-48e4-8940-3d57b884b8a0, model claude-haiku-5-5, permission mode acceptEdits)",
                "tool_use: Glob *\n{\"pattern\":\"*\",\"path\":\"/work/demo\"}",
                "tool_result: real.err\nreal.jsonl",
                "text: pong");
    }

    @Test
    void anErrorResultIsAnErrorAndSkipsLinesThatAreNotJson() throws IOException {
        feed("error.jsonl");

        assertThat(parser.result().error()).isTrue();
        assertThat(parser.result().output()).isEqualTo("API Error: 529 Overloaded");
        assertThat(log).last().isEqualTo("error: API Error: 529 Overloaded");
        assertThat(parser.messages()).isEqualTo(3);
    }

    @Test
    void aPromptTooLongIsAnErrorEvenWhenTheCliCallsItASuccess() throws IOException {
        feed("prompt-too-long.jsonl");

        assertThat(parser.result().error()).isTrue();
        assertThat(parser.result().terminalReason()).isEqualTo("prompt_too_long");
        assertThat(parser.result().output()).contains("context window").contains("Prompt is too long");
    }

    @Test
    void withoutAResultThereIsNoResult() {
        parser.accept("{\"type\":\"system\",\"subtype\":\"init\",\"session_id\":\"s-1\"}");
        parser.accept("{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"working\"}]}}");

        assertThat(parser.result()).isNull();
        assertThat(parser.sessionId()).isEqualTo("s-1");
    }

    @Test
    void cutsVeryLongToolResults() {
        String big = "x".repeat(StreamJsonParser.MAX_TOOL_RESULT + 50);
        parser.accept("{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"tool_result\",\"content\":\"" + big + "\"}]}}");

        assertThat(log.getFirst()).hasSize("tool_result: ".length() + StreamJsonParser.MAX_TOOL_RESULT + "\n… (cut)".length());
    }

    @Test
    void showsPathsInsideTheWorkFolderRelativeToIt() {
        assertThat(StreamJsonParser.relative("/work/3-ab/docs/HELLO.md", "/work/3-ab")).isEqualTo("docs/HELLO.md");
        assertThat(StreamJsonParser.relative("/work/3-abc/x.md", "/work/3-ab")).isEqualTo("/work/3-abc/x.md");
        assertThat(StreamJsonParser.relative("/elsewhere/x.md", "/work/3-ab")).isEqualTo("/elsewhere/x.md");
        assertThat(StreamJsonParser.relative("/work/3-ab", "/work/3-ab")).isEqualTo("/work/3-ab");
        assertThat(StreamJsonParser.relative("*.md", null)).isEqualTo("*.md");
        if (java.io.File.separatorChar == '\\') {
            assertThat(StreamJsonParser.relative("c:\\Work\\3-ab\\HELLO.md", "C:\\work\\3-ab")).isEqualTo("HELLO.md");
            assertThat(StreamJsonParser.relative("C:/work/3-ab/a/b.md", "C:\\work\\3-ab")).isEqualTo("a/b.md");
        }
    }

    @Test
    void aToolCallInTheWorkFolderShowsTheFileName() {
        Path folder = Path.of("/work/3-ab").toAbsolutePath();
        StreamJsonParser inFolder = new StreamJsonParser((type, content) -> log.add(content), folder);
        String message = StreamJsonParser.JSON.writeValueAsString(Map.of("type", "assistant", "message", Map.of("content",
                List.of(Map.of("type", "tool_use", "name", "Write", "input", Map.of("file_path", folder.resolve("HELLO.md").toString()))))));

        inFolder.accept(message);

        assertThat(log.getFirst().lines().findFirst()).hasValue("Write HELLO.md");
        assertThat(log.getFirst()).contains("\"file_path\":");
    }

    @Test
    void unknownKindsAndBrokenLinesAreSkipped() {
        parser.accept("");
        parser.accept("{broken");
        parser.accept("{\"type\":\"stream_event\",\"event\":{}}");
        parser.accept("{\"type\":\"system\",\"subtype\":\"hook_response\"}");

        assertThat(log).isEmpty();
        assertThat(parser.result()).isNull();
    }
}
