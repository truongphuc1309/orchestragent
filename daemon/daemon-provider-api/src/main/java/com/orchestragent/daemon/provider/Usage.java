package com.orchestragent.daemon.provider;

import java.math.BigDecimal;

/** What a run cost, as far as the tool says. Every part may be null (unknown). */
public record Usage(Long inputTokens, Long outputTokens, BigDecimal costUsd) {

    public static final Usage NONE = new Usage(null, null, null);
}
