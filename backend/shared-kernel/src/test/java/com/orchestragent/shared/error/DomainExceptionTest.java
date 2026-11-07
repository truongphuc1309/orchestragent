package com.orchestragent.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    @Test
    void subtypesHaveDefaultCodes() {
        assertThat(new NotFoundException("x").code()).isEqualTo("not_found");
        assertThat(new ForbiddenException("x").code()).isEqualTo("forbidden");
        assertThat(new ConflictException("x").code()).isEqualTo("conflict");
        assertThat(new ValidationException("x").code()).isEqualTo("validation");
    }

    @Test
    void customCodeAndMessageAreKept() {
        DomainException e = new ConflictException("workspace.slug_taken", "Slug is already used");

        assertThat(e.code()).isEqualTo("workspace.slug_taken");
        assertThat(e).hasMessage("Slug is already used");
    }
}
