package com.orchestragent.daemon.provider.claude;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Finds a program the way a shell would, and says how to start it. On Windows a name is tried with each extension
 * of {@code PATHEXT} ({@code claude.exe}, then {@code claude.cmd} as npm installs it), and a {@code .cmd} or
 * {@code .bat} file is started through {@code cmd.exe /c}, because it is not a program of its own.
 */
final class Executables {

    private final String path;
    private final String pathExt;
    private final boolean windows;

    Executables(String path, String pathExt, boolean windows) {
        this.path = path == null ? "" : path;
        this.pathExt = pathExt == null || pathExt.isBlank() ? ".COM;.EXE;.BAT;.CMD" : pathExt;
        this.windows = windows;
    }

    static Executables fromEnvironment() {
        return new Executables(System.getenv("PATH"), System.getenv("PATHEXT"),
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"));
    }

    /**
     * The file for {@code nameOrPath}: a path (anything with a folder separator) must exist; a bare name is looked
     * up on {@code PATH}.
     */
    Optional<Path> find(String nameOrPath) {
        if (nameOrPath == null || nameOrPath.isBlank()) {
            return Optional.empty();
        }
        String name = nameOrPath.strip();
        try {
            if (name.contains("/") || name.contains("\\")) {
                return candidates(Path.of(name)).stream().filter(Executables::runnable).findFirst();
            }
            for (String folder : path.split(File.pathSeparator)) {
                if (folder.isBlank()) {
                    continue;
                }
                Optional<Path> found = candidates(Path.of(folder.strip().replace("\"", "")).resolve(name)).stream()
                        .filter(Executables::runnable)
                        .findFirst();
                if (found.isPresent()) {
                    return found;
                }
            }
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** How to start {@code program}. */
    Launcher launcherFor(Path program) {
        String file = program.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean viaCmd = windows && (file.endsWith(".cmd") || file.endsWith(".bat"));
        return new Launcher(List.of(program.toString()), viaCmd, program.toString());
    }

    /**
     * Builds the command line of a program. A {@code .cmd} file goes through {@code cmd.exe /d /s /c "<line>"}:
     * the line is quoted by hand, because {@code cmd.exe} has its own quoting rules ({@code /s} makes it drop only
     * the outer pair of quotes). Arguments with {@code "} or {@code %} cannot be passed safely that way and are
     * refused.
     */
    record Launcher(List<String> prefix, boolean viaCmd, String location) {

        Launcher {
            prefix = List.copyOf(prefix);
        }

        /** Starts a fixed command line, such as {@code java -cp ... FakeClaude} in tests. */
        static Launcher of(List<String> prefix) {
            return new Launcher(prefix, false, prefix.getLast());
        }

        List<String> command(List<String> args) {
            List<String> all = new ArrayList<>(prefix);
            all.addAll(args);
            if (!viaCmd) {
                return all;
            }
            StringBuilder line = new StringBuilder("\"");
            for (String arg : all) {
                if (line.length() > 1) {
                    line.append(' ');
                }
                line.append(quoteForCmd(arg));
            }
            return List.of("cmd.exe", "/d", "/s", "/c", line.append('"').toString());
        }

        private static String quoteForCmd(String arg) {
            if (arg.indexOf('"') >= 0 || arg.indexOf('%') >= 0) {
                throw new IllegalArgumentException("Cannot pass " + arg + " to a .cmd program: it contains \" or %");
            }
            boolean plain = !arg.isEmpty() && arg.chars().noneMatch(c -> " \t&|<>^()".indexOf(c) >= 0);
            return plain ? arg : "\"" + arg + "\"";
        }
    }

    private List<Path> candidates(Path base) {
        List<Path> candidates = new ArrayList<>();
        if (windows) {
            String file = base.getFileName().toString().toLowerCase(Locale.ROOT);
            boolean hasExtension = false;
            for (String ext : pathExt.split(";")) {
                if (!ext.isBlank() && file.endsWith(ext.strip().toLowerCase(Locale.ROOT))) {
                    hasExtension = true;
                }
            }
            if (hasExtension) {
                candidates.add(base);
            }
            for (String ext : pathExt.split(";")) {
                if (!ext.isBlank()) {
                    candidates.add(base.resolveSibling(base.getFileName() + ext.strip().toLowerCase(Locale.ROOT)));
                }
            }
        } else {
            candidates.add(base);
        }
        return candidates;
    }

    private static boolean runnable(Path file) {
        return Files.isRegularFile(file) && Files.isExecutable(file);
    }
}
