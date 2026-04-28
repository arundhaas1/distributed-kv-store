package io.arundhaas.kvstore;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public class Router<K, V> implements AutoCloseable {

	private final HashRing ring;
	private final Map<String, KvStore<K, V>> nodes;

	/**
	 * @param nodePaths
	 *            nodeId → WAL file path. One KvStore is opened per node.
	 */
	public Router(Map<String, String> nodePaths) throws IOException {
		if (nodePaths == null || nodePaths.isEmpty()) {
			throw new IllegalArgumentException("Need at least one node");
		}
		this.ring = new HashRing();
		this.nodes = new HashMap<>();
		for (Map.Entry<String, String> e : nodePaths.entrySet()) {
			KvStore<K, V> store = new KvStore<>(e.getValue());
			nodes.put(e.getKey(), store);
			ring.addNode(e.getKey());
		}
	}

	public void put(K key, V value) throws IOException {
		nodeFor(key).put(key, value);
	}

	public V get(K key) throws IOException {
		return nodeFor(key).get(key);
	}

	public void delete(K key) throws IOException {
		nodeFor(key).delete(key);
	}

	/** Which node would this key live on? Useful for tests + debugging. */
	public String nodeIdForKey(K key) {
		Objects.requireNonNull(key, "Key cannot be null");
		return ring.getNodeForKey(String.valueOf(key));
	}

	private KvStore<K, V> nodeFor(K key) {
		Objects.requireNonNull(key, "Key cannot be null");
		String nodeId = ring.getNodeForKey(String.valueOf(key));
		KvStore<K, V> store = nodes.get(nodeId);
		if (store == null) {
			throw new IllegalStateException("No KvStore registered for nodeId " + nodeId);
		}
		return store;
	}

	@Override
	public void close() throws IOException {
		IOException firstError = null;
		for (KvStore<K, V> store : nodes.values()) {
			try {
				store.close();
			} catch (IOException e) {
				if (firstError == null)
					firstError = e;
			}
		}
		if (firstError != null)
			throw firstError;
	}
}