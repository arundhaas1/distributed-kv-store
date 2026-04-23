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
}
