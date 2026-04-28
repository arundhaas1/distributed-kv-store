package io.arundhaas.kvstore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaftNodeTest {

    @Test
    void newNode_isFollowerWithTermZero() {
        RaftNode node = new RaftNode("node-1");
        assertEquals(RaftState.FOLLOWER, node.getState());
        assertEquals(0, node.getCurrentTerm());
        assertNull(node.getVotedFor());
        assertEquals("node-1", node.getNodeId());
    }

    @Test
    void newNode_hasElectionDeadlineInValidRange() {
        RaftNode node = new RaftNode("node-1");
        long now = System.currentTimeMillis();
        long deadline = node.getElectionDeadlineMs();
        assertTrue(deadline > now);
        assertTrue(deadline - now <= 300);
        assertTrue(deadline - now >= 150);
    }

    @Test
    void newNode_nullId_throws() {
        assertThrows(NullPointerException.class, () -> new RaftNode(null));
    }

    @Test
    void becomeCandidate_bumpsTermAndVotesSelf() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();
        assertEquals(RaftState.CANDIDATE, node.getState());
        assertEquals(1, node.getCurrentTerm());
        assertEquals("node-1", node.getVotedFor());
    }

    @Test
    void becomeCandidate_thenBecomeLeader_works() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();
        node.becomeLeader();
        assertEquals(RaftState.LEADER, node.getState());
        assertEquals(1, node.getCurrentTerm());
        assertEquals("node-1", node.getVotedFor());
    }

    @Test
    void becomeLeader_directlyFromFollower_throws() {
        RaftNode node = new RaftNode("node-1");
        assertThrows(IllegalStateException.class, node::becomeLeader);
    }

    @Test
    void becomeLeader_twice_throws() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();
        node.becomeLeader();
        assertThrows(IllegalStateException.class, node::becomeLeader);
    }

    @Test
    void becomeFollower_resetsVotedFor() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();
        node.becomeFollower(5);
        assertEquals(RaftState.FOLLOWER, node.getState());
        assertEquals(5, node.getCurrentTerm());
        assertNull(node.getVotedFor());
    }

    @Test
    void becomeFollower_lowerTerm_throws() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();
        assertThrows(IllegalArgumentException.class, () -> node.becomeFollower(0));
    }

    @Test
    void becomeFollower_sameTerm_isAllowed() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();
        node.becomeFollower(1);
        assertEquals(RaftState.FOLLOWER, node.getState());
        assertEquals(1, node.getCurrentTerm());
        assertNull(node.getVotedFor());
    }

    @Test
    void becomeCandidate_multipleTimes_keepsBumpingTerm() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();
        assertEquals(1, node.getCurrentTerm());
        node.becomeCandidate();
        assertEquals(2, node.getCurrentTerm());
        node.becomeCandidate();
        assertEquals(3, node.getCurrentTerm());
    }

    @Test
    void resetElectionDeadline_setsDeadlineInFuture() {
        RaftNode node = new RaftNode("node-1");
        node.resetElectionDeadline();
        long now = System.currentTimeMillis();
        long deadline = node.getElectionDeadlineMs();
        assertTrue(deadline > now);
        assertTrue(deadline - now <= 300);
        assertTrue(deadline - now >= 150);
    }

    @Test
    void isElectionDeadlinePassed_falseInitially() {
        RaftNode node = new RaftNode("node-1");
        assertFalse(node.isElectionDeadlinePassed());
    }

    @Test
    void isElectionDeadlinePassed_trueAfterTimeout() throws InterruptedException {
        RaftNode node = new RaftNode("node-1");
        Thread.sleep(320);
        assertTrue(node.isElectionDeadlinePassed());
    }

    @Test
    void isHeartbeatDue_followerAlwaysFalse() {
        RaftNode node = new RaftNode("node-1");
        assertFalse(node.isHeartbeatDue());
    }

    @Test
    void isHeartbeatDue_candidateAlwaysFalse() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();
        assertFalse(node.isHeartbeatDue());
    }

    @Test
    void isHeartbeatDue_leaderTrueAfterInterval() throws InterruptedException {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();
        node.becomeLeader();
        assertFalse(node.isHeartbeatDue());
        Thread.sleep(60);
        assertTrue(node.isHeartbeatDue());
    }

    @Test
    void markHeartbeatSent_resetsHeartbeatTimer() throws InterruptedException {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();
        node.becomeLeader();
        Thread.sleep(60);
        assertTrue(node.isHeartbeatDue());
        node.markHeartbeatSent();
        assertFalse(node.isHeartbeatDue());
    }

    @Test
    void failedElection_thenStepDownToFollower_inHigherTerm() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();
        node.becomeFollower(5);
        assertEquals(RaftState.FOLLOWER, node.getState());
        assertEquals(5, node.getCurrentTerm());
        assertNull(node.getVotedFor());
    }

    @Test
    void leaderToFollower_onHigherTermSeen() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();
        node.becomeLeader();
        assertEquals(RaftState.LEADER, node.getState());
        node.becomeFollower(10);
        assertEquals(RaftState.FOLLOWER, node.getState());
        assertEquals(10, node.getCurrentTerm());
        assertFalse(node.isHeartbeatDue());
    }
}
