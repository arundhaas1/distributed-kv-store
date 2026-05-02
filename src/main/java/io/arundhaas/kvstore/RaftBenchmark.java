package io.arundhaas.kvstore;

import java.util.Arrays;
import java.util.List;

/**
 * Microbenchmarks for the in-process Raft cluster.
 *
 * Important caveat: every RPC here is a direct method call on the same JVM.
 * Numbers are dominated by JIT, GC, and hashmap lookups — not network. Use them
 * to compare relative trends (e.g. how lagging peers affect latency), not as
 * absolute "this Raft cluster does X ops/sec on real hardware" claims.
 */
public class RaftBenchmark {

    private static final int WARMUP_OPS = 200;
    private static final int THROUGHPUT_OPS = 5_000;
    private static final int LATENCY_OPS = 5_000;
    private static final int CATCHUP_OPS = 500;

    public static void main(String[] args) {
        System.out.println("=== Raft microbenchmarks (in-process, single thread) ===");
        System.out.println();
        benchmarkThroughput();
        System.out.println();
        benchmarkLatency();
        System.out.println();
        benchmarkElection();
        System.out.println();
        benchmarkCatchup();
    }

    private static Cluster freshCluster() {
        return new Cluster();
    }

    /** Sustained writes/sec on a healthy 3-node cluster. */
    private static void benchmarkThroughput() {
        System.out.println("--- Throughput (sustained clientAppend ops/sec) ---");
        Cluster c = freshCluster();
        c.n1.startElection();
        // Warm up JIT.
        for (int i = 0; i < WARMUP_OPS; i++) {
            c.n1.clientAppend("PUT|warm" + i + "|v");
        }
        long start = System.nanoTime();
        for (int i = 0; i < THROUGHPUT_OPS; i++) {
            c.n1.clientAppend("PUT|k" + i + "|v" + i);
        }
        long elapsedNanos = System.nanoTime() - start;
        double seconds = elapsedNanos / 1_000_000_000.0;
        double opsPerSec = THROUGHPUT_OPS / seconds;
        System.out.printf("  %,d ops in %.3f s → %,.0f ops/sec%n",
            THROUGHPUT_OPS, seconds, opsPerSec);
    }

    /** Per-call clientAppend latency on a healthy cluster. */
    private static void benchmarkLatency() {
        System.out.println("--- Latency per clientAppend (microseconds, healthy cluster) ---");
        Cluster c = freshCluster();
        c.n1.startElection();
        for (int i = 0; i < WARMUP_OPS; i++) {
            c.n1.clientAppend("PUT|warm" + i + "|v");
        }
        long[] samples = new long[LATENCY_OPS];
        for (int i = 0; i < LATENCY_OPS; i++) {
            long t0 = System.nanoTime();
            c.n1.clientAppend("PUT|k" + i + "|v");
            samples[i] = System.nanoTime() - t0;
        }
        Arrays.sort(samples);
        long p50 = samples[(int) (LATENCY_OPS * 0.50)];
        long p95 = samples[(int) (LATENCY_OPS * 0.95)];
        long p99 = samples[(int) (LATENCY_OPS * 0.99)];
        long max = samples[LATENCY_OPS - 1];
        System.out.printf("  p50 = %d µs   p95 = %d µs   p99 = %d µs   max = %d µs%n",
            p50 / 1000, p95 / 1000, p99 / 1000, max / 1000);
    }

    /** Time to run a full election cycle (RPC roundtrip, in-process). */
    private static void benchmarkElection() {
        System.out.println("--- Election cycle (RPC roundtrip; not real-world wall time) ---");
        int trials = 50;
        long[] samples = new long[trials];
        for (int t = 0; t < trials; t++) {
            Cluster c = freshCluster();
            long t0 = System.nanoTime();
            c.n1.startElection();
            samples[t] = System.nanoTime() - t0;
        }
        Arrays.sort(samples);
        long median = samples[trials / 2];
        long p95 = samples[(int) (trials * 0.95)];
        System.out.printf("  median = %d µs   p95 = %d µs   (over %d trials)%n",
            median / 1000, p95 / 1000, trials);
        System.out.println("  (Real-world cluster includes 150–300ms election timeout — not measured here.)");
    }

    /** Lagging peer catching up after reconnect. */
    private static void benchmarkCatchup() {
        System.out.println("--- Lagging peer catch-up (" + CATCHUP_OPS + " entries, reconnect + heartbeats) ---");
        Cluster c = freshCluster();
        c.n1.startElection();

        c.transport.disconnect("node-3");
        for (int i = 0; i < CATCHUP_OPS; i++) {
            c.n1.clientAppend("PUT|k" + i + "|v");
        }
        c.transport.reconnect("node-3");

        long t0 = System.nanoTime();
        int heartbeats = 0;
        while (c.n3.getLog().lastIndex() < CATCHUP_OPS) {
            c.n1.sendHeartbeats();
            heartbeats++;
            if (heartbeats > 2 * CATCHUP_OPS + 10) {
                throw new IllegalStateException("catch-up did not converge");
            }
        }
        long elapsedNanos = System.nanoTime() - t0;

        System.out.printf("  caught up via %d heartbeat rounds in %.3f ms%n",
            heartbeats, elapsedNanos / 1_000_000.0);
        System.out.printf("  ≈ %.2f ms per heartbeat round%n",
            (elapsedNanos / 1_000_000.0) / heartbeats);
    }

    private static class Cluster {
        final InProcessRaftTransport transport = new InProcessRaftTransport();
        final RaftNode n1, n2, n3;

        Cluster() {
            n1 = new RaftNode("node-1", List.of("node-2", "node-3"), transport);
            n2 = new RaftNode("node-2", List.of("node-1", "node-3"), transport);
            n3 = new RaftNode("node-3", List.of("node-1", "node-2"), transport);
            transport.register(n1);
            transport.register(n2);
            transport.register(n3);
        }
    }
}
