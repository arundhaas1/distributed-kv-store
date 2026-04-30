package io.arundhaas.kvstore;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.arundhaas.kvstore.Modals.AppendEntriesRequest;
import io.arundhaas.kvstore.Modals.AppendEntriesResponse;
import io.arundhaas.kvstore.Modals.RequestVoteRequest;
import io.arundhaas.kvstore.Modals.RequestVoteResponse;

public class InProcessRaftTransport implements RaftTransport{
	Map<String, RaftNode> peer = new HashMap<>();
	
	public void register(RaftNode node){
		Objects.requireNonNull(node, "node required");
		peer.put(node.getNodeId(), node);
	}
	
	public RequestVoteResponse sendRequestForVote(String toNodeId, RequestVoteRequest req) {
		RaftNode target = peer.get(toNodeId);
		if(target == null) {
			throw new IllegalStateException("Unknown peer: " + toNodeId);
		}
		return target.handleRequestVote(req);	
	}

	@Override
	public AppendEntriesResponse sendAppendEntries(String toNodeId, AppendEntriesRequest req) {
		RaftNode target = peer.get(toNodeId);
		if(target == null) {
			throw new IllegalStateException("Unknown peer: " + toNodeId);
		}
		return target.handleAppendEntries(req);
	}
}