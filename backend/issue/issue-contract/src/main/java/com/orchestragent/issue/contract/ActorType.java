package com.orchestragent.issue.contract;

/** Who did something on an issue. Agents appear from step 7; {@code SYSTEM} is for automatic comments. */
public enum ActorType {
    MEMBER,
    AGENT,
    SYSTEM
}
