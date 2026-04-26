package io.arundhaas.kvstore;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class Snapshot {
	
	 public static void write(String snapshotPath, Map<String, String> memtable) throws IOException {                                                                                                               
         Path finalPath = Path.of(snapshotPath);                                                                                                                                                                    
         Path tempPath  = Path.of(snapshotPath + ".tmp"); //Avoid data loss mid process                                                                                                                                                          
                                             
         try (BufferedWriter w = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
        	    for (Map.Entry<String, String> e : memtable.entrySet()) {
        	        String value = e.getValue() == null ? "" : e.getValue();
        	        w.write(e.getKey() + "|" + value);
        	        w.newLine();
        	    }
         }                                                                                                                                                                                                     
                                     
                                                                                                                                                                                                                                               
         Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);                                                                                                   
     }
	 
	 
	 public static Map<String, String> load(String snapshotPath) throws IOException {                                                                                                                               
         Map<String, String> result = new HashMap<>();                                                                
         Path path = Path.of(snapshotPath);                                                                                                                                                                         
         if (!Files.exists(path)) return result;                                                                      
                                                                                                                                                                                                                    
         try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {                                                                                                                           
             String line;                
             while ((line = r.readLine()) != null) {                                                                                                                                                                
                 String[] parts = line.split("\\|", -1);                                                                                                                                                            
                 if (parts.length < 2) continue;
                 result.put(parts[0], parts[1]);                                                                                                                                                                    
             }                                                                                                        
         }                                                                                                                                                                                                          
         return result;
     } 
}