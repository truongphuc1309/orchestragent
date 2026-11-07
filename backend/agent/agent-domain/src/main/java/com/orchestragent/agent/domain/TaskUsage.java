package com.orchestragent.agent.domain;

import com.orchestragent.shared.error.ValidationException;
import java.math.BigDecimal;

/** What a run cost. Every part is optional: a tool may report only some of them. */
public record TaskUsage(Long inputTokens, Long outputTokens, BigDecimal costUsd) {

    public static final TaskUsage NONE = new TaskUsage(null, null, null);

    public TaskUsage {
        if ((inputTokens != null && inputTokens < 0)
                || (outputTokens != null && outputTokens < 0)
                || (costUsd != null && costUsd.signum() < 0)) {
            throw new ValidationException("task.invalid_usage", "Usage numbers cannot be negative");
        }
    }
}
