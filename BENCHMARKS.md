# Benchmarks

Microbenchmarks for the in-process Raft cluster. Run with:

```bash
mvn -q compile exec:java -Dexec.mainClass=io.arundhaas.kvstore.RaftBenchmark
```

## Important caveats

These numbers measure a **3-node Raft cluster running in a single JVM with synchronous in-process RPC** — every "RPC" is a direct method call. Numbers are dominated by JIT, GC, and HashMap lookups; **not** by network or disk.

Don't read these as "this Raft implementation does X ops/sec on real hardware." Read them as:
- Sanity check that the protocol terminates and converges.
- Relative comparisons (lagging peers vs healthy cluster, election cost vs replication cost).
- A floor — real network deployment will be 100–10,000× slower.

For a network-realistic comparison, you'd need a gRPC transport (deferred — see [README → Future Improvements](README.md)).

## Results (sample run, MacBook Pro M-series, JDK 11)

| Metric | Value |
|---|---|
| Sustained throughput (5,000 sequential clientAppend) | **~92,000 ops/sec** |
| Latency p50 | **1 µs** |
| Latency p95 | **3 µs** |
| Latency p99 | **6 µs** |
| Latency max (single sample) | **~1.5 ms** (likely GC pause) |
| Election cycle (full RPC roundtrip, 50 trials median) | **1 µs** |
| Lagging peer catch-up (500 entries, post-reconnect) | **1 heartbeat round, ~0.8 ms** |

Numbers vary run-to-run by ~10–20% due to JIT and GC.

## Reading the catch-up number

The "1 heartbeat round" result is interesting: when a 500-entry-behind peer reconnects, the leader's next heartbeat carries `prevLogIndex=0, prevLogTerm=0, entries=[e1..e500]` because `nextIndex[peer]` got pinned at the floor (1) during disconnection. The receiver's consistency check `matches(0, 0)` succeeds (empty-prefix sentinel), so all 500 entries land in one round.

In a real deployment, the leader would batch this differently and the back-off-then-replay pattern would take more rounds (one per missed entry, in the worst case, until the matching prefix is found). This is exactly the "decrement nextIndex on success=false until match" loop in `RaftNode.sendHeartbeats`.

## What's NOT measured

| Metric | Why not measured |
|---|---|
| Real-world election timeout (150–300 ms wait before a follower runs) | Tests drive elections deterministically; no `RaftRunner`/scheduler in this build |
| Disk fsync latency | RaftLog is in-memory only; persistence is deferred |
| Network round-trip | In-process transport |
| Concurrent client throughput | Single-threaded driver |
| Recovery time under leader kill | Synchronous test harness — wall time is dominated by manual `startElection()` calls, not real timer expiry |

These metrics depend on production-grade additions (RaftRunner, disk persistence, gRPC transport) that the 14-day plan deferred.

## What the numbers do prove

- **Protocol terminates** under all benchmark scenarios (no infinite retries, no hung commits).
- **Catch-up convergence is fast** when reconnected peers can be served from the leader's log.
- **Per-call protocol overhead** (vote counting, log append, replication, commit advance, apply) is small relative to typical disk fsync (~100 µs–10 ms) and network round-trip (~100 µs–10 ms in same-DC, ~50–200 ms cross-region) — meaning a network-backed deployment would be **dominated by I/O, not by the Raft logic itself**.

In practical terms: if you wired this code to gRPC + persistent disk, you'd expect throughput around 1,000–10,000 ops/sec (depending on disk and network), with p99 latency 5–50 ms. The protocol overhead is the small constant.
