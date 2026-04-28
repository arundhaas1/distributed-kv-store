package io.arundhaas.kvstore;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HashRingTest {

    // ─── Basic ─────────────────────────────────────────────────────────────

    @Test
    void singleNode_allKeysGoToIt() {
        HashRing ring = new HashRing();
        ring.addNode("node-1");
        for (int i = 0; i < 100; i++) {
            assertEquals("node-1", ring.getNodeForKey("key-" + i));
        }
    }

    @Test
    void emptyRing_throws() {
        HashRing ring = new HashRing();
        assertThrows(IllegalStateException.class, () -> ring.getNodeForKey("key"));
    }

    @Test
    void getNodeForKey_isDeterministic() {
        HashRing ring = new HashRing();
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");
        for (int i = 0; i < 100; i++) {
            String key = "user:" + i;
            assertEquals(ring.getNodeForKey(key), ring.getNodeForKey(key));
        }
    }

    @Test
    void getAllNodes_returnsUniqueSet() {
        HashRing ring = new HashRing();
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");
        assertEquals(3, ring.getAllNodes().size());
        assertTrue(ring.getAllNodes().containsAll(
                Arrays.asList("node-1", "node-2", "node-3")));
    }

    // ─── Distribution ──────────────────────────────────────────────────────

    @Test
    void threeNodes_10kKeys_splitWithin10pct() {
        HashRing ring = new HashRing();
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        Map<String, Integer> counts = new HashMap<>();
        int totalKeys = 10_000;
        for (int i = 0; i < totalKeys; i++) {
            counts.merge(ring.getNodeForKey("key-" + i), 1, Integer::sum);
        }

        int expected = totalKeys / 3;
        int tolerance = (int) (expected * 0.10);
        counts.forEach((node, count) -> {
            int diff = Math.abs(count - expected);
            assertTrue(diff <= tolerance,
                    node + " owns " + count + " keys, expected ~" + expected
                            + " (within +/-" + tolerance + ")");
        });
    }

    // ─── Redistribution — the proof of consistent hashing ──────────────────

    @Test
    void removeNode_redistributesOnlyAffectedKeys() {
        HashRing ring = new HashRing();
        ring.addNode("node-1");
        ring.addNode("node-2");
        ring.addNode("node-3");

        int totalKeys = 10_000;
        Map<String, String> before = new HashMap<>();
        for (int i = 0; i < totalKeys; i++) {
            String key = "key-" + i;
            before.put(key, ring.getNodeForKey(key));
        }

        ring.removeNode("node-2");

        int n2KeysMoved = 0, otherKeysMoved = 0;
        for (Map.Entry<String, String> e : before.entrySet()) {
            String now = ring.getNodeForKey(e.getKey());
            assertNotEquals("node-2", now, "Removed node must never be returned");
            if ("node-2".equals(e.getValue())) {
                n2KeysMoved++;
            } else if (!e.getValue().equals(now)) {
                otherKeysMoved++;
            }
        }
        assertTrue(n2KeysMoved > 0, "Removed node should have owned some keys");
        assertEquals(0, otherKeysMoved, "Only the removed node's keys should move");
    }

    @Test
    void addNode_redistributesOnlyToNewNode() {
        HashRing ring = new HashRing();
        ring.addNode("node-1");
        ring.addNode("node-2");

        int totalKeys = 10_000;
        Map<String, String> before = new HashMap<>();
        for (int i = 0; i < totalKeys; i++) {
            String key = "key-" + i;
            before.put(key, ring.getNodeForKey(key));
        }

        ring.addNode("node-3");

        int movedToNewNode = 0, movedElsewhere = 0;
        for (Map.Entry<String, String> e : before.entrySet()) {
            String now = ring.getNodeForKey(e.getKey());
            if (e.getValue().equals(now)) continue;
            if ("node-3".equals(now)) movedToNewNode++;
            else movedElsewhere++;
        }
        assertTrue(movedToNewNode > 0, "Adding node-3 should claim some keys");
        assertEquals(0, movedElsewhere, "Keys should ONLY move to the new node");
    }

    // ─── Wrap-around sanity ────────────────────────────────────────────────

    @Test
    void wrapAround_doesNotCrash() {
        HashRing ring = new HashRing();
        ring.addNode("node-1");
        assertDoesNotThrow(() -> ring.getNodeForKey("any-key"));
    }
}
