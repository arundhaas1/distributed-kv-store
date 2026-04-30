package io.arundhaas.kvstore;

import io.arundhaas.kvstore.Modals.AppendEntriesRequest;
import io.arundhaas.kvstore.Modals.AppendEntriesResponse;
import io.arundhaas.kvstore.Modals.RequestVoteRequest;
import io.arundhaas.kvstore.Modals.RequestVoteResponse;
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

    @Test
    void handleRequestVote_nullRequest_throws() {
        RaftNode node = new RaftNode("node-1");
        assertThrows(NullPointerException.class, () -> node.handleRequestVote(null));
    }

    @Test
    void handleRequestVote_staleTerm_rejected() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();   // currentTerm=1
        RequestVoteRequest req = new RequestVoteRequest(0, "node-2", 0, 0);

        RequestVoteResponse resp = node.handleRequestVote(req);

        assertFalse(resp.isVoteGranted());
        assertEquals(1, resp.getTerm());
    }

    @Test
    void handleRequestVote_higherTerm_stepsDownAndGrants() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();  // term=1, votedFor=node-1
        RequestVoteRequest req = new RequestVoteRequest(5, "node-2", 0, 0);

        RequestVoteResponse resp = node.handleRequestVote(req);

        assertTrue(resp.isVoteGranted());
        assertEquals(5, resp.getTerm());
        assertEquals(RaftState.FOLLOWER, node.getState());
        assertEquals("node-2", node.getVotedFor());
    }

    @Test
    void handleRequestVote_sameTerm_firstVote_granted() {
        RaftNode node = new RaftNode("node-1");

        RequestVoteResponse resp =
            node.handleRequestVote(new RequestVoteRequest(1, "node-2", 0, 0));

        assertTrue(resp.isVoteGranted());
        assertEquals(1, resp.getTerm());
        assertEquals("node-2", node.getVotedFor());
    }

    @Test
    void handleRequestVote_sameTerm_alreadyVotedForOther_rejected() {
        RaftNode node = new RaftNode("node-1");
        node.handleRequestVote(new RequestVoteRequest(1, "node-2", 0, 0));

        RequestVoteResponse resp =
            node.handleRequestVote(new RequestVoteRequest(1, "node-3", 0, 0));

        assertFalse(resp.isVoteGranted());
        assertEquals(1, resp.getTerm());
        assertEquals("node-2", node.getVotedFor());
    }

    @Test
    void handleRequestVote_sameCandidate_idempotent() {
        RaftNode node = new RaftNode("node-1");
        RequestVoteRequest req = new RequestVoteRequest(1, "node-2", 0, 0);

        assertTrue(node.handleRequestVote(req).isVoteGranted());
        assertTrue(node.handleRequestVote(req).isVoteGranted());
        assertEquals("node-2", node.getVotedFor());
    }

    @Test
    void handleRequestVote_granted_resetsElectionDeadline() throws InterruptedException {
        RaftNode node = new RaftNode("node-1");
        Thread.sleep(310);
        assertTrue(node.isElectionDeadlinePassed());

        node.handleRequestVote(new RequestVoteRequest(1, "node-2", 0, 0));

        assertFalse(node.isElectionDeadlinePassed());
    }

    @Test
    void handleRequestVote_lowerTerm_doesNotResetElectionDeadline() throws InterruptedException {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();   // term=1
        Thread.sleep(310);
        assertTrue(node.isElectionDeadlinePassed());

        node.handleRequestVote(new RequestVoteRequest(0, "node-2", 0, 0));

        assertTrue(node.isElectionDeadlinePassed(), "stale-term reject must not reset deadline");
    }

    @Test
    void handleAppendEntries_nullRequest_throws() {
        RaftNode node = new RaftNode("node-1");
        assertThrows(NullPointerException.class, () -> node.handleAppendEntries(null));
    }

    @Test
    void handleAppendEntries_staleTerm_rejected() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();   // currentTerm=1
        AppendEntriesRequest req = new AppendEntriesRequest(0, "node-2");

        AppendEntriesResponse resp = node.handleAppendEntries(req);

        assertFalse(resp.isSuccess());
        assertEquals(1, resp.getTerm());
        assertEquals(RaftState.CANDIDATE, node.getState());  // unchanged
    }

    @Test
    void handleAppendEntries_higherTerm_stepsDownAndAccepts() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();   // term=1, votedFor=node-1

        AppendEntriesResponse resp =
            node.handleAppendEntries(new AppendEntriesRequest(5, "node-2"));

        assertTrue(resp.isSuccess());
        assertEquals(5, resp.getTerm());
        assertEquals(RaftState.FOLLOWER, node.getState());
        assertEquals(5, node.getCurrentTerm());
        assertNull(node.getVotedFor());                      // cleared by becomeFollower
    }

    @Test
    void handleAppendEntries_sameTermFollower_accepts() {
        RaftNode node = new RaftNode("node-1");
        node.becomeFollower(3);

        AppendEntriesResponse resp =
            node.handleAppendEntries(new AppendEntriesRequest(3, "node-2"));

        assertTrue(resp.isSuccess());
        assertEquals(3, resp.getTerm());
        assertEquals(RaftState.FOLLOWER, node.getState());
    }

    @Test
    void handleAppendEntries_sameTermCandidate_concedesAndBecomesFollower() {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();   // term=1, state=CANDIDATE

        AppendEntriesResponse resp =
            node.handleAppendEntries(new AppendEntriesRequest(1, "node-2"));

        assertTrue(resp.isSuccess());
        assertEquals(1, resp.getTerm());
        assertEquals(RaftState.FOLLOWER, node.getState(),
            "a candidate that hears from a same-term leader must concede");
    }

    @Test
    void handleAppendEntries_acceptedRequest_resetsElectionDeadline() throws InterruptedException {
        RaftNode node = new RaftNode("node-1");
        Thread.sleep(310);
        assertTrue(node.isElectionDeadlinePassed());

        node.handleAppendEntries(new AppendEntriesRequest(1, "node-2"));

        assertFalse(node.isElectionDeadlinePassed(),
            "valid heartbeat must suppress election timer");
    }

    @Test
    void handleAppendEntries_staleTerm_doesNotResetDeadline() throws InterruptedException {
        RaftNode node = new RaftNode("node-1");
        node.becomeCandidate();   // term=1
        Thread.sleep(310);
        assertTrue(node.isElectionDeadlinePassed());

        node.handleAppendEntries(new AppendEntriesRequest(0, "ghost"));

        assertTrue(node.isElectionDeadlinePassed(),
            "stale-term heartbeat must not extend our deadline");
    }

    @Test
    void sendHeartbeats_nonLeader_isNoOp() {
        RaftNode node = new RaftNode("node-1");   // FOLLOWER, no peers
        long deadlineBefore = node.getElectionDeadlineMs();

        node.sendHeartbeats();   // must do nothing

        assertEquals(RaftState.FOLLOWER, node.getState());
        assertEquals(deadlineBefore, node.getElectionDeadlineMs());
    }
}
