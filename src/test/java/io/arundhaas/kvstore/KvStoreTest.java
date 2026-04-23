package io.arundhaas.kvstore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KvStoreTest {

    @Test
    void put_and_get_shouldStoreAndReturnValue() {
        KvStore<String, Integer> store = new KvStore<>();

        store.put("age", 25);

        assertEquals(25, store.get("age"));
    }

    @Test
    void put_shouldOverwriteExistingValue() {
        KvStore<String, String> store = new KvStore<>();

        store.put("key", "value1");
        store.put("key", "value2");

        assertEquals("value2", store.get("key"));
    }

    @Test
    void get_shouldReturnNull_whenKeyNotPresent() {
        KvStore<String, String> store = new KvStore<>();

        assertNull(store.get("missing"));
    }

    @Test
    void delete_shouldRemoveKey() {
        KvStore<String, String> store = new KvStore<>();

        store.put("key", "value");
        store.delete("key");

        assertNull(store.get("key"));
    }

    @Test
    void put_shouldThrowException_whenKeyIsNull() {
        KvStore<String, String> store = new KvStore<>();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> store.put(null, "value")
        );

        assertEquals("Key cannot be null", ex.getMessage());
    }

    @Test
    void delete_shouldThrowException_whenKeyIsNull() {
        KvStore<String, String> store = new KvStore<>();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> store.delete(null)
        );

        assertEquals("Key cannot be null", ex.getMessage());
    }

    @Test
    void get_shouldReturnNull_whenKeyIsNull() {
        KvStore<String, String> store = new KvStore<>();

        // HashMap allows null key for get → returns null
        assertNull(store.get(null));
    }
}