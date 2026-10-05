package org.minecraftsmp.dynamicshop.web;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

/** Installs only the known bundled web assets. Backups live outside the served web directory. */
public final class WebFileManager {
    public static final List<String> FILES =
            List.of(
                    "index.html",
                    "style.css",
                    "app.js",
                    "dashboard.js",
                    "items.html",
                    "items.js",
                    "admin.html",
                    "web-update.html");
    private final Path data;
    private final Function<String, InputStream> resources;

    public WebFileManager(Path data, Function<String, InputStream> resources) {
        this.data = data;
        this.resources = resources;
    }

    public synchronized Map<String, Object> status() throws IOException {
        List<String> outdated = new ArrayList<>();
        Map<String, String> revisions = new TreeMap<>();
        for (String name : FILES) {
            try (InputStream in = resources.apply("web/" + name)) {
                if (in == null) throw new IOException("Bundled web file missing: " + name);
                String expected = contentRevision(in.readAllBytes());
                revisions.put(name, expected);
                Path target = data.resolve("web").resolve(name);
                if (!Files.isRegularFile(target)
                        || !matchesRevision(expected, Files.readAllBytes(target)))
                    outdated.add(name);
            }
        }
        return Map.of(
                "version",
                bundleRevision(revisions),
                "needsUpdate",
                !outdated.isEmpty(),
                "outdatedFiles",
                outdated);
    }

    public synchronized void installMissing() throws IOException {
        Path web = data.resolve("web");
        Files.createDirectories(web);
        for (String name : FILES)
            if (!Files.exists(web.resolve(name)))
                try (InputStream in = resources.apply("web/" + name)) {
                    if (in == null) throw new IOException("Bundled web file missing: " + name);
                    Files.copy(in, web.resolve(name));
                }
    }

    public synchronized Map<String, Object> update() throws IOException {
        Path web = data.resolve("web");
        Files.createDirectories(web);
        // Load every resource before touching any installed file.
        Map<String, byte[]> bundled = new LinkedHashMap<>(), previous = new LinkedHashMap<>();
        for (String name : FILES) {
            try (InputStream in = resources.apply("web/" + name)) {
                if (in == null) throw new IOException("Bundled web file missing: " + name);
                bundled.put(name, in.readAllBytes());
            }
            Path target = web.resolve(name);
            if (Files.exists(target)) previous.put(name, Files.readAllBytes(target));
        }
        Path backup =
                data.resolve("web-backups")
                        .resolve(
                                LocalDateTime.now()
                                                .format(
                                                        DateTimeFormatter.ofPattern(
                                                                "yyyyMMdd-HHmmss"))
                                        + "-"
                                        + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectories(backup);
        for (var entry : previous.entrySet())
            Files.write(backup.resolve(entry.getKey()), entry.getValue());
        List<String> changed = new ArrayList<>();
        try {
            for (var entry : bundled.entrySet()) {
                replace(web.resolve(entry.getKey()), entry.getValue());
                changed.add(entry.getKey());
            }
        } catch (IOException error) {
            for (String name : changed)
                try {
                    if (previous.containsKey(name)) replace(web.resolve(name), previous.get(name));
                    else Files.deleteIfExists(web.resolve(name));
                } catch (IOException rollback) {
                    error.addSuppressed(rollback);
                }
            throw error;
        }
        Map<String, String> revisions = new TreeMap<>();
        bundled.forEach((name, bytes) -> revisions.put(name, contentRevision(bytes)));
        return Map.of(
                "success",
                true,
                "backup",
                data.relativize(backup).toString(),
                "files",
                FILES.size(),
                "version",
                bundleRevision(revisions));
    }

    private void replace(Path target, byte[] bytes) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), "web-update-", ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static String version(byte[] bytes) {
        String line = new String(bytes, StandardCharsets.UTF_8).split("\\R", 2)[0];
        int start = line.indexOf("DS-WEB-VERSION:");
        return start < 0
                ? "unknown"
                : line.substring(start + 15).replace("-->", "").replace("*/", "").trim();
    }

    private static boolean matchesRevision(String expected, byte[] installed) {
        // A matching content marker identifies the installed bundle even after local edits.
        // Old plugin-version markers fall back to comparing the actual file contents.
        return expected.equals(version(installed)) || expected.equals(contentRevision(installed));
    }

    static String contentRevision(byte[] bytes) {
        String text =
                new String(bytes, StandardCharsets.UTF_8)
                        .replaceFirst("^\\uFEFF", "")
                        .replace("\r\n", "\n")
                        .replace('\r', '\n');
        text = text.replaceFirst("^(?://|/\\*|<!--)\\s*DS-WEB-VERSION:[^\\n]*(?:\\n|$)", "");
        return "sha256-" + digest(text);
    }

    private static String bundleRevision(Map<String, String> revisions) {
        StringBuilder contents = new StringBuilder();
        revisions.forEach(
                (name, revision) ->
                        contents.append(name).append(':').append(revision).append('\n'));
        return "web-" + digest(contents.toString()).substring(0, 12);
    }

    private static String digest(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
