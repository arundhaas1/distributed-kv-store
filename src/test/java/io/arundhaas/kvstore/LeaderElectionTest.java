package io.arundhaas.kvstore;

import io.arundhaas.kvstore.Modals.RequestVoteRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeaderElectionTest {

    /** Helper: build a fully-connected 3-node cluster sharing a transport. */
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
    void threeNodeCluster_majorityGrants_candidateBecomesLeader() {
        Cluster c = new Cluster();

        c.n1.startElection();

        assertEquals(RaftState.LEADER, c.n1.getState());
        assertEquals(1, c.n1.getCurrentTerm());
        // Early-exit on majority means at least the first peer was contacted and granted.
        assertEquals(1, c.n2.getCurrentTerm());
        assertEquals("node-1", c.n2.getVotedFor());
    }

    @Test
    void candidate_higherTermInReply_stepsDownToFollower() {
        Cluster c = new Cluster();
        // Push n2 into term 5 ahead of the election.
        c.n2.becomeFollower(5);

        c.n1.startElection(); // n1 jumps to term 1, n2 replies with term=5

        assertEquals(RaftState.FOLLOWER, c.n1.getState());
        assertEquals(5, c.n1.getCurrentTerm());
        assertNull(c.n1.getVotedFor());
    }

    @Test
    void minorityVotes_remainsCandidate() {
        Cluster c = new Cluster();
        // Both peers already voted for a phantom candidate this term.
        c.n2.handleRequestVote(new RequestVoteRequest(1, "ghost", 0, 0));
        c.n3.handleRequestVote(new RequestVoteRequest(1, "ghost", 0, 0));

        c.n1.startElection(); // n1 also lands at term 1, but peers reject

        assertEquals(RaftState.CANDIDATE, c.n1.getState());
        assertEquals(1, c.n1.getCurrentTerm());
        assertEquals("node-1", c.n1.getVotedFor());
    }

    @Test
    void winnerReceivesMajority_evenIfOneRejects() {
        Cluster c = new Cluster();
        // n3 already voted for ghost in term 1; n2 is fresh.
        c.n3.handleRequestVote(new RequestVoteRequest(1, "ghost", 0, 0));

        c.n1.startElection(); // self + n2 grant = 2 of 3 = majority

        assertEquals(RaftState.LEADER, c.n1.getState());
        assertEquals(1, c.n1.getCurrentTerm());
        assertEquals("node-1", c.n2.getVotedFor());
        assertEquals("ghost", c.n3.getVotedFor()); // n3 keeps prior vote
    }

    @Test
    void leaderHandoff_acrossTerms() {
        Cluster c = new Cluster();
        c.n1.startElection();
        assertEquals(RaftState.LEADER, c.n1.getState());

        c.n2.startElection(); // bumps term, asks n1 + n3

        assertEquals(RaftState.LEADER, c.n2.getState());
        assertEquals(2, c.n2.getCurrentTerm());
        assertEquals(RaftState.FOLLOWER, c.n1.getState(),
            "old leader must step down on higher-term RequestVote");
        assertEquals(2, c.n1.getCurrentTerm());
    }

    @Test
    void atMostOneLeaderPerTerm() {
        Cluster c = new Cluster();
        c.n1.startElection();              // n1 leader at term 1

        // n2 attempts election within the same term — but becomeCandidate bumps to 2.
        // What we're really checking: at no point are two nodes leaders at the SAME term.
        int n1Term = c.n1.getCurrentTerm();
        c.n2.startElection();

        // n2's election forced n1 to step down to term 2 (followers).
        assertNotEquals(c.n1.getState(), RaftState.LEADER,
            "n1 must not still be leader once a higher-term election completes");
        assertNotEquals(n1Term, c.n2.getCurrentTerm(),
            "the new leader must be in a strictly higher term than the prior leader's term");
    }
}
