package io.arundhaas.kvstore;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import io.arundhaas.kvstore.Modals.AppendEntriesRequest;
import io.arundhaas.kvstore.Modals.AppendEntriesResponse;
import io.arundhaas.kvstore.Modals.LogEntry;
import io.arundhaas.kvstore.Modals.RequestVoteRequest;
import io.arundhaas.kvstore.Modals.RequestVoteResponse;

public class RaftNode {

	private static final int ELECTION_TIMEOUT_MIN_MS = 150;
	private static final int ELECTION_TIMEOUT_MAX_MS = 300;
	private static final int HEARTBEAT_INTERVAL_MS = 50;
	
	private final Map<String, Integer> nextIndex = new HashMap<>();
	private final Map<String, Integer> matchIndex = new HashMap<>();
	private final Map<String, String> stateMachine = new HashMap<>();     
	
	private int lastApplied; 
	private final String nodeId;
	private RaftState state;
	private int currentTerm;
	private String votedFor;
	private long electionDeadlineMs;
	private long lastHeartbeatSentMs;
	private final List<String> peerIds;
	private final RaftTransport transport;
	private final RaftLog log;                                                                                                                                         
	private int commitIndex;

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
	      this.log = new RaftLog();                                                                                                                                          
	      this.commitIndex = 0;  
	      this.lastApplied = 0;
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
	
	public RaftLog getLog() {           
		return log;                                 
	}                                                                                                                                                                  
	                                                  
	public int getCommitIndex() {                                                                                                                                      
		return commitIndex;                                                                                              
	}
	
	public int getLastApplied() {          
		return lastApplied;
	}                                                                                                                                                                  
	                                                                                                                                                                     
	public Map<String, String> getStateMachine() {                                                                                                                     
	    return java.util.Collections.unmodifiableMap(stateMachine);                                                                                                    
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

		if (req.getTerm() < currentTerm) {
			return new AppendEntriesResponse(currentTerm, false);
		}

		if (req.getTerm() > currentTerm) {
			becomeFollower(req.getTerm());
		}

		if (state == RaftState.CANDIDATE) {
			state = RaftState.FOLLOWER;
		}

		if (!log.matches(req.getPrevLogIndex(), req.getPrevLogTerm())) {
			return new AppendEntriesResponse(currentTerm, false);
		}

		int slot = req.getPrevLogIndex() + 1;
		for (LogEntry entry : req.getEntries()) {
			LogEntry existing = log.get(slot);
			if (existing == null) {
				log.append(entry);
			} else if (existing.getTerm() != entry.getTerm()) {
				log.truncateAfter(slot - 1);
				log.append(entry);
			}
			slot++;
		}
		if (req.getLeaderCommit() > commitIndex) {
			commitIndex = Math.min(req.getLeaderCommit(), log.lastIndex());
			applyCommitted();
		}

		resetElectionDeadline();
		return new AppendEntriesResponse(currentTerm, true);
	}
	
	public void sendHeartbeats() {
		if(state != RaftState.LEADER) return;	
		
		for (String peerId : peerIds) {  
			int next     = nextIndex.get(peerId);                                                                        
	        int prevIdx  = next - 1;                
	        int prevTerm = (prevIdx == 0) ? 0 : log.get(prevIdx).getTerm();                                                                                            
	        List<LogEntry> entries = log.from(next);
	        
	        AppendEntriesRequest req = new AppendEntriesRequest(currentTerm, nodeId, prevIdx, prevTerm, entries, commitIndex);
	        AppendEntriesResponse resp = transport.sendAppendEntries(peerId, req);

	        if(resp.getTerm() > currentTerm) {
				becomeFollower(resp.getTerm());
				return;
			}
	        
	        if (resp.isSuccess()) {                                                                                      
	              int newMatch = prevIdx + entries.size();                                                                                                               
	              matchIndex.put(peerId, newMatch);                                                                                                                      
	              nextIndex.put(peerId, newMatch + 1);
	          } else {
	              nextIndex.put(peerId, Math.max(1, next - 1));
	          }
		}

		advanceCommitIndex();
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
		
		for (String peer : peerIds) {                                                                                                                                  
	          nextIndex.put(peer, log.lastIndex() + 1);
	          matchIndex.put(peer, 0);                                                                                                                                   
	    } 
	}
	
	private void applyToStateMachine(String command) {                                                                                                                 
	      String[] parts = command.split("\\|", -1);                                                                       
	      if (parts.length < 2) return;                                                                                                                                  
	      String op    = parts[0];    
	      String key   = parts[1];                                                                                                                                       
	      String value = (parts.length > 2) ? parts[2] : "";                                                               
	      switch (op) {                               
	          case "PUT":                      
	              stateMachine.put(key, value);                                                                                                                          
	              break;           
	          case "DELETE":                                                                                                                                             
	              stateMachine.remove(key);                                                                                
	              break;                       
	      }                                                                                                                                                              
	}
	
	private void applyCommitted() {                                                                                                                                    
	      while (lastApplied < commitIndex) {                                                                              
	          lastApplied++;                 
	          LogEntry entry = log.get(lastApplied);  
	          applyToStateMachine(entry.getCommand());                                                                                                                   
	      }                           
	} 
	
	private void advanceCommitIndex() {                                                                                  
	      int needed = (peerIds.size() + 1) / 2 + 1;
	      for (int N = log.lastIndex(); N > commitIndex; N--) {
	          LogEntry entry = log.get(N);                                                                                                                               
	          if (entry.getTerm() != currentTerm) continue;
	                                                                                                                                                                     
	          int count = 1;                                                                                                
	          for (int m : matchIndex.values()) {
	              if (m >= N) count++;                                                                                                                                   
	          }                                                                                                            
	          if (count >= needed) {                                                                                                                                     
	              commitIndex = N;    
	              applyCommitted();                                                                                                                                      
	              return;                                                                                                                                                
	          }                              
	      }                                                                                                                                                              
	}   
	
	public boolean clientAppend(String command) {                                                                                                                      
	      Objects.requireNonNull(command, "command required");                                                             
	      if (state != RaftState.LEADER) return false;                                                                                                                   
	                                                                                                                       
	      LogEntry entry = new LogEntry(log.lastIndex() + 1, currentTerm, command);                                                                                      
	      log.append(entry);                                                                                               
	                                                                                                                                                                     
	      sendHeartbeats();                                                
	                                                                                                                                                                     
	      return entry.getIndex() <= commitIndex;                                                                                                                        
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