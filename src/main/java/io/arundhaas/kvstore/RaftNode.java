package io.arundhaas.kvstore;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import io.arundhaas.kvstore.Modals.AppendEntriesRequest;
import io.arundhaas.kvstore.Modals.AppendEntriesResponse;
import io.arundhaas.kvstore.Modals.RequestVoteRequest;
import io.arundhaas.kvstore.Modals.RequestVoteResponse;

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
	private final List<String> peerIds;
	private final RaftTransport transport;

	public RaftNode(String nodeId) {
		 this(nodeId, Collections.emptyList(), null);
	}
	
	 public RaftNode(String nodeId, List<String> peerIds, RaftTransport transport) {                                      
	      this.nodeId = Objects.requireNonNull(nodeId, "nodeId required");
	      this.peerIds = List.copyOf(Objects.requireNonNull(peerIds, "peerIds required"));                                                                                                                               
	      if (!this.peerIds.isEmpty() && transport == null) {
	          throw new IllegalArgumentException("transport required when peers exist");                                                                                                                                 
	      }                                                                                                                
	      this.transport = transport;                                                                                                                                                                                    
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
	
	public RequestVoteResponse handleRequestVote(RequestVoteRequest req) {
		Objects.requireNonNull(req, "request required");
		
		if(req.getTerm() < this.currentTerm) {
			return new RequestVoteResponse(currentTerm, false);
		}
		
		if(req.getTerm() > this.currentTerm) {
			becomeFollower(req.getTerm());
		}
		
		if(votedFor == null || votedFor.equals(req.getCandidateId())){
			votedFor = req.getCandidateId();
			resetElectionDeadline();
			return new RequestVoteResponse(currentTerm, true);
		}
		
		return new RequestVoteResponse(currentTerm, false);
	}
	
	public AppendEntriesResponse handleAppendEntries(AppendEntriesRequest req) {
		Objects.requireNonNull(req, "request required");

		if(req.getTerm() < currentTerm) {
			return new AppendEntriesResponse(currentTerm, false);
		}

		if(req.getTerm() > currentTerm) {
			becomeFollower(req.getTerm());
		}

		if(state == RaftState.CANDIDATE) {
			state = RaftState.FOLLOWER;
		}

		resetElectionDeadline();
		return new AppendEntriesResponse(currentTerm, true);
	}
	
	public void sendHeartbeats() {
		if(state != RaftState.LEADER) return;
		
		AppendEntriesRequest req = new AppendEntriesRequest(currentTerm, nodeId);
		
		for(String peerId: peerIds) {
			AppendEntriesResponse resp = transport.sendAppendEntries(peerId, req);
			
			if(resp.getTerm() > currentTerm) {
				becomeFollower(resp.getTerm());
				return;
			}
		}
		
		markHeartbeatSent();
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
	
	public void startElection() {
		becomeCandidate();
		int votes = 1;
		int voteNeeded = ((peerIds.size()+1)/2)+1;
		
		RequestVoteRequest req = new RequestVoteRequest(currentTerm, nodeId, 0, 0);
		for(String peerId : peerIds) {
			RequestVoteResponse resp = transport.sendRequestForVote(peerId, req);
			if(resp.getTerm() > currentTerm) {
				becomeFollower(resp.getTerm());
				return;
			}
			
			if (resp.isVoteGranted()) votes++; 
			
			if(state == RaftState.CANDIDATE && votes >= voteNeeded) {
				becomeLeader();
				return;
			}
		}
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
		if (state != RaftState.LEADER) return false;
		
		return System.currentTimeMillis() - lastHeartbeatSentMs >= HEARTBEAT_INTERVAL_MS;
	}

	public void markHeartbeatSent() {
		this.lastHeartbeatSentMs = System.currentTimeMillis();
	}
}