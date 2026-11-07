package com.orchestragent.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Acme Team                | acme-team",
        "  Acme   Team!!  | acme-team",
        "ACME_corp.2026           | acme-corp-2026",
        "Đội ngũ Việt Nam         | doi-ngu-viet-nam",
        "Café Münster            | cafe-munster",
        "---weird---             | weird",
    })
    void turnsNamesIntoUrlSafeSlugs(String name, String expected) {
        assertThat(Slug.fromName(name)).isEqualTo(expected);
    }

    @Test
    void namesWithoutLettersOrDigitsFallBack() {
        assertThat(Slug.fromName("!!!")).isEqualTo("workspace");
        assertThat(Slug.fromName("日本語")).isEqualTo("workspace");
    }

    @Test
    void longNamesAreCutWithoutATrailingDash() {
        String slug = Slug.fromName("a".repeat(39) + " b" + " c".repeat(10));

        assertThat(slug.length()).isLessThanOrEqualTo(Slug.MAX_LENGTH);
        assertThat(slug).doesNotEndWith("-");
    }

    @Test
    void candidatesAppendACounterAfterTheFirstAttempt() {
        assertThat(Slug.candidate("acme", 1)).isEqualTo("acme");
        assertThat(Slug.candidate("acme", 2)).isEqualTo("acme-2");
        assertThat(Slug.candidate("acme", 10)).isEqualTo("acme-10");
    }
}
