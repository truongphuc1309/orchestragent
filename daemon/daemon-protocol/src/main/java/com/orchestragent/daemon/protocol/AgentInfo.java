package com.orchestragent.daemon.protocol;

import java.util.UUID;

/** Who the agent is: its standing instructions (markdown) and the model to ask the tool for (null: default). */
public record AgentInfo(UUID id, String name, String instructions, String model) {
}
