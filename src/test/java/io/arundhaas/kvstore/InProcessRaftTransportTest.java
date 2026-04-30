package io.arundhaas.kvstore;

import io.arundhaas.kvstore.Modals.RequestVoteRequest;
import io.arundhaas.kvstore.Modals.RequestVoteResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InProcessRaftTransportTest {

    @Test
    void sendRequestForVote_unknownPeer_throws() {
        InProcessRaftTransport transport = new InProcessRaftTransport();
        RequestVoteRequest req = new RequestVoteRequest(1, "node-1", 0, 0);

        assertThrows(IllegalStateException.class,
            () -> transport.sendRequestForVote("ghost", req));
    }

    @Test
    void sendRequestForVote_routesToHandleRequestVote() {
        InProcessRaftTransport transport = new InProcessRaftTransport();
        RaftNode peer = new RaftNode("node-2");
        transport.register(peer);

        RequestVoteResponse resp = transport.sendRequestForVote(
            "node-2", new RequestVoteRequest(1, "node-1", 0, 0));

        assertTrue(resp.isVoteGranted());
        assertEquals("node-1", peer.getVotedFor());
        assertEquals(1, peer.getCurrentTerm());
    }

    @Test
    void register_nullNode_throws() {
        InProcessRaftTransport transport = new InProcessRaftTransport();
        assertThrows(NullPointerException.class, () -> transport.register(null));
    }
}
