package io.arundhaas.kvstore.Modals;

import java.util.Objects;

public final class RequestVoteRequest {
	private final int term;
	private final String candidateId;
	private final int lastLogIndex;
	private final int lastLogTerm;

	public RequestVoteRequest(int term, String candidateId, int lastLogIndex, int lastLogTerm) {
		this.term = term;
		this.candidateId = Objects.requireNonNull(candidateId, "candidateId required");
		this.lastLogIndex = lastLogIndex;
		this.lastLogTerm = lastLogTerm;
	}

	public int getTerm() {
		return term;
	}

	public String getCandidateId() {
		return candidateId;
	}

	public int getLastLogIndex() {
		return lastLogIndex;
	}

	public int getLastLogTerm() {
		return lastLogTerm;
	}
}