package io.arundhaas.kvstore;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;

public class HashRing {               
	private final TreeMap<Long, String> ring = new TreeMap<>(); 
	private static final int VNODES_PER_NODE = 32; 
	
	public void addNode(String nodeId) {
        for (int i = 0; i < VNODES_PER_NODE; i++) {                                                                                                                                                                
            ring.put(hash(nodeId + ":" + i), nodeId);
        }                                                                                                                                                                                                          
    } 
	
	 public void removeNode(String nodeId) {
         for (int i = 0; i < VNODES_PER_NODE; i++) {                                                                                                                                                                
             ring.remove(hash(nodeId + ":" + i));                                                                     
         }                               
     }
	 
	 public String getNodeForKey(String key) {
         if (ring.isEmpty()) {                                                                                                                                                                                      
             throw new IllegalStateException("HashRing is empty — add nodes first");                                  
         }                                                                                                                                                                                                          
         long h = hash(key);
         Map.Entry<Long, String> entry = ring.ceilingEntry(h);                                                                                                                                                      
         if (entry == null) {                                                                                         
             entry = ring.firstEntry();   // wrap around
         }
         return entry.getValue();                                                                                                                                                                                   
     }  
	 
	 public Set<String> getAllNodes() {                                                                               
         return new HashSet<>(ring.values());                                                                                                                                                                       
     }
	
	private long hash(String s) {       
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");                                                                                                                                                   
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest, 0, 8).getLong();                                                                                                                                                        
        } catch (NoSuchAlgorithmException e) {                                                                       
            throw new IllegalStateException("MD5 unavailable", e);                                                                                                                                                 
        }
    }
	
}                                                                                                                                                                             