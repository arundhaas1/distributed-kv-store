package io.arundhaas.kvstore.Modals;

import java.util.List;
import java.util.Objects;

public final class AppendEntriesRequest {
	private final int term;
	private final String leaderId;
	private final int prevLogIndex;
	private final int prevLogTerm;
	private final List<LogEntry> entries;
	private final int leaderCommit;

	public AppendEntriesRequest(int term, String leaderId, int prevLogIndex, int prevLogTerm, List<LogEntry> entries, int leaderCommit) {
		this.term = term;
		this.leaderId = Objects.requireNonNull(leaderId, "leaderId required");
		this.prevLogIndex = prevLogIndex;
		this.prevLogTerm = prevLogTerm;
		this.entries = List.copyOf(Objects.requireNonNull(entries, "entries required"));
		this.leaderCommit = leaderCommit;
	}

	public int getTerm() {
		return term;
	}

	public String getLeaderId() {
		return leaderId;
	}

	public int getPrevLogIndex() {
		return prevLogIndex;
	}

	public int getPrevLogTerm() {
		return prevLogTerm;
	}

	public List<LogEntry> getEntries() {
		return entries;
	}

	public int getLeaderCommit() {
		return leaderCommit;
	}
}