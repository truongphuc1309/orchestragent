package com.orchestragent.daemon.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RunResultTest {

    @Test
    void aSuccessHasNoErrorAndNoReason() {
        RunResult result = RunResult.succeeded("done", "s1", null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.failureReason()).isNull();
        assertThat(result.usage()).isEqualTo(Usage.NONE);
    }

    @Test
    void aFailureWithoutReasonIsAnAgentError() {
        RunResult result = RunResult.failed("it broke", " ", null, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failureReason()).isEqualTo(RunResult.AGENT_ERROR);
        assertThat(result.output()).isEmpty();
    }

    @Test
    void aFailureWithoutMessageStillExplainsItself() {
        RunResult result = RunResult.failed(null, RunResult.TIMEOUT, null, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).isNotBlank();
        assertThat(result.failureReason()).isEqualTo(RunResult.TIMEOUT);
    }

    @Test
    void aRequestNeedsAPositiveTimeoutAndDropsBlankOptions() {
        RunRequest request = new RunRequest("p", Path.of("."), " ", "", Duration.ofMinutes(1));

        assertThat(request.model()).isNull();
        assertThat(request.priorSessionId()).isNull();
        assertThatThrownBy(() -> new RunRequest("p", Path.of("."), null, null, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
