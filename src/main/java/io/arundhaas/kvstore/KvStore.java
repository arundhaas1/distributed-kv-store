//$Id$
package io.arundhaas.kvstore;

import java.util.HashMap;
import java.util.Objects;

public class KvStore<K, V> {
	private final HashMap<K, V> kvStore = new HashMap<>();
	
	public void put(K key, V value) {
		Objects.requireNonNull(key, "Key cannot be null");
		
		kvStore.put(key, value);
	}
	
	public V get(K key) {
		return kvStore.get(key);
	}
	
	public void delete(K key) {
		Objects.requireNonNull(key, "Key cannot be null");
		
		kvStore.remove(key);
	}
	
}