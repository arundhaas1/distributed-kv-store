package io.arundhaas.kvstore;

import io.arundhaas.kvstore.Modals.RequestVoteRequest;
import io.arundhaas.kvstore.Modals.RequestVoteResponse;

public interface RaftTransport{
	public RequestVoteResponse sendRequestForVote(String toNodeId, RequestVoteRequest req);
}