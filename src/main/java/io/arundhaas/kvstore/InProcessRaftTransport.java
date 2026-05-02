package io.arundhaas.kvstore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.arundhaas.kvstore.Modals.AppendEntriesRequest;
import io.arundhaas.kvstore.Modals.AppendEntriesResponse;
import io.arundhaas.kvstore.Modals.RequestVoteRequest;
import io.arundhaas.kvstore.Modals.RequestVoteResponse;

public class InProcessRaftTransport implements RaftTransport{
	Map<String, RaftNode> peer = new HashMap<>();
	private final Set<String> disconnected = new HashSet<>();                                                                                                      
    private final Map<String, Integer> nodeSide = new HashMap<>();
	
	public void register(RaftNode node){
		Objects.requireNonNull(node, "node required");
		peer.put(node.getNodeId(), node);
	}
	
	public void disconnect(String nodeId) {
        disconnected.add(nodeId);                                                                                                                                  
    }
	
	public void reconnect(String nodeId) {
        disconnected.remove(nodeId);                                                                                                                               
    }
	
	public void partition(Set<String> sideA, Set<String> sideB) {
        nodeSide.clear();                                                                                                                                          
        for (String id : sideA) nodeSide.put(id, 1);
        for (String id : sideB) nodeSide.put(id, 2);                                                                                                               
    }  
	
	 public void heal() {    
         nodeSide.clear();                                                                                                                                          
         disconnected.clear();                                                                                        
     }
	 
	 private boolean canCommunicate(String from, String to) {                                                                                                       
         if (disconnected.contains(from) || disconnected.contains(to)) return false;
         
         Integer fromSide = nodeSide.get(from);
         Integer toSide = nodeSide.get(to);           
         
         if (fromSide == null || toSide == null) return true;
         
         return fromSide.equals(toSide);                                                                                                                            
     } 
	
	@Override
	public RequestVoteResponse sendRequestForVote(String toNodeId, RequestVoteRequest req) {
		RaftNode target = peer.get(toNodeId);
		if(target == null) {
			throw new IllegalStateException("Unknown peer: " + toNodeId);
		}
		
		 if (!canCommunicate(req.getCandidateId(), toNodeId)) {  
             return new RequestVoteResponse(0, false);   // synthetic timeout                                                                                       
         } 
		
		return target.handleRequestVote(req);	
	}

	@Override
	public AppendEntriesResponse sendAppendEntries(String toNodeId, AppendEntriesRequest req) {
		RaftNode target = peer.get(toNodeId);
		if(target == null) {
			throw new IllegalStateException("Unknown peer: " + toNodeId);
		}
		
		if (!canCommunicate(req.getLeaderId(), toNodeId)) {                                                          
            return new AppendEntriesResponse(0, false); // synthetic timeout
        } 
		
		return target.handleAppendEntries(req);
	}
}