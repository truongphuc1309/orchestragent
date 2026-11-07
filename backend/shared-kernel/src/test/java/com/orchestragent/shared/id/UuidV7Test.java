package com.orchestragent.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void generatesVersion7Uuids() {
        UUID id = UuidV7.next();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void generatesUniqueValues() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(UuidV7.next());
        }

        assertThat(ids).hasSize(10_000);
    }

    @Test
    void generatedValuesAreTimeOrderedAcrossMilliseconds() throws InterruptedException {
        UUID first = UuidV7.next();
        Thread.sleep(5);
        UUID second = UuidV7.next();

        assertThat(first.toString().compareTo(second.toString())).isNegative();
    }
}
