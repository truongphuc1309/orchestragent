package com.orchestragent.daemon.protocol;

import java.util.List;

public record RegisterResponse(List<RegisteredRuntime> runtimes) {
}
