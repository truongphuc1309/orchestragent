package com.orchestragent.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.shared.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @Test
    void normalizesCaseAndWhitespace() {
        assertThat(Email.of("  Ada.Lovelace@Example.COM ").value()).isEqualTo("ada.lovelace@example.com");
    }

    @Test
    void differentSpellingsOfTheSameAddressAreEqual() {
        assertThat(Email.of("ADA@example.com")).isEqualTo(Email.of(" ada@EXAMPLE.com"));
    }

    @Test
    void localPartIsTheNamePartBeforeTheAt() {
        assertThat(Email.of("ada@example.com").localPart()).isEqualTo("ada");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "no-at-sign", "a@b", "a b@example.com", "@example.com", "a@@example.com"})
    void rejectsInvalidAddresses(String raw) {
        assertThatThrownBy(() -> Email.of(raw)).isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> Email.of(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsTooLongAddress() {
        String tooLong = "a".repeat(250) + "@example.com";

        assertThatThrownBy(() -> Email.of(tooLong)).isInstanceOf(ValidationException.class);
    }
}
