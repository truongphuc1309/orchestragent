package com.orchestragent.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.shared.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Display name, bio and avatar files: the rules of the profile a user edits. */
class ProfileValuesTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 16};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 36, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    @Test
    void displayNameIsTrimmed() {
        assertThat(DisplayName.of("  Ada Lovelace  ").value()).isEqualTo("Ada Lovelace");
    }

    @Test
    void displayNameMayHaveHundredCharacters() {
        assertThat(DisplayName.of("a".repeat(100)).value()).hasSize(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t"})
    void displayNameIsRequired(String raw) {
        assertCode(() -> DisplayName.of(raw), "profile.name_required");
    }

    @Test
    void displayNameIsRequiredWhenNull() {
        assertCode(() -> DisplayName.of(null), "profile.name_required");
    }

    @Test
    void displayNameHasAtMostHundredCharacters() {
        assertCode(() -> DisplayName.of("a".repeat(101)), "profile.name_too_long");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Ada\nLovelace", "Ada\u0000", "Ada\u001b[31m"})
    void displayNameStaysOnOneLine(String raw) {
        assertCode(() -> DisplayName.of(raw), "profile.name_invalid");
    }

    @Test
    void bioMayBeEmpty() {
        assertThat(Bio.of(null).value()).isEmpty();
        assertThat(Bio.of("   ").value()).isEmpty();
    }

    @Test
    void bioKeepsLineBreaksAndNormalizesWindowsOnes() {
        assertThat(Bio.of(" Line one\r\nLine two\n\tindented ").value()).isEqualTo("Line one\nLine two\n\tindented");
    }

    @Test
    void bioHasAtMostFiveHundredCharacters() {
        assertThat(Bio.of("b".repeat(500)).value()).hasSize(500);
        assertCode(() -> Bio.of("b".repeat(501)), "profile.bio_too_long");
    }

    @Test
    void bioRefusesOtherControlCharacters() {
        assertCode(() -> Bio.of("bell\u0007"), "profile.bio_invalid");
    }

    @Test
    void detectsPngJpegAndWebp() {
        assertThat(ImageType.detect(PNG)).isEqualTo(ImageType.PNG);
        assertThat(ImageType.detect(JPEG)).isEqualTo(ImageType.JPEG);
        assertThat(ImageType.detect(WEBP)).isEqualTo(ImageType.WEBP);
        assertThat(ImageType.WEBP.mediaType()).isEqualTo("image/webp");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>",
        "GIF89a........",
        "plain text pretending to be a .png",
        "RIFF....WAVEfmt ",
        "\u0089PN"
    })
    void refusesOtherFiles(String content) {
        assertCode(() -> ImageType.detect(content.getBytes(StandardCharsets.ISO_8859_1)), "avatar.unsupported_type");
    }

    @Test
    void refusesEmptyFiles() {
        assertCode(() -> ImageType.detect(new byte[0]), "avatar.empty");
        assertCode(() -> ImageType.detect(null), "avatar.empty");
    }

    @Test
    void avatarUploadAllowsTwoMegabytes() {
        Avatar avatar = Avatar.forUpload(Arrays.copyOf(PNG, Avatar.MAX_BYTES));

        assertThat(avatar.type()).isEqualTo(ImageType.PNG);
        assertThat(Avatar.forUpload(PNG).id()).isNotEqualTo(avatar.id());
    }

    @Test
    void avatarUploadRefusesMoreThanTwoMegabytes() {
        assertCode(() -> Avatar.forUpload(Arrays.copyOf(PNG, Avatar.MAX_BYTES + 1)), "avatar.too_large");
    }

    @Test
    void newUserHasEmptyBioAndNoAvatar() {
        User user = User.register(Email.of("ada@example.com"), java.time.Instant.EPOCH);

        assertThat(user.name()).isEqualTo("ada");
        assertThat(user.bio()).isEmpty();
        assertThat(user.avatar()).isEmpty();
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).code())
                .isEqualTo(code);
    }
}
