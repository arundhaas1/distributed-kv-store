package io.arundhaas.kvstore.Modals;

public final class RequestVoteResponse {
	private final int term;
	private final boolean voteGranted;

	public RequestVoteResponse(int term, boolean voteGranted) {
		this.term = term;
		this.voteGranted = voteGranted;
	}

	public int getTerm() {
		return term;
	}

	public boolean isVoteGranted() {
		return voteGranted;
	}
}