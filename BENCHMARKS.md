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

## Durability under `kill -9`

A separate test verifies that no acknowledged write is lost across a hard process crash. Implementation lives at `src/main/java/io/arundhaas/kvstore/BenchMark/`.

### Method

1. **Writer JVM** continuously calls `KvStore.put("k$i", "v$i")` in a tight loop. After every put returns (which fsyncs the WAL), the writer also appends `$i` to a separate `audit.log` and fsyncs it.
2. **Orchestrator** (`kill-test.sh`) sleeps a random 0.8–3.0 s, then sends `SIGKILL` (`kill -9`) to the writer.
3. **Verifier JVM** opens the same KvStore (which replays WAL on top of the snapshot) and checks that every key listed in `audit.log` is recoverable with the correct value.
4. Loop for N trials with randomized kill timing.

The audit fsync ordering matters: put first, audit second. So a kill between them = key on disk but not in audit (verifier under-reports — not a corruption); the reverse order would be a real bug.

### Result

```
100 trials, kill timing randomized 0.8–3.0 s, MacBook Pro M-series, JDK 11

acked per trial: 6,699 – 36,899   (median ~20,000)

================================================
kill-9 durability: 100/100 passed, 0 failed
================================================
```

**No missing writes, no corruption events across 100 randomized kill points** spanning the full timing window — including kills mid-fsync, mid-snapshot, and between WAL append and HashMap mutation.

### What this proves

- Per-write WAL fsync is durable under SIGKILL.
- Atomic temp+rename for snapshots survives mid-write kills (the `.tmp` is either fully written + renamed or doesn't exist).
- WAL replay on top of snapshot is idempotent and correct after any crash point.

### What this does NOT prove

| Gap | Why |
|---|---|
| **Real power-loss durability on macOS** | macOS `fsync` flushes to the SSD's write cache, not the platter. Kill-9 doesn't trigger the OS to discard caches; a real power loss could. Linux `fsync` is honest by default. For full durability on Mac, you'd need `F_FULLFSYNC` (a separate fcntl, not exposed by Java's `FileDescriptor.sync()`). |
| **Raft path durability** | The Raft state machine (`Map<String,String>` on `RaftNode`) is in-memory only. This test only exercises the legacy single-node `KvStore`. Raft persistence is a deferred extension. |
| **Concurrent-writer scenarios** | Single writer, single thread. Multi-threaded writes weren't tested. |

### Run it

```bash
./src/main/java/io/arundhaas/kvstore/BenchMark/kill-test.sh 100
```

Defaults to 100 trials; pass `10` or `50` for shorter runs. Each trial is ~1–3 s with the plain-`java` invocation (vs. ~10–15 s with `mvn exec:java`).

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
