package com.occ.housecleaning.reporting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves a setting, highest priority first:
 * system property {@code <key>} > environment variable > {@code secured.<env>.json}
 * > {@code environment.<env>.json} (both in {@code src/test/resources/configs}) > default.
 *
 * <p>{@code key} is the dotted JSON path, for example {@code mail.host}. {@code <env>} comes from {@code -Denv} or
 * {@code HC_ENV} (default {@code minikube}). The config directory is {@code -Dconfig.dir} / {@code CONFIG_DIR}, or
 * the config directory found walking up from the working directory.
 */
final class Settings {

    private static final Path CONFIG_PATH = Path.of("src", "test", "resources", "configs");
    private static volatile Map<String, String> files;

    private Settings() {
    }

    static String get(String key, String env, String fallback) {
        String v = blankToNull(System.getProperty(key));
        if (v == null) v = blankToNull(System.getenv(env));
        if (v == null) v = file().get(key);
        return v == null ? fallback : v.trim();
    }

    /** For tests: forget the cached file contents. */
    static void reset() {
        files = null;
    }

    private static Map<String, String> file() {
        Map<String, String> f = files;
        if (f == null) files = f = load();
        return f;
    }

    private static Map<String, String> load() {
        Map<String, String> out = new HashMap<>();
        try {
            String env = firstNonBlank(System.getProperty("env"), System.getenv("HC_ENV"), "minikube");
            Path dir = configDir();
            if (dir == null) return out;
            for (String name : new String[]{"environment." + env + ".json", "secured." + env + ".json"}) {
                Path p = dir.resolve(name);
                if (Files.isRegularFile(p)) flatten("", new ObjectMapper().readTree(p.toFile()), out);   // secured last: wins
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[RunReporter] ignoring unreadable config: " + e);
        }
        return out;
    }

    private static Path configDir() {
        String explicit = firstNonBlank(System.getProperty("config.dir"), System.getenv("CONFIG_DIR"), null);
        if (explicit != null) return Path.of(explicit);
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            for (Path candidate : new Path[]{p.resolve(CONFIG_PATH), p.resolve("house-cleaning-integration-tests").resolve(CONFIG_PATH)}) {
                if (Files.isDirectory(candidate)) return candidate;
            }
        }
        return null;
    }

    private static void flatten(String prefix, JsonNode node, Map<String, String> out) {
        if (node.isObject()) {
            node.fields().forEachRemaining(e -> flatten(prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey(), e.getValue(), out));
        } else if (!node.isNull()) {
            out.put(prefix, node.asText());
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    private static String firstNonBlank(String a, String b, String c) {
        return blankToNull(a) != null ? a : blankToNull(b) != null ? b : c;
    }
}
