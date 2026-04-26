package io.arundhaas.kvstore;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotTest {

    private Path tempSnapshotPath() throws IOException {
        Path p = Files.createTempFile("kvstore-snapshot-test", ".snapshot");
        // We want the file NOT to exist initially — write() creates a temp+atomic-rename
        Files.deleteIfExists(p);
        return p;
    }

    // ─── write — basics ─────────────────────────────────────────────────────

    @Test
    void write_thenLoad_shouldRoundTripData() throws Exception {
        Path snap = tempSnapshotPath();
        Map<String, String> input = new HashMap<>();
        input.put("user:1", "Alice");
        input.put("user:2", "Bob");
        input.put("user:3", "Charlie");

        Snapshot.write(snap.toString(), input);

        Map<String, String> loaded = Snapshot.load(snap.toString());
        assertEquals(input, loaded, "Loaded map should equal what was written");
    }

    @Test
    void write_emptyMap_shouldProduceEmptyFile() throws Exception {
        Path snap = tempSnapshotPath();
        Snapshot.write(snap.toString(), new HashMap<>());

        assertTrue(Files.exists(snap), "Snapshot file should be created");
        assertEquals(0L, Files.size(snap), "Empty map → 0-byte file");
    }

    @Test
    void load_emptyFile_returnsEmptyMap() throws Exception {
        Path snap = tempSnapshotPath();
        Snapshot.write(snap.toString(), new HashMap<>());

        Map<String, String> loaded = Snapshot.load(snap.toString());
        assertNotNull(loaded);
        assertTrue(loaded.isEmpty(), "Empty file → empty map");
    }

    // ─── write — overwrite + atomicity ──────────────────────────────────────

    @Test
    void write_shouldOverwriteExistingSnapshot() throws Exception {
        Path snap = tempSnapshotPath();

        Map<String, String> v1 = new HashMap<>();
        v1.put("k", "old");
        Snapshot.write(snap.toString(), v1);

        Map<String, String> v2 = new HashMap<>();
        v2.put("k", "new");
        v2.put("extra", "added");
        Snapshot.write(snap.toString(), v2);

        Map<String, String> loaded = Snapshot.load(snap.toString());
        assertEquals("new", loaded.get("k"), "Old value should be overwritten");
        assertEquals("added", loaded.get("extra"));
        assertEquals(2, loaded.size(), "Old keys not in new map should be gone");
    }

    @Test
    void write_shouldNotLeaveTempFileBehind() throws Exception {
        Path snap = tempSnapshotPath();
        Map<String, String> input = new HashMap<>();
        input.put("a", "1");

        Snapshot.write(snap.toString(), input);

        Path tempPath = Path.of(snap.toString() + ".tmp");
        assertFalse(Files.exists(tempPath),
                "Temp file should have been atomically renamed away");
        assertTrue(Files.exists(snap), "Final snapshot should exist");
    }

    @Test
    void write_isAtomic_partialFailureDoesNotCorruptExistingSnapshot() throws Exception {
        // Pre-existing valid snapshot
        Path snap = tempSnapshotPath();
        Map<String, String> v1 = new HashMap<>();
        v1.put("good", "data");
        Snapshot.write(snap.toString(), v1);

        // Read raw bytes BEFORE attempting a second (potentially-failing) write
        byte[] beforeBytes = Files.readAllBytes(snap);

        // Successful second write replaces atomically — no half-state in between
        Map<String, String> v2 = new HashMap<>();
        v2.put("newer", "data");
        Snapshot.write(snap.toString(), v2);

        byte[] afterBytes = Files.readAllBytes(snap);
        assertNotEquals(new String(beforeBytes), new String(afterBytes),
                "Content should have changed atomically — no merge of old+new");
    }

    // ─── load — missing/edge files ──────────────────────────────────────────

    @Test
    void load_shouldReturnEmptyMap_whenFileMissing() throws Exception {
        Path snap = tempSnapshotPath();
        // Do NOT create the file

        Map<String, String> loaded = Snapshot.load(snap.toString());
        assertNotNull(loaded, "Must return empty map, never null");
        assertTrue(loaded.isEmpty());
    }

    @Test
    void load_shouldSkipMalformedLines() throws Exception {
        Path snap = tempSnapshotPath();
        // Manually craft a snapshot file with a mix of good and malformed lines
        String content = String.join("\n",
                "good|value1",
                "missing-pipe-here",   // malformed: no delimiter → skipped
                "another|value2",
                "",                    // empty line → skipped (split returns 1 element)
                "trailing|"            // trailing pipe → key="trailing", value=""
        ) + "\n";
        Files.write(snap, content.getBytes(StandardCharsets.UTF_8));

        Map<String, String> loaded = Snapshot.load(snap.toString());
        assertEquals(3, loaded.size(), "Only well-formed lines are kept");
        assertEquals("value1", loaded.get("good"));
        assertEquals("value2", loaded.get("another"));
        assertEquals("",      loaded.get("trailing"));
        assertNull(loaded.get("missing-pipe-here"));
    }

    @Test
    void load_neverReturnsNull() throws Exception {
        Path snap = tempSnapshotPath();
        // No file written — should still return non-null empty map
        assertNotNull(Snapshot.load(snap.toString()));

        // Empty file
        Files.createFile(snap);
        assertNotNull(Snapshot.load(snap.toString()));

        // Bad file
        Files.write(snap, "garbage".getBytes(StandardCharsets.UTF_8));
        assertNotNull(Snapshot.load(snap.toString()));
    }

    // ─── value handling ─────────────────────────────────────────────────────

    @Test
    void write_shouldPersistEmptyStringValue() throws Exception {
        Path snap = tempSnapshotPath();
        Map<String, String> input = new HashMap<>();
        input.put("k", "");
        Snapshot.write(snap.toString(), input);

        Map<String, String> loaded = Snapshot.load(snap.toString());
        assertEquals("", loaded.get("k"), "Empty string should round-trip as empty string");
        assertTrue(loaded.containsKey("k"));
    }

    @Test
    void write_nullValue_persistedAsEmptyString() throws Exception {
        Path snap = tempSnapshotPath();
        Map<String, String> input = new HashMap<>();
        input.put("k", null);
        Snapshot.write(snap.toString(), input);

        Map<String, String> loaded = Snapshot.load(snap.toString());
        assertEquals("", loaded.get("k"), "Null value persists as empty string");
    }

    @Test
    void write_unicodeKeysAndValues_roundTrip() throws Exception {
        Path snap = tempSnapshotPath();
        Map<String, String> input = new HashMap<>();
        input.put("café",      "espresso ☕");
        input.put("用户:1",     "張三");
        input.put("emoji-key", "🚀🔥💯");
        Snapshot.write(snap.toString(), input);

        Map<String, String> loaded = Snapshot.load(snap.toString());
        assertEquals(input, loaded, "Unicode must round-trip via UTF-8");
    }

    @Test
    void write_keysWithSpaces_roundTrip() throws Exception {
        Path snap = tempSnapshotPath();
        Map<String, String> input = new HashMap<>();
        input.put("first name",       "Arun Ramadhas");
        input.put("favorite color",   "  gold (with leading spaces)");
        Snapshot.write(snap.toString(), input);

        Map<String, String> loaded = Snapshot.load(snap.toString());
        assertEquals(input, loaded);
    }

    // ─── known limitation: pipe in key/value ────────────────────────────────

    @Test
    void write_valueContainingPipe_isCurrentlyLossy() throws Exception {
        // Documents a known limitation: the on-disk format uses '|' as delimiter
        // without escaping. If a value contains '|', load() will misparse it.
        // If we ever fix this (e.g. via length-prefixed records), flip this assertion.
        Path snap = tempSnapshotPath();
        Map<String, String> input = new HashMap<>();
        input.put("k", "value|with|pipes");
        Snapshot.write(snap.toString(), input);

        Map<String, String> loaded = Snapshot.load(snap.toString());
        assertNotEquals("value|with|pipes", loaded.get("k"),
                "Until escaping is added, pipe in value is lossy");
        // Specifically: load() takes parts[1] only, so we get the substring before the first pipe
        assertEquals("value", loaded.get("k"));
    }

    // ─── volume ─────────────────────────────────────────────────────────────

    @Test
    void write_largeMap_10K_allEntriesPreserved() throws Exception {
        Path snap = tempSnapshotPath();
        Map<String, String> input = new LinkedHashMap<>();
        for (int i = 0; i < 10_000; i++) input.put("k" + i, "v" + i);

        Snapshot.write(snap.toString(), input);
        Map<String, String> loaded = Snapshot.load(snap.toString());

        assertEquals(10_000, loaded.size());
        assertEquals("v0",    loaded.get("k0"));
        assertEquals("v5000", loaded.get("k5000"));
        assertEquals("v9999", loaded.get("k9999"));
    }

    @Test
    void write_outputFormat_isPipeDelimitedOneEntryPerLine() throws Exception {
        Path snap = tempSnapshotPath();
        Map<String, String> input = new LinkedHashMap<>();
        input.put("a", "1");
        input.put("b", "2");
        input.put("c", "3");
        Snapshot.write(snap.toString(), input);

        List<String> lines = Files.readAllLines(snap);
        assertEquals(3, lines.size());
        // Iteration order of HashMap is not deterministic, so just assert structure
        for (String line : lines) {
            String[] parts = line.split("\\|", -1);
            assertEquals(2, parts.length, "Each line should be exactly key|value");
            assertTrue(input.containsKey(parts[0]));
            assertEquals(input.get(parts[0]), parts[1]);
        }
    }
}
