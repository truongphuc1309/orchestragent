package com.orchestragent.daemon.protocol;

import java.util.List;

public record ClaimResponse(List<ClaimedTask> tasks) {
}
