# Distributed Key-Value Store

A Java implementation of a distributed KV store built from first principles — consensus, durability, and sharding.

> **Status:** 🚧 In active development · Day 1 of 14 · See roadmap below

## Goals

- **Durability** — writes survive crashes (Write-Ahead Log + snapshots)
- **Consensus** — 3-node replicated state machine with Raft
- **Sharding** — keys distributed across nodes via consistent hashing
- **Failure tolerance** — survives node crashes + network partitions

## Tech Stack

- **Language:** Java 11
- **Build:** Maven
- **Testing:** JUnit 5
- **Logging:** SLF4J + slf4j-simple

## Roadmap

| Day | Milestone                                 | Status |
| --- | ----------------------------------------- | ------ |
|  1  | Project scaffold + README                 | ✅     |
|  2  | Basic in-memory ops (get / put / delete)  | ⏳     |
|  3  | Write-Ahead Log append                    |        |
|  4  | WAL replay on startup (crash recovery)    |        |
|  5  | Periodic snapshots                        |        |
|  6  | Snapshot + WAL-tail hybrid recovery       |        |
|  7  | Consistent hashing ring                   |        |
|  8  | Multi-node routing (simulated cluster)    |        |
|  9  | Raft node state machine                   |        |
| 10  | Raft leader election (RequestVote)        |        |
| 11  | Raft heartbeats (AppendEntries)           |        |
| 12  | Raft log replication                      |        |
| 13  | Failure tests (kill-leader, partition)    |        |
| 14  | Benchmarks + architecture diagram         |        |

## Running Locally

```bash
mvn clean test
```

## License

MIT
