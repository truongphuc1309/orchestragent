package com.orchestragent.daemon.cli.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.orchestragent.daemon.cli.UserError;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigStoreTest {

    @TempDir
    Path home;

    private ConfigStore store() {
        return new ConfigStore(home.resolve(".orchestragent"));
    }

    @Test
    void isEmptyBeforeTheFirstLogin() {
        assertThat(store().load()).isEmpty();
    }

    @Test
    void savesAndLoadsTheLogin() {
        DaemonConfig saved = store().saveLogin(URI.create("http://localhost:8080"), "oat_abc");

        DaemonConfig loaded = store().load().orElseThrow();

        assertThat(loaded).isEqualTo(saved);
        assertThat(loaded.daemonId()).isNotBlank();
        assertThat(loaded.toString()).doesNotContain("oat_abc");
    }

    @Test
    void keepsTheDaemonIdAndUnknownSettingsWhenLoggingInAgain() throws Exception {
        String firstId = store().saveLogin(URI.create("http://localhost:8080"), "oat_one").daemonId();
        Path file = store().file();
        String withExtra = Files.readString(file).replaceFirst("\\{", "{\"claude\":{\"permissionMode\":\"plan\"},");
        Files.writeString(file, withExtra);

        DaemonConfig second = store().saveLogin(URI.create("https://example.org"), "oat_two");

        assertThat(second.daemonId()).isEqualTo(firstId);
        assertThat(second.token()).isEqualTo("oat_two");
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("\"permissionMode\"").contains("plan");
    }

    @Test
    void onlyTheOwnerCanReadTheFile() throws Exception {
        store().saveLogin(URI.create("http://localhost:8080"), "oat_abc");
        Path file = store().file();

        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            assertThat(PosixFilePermissions.toString(posix.readAttributes().permissions())).isEqualTo("rw-------");
        } else {
            AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
            List<AclEntry> entries = acl.getAcl();
            assertThat(entries).singleElement().satisfies(entry -> assertThat(entry.principal()).isEqualTo(acl.getOwner()));
        }
        try (var leftovers = Files.list(store().home())) {
            assertThat(leftovers.map(path -> path.getFileName().toString())).containsExactly("config.json");
        }
    }

    @Test
    void anIncompleteOrBrokenFileAsksForANewLogin() throws Exception {
        Files.createDirectories(store().home());
        Files.writeString(store().file(), "{\"server\":\"http://x\"}");
        assertThatThrownBy(() -> store().load()).isInstanceOf(UserError.class).hasMessageContaining("login again");

        Files.writeString(store().file(), "not json");
        assertThatThrownBy(() -> store().load()).isInstanceOf(UserError.class).hasMessageContaining("not valid JSON");
    }

    @Test
    void acceptsOnlyHttpServerUrls() {
        assertThat(ConfigStore.parseServer(" http://localhost:8080/ ")).isEqualTo(URI.create("http://localhost:8080"));
        assertThat(ConfigStore.parseServer("https://example.org/base")).isEqualTo(URI.create("https://example.org/base"));
        for (String bad : List.of("", "localhost:8080", "ftp://example.org", "http://", "http://u:p@example.org", "http://x?a=1")) {
            assertThatThrownBy(() -> ConfigStore.parseServer(bad)).as(bad).isInstanceOf(UserError.class);
        }
    }

    @Test
    void acceptsOnlyApiTokens() {
        assertThat(ConfigStore.validToken(" oat_abc ")).isEqualTo("oat_abc");
        for (String bad : List.of("", "oat_", "abc", "Bearer oat_abc", "oat_a b")) {
            assertThatThrownBy(() -> ConfigStore.validToken(bad)).as(bad).isInstanceOf(UserError.class);
        }
        // A wrong token is never echoed back.
        assertThatThrownBy(() -> ConfigStore.validToken("oat_secret value"))
                .isInstanceOf(UserError.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("secret"));
    }
}
