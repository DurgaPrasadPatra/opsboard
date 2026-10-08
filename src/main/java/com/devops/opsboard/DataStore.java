package com.devops.opsboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * File-backed deploy journal. Data lives in APP_DATA_DIR (default /data).
 * Map a host folder there (-v /opt/tomcat/data:/data) and entries survive
 * redeploys and container recreation. Without a mount they die with the container.
 */
public final class DataStore {
    public record Entry(long time, String text) {}

    private static final Object LOCK = new Object();
    private static final int MAX_LEN = 500;
    private static final int MAX_ENTRIES = 100;
    private static final Path DIR = resolveDir();
    private static final Path FILE = DIR.resolve("journal.tsv");

    private DataStore() {}

    public static Path dir() { return DIR; }

    public static boolean writable() { return Files.isWritable(DIR); }

    private static Path resolveDir() {
        String[] candidates = { System.getenv("APP_DATA_DIR"), "/data" };
        for (String c : candidates) {
            if (c == null || c.isBlank()) continue;
            try {
                Path p = Paths.get(c);
                Files.createDirectories(p);
                if (Files.isWritable(p)) return p;
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        Path tmp = Paths.get(System.getProperty("java.io.tmpdir"), "opsboard-data");
        try { Files.createDirectories(tmp); } catch (IOException ignored) { }
        return tmp;
    }

    public static Entry add(String raw) throws IOException {
        String text = raw.replaceAll("[\\t\\r\\n]+", " ").trim();
        if (text.length() > MAX_LEN) text = text.substring(0, MAX_LEN);
        Entry e = new Entry(System.currentTimeMillis(), text);
        synchronized (LOCK) {
            Files.writeString(FILE, e.time() + "\t" + text + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        return e;
    }

    /** Newest first, capped at MAX_ENTRIES. */
    public static List<Entry> list() throws IOException {
        List<Entry> out = new ArrayList<>();
        synchronized (LOCK) {
            if (!Files.exists(FILE)) return out;
            List<String> lines = Files.readAllLines(FILE, StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0 && out.size() < MAX_ENTRIES; i--) {
                String[] p = lines.get(i).split("\t", 2);
                if (p.length != 2) continue;
                try {
                    out.add(new Entry(Long.parseLong(p[0]), p[1]));
                } catch (NumberFormatException ignored) {
                    // skip corrupt line
                }
            }
        }
        return out;
    }

    public static void clear() throws IOException {
        synchronized (LOCK) { Files.deleteIfExists(FILE); }
    }
}
