package com.orchestragent.daemon.cli.config;

import com.orchestragent.daemon.cli.UserError;
import com.orchestragent.daemon.cli.client.DaemonClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The daemon's home folder ({@code ~/.orchestragent}, or {@code ORCHESTRAGENT_HOME}) and its {@code config.json}.
 * The file holds an API token, so only its owner may read it: {@code rw-------} on POSIX systems, an access list
 * with only the owner on Windows. Settings the daemon does not know (added by a newer version) are kept on save.
 */
public final class ConfigStore {

    public static final String HOME_ENV = "ORCHESTRAGENT_HOME";
    public static final String TOKEN_PREFIX = "oat_";

    private static final String FILE_NAME = "config.json";

    private final Path home;

    public ConfigStore(Path home) {
        this.home = home.toAbsolutePath().normalize();
    }

    /** {@code ORCHESTRAGENT_HOME} if set, otherwise {@code .orchestragent} in the user's home folder. */
    public static ConfigStore fromEnvironment() {
        String override = System.getenv(HOME_ENV);
        Path home = override != null && !override.isBlank()
                ? Path.of(override)
                : Path.of(System.getProperty("user.home"), ".orchestragent");
        return new ConfigStore(home);
    }

    public Path home() {
        return home;
    }

    public Path file() {
        return home.resolve(FILE_NAME);
    }

    /** Where task folders are made: {@code <home>/workspaces}. */
    public Path workspacesRoot() {
        return home.resolve("workspaces");
    }

    /** The saved configuration, or empty before the first {@code login}. */
    public Optional<DaemonConfig> load() {
        Path file = file();
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        JsonNode tree = readTree(file);
        String server = text(tree, "server");
        String token = text(tree, "token");
        String daemonId = text(tree, "daemonId");
        if (server == null || token == null || daemonId == null) {
            throw new UserError(file + " is incomplete; run login again");
        }
        return Optional.of(new DaemonConfig(parseServer(server), token, daemonId));
    }

    /**
     * Saves the server and token. The daemon ID of an earlier login is kept; the first login makes one.
     *
     * @return what was saved
     */
    public DaemonConfig saveLogin(URI server, String token) {
        ObjectNode tree = Files.exists(file()) ? asObject(readTree(file())) : DaemonClient.JSON.createObjectNode();
        String daemonId = text(tree, "daemonId");
        if (daemonId == null) {
            daemonId = UUID.randomUUID().toString();
        }
        tree.put("server", server.toString());
        tree.put("token", token);
        tree.put("daemonId", daemonId);
        write(DaemonClient.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(tree));
        return new DaemonConfig(server, token, daemonId);
    }

    // ---- validation --------------------------------------------------------------------------------------

    /** An {@code http} or {@code https} URL with a host, without a trailing slash. */
    public static URI parseServer(String raw) {
        String text = raw == null ? "" : raw.strip();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null
                    || uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
                throw new UserError("The server must be an http or https URL such as http://localhost:8080, without user name, query or fragment");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new UserError("The server must be an http or https URL such as http://localhost:8080, without user name, query or fragment");
        }
    }

    /** An API token as the web app shows it once: {@code oat_} and more. The value itself is never echoed. */
    public static String validToken(String raw) {
        String token = raw == null ? "" : raw.strip();
        if (!token.startsWith(TOKEN_PREFIX) || token.length() <= TOKEN_PREFIX.length() || token.chars().anyMatch(Character::isWhitespace)) {
            throw new UserError("The token must be an API token starting with " + TOKEN_PREFIX
                    + " (create one under Settings > API tokens in the web app)");
        }
        return token;
    }

    // ---- files -------------------------------------------------------------------------------------------

    private static JsonNode readTree(Path file) {
        try {
            return DaemonClient.JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        } catch (JacksonException e) {
            throw new UserError(file + " is not valid JSON; fix it or delete it and run login again");
        }
    }

    private ObjectNode asObject(JsonNode tree) {
        if (tree instanceof ObjectNode object) {
            return object;
        }
        throw new UserError(file() + " must hold a JSON object; fix it or delete it and run login again");
    }

    private static String text(JsonNode tree, String field) {
        JsonNode value = tree.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            return null;
        }
        return value.asString();
    }

    /** Writes the file through a private temporary file, so the token is never readable by others, even briefly. */
    private void write(String content) {
        try {
            Files.createDirectories(home);
            restrict(home, true);
            Path temporary = Files.createTempFile(home, "config", ".tmp");
            try {
                restrict(temporary, false);
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
                Files.move(temporary, file(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            restrict(file(), false);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file(), e);
        }
    }

    /** Leaves only the owner able to use the file or folder. */
    static void restrict(Path path, boolean directory) throws IOException {
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) {
            posix.setPermissions(PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------"));
            return;
        }
        AclFileAttributeView acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (acl != null) {
            AclEntry.Builder owner = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if (directory) {
                owner.setFlags(EnumSet.of(
                        java.nio.file.attribute.AclEntryFlag.FILE_INHERIT, java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT));
            }
            acl.setAcl(List.of(owner.build()));
        }
    }
}
