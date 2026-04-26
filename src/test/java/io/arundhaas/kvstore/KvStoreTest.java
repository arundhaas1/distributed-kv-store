package io.arundhaas.kvstore;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KvStoreTest {

    private Path tempWalPath() throws IOException {
        return Files.createTempFile("kvstore-test-wal", ".log");
    }

    // ─── Basic ops ─────────────────────────────────────────────────────────

    @Test
    void put_and_get_shouldStoreAndReturnValue() throws Exception {
        try (KvStore<String, Integer> store = new KvStore<>(tempWalPath().toString())) {
            store.put("age", 25);
            assertEquals(25, store.get("age"));
        }
    }

    @Test
    void put_shouldOverwriteExistingValue() throws Exception {
        try (KvStore<String, String> store = new KvStore<>(tempWalPath().toString())) {
            store.put("key", "value1");
            store.put("key", "value2");
            assertEquals("value2", store.get("key"));
        }
    }

    @Test
    void get_shouldReturnNull_whenKeyNotPresent() throws Exception {
        try (KvStore<String, String> store = new KvStore<>(tempWalPath().toString())) {
            assertNull(store.get("missing"));
        }
    }

    @Test
    void delete_shouldRemoveKey() throws Exception {
        try (KvStore<String, String> store = new KvStore<>(tempWalPath().toString())) {
            store.put("key", "value");
            store.delete("key");
            assertNull(store.get("key"));
        }
    }

    // ─── Null handling ─────────────────────────────────────────────────────

    @Test
    void put_shouldThrowException_whenKeyIsNull() throws Exception {
        try (KvStore<String, String> store = new KvStore<>(tempWalPath().toString())) {
            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> store.put(null, "value"));
            assertEquals("Key cannot be null", ex.getMessage());
        }
    }

    @Test
    void delete_shouldThrowException_whenKeyIsNull() throws Exception {
        try (KvStore<String, String> store = new KvStore<>(tempWalPath().toString())) {
            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> store.delete(null));
            assertEquals("Key cannot be null", ex.getMessage());
        }
    }

    @Test
    void get_shouldThrowException_whenKeyIsNull() throws Exception {
        try (KvStore<String, String> store = new KvStore<>(tempWalPath().toString())) {
            assertThrows(NullPointerException.class, () -> store.get(null));
        }
    }

    // ─── Multi-key / mixed scenarios ───────────────────────────────────────

    @Test
    void put_multipleKeys_allAccessible() throws Exception {
        try (KvStore<String, String> store = new KvStore<>(tempWalPath().toString())) {
            store.put("a", "1");
            store.put("b", "2");
            store.put("c", "3");

            assertEquals("1", store.get("a"));
            assertEquals("2", store.get("b"));
            assertEquals("3", store.get("c"));
        }
    }

    @Test
    void delete_nonExistentKey_isSafeNoOp() throws Exception {
        try (KvStore<String, String> store = new KvStore<>(tempWalPath().toString())) {
            assertDoesNotThrow(() -> store.delete("nonexistent"));
            assertNull(store.get("nonexistent"));
        }
    }

    @Test
    void put_delete_put_sameKey_restoresValue() throws Exception {
        try (KvStore<String, String> store = new KvStore<>(tempWalPath().toString())) {
            store.put("key", "first");
            store.delete("key");
            store.put("key", "second");

            assertEquals("second", store.get("key"));
        }
    }

    @Test
    void genericTypes_IntegerKeyStringValue_works() throws Exception {
        try (KvStore<Integer, String> store = new KvStore<>(tempWalPath().toString())) {
            store.put(1, "one");
            store.put(2, "two");

            assertEquals("one", store.get(1));
            assertEquals("two", store.get(2));
        }
    }

    // ─── WAL integration ───────────────────────────────────────────────────

    @Test
    void put_shouldAppendToWAL() throws Exception {
        Path walPath = tempWalPath();
        try (KvStore<String, String> store = new KvStore<>(walPath.toString())) {
            store.put("foo", "bar");
            store.put("baz", "qux");
        }

        List<String> lines = Files.readAllLines(walPath);
        assertEquals(2, lines.size());
        assertEquals("PUT|foo|bar", lines.get(0));
        assertEquals("PUT|baz|qux", lines.get(1));
    }

    @Test
    void delete_shouldAppendDeleteToWAL() throws Exception {
        Path walPath = tempWalPath();
        try (KvStore<String, String> store = new KvStore<>(walPath.toString())) {
            store.put("foo", "bar");
            store.delete("foo");
        }

        List<String> lines = Files.readAllLines(walPath);
        assertEquals(2, lines.size());
        assertEquals("PUT|foo|bar", lines.get(0));
        assertEquals("DELETE|foo|", lines.get(1));
    }

    @Test
    void get_shouldNotAppendToWAL() throws Exception {
        Path walPath = tempWalPath();
        try (KvStore<String, String> store = new KvStore<>(walPath.toString())) {
            store.put("foo", "bar");
            store.get("foo");
            store.get("foo");
            store.get("foo");
        }

        List<String> lines = Files.readAllLines(walPath);
        assertEquals(1, lines.size(), "Reads must not log to WAL");
        assertEquals("PUT|foo|bar", lines.get(0));
    }

    @Test
    void largeBatch_100Puts_allLoggedInOrder() throws Exception {
        Path walPath = tempWalPath();
        try (KvStore<String, String> store = new KvStore<>(walPath.toString())) {
            for (int i = 0; i < 100; i++) {
                store.put("k" + i, "v" + i);
            }
        }

        List<String> lines = Files.readAllLines(walPath);
        assertEquals(100, lines.size());
        assertEquals("PUT|k0|v0", lines.get(0));
        assertEquals("PUT|k99|v99", lines.get(99));
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────

    @Test
    void useAfterClose_shouldThrow() throws Exception {
        KvStore<String, String> store = new KvStore<>(tempWalPath().toString());
        store.put("k", "v");
        store.close();

        // After close, the underlying WAL stream is dead — writes throw
        assertThrows(IOException.class, () -> store.put("k2", "v2"));
    }

    // ─── Recovery: snapshot + WAL-tail hybrid ──────────────────────────────

    private Path snapPathFor(Path walPath) {
        return Path.of(walPath.toString() + ".snapshot");
    }

    @Test
    void recovery_shouldReplayWal_whenNoSnapshotExists() throws Exception {
        Path walPath = tempWalPath();

        // Session 1 — write below the snapshot threshold
        try (KvStore<String, String> s = new KvStore<>(walPath.toString())) {
            s.put("a", "1");
            s.put("b", "2");
            s.put("c", "3");
            s.delete("b");
        }

        assertFalse(Files.exists(snapPathFor(walPath)),
                "No snapshot should exist below the write threshold");
        assertTrue(Files.size(walPath) > 0, "WAL should hold all writes");

        // Session 2 — reopen, state must be reconstructed from WAL alone
        try (KvStore<String, String> s = new KvStore<>(walPath.toString())) {
            assertEquals("1", s.get("a"));
            assertNull(s.get("b"), "Deleted key should remain deleted after recovery");
            assertEquals("3", s.get("c"));
        }
    }

    @Test
    void snapshot_shouldFire_andTruncateWal_at1000Writes() throws Exception {
        Path walPath = tempWalPath();
        Path snapPath = snapPathFor(walPath);

        try (KvStore<String, String> s = new KvStore<>(walPath.toString())) {
            for (int i = 0; i < 1000; i++) s.put("k" + i, "v" + i);
        }

        assertTrue(Files.exists(snapPath), "Snapshot should exist after 1000 writes");
        assertEquals(1000, Files.readAllLines(snapPath).size(),
                "Snapshot should contain all 1000 keys");
        assertEquals(0L, Files.size(walPath),
                "WAL should be truncated to 0 bytes after snapshot");
    }

    @Test
    void recovery_shouldUseSnapshot_plusWalDelta() throws Exception {
        Path walPath = tempWalPath();
        Path snapPath = snapPathFor(walPath);

        // Session 1 — cross the snapshot threshold, then write more
        try (KvStore<String, String> s = new KvStore<>(walPath.toString())) {
            for (int i = 0; i < 1000; i++) s.put("k" + i, "v" + i); // triggers snapshot at 1000th
            // These live ONLY in the WAL (the snapshot was already flushed)
            s.put("k500", "overwritten");
            s.put("late", "delta");
            s.delete("k0");
        }

        assertTrue(Files.exists(snapPath), "Snapshot must exist");
        assertEquals(3, Files.readAllLines(walPath).size(),
                "WAL should contain only the 3 post-snapshot operations");

        // Session 2 — recovery loads snapshot, then overlays WAL delta
        try (KvStore<String, String> s = new KvStore<>(walPath.toString())) {
            assertEquals("v999", s.get("k999"),         "From snapshot");
            assertEquals("overwritten", s.get("k500"),  "WAL overrides snapshot value");
            assertEquals("delta", s.get("late"),        "Post-snapshot WAL entry replayed");
            assertNull(s.get("k0"),                     "Post-snapshot delete replayed");
            assertNull(s.get("missing"),                "Unknown key still null");
        }
    }

    @Test
    void multipleSnapshotCycles_shouldKeepStateConsistent() throws Exception {
        Path walPath = tempWalPath();
        Path snapPath = snapPathFor(walPath);

        // Cycle through 2500 writes — snapshot should fire twice (at 1000 and 2000)
        try (KvStore<String, String> s = new KvStore<>(walPath.toString())) {
            for (int i = 0; i < 2500; i++) s.put("k" + i, "v" + i);
        }

        assertTrue(Files.exists(snapPath));
        // After last snapshot at 2000, 500 writes went to fresh WAL
        assertEquals(500, Files.readAllLines(walPath).size(),
                "WAL should hold only the writes since the most recent snapshot");

        try (KvStore<String, String> s = new KvStore<>(walPath.toString())) {
            // Spot-check across all three regions: pre-1st snap, between snaps, post-last snap
            assertEquals("v0",    s.get("k0"));
            assertEquals("v1500", s.get("k1500"));
            assertEquals("v2499", s.get("k2499"));
        }
    }

    @Test
    void recovery_shouldHandleUpdate_acrossSnapshotBoundary() throws Exception {
        Path walPath = tempWalPath();

        try (KvStore<String, String> s = new KvStore<>(walPath.toString())) {
            // 999 writes, snapshot has not fired yet
            for (int i = 0; i < 999; i++) s.put("k" + i, "v" + i);
            // The 1000th write triggers snapshot. Use it to set the canary value.
            s.put("canary", "before-snapshot");
            // After snapshot, overwrite the canary — this lives only in the truncated WAL
            s.put("canary", "after-snapshot");
        }

        try (KvStore<String, String> s = new KvStore<>(walPath.toString())) {
            assertEquals("after-snapshot", s.get("canary"),
                    "WAL delta must override the snapshot's value for the same key");
        }
    }

    @Test
    void recovery_shouldHandleDelete_ofKeyThatExistedInSnapshot() throws Exception {
        Path walPath = tempWalPath();

        try (KvStore<String, String> s = new KvStore<>(walPath.toString())) {
            // Make sure "victim" lands in the snapshot
            for (int i = 0; i < 1000; i++) s.put("k" + i, "v" + i); // includes k500 → snapshot
            // Snapshot fired. Now delete a key that exists in snapshot — only WAL records it.
            s.delete("k500");
        }

        try (KvStore<String, String> s = new KvStore<>(walPath.toString())) {
            assertNull(s.get("k500"),
                    "Snapshot loaded k500, but post-snapshot WAL DELETE must remove it");
            assertEquals("v999", s.get("k999"), "Other snapshot keys still present");
        }
    }
}
