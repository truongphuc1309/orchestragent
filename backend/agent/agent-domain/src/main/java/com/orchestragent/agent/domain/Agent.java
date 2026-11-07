package com.orchestragent.agent.domain;

import com.orchestragent.shared.error.ConflictException;
import com.orchestragent.shared.error.ValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An AI teammate: a name, standing instructions and the runtime (a computer with an AI tool) that does its work.
 * Agents are archived, never deleted, so old issues and comments keep a name to show. An archived agent can
 * be restored but not edited or given work.
 */
public final class Agent {

    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_INSTRUCTIONS_LENGTH = 20_000;
    public static final int MAX_MODEL_LENGTH = 100;

    private final AgentId id;
    private final UUID workspaceId;
    private final UUID createdBy;
    private final Instant createdAt;
    private String name;
    private String instructions;
    private RuntimeId runtimeId;
    private String model;
    private Instant archivedAt;

    public Agent(
            AgentId id,
            UUID workspaceId,
            String name,
            String instructions,
            RuntimeId runtimeId,
            String model,
            UUID createdBy,
            Instant archivedAt,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.workspaceId = Objects.requireNonNull(workspaceId, "workspaceId");
        this.name = Objects.requireNonNull(name, "name");
        this.instructions = Objects.requireNonNull(instructions, "instructions");
        this.runtimeId = Objects.requireNonNull(runtimeId, "runtimeId");
        this.model = model;
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        this.archivedAt = archivedAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public static Agent create(
            UUID workspaceId,
            String name,
            String instructions,
            RuntimeId runtimeId,
            String model,
            UUID createdBy,
            Instant now) {
        return new Agent(
                AgentId.generate(),
                workspaceId,
                validName(name),
                validInstructions(instructions),
                runtimeId,
                validModel(model),
                createdBy,
                null,
                now);
    }

    public static String validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("agent.invalid_name", "Name must be 1 to " + MAX_NAME_LENGTH + " characters");
        }
        if (name.chars().anyMatch(Character::isISOControl)) {
            throw new ValidationException("agent.invalid_name", "Name must be a single line of text");
        }
        return name;
    }

    /** Instructions are markdown: they may be empty and span lines. */
    public static String validInstructions(String raw) {
        String instructions = raw == null ? "" : raw.strip();
        if (instructions.length() > MAX_INSTRUCTIONS_LENGTH) {
            throw new ValidationException(
                    "agent.invalid_instructions", "Instructions must be at most " + MAX_INSTRUCTIONS_LENGTH + " characters");
        }
        return instructions;
    }

    /** The model is optional: blank means "the tool's default" and is stored as null. */
    public static String validModel(String raw) {
        String model = raw == null ? "" : raw.trim();
        if (model.isEmpty()) {
            return null;
        }
        if (model.length() > MAX_MODEL_LENGTH || model.chars().anyMatch(Character::isISOControl)) {
            throw new ValidationException("agent.invalid_model", "Model must be at most " + MAX_MODEL_LENGTH + " characters");
        }
        return model;
    }

    public boolean rename(String newName) {
        requireActive();
        String valid = validName(newName);
        if (valid.equals(name)) {
            return false;
        }
        name = valid;
        return true;
    }

    public boolean changeInstructions(String newInstructions) {
        requireActive();
        String valid = validInstructions(newInstructions);
        if (valid.equals(instructions)) {
            return false;
        }
        instructions = valid;
        return true;
    }

    public boolean changeRuntime(RuntimeId newRuntime) {
        requireActive();
        Objects.requireNonNull(newRuntime, "newRuntime");
        if (newRuntime.equals(runtimeId)) {
            return false;
        }
        runtimeId = newRuntime;
        return true;
    }

    public boolean changeModel(String newModel) {
        requireActive();
        String valid = validModel(newModel);
        if (Objects.equals(valid, model)) {
            return false;
        }
        model = valid;
        return true;
    }

    /** Returns whether the agent was active before. */
    public boolean archive(Instant now) {
        if (archivedAt != null) {
            return false;
        }
        archivedAt = now;
        return true;
    }

    /** Returns whether the agent was archived before. */
    public boolean restore() {
        if (archivedAt == null) {
            return false;
        }
        archivedAt = null;
        return true;
    }

    /** @throws ConflictException when the agent is archived */
    public void requireActive() {
        if (archivedAt != null) {
            throw new ConflictException("agent.archived", "An archived agent cannot be changed. Restore it first.");
        }
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public AgentId id() {
        return id;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public String name() {
        return name;
    }

    public String instructions() {
        return instructions;
    }

    public RuntimeId runtimeId() {
        return runtimeId;
    }

    /** The model to ask the tool for, or null for the tool's default. */
    public String model() {
        return model;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public Instant archivedAt() {
        return archivedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
