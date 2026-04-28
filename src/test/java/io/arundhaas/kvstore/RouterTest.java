package io.arundhaas.kvstore;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RouterTest {

    private Map<String, String> threeNodePaths() throws IOException {
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("node-1", Files.createTempFile("kv-router-n1-", ".log").toString());
        paths.put("node-2", Files.createTempFile("kv-router-n2-", ".log").toString());
        paths.put("node-3", Files.createTempFile("kv-router-n3-", ".log").toString());
        return paths;
    }

    @Test
    void put_then_get_roundTripsThroughRouter() throws Exception {
        try (Router<String, String> router = new Router<>(threeNodePaths())) {
            router.put("user:1", "Alice");
            router.put("user:2", "Bob");
            assertEquals("Alice", router.get("user:1"));
            assertEquals("Bob",   router.get("user:2"));
        }
    }

    @Test
    void delete_removesKey() throws Exception {
        try (Router<String, String> router = new Router<>(threeNodePaths())) {
            router.put("k", "v");
            router.delete("k");
            assertNull(router.get("k"));
        }
    }

    @Test
    void emptyConstructor_throws() {
        assertThrows(IllegalArgumentException.class, () -> new Router<>(new HashMap<>()));
    }

    @Test
    void nullKey_throws() throws Exception {
        try (Router<String, String> router = new Router<>(threeNodePaths())) {
            assertThrows(NullPointerException.class, () -> router.put(null, "v"));
            assertThrows(NullPointerException.class, () -> router.get(null));
            assertThrows(NullPointerException.class, () -> router.delete(null));
        }
    }

    @Test
    void sameKey_alwaysRoutesToSameNode() throws Exception {
        try (Router<String, String> router = new Router<>(threeNodePaths())) {
            for (int i = 0; i < 100; i++) {
                String key = "key-" + i;
                String first = router.nodeIdForKey(key);
                String second = router.nodeIdForKey(key);
                assertEquals(first, second);
            }
        }
    }

    @Test
    void tenThousandKeys_distributeAcrossThreeNodes_within10pct() throws Exception {
        try (Router<String, String> router = new Router<>(threeNodePaths())) {
            Map<String, Integer> counts = new HashMap<>();
            int totalKeys = 10_000;
            for (int i = 0; i < totalKeys; i++) {
                counts.merge(router.nodeIdForKey("key-" + i), 1, Integer::sum);
            }
            int expected = totalKeys / 3;
            int tolerance = (int) (expected * 0.10);
            counts.forEach((node, count) -> {
                int diff = Math.abs(count - expected);
                assertTrue(diff <= tolerance,
                        node + " owns " + count + " keys, expected ~" + expected);
            });
        }
    }

    @Test
    void differentKeys_landOnDifferentNodes() throws Exception {
        try (Router<String, String> router = new Router<>(threeNodePaths())) {
            Set<String> nodesUsed = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                nodesUsed.add(router.nodeIdForKey("key-" + i));
            }
            assertTrue(nodesUsed.size() >= 2, "Routing should spread across multiple nodes");
        }
    }

    @Test
    void writesPersist_perNode_independently() throws Exception {
        Map<String, String> paths = threeNodePaths();

        try (Router<String, String> router = new Router<>(paths)) {
            for (int i = 0; i < 50; i++) router.put("key-" + i, "v" + i);
        }

        try (Router<String, String> router = new Router<>(paths)) {
            for (int i = 0; i < 50; i++) {
                assertEquals("v" + i, router.get("key-" + i),
                        "key-" + i + " should survive close+reopen");
            }
        }
    }
}
