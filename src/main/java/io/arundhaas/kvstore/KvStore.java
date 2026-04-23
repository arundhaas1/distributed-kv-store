package io.arundhaas.kvstore;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;

public class KvStore<K, V> implements AutoCloseable{
	private final HashMap<K, V> kvStore = new HashMap<>();
	private final WAL wal;
	
	public KvStore(String filePath) throws FileNotFoundException{
		this.wal = new WAL(filePath);
	}
	
	public void put(K key, V value) throws IOException {
		Objects.requireNonNull(key, "Key cannot be null");
		
		wal.append("PUT", String.valueOf(key), String.valueOf(value));
		kvStore.put(key, value);
	}
	
	public V get(K key) throws IOException {
		Objects.requireNonNull(key, "Key cannot be null");
		
		return kvStore.get(key);
	}
	
	public void delete(K key) throws IOException {
		Objects.requireNonNull(key, "Key cannot be null");
		
		wal.append("DELETE", String.valueOf(key), "");
		kvStore.remove(key);
	}

	@Override
	public void close() throws IOException {
		wal.close();
	}
	
}