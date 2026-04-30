package io.arundhaas.kvstore.Modals;

import java.util.Objects;

public final class AppendEntriesRequest {
	private final int term;
	private final String leaderId;

	public AppendEntriesRequest(int term, String leaderId) {
		this.term = term;
		this.leaderId = Objects.requireNonNull(leaderId, "leaderId required");
	}

	public int getTerm() {
		return term;
	}

	public String getLeaderId() {
		return leaderId;
	}
}