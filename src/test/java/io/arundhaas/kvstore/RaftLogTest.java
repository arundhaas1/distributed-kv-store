package io.arundhaas.kvstore;

import io.arundhaas.kvstore.Modals.LogEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaftLogTest {

    @Test
    void emptyLog_lastIndexZero_lastTermZero() {
        RaftLog log = new RaftLog();
        assertEquals(0, log.lastIndex());
        assertEquals(0, log.lastTerm());
    }

    @Test
    void append_advancesLastIndexAndLastTerm() {
        RaftLog log = new RaftLog();
        log.append(new LogEntry(1, 1, "PUT|a|1"));
        log.append(new LogEntry(2, 1, "PUT|b|2"));
        log.append(new LogEntry(3, 2, "PUT|c|3"));

        assertEquals(3, log.lastIndex());
        assertEquals(2, log.lastTerm());
    }

    @Test
    void get_outOfRange_returnsNull() {
        RaftLog log = new RaftLog();
        log.append(new LogEntry(1, 1, "PUT|a|1"));

        assertNull(log.get(0));
        assertNull(log.get(2));
        assertNull(log.get(-1));
    }

    @Test
    void get_inRange_returnsEntry() {
        RaftLog log = new RaftLog();
        log.append(new LogEntry(1, 1, "PUT|a|1"));
        log.append(new LogEntry(2, 1, "PUT|b|2"));

        assertEquals("PUT|b|2", log.get(2).getCommand());
        assertEquals(1, log.get(2).getTerm());
    }

    @Test
    void matches_emptyPrefix_alwaysTrueForZeroZero() {
        RaftLog log = new RaftLog();
        assertTrue(log.matches(0, 0));

        log.append(new LogEntry(1, 1, "PUT|a|1"));
        assertTrue(log.matches(0, 0), "matches(0,0) is the empty-prefix sentinel");
    }

    @Test
    void matches_existingIndex_correctTerm_true() {
        RaftLog log = new RaftLog();
        log.append(new LogEntry(1, 3, "PUT|a|1"));
        assertTrue(log.matches(1, 3));
    }

    @Test
    void matches_existingIndex_wrongTerm_false() {
        RaftLog log = new RaftLog();
        log.append(new LogEntry(1, 3, "PUT|a|1"));
        assertFalse(log.matches(1, 4));
    }

    @Test
    void matches_outOfRangeIndex_false() {
        RaftLog log = new RaftLog();
        log.append(new LogEntry(1, 1, "x"));
        assertFalse(log.matches(2, 1));
    }

    @Test
    void truncateAfter_removesTail() {
        RaftLog log = new RaftLog();
        log.append(new LogEntry(1, 1, "a"));
        log.append(new LogEntry(2, 1, "b"));
        log.append(new LogEntry(3, 2, "c"));

        log.truncateAfter(1);

        assertEquals(1, log.lastIndex());
        assertEquals("a", log.get(1).getCommand());
        assertNull(log.get(2));
    }

    @Test
    void truncateAfter_indexBeyondEnd_isNoOp() {
        RaftLog log = new RaftLog();
        log.append(new LogEntry(1, 1, "a"));

        log.truncateAfter(5);

        assertEquals(1, log.lastIndex());
    }

    @Test
    void from_returnsTailFromIndex() {
        RaftLog log = new RaftLog();
        log.append(new LogEntry(1, 1, "a"));
        log.append(new LogEntry(2, 1, "b"));
        log.append(new LogEntry(3, 2, "c"));

        var tail = log.from(2);

        assertEquals(2, tail.size());
        assertEquals("b", tail.get(0).getCommand());
        assertEquals("c", tail.get(1).getCommand());
    }

    @Test
    void from_pastEnd_returnsEmpty() {
        RaftLog log = new RaftLog();
        log.append(new LogEntry(1, 1, "a"));
        assertTrue(log.from(2).isEmpty());
        assertTrue(log.from(99).isEmpty());
    }

    @Test
    void append_nullEntry_throws() {
        RaftLog log = new RaftLog();
        assertThrows(NullPointerException.class, () -> log.append(null));
    }
}
