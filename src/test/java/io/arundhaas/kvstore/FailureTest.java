package io.arundhaas.kvstore;

import io.arundhaas.kvstore.Modals.RequestVoteRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FailureTest {

    private static class Cluster {
        final InProcessRaftTransport transport = new InProcessRaftTransport();
        final RaftNode n1, n2, n3;

        Cluster() {
            n1 = new RaftNode("node-1", List.of("node-2", "node-3"), transport);
            n2 = new RaftNode("node-2", List.of("node-1", "node-3"), transport);
            n3 = new RaftNode("node-3", List.of("node-1", "node-2"), transport);
            transport.register(n1);
            transport.register(n2);
            transport.register(n3);
        }
    }

    @Test
    void killLeader_majoritySurvives_newLeaderElected() {
        Cluster c = new Cluster();
        c.n1.startElection();   // n1 leader at term 1
        assertEquals(RaftState.LEADER, c.n1.getState());

        c.transport.disconnect("node-1");

        // Surviving 2 of 3 — n2 starts an election (the runner would do this on timeout).
        c.n2.startElection();

        assertEquals(RaftState.LEADER, c.n2.getState());
        assertEquals(2, c.n2.getCurrentTerm());
    }

    @Test
    void killMajority_minoritySurvives_noNewLeader() {
        Cluster c = new Cluster();
        c.n1.startElection();   // n1 leader at term 1

        c.transport.disconnect("node-1");
        c.transport.disconnect("node-3");

        // Only n2 alive; it can't reach anyone. Tries an election anyway.
        c.n2.startElection();

        // Self-vote only → 1 of 3 → not majority → stays candidate.
        assertEquals(RaftState.CANDIDATE, c.n2.getState());
        assertEquals(2, c.n2.getCurrentTerm());
    }

    @Test
    void partition_minorityLeader_writesDontCommit() {
        Cluster c = new Cluster();
        c.n1.startElection();   // n1 leader at term 1
        c.n1.clientAppend("PUT|seed|0");   // commits via n2/n3 normally

        c.transport.partition(Set.of("node-1"), Set.of("node-2", "node-3"));

        boolean committed = c.n1.clientAppend("PUT|stranded|1");

        assertFalse(committed,
            "minority-side leader cannot commit — no majority to ack");
        assertEquals(RaftState.LEADER, c.n1.getState(),
            "but it still thinks it's leader (no higher-term evidence yet)");
    }

    @Test
    void partition_majorityElectsNewLeader_atHigherTerm() {
        Cluster c = new Cluster();
        c.n1.startElection();   // n1 leader at term 1

        c.transport.partition(Set.of("node-1"), Set.of("node-2", "node-3"));

        c.n2.startElection();

        assertEquals(RaftState.LEADER, c.n2.getState());
        assertEquals(2, c.n2.getCurrentTerm());
        assertEquals(RaftState.LEADER, c.n1.getState(),
            "stale leader on minority side hasn't been informed yet");
    }

    @Test
    void healPartition_staleLeaderStepsDown() {
        Cluster c = new Cluster();
        c.n1.startElection();   // n1 leader term 1
        c.transport.partition(Set.of("node-1"), Set.of("node-2", "node-3"));
        c.n2.startElection();   // n2 leader term 2 on majority side

        c.transport.heal();

        // n1's next heartbeat now reaches the cluster — peers are at term 2.
        c.n1.sendHeartbeats();

        assertEquals(RaftState.FOLLOWER, c.n1.getState());
        assertEquals(2, c.n1.getCurrentTerm());
    }

    @Test
    void laggingPeer_catchesUpAfterReconnect() {
        Cluster c = new Cluster();
        c.n1.startElection();   // n1 leader term 1

        c.transport.disconnect("node-3");

        // Append two entries while n3 is unreachable. Majority (n1+n2) commits them.
        c.n1.clientAppend("PUT|a|1");
        c.n1.clientAppend("PUT|b|2");
        assertEquals(2, c.n1.getCommitIndex());
        assertEquals(0, c.n3.getLog().lastIndex(), "n3 missed both writes");

        c.transport.reconnect("node-3");

        // Heartbeat fans the entries out to n3 via nextIndex back-off.
        c.n1.sendHeartbeats();
        c.n1.sendHeartbeats();   // second tick to let commit propagate via leaderCommit

        assertEquals(2, c.n3.getLog().lastIndex());
        assertEquals(2, c.n3.getCommitIndex());
        assertEquals("1", c.n3.getStateMachine().get("a"));
        assertEquals("2", c.n3.getStateMachine().get("b"));
    }

    @Test
    void candidateOnMinoritySide_cannotWin() {
        Cluster c = new Cluster();
        // No leader yet. Partition before any election.
        c.transport.partition(Set.of("node-1"), Set.of("node-2", "node-3"));

        c.n1.startElection();   // n1 stranded alone on side A

        assertEquals(RaftState.CANDIDATE, c.n1.getState(),
            "1 of 3 votes is not majority — stays candidate");
        assertEquals(1, c.n1.getCurrentTerm());
    }

    @Test
    void doubleLeaderFailure_recoveryViaSecondElection() {
        Cluster c = new Cluster();
        c.n1.startElection();   // n1 leader term 1
        c.n1.clientAppend("PUT|x|1");

        c.transport.disconnect("node-1");
        c.n2.startElection();   // n2 leader term 2

        c.transport.disconnect("node-2");
        c.transport.reconnect("node-1");
        // Now n1 is back but stale (term 1); n3 + n1 form the majority.

        c.n3.startElection();

        assertEquals(RaftState.LEADER, c.n3.getState());
        assertEquals(3, c.n3.getCurrentTerm());
        // n3 inherited the entry committed at term 1.
        assertTrue(c.n3.getLog().lastIndex() >= 1);
    }
}
