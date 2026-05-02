# Distributed Key-Value Store

A Java implementation of a distributed KV store built from first principles — consensus, durability, and sharding.

> **Status:** 🚧 In active development · Day 13 of 14 · 151 tests green

## Goals

- **Durability** — writes survive crashes (Write-Ahead Log + snapshots)
- **Consensus** — 3-node replicated state machine with Raft
- **Sharding** — keys distributed across nodes via consistent hashing
- **Failure tolerance** — survives node crashes + network partitions

## Tech Stack

- **Language:** Java 11
- **Build:** Maven
- **Testing:** JUnit 5

## Architecture (so far)

```
   client.put("user:42", "Alice")
              │
              ▼
        ┌──────────┐
        │  Router  │   ← consistent-hash routing
        └─────┬────┘
              │ ring.getNodeForKey("user:42") → "node-2"
              ▼
   ┌──────────┬──────────┬──────────┐
   │ KvStore  │ KvStore  │ KvStore  │
   │  node-1  │  node-2  │  node-3  │
   │          │          │          │
   │  WAL +   │  WAL +   │  WAL +   │
   │ snapshot │ snapshot │ snapshot │
   └──────────┴──────────┴──────────┘
```

- **HashRing** — TreeMap-backed ring with 32 virtual nodes per physical node. O(log N) key→node lookup.
- **Router** — hides cluster topology behind a single put/get/delete API.
- **KvStore** — single-node store. WAL append + fsync per write, periodic snapshots every 1000 writes, atomic temp+rename for snapshots.
- **WAL** — line-delimited log, fsynced on every write. `reset()` truncates after a snapshot is durable.
- **Snapshot** — pipe-delimited key/value file. Atomic rename so partial writes never corrupt prior snapshot.

## Roadmap

| Day | Milestone                                 | Status |
| --- | ----------------------------------------- | ------ |
|  1  | Project scaffold + README                 | ✅     |
|  2  | Basic in-memory ops (get / put / delete)  | ✅     |
|  3  | Write-Ahead Log append + fsync            | ✅     |
|  4  | WAL replay on startup (crash recovery)    | ✅     |
|  5  | Periodic snapshots                        | ✅     |
|  6  | Snapshot + WAL-tail hybrid recovery       | ✅     |
|  7  | Consistent hashing ring                   | ✅     |
|  8  | Multi-node routing (simulated cluster)    | ✅     |
|  9  | Raft node state machine                   | ✅     |
| 10  | Raft leader election (RequestVote)        | ✅     |
| 11  | Raft heartbeats (AppendEntries)           | ✅     |
| 12  | Raft log replication                      | ✅     |
| 13  | Failure tests (kill-leader, partition)    | ✅     |
| 14  | Benchmarks + architecture diagram         |        |

## Test coverage

| Suite                         | Tests | What it proves |
| ----------------------------- | ----: | -------------- |
| `KvStoreTest`                 |    22 | Basic ops, NPE, WAL integration, lifecycle, snapshot+WAL recovery contract |
| `SnapshotTest`                |    16 | Round-trip, atomicity (no `.tmp` leak), overwrite, missing/malformed file handling, unicode, 10K volume |
| `WALTest`                     |    11 | Append, fsync, reset, close |
| `HashRingTest`                |     8 | Distribution within ±10% across 3 nodes (10K keys), redistribution only of affected keys on add/remove |
| `RouterTest`                  |     8 | Route determinism, distribution, persistence per-node, close+reopen recovery |
| `RaftNodeTest`                |    43 | State transitions, term/votedFor invariants, election + heartbeat timers, RequestVote + AppendEntries receiver rules, log consistency, commit + apply, clientAppend guards |
| `InProcessRaftTransportTest`  |     3 | Peer registration, RPC routing to handler, unknown-peer error |
| `LeaderElectionTest`          |     6 | 3-node majority win, minority stay-candidate, higher-term step-down, leader handoff across terms |
| `HeartbeatTest`               |     5 | Heartbeat suppresses follower elections, candidate concedes on same-term heartbeat, leader steps down on higher term, stale leader rejected |
| `RaftLogTest`                 |    13 | Append/get/lastIndex/lastTerm, matches (incl. empty-prefix sentinel), truncateAfter, from(start), null guards |
| `LogReplicationTest`          |     8 | clientAppend rejects non-leader, replicates entry to all peers, leader applies committed entries, followers apply on next heartbeat, multi-write commit + ordering, lagging-peer catch-up, Figure-8 prior-term safety |
| `FailureTest`                 |     8 | Kill-leader recovery, minority halts, partition isolates stale leader, heal forces step-down, lagging peer catches up after reconnect, no win on minority side, double leader failure recovery |
| **Total**                     | **151** | |

## Running Locally

```bash
mvn clean test
```

Expected: `Tests run: 151, Failures: 0, Errors: 0, Skipped: 0`.

## Notable design decisions

- **Atomic snapshot via temp+rename** — `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` ensures a crash mid-write never corrupts the previous snapshot.
- **Snapshot first, then truncate WAL** — order matters. If the order were reversed, a crash between truncate and snapshot would lose every write since the previous snapshot.
- **WAL replay is idempotent on top of snapshot** — re-applying the same PUTs/DELETEs converges to the same state, so a crash between snapshot and truncate is recoverable.
- **32 virtual nodes per physical node** — empirically gives ±10% distribution across 3 nodes; 256 vnodes would tighten this further but cost more memory.

## Future improvements

- Hybrid snapshot trigger (count + WAL-size + time) — currently count-only, idle workloads can accumulate stale WAL.
- Replication factor > 1 (Day 12) — currently single replica per shard, so disk loss = data loss for that shard.
- gRPC between nodes (post-Day 14) — today's "cluster" is in-process simulation.

### Architecture pivot (Day 12 onward)

Days 7–8 built consistent-hash sharding: 3 nodes, each owns a partition of the keyspace. That gives capacity but no fault tolerance — losing a node loses its shard. Days 9–14 pivot to single-Raft-group replication: 3 nodes, one leader, every key on every node. That gives fault tolerance but trades capacity (3× storage). Both designs remain in the codebase as the canonical capacity-vs-availability trade-off.

### Planned next phase — sharded replication (multi-Raft)

Production systems (CockroachDB, TiKV, Spanner) combine sharding and replication via per-shard Raft groups. After Day 14 ships single-Raft, the natural extension is:

- Split the keyspace into N shards (the existing `HashRing` returns from dormancy as the shard locator).
- Each shard runs its own independent Raft group with its own leader + 2 followers.
- Each physical node hosts one or more `RaftNode` instances (one per shard it participates in), and plays leader for some shards, follower for others.
- Adds: shard locator, leader directory (which node leads which shard), per-shard election/heartbeat timers.
- Defers: cross-shard transactions (would need 2-phase commit on top of Raft), reconfiguration / shard splitting / merging.

The core consensus types (`RaftNode`, `RaftLog`, RPC types, transport) are already shaped for multi-instance use, so they extend without redesign — the new code is mostly orchestration around them. **Estimated effort: weeks.** Treated here as documented next direction, not committed work.

## License

MIT
