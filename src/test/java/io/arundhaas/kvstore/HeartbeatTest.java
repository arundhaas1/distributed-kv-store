package io.arundhaas.kvstore;

import io.arundhaas.kvstore.Modals.AppendEntriesRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatTest {

    /** 3-node cluster sharing one in-process transport. */
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
    void leaderHeartbeats_resetFollowerElectionDeadlines() throws InterruptedException {
        Cluster c = new Cluster();
        c.n1.startElection();   // n1 becomes leader of term 1
        assertEquals(RaftState.LEADER, c.n1.getState());

        Thread.sleep(310);
        // Followers' deadlines have all expired by now.
        assertTrue(c.n2.isElectionDeadlinePassed());
        assertTrue(c.n3.isElectionDeadlinePassed());

        c.n1.sendHeartbeats();

        assertFalse(c.n2.isElectionDeadlinePassed(),
            "follower must defer its election after receiving a heartbeat");
        assertFalse(c.n3.isElectionDeadlinePassed(),
            "follower must defer its election after receiving a heartbeat");
    }

    @Test
    void leaderSeesHigherTermInReply_stepsDown() {
        Cluster c = new Cluster();
        c.n1.startElection();   // n1 leader at term 1
        // While n1 is leader, n2 silently advances to term 5 (e.g. partition healed).
        c.n2.becomeFollower(5);

        c.n1.sendHeartbeats();

        assertEquals(RaftState.FOLLOWER, c.n1.getState(),
            "leader must step down when any follower replies with a higher term");
        assertEquals(5, c.n1.getCurrentTerm());
    }

    @Test
    void candidateReceivesSameTermHeartbeat_concedesToLeader() {
        Cluster c = new Cluster();
        c.n2.becomeCandidate();   // n2 candidate at term 1
        AppendEntriesRequest leaderHeartbeat = new AppendEntriesRequest(1, "node-1");

        c.n2.handleAppendEntries(leaderHeartbeat);

        assertEquals(RaftState.FOLLOWER, c.n2.getState());
        assertEquals(1, c.n2.getCurrentTerm());
    }

    @Test
    void repeatedHeartbeats_keepLeaderElected() throws InterruptedException {
        Cluster c = new Cluster();
        c.n1.startElection();
        assertEquals(RaftState.LEADER, c.n1.getState());

        // Tick heartbeats four times, ~130ms apart — well under the 150ms minimum
        // election timeout. If suppression works, no follower will time out.
        for (int i = 0; i < 4; i++) {
            Thread.sleep(130);
            c.n1.sendHeartbeats();
        }

        // n1 still leader, peers still followers in term 1.
        assertEquals(RaftState.LEADER, c.n1.getState());
        assertEquals(1, c.n1.getCurrentTerm());
        assertEquals(RaftState.FOLLOWER, c.n2.getState());
        assertEquals(RaftState.FOLLOWER, c.n3.getState());
        assertEquals(1, c.n2.getCurrentTerm());
        assertEquals(1, c.n3.getCurrentTerm());
    }

    @Test
    void staleLeaderHeartbeat_rejectedByAdvancedFollower() {
        Cluster c = new Cluster();
        c.n1.startElection();   // term 1, n1 leader
        c.n2.becomeFollower(7); // n2 jumps to term 7 independently

        // Direct call so we observe the response, not just the side effects on n1.
        var resp = c.n2.handleAppendEntries(new AppendEntriesRequest(1, "node-1"));

        assertFalse(resp.isSuccess());
        assertEquals(7, resp.getTerm());
        assertEquals(RaftState.FOLLOWER, c.n2.getState());
        assertEquals(7, c.n2.getCurrentTerm());
    }
}
