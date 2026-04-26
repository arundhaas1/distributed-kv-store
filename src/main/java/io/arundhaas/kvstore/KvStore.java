package io.arundhaas.kvstore;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KvStore<K, V> implements AutoCloseable{
	private HashMap<K, V> kvStore = new HashMap<>();
	
	private final int SNAPSHOT_EVERY_N_WRITES = 1000;
	private final WAL wal;
	private final String snapshotPath;
	private int writeCount = 0;
	
	public KvStore(String filePath) throws FileNotFoundException, IOException{
		this.wal = new WAL(filePath);
		this.snapshotPath =  filePath + ".snapshot";
		this.kvStore = (HashMap<K, V>) Snapshot.load(snapshotPath);
		refetch(filePath);
	}
	
	public void put(K key, V value) throws IOException {
		Objects.requireNonNull(key, "Key cannot be null");
		
		wal.append("PUT", String.valueOf(key), String.valueOf(value));
		kvStore.put(key, value);
		maybeSnapshot();
	}
	
	public V get(K key) throws IOException {
		Objects.requireNonNull(key, "Key cannot be null");
		
		return kvStore.get(key);
	}
	
	public void delete(K key) throws IOException {
		Objects.requireNonNull(key, "Key cannot be null");
		
		wal.append("DELETE", String.valueOf(key), "");
		kvStore.remove(key);
		maybeSnapshot();
	}

	@Override
	public void close() throws IOException {
		wal.close();
	}
	
	@SuppressWarnings("unchecked")
	private void refetch(String filePath) throws IOException {
		Path path = Path.of(filePath);
		if(!Files.exists(path)) return;
		
		List<String> lines = Files.readAllLines(path); 
		for(String line: lines) {
			String[] parts = line.split("\\|", -1);
			if(parts.length < 2) continue;
			
			String op = parts[0];
			String key = parts[1];
			String value = (parts.length > 2) ? parts[2]: "";
			
			switch(op) {
			case "PUT":
				kvStore.put((K)key, (V)value);
				break;
			case "DELETE":
				kvStore.remove((K) key);
				break;
			}
			
		}
	}
	
	private void maybeSnapshot() throws IOException {                                                                                                                                                                  
	      writeCount++;                           
	      if (writeCount >= SNAPSHOT_EVERY_N_WRITES) {                                                                                                                                                                   
	          Snapshot.write(snapshotPath, (Map) kvStore);
	          wal.reset();
	          writeCount = 0;                                                                                                                                                                                            
	      }                                   
	  }
	
}