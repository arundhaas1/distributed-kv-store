package io.arundhaas.kvstore;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WALTest {

    private Path tempWalPath() throws IOException {
        return Files.createTempFile("wal-test", ".log");
    }

    // ─── Basic writes ──────────────────────────────────────────────────────

    @Test
    void append_writesSingleLine() throws IOException {
        Path path = tempWalPath();
        try (WAL wal = new WAL(path.toString())) {
            wal.append("PUT", "foo", "bar");
        }

        List<String> lines = Files.readAllLines(path);
        assertEquals(1, lines.size());
        assertEquals("PUT|foo|bar", lines.get(0));
    }

    @Test
    void append_writes100LinesInOrder() throws IOException {
        Path path = tempWalPath();
        try (WAL wal = new WAL(path.toString())) {
            for (int i = 0; i < 100; i++) {
                wal.append("PUT", "k" + i, "v" + i);
            }
        }

        List<String> lines = Files.readAllLines(path);
        assertEquals(100, lines.size());
        assertEquals("PUT|k0|v0", lines.get(0));
        assertEquals("PUT|k50|v50", lines.get(50));
        assertEquals("PUT|k99|v99", lines.get(99));
    }

    @Test
    void append_writes1000Lines_stressTest() throws IOException {
        Path path = tempWalPath();
        try (WAL wal = new WAL(path.toString())) {
            for (int i = 0; i < 1000; i++) {
                wal.append("PUT", "k" + i, "v" + i);
            }
        }

        List<String> lines = Files.readAllLines(path);
        assertEquals(1000, lines.size());
        assertEquals("PUT|k999|v999", lines.get(999));
    }

    // ─── Null / empty handling ─────────────────────────────────────────────

    @Test
    void append_handlesNullValue() throws IOException {
        Path path = tempWalPath();
        try (WAL wal = new WAL(path.toString())) {
            wal.append("DELETE", "foo", null);
        }

        List<String> lines = Files.readAllLines(path);
        assertEquals("DELETE|foo|", lines.get(0));
    }

    @Test
    void append_handlesEmptyValue() throws IOException {
        Path path = tempWalPath();
        try (WAL wal = new WAL(path.toString())) {
            wal.append("DELETE", "foo", "");
        }

        List<String> lines = Files.readAllLines(path);
        assertEquals("DELETE|foo|", lines.get(0));
    }

    @Test
    void append_handlesEmptyKey() throws IOException {
        Path path = tempWalPath();
        try (WAL wal = new WAL(path.toString())) {
            wal.append("PUT", "", "value");
        }

        List<String> lines = Files.readAllLines(path);
        assertEquals("PUT||value", lines.get(0));
    }

    // ─── Mixed ops ─────────────────────────────────────────────────────────

    @Test
    void append_mixedOps() throws IOException {
        Path path = tempWalPath();
        try (WAL wal = new WAL(path.toString())) {
            wal.append("PUT", "a", "1");
            wal.append("PUT", "b", "2");
            wal.append("DELETE", "a", null);
            wal.append("PUT", "a", "3");
        }

        List<String> lines = Files.readAllLines(path);
        assertEquals(4, lines.size());
        assertEquals("PUT|a|1", lines.get(0));
        assertEquals("PUT|b|2", lines.get(1));
        assertEquals("DELETE|a|", lines.get(2));
        assertEquals("PUT|a|3", lines.get(3));
    }

    // ─── Append-only durability ────────────────────────────────────────────

    @Test
    void append_isAppendOnly_doesNotOverwrite() throws IOException {
        Path path = tempWalPath();

        try (WAL wal1 = new WAL(path.toString())) {
            wal1.append("PUT", "foo", "bar");
        }

        try (WAL wal2 = new WAL(path.toString())) {
            wal2.append("PUT", "baz", "qux");
        }

        List<String> lines = Files.readAllLines(path);
        assertEquals(2, lines.size(), "Second WAL instance must append, not overwrite");
        assertEquals("PUT|foo|bar", lines.get(0));
        assertEquals("PUT|baz|qux", lines.get(1));
    }

    @Test
    void append_survivesExplicitClose_canBeReopened() throws IOException {
        Path path = tempWalPath();

        WAL wal1 = new WAL(path.toString());
        wal1.append("PUT", "foo", "bar");
        wal1.close();

        // Data on disk regardless of any further use
        List<String> linesAfterFirstClose = Files.readAllLines(path);
        assertEquals(1, linesAfterFirstClose.size());

        // Reopen, append more
        WAL wal2 = new WAL(path.toString());
        wal2.append("PUT", "baz", "qux");
        wal2.close();

        List<String> linesAfterSecond = Files.readAllLines(path);
        assertEquals(2, linesAfterSecond.size());
    }

    // ─── Character encoding ────────────────────────────────────────────────

    @Test
    void append_writesUTF8() throws IOException {
        Path path = tempWalPath();
        try (WAL wal = new WAL(path.toString())) {
            wal.append("PUT", "naïve", "café");
            wal.append("PUT", "emoji", "🚀");
        }

        // Read bytes directly and decode UTF-8
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        assertTrue(content.contains("naïve"));
        assertTrue(content.contains("café"));
        assertTrue(content.contains("🚀"));
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────

    @Test
    void append_afterClose_shouldThrow() throws IOException {
        Path path = tempWalPath();
        WAL wal = new WAL(path.toString());
        wal.append("PUT", "foo", "bar");
        wal.close();

        // Appending to a closed stream must throw
        assertThrows(IOException.class, () -> wal.append("PUT", "baz", "qux"));
    }
}
