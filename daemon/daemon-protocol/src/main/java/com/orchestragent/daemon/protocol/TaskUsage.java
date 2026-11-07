package com.orchestragent.daemon.protocol;

import java.math.BigDecimal;

/** What a run cost. Every part is optional. */
public record TaskUsage(Long inputTokens, Long outputTokens, BigDecimal costUsd) {
}
