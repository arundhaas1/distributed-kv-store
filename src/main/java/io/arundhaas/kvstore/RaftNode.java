package io.arundhaas.kvstore;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class RaftNode {

	private static final int ELECTION_TIMEOUT_MIN_MS = 150;
	private static final int ELECTION_TIMEOUT_MAX_MS = 300;
	private static final int HEARTBEAT_INTERVAL_MS = 50;

	private final String nodeId;
	private RaftState state;
	private int currentTerm;
	private String votedFor;
	private long electionDeadlineMs;
	private long lastHeartbeatSentMs;

	public RaftNode(String nodeId) {
		this.nodeId = Objects.requireNonNull(nodeId, "nodeId required");
		this.state = RaftState.FOLLOWER;
		this.currentTerm = 0;
		this.votedFor = null;
		resetElectionDeadline();
	}

	public String getNodeId() {
		return nodeId;
	}

	public RaftState getState() {
		return state;
	}

	public int getCurrentTerm() {
		return currentTerm;
	}

	public String getVotedFor() {
		return votedFor;
	}

	public long getElectionDeadlineMs() {
		return electionDeadlineMs;
	}

	public void becomeFollower(int newTerm) {
		if (newTerm < currentTerm) {
			throw new IllegalArgumentException("Cannot move to lower term: current=" + currentTerm + ", attempted=" + newTerm);
		}
		this.state = RaftState.FOLLOWER;
		this.currentTerm = newTerm;
		this.votedFor = null;
		resetElectionDeadline();
	}

	public void becomeCandidate() {
		this.state = RaftState.CANDIDATE;
		this.currentTerm += 1;
		this.votedFor = nodeId;
		resetElectionDeadline();
	}

	public void becomeLeader() {
		if (state != RaftState.CANDIDATE) {
			throw new IllegalStateException("Only a CANDIDATE can become LEADER, was " + state);
		}
		this.state = RaftState.LEADER;
		this.lastHeartbeatSentMs = System.currentTimeMillis();
	}

	public void resetElectionDeadline() {
		long now = System.currentTimeMillis();
		int randomMs = ThreadLocalRandom.current().nextInt(ELECTION_TIMEOUT_MIN_MS, ELECTION_TIMEOUT_MAX_MS + 1);
		this.electionDeadlineMs = now + randomMs;
	}

	public boolean isElectionDeadlinePassed() {
		return System.currentTimeMillis() >= electionDeadlineMs;
	}

	public boolean isHeartbeatDue() {
		if (state != RaftState.LEADER)
			return false;
		return System.currentTimeMillis() - lastHeartbeatSentMs >= HEARTBEAT_INTERVAL_MS;
	}

	public void markHeartbeatSent() {
		this.lastHeartbeatSentMs = System.currentTimeMillis();
	}
}