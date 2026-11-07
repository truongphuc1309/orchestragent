package com.orchestragent.shared.id;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

/**
 * Generates time-ordered UUIDv7 values in the application, so inserts stay index-friendly and no database
 * sequence is needed.
 */
public final class UuidV7 {

    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private UuidV7() {
    }

    public static UUID next() {
        return GENERATOR.generate();
    }
}
