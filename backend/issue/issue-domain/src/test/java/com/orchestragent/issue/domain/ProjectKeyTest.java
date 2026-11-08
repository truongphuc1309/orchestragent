package com.orchestragent.issue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.shared.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectKeyTest {

    @ParameterizedTest
    @ValueSource(strings = {"WE", "WEB", "API2", "A1", "ABCDEFGHIJ", "Q9Z"})
    void acceptsTwoToTenUpperCaseLettersAndDigitsStartingWithALetter(String key) {
        assertThat(new ProjectKey(key).value()).isEqualTo(key);
    }

    @Test
    void parsingTrimsAndUpperCases() {
        assertThat(ProjectKey.parse("  web  ").value()).isEqualTo("WEB");
        assertThat(ProjectKey.parse("Api2").value()).isEqualTo("API2");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "W", "ABCDEFGHIJK", "1WEB", "WE B", "WEB-1", "WÉB", "WEB_", "-WEB"})
    void rejectsEverythingElse(String raw) {
        assertThatThrownBy(() -> ProjectKey.parse(raw))
                .isInstanceOf(ValidationException.class)
                .hasFieldOrPropertyWithValue("code", "project.invalid_key");
    }

    @Test
    void theConstructorDoesNotNormalize() {
        assertThatThrownBy(() -> new ProjectKey("web")).isInstanceOf(ValidationException.class);
        assertThat(ProjectKey.isValid("web")).isFalse();
        assertThat(ProjectKey.isValid("WEB")).isTrue();
    }

    @Test
    void issueKeysReadAndWriteTheUsualForm() {
        IssueKey key = IssueKey.parse("WEB-12").orElseThrow();

        assertThat(key.project()).isEqualTo(new ProjectKey("WEB"));
        assertThat(key.number()).isEqualTo(12);
        assertThat(key).hasToString("WEB-12");
        assertThat(new IssueKey(new ProjectKey("API2"), 3)).hasToString("API2-3");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "WEB", "WEB-", "-12", "web-12", "WEB-0", "WEB-012", "WEB-12 ", "W-1", "ABCDEFGHIJK-1",
            "WEB-1234567890", "WEB--1", "WEB-1-2", "1WEB-1"})
    void anIssueKeyThatIsNotWellFormedIsEmpty(String text) {
        assertThat(IssueKey.parse(text)).isEmpty();
    }
}
