#!/usr/bin/env bash
set -e

TRIALS=${1:-100}
WAL=/tmp/kill-test/wal.log
AUDIT=/tmp/kill-test/audit.log
PASSED=0
FAILED=0

# Compile once up front, then use plain `java` for each trial (Maven exec:java
# has ~5-10s overhead per invocation which would let kill land before the
# writer's loop even starts).
mvn -q compile

CP="target/classes"

for ((trial=1; trial<=TRIALS; trial++)); do
    rm -rf /tmp/kill-test
    mkdir -p /tmp/kill-test

    # Start writer
    java -cp "$CP" io.arundhaas.kvstore.BenchMark.KillTestWriter "$WAL" "$AUDIT" > /dev/null 2>&1 &
    WRITER_PID=$!

    # Random kill delay between 0.8s and 3.0s. Minimum 0.8s ensures the JVM has
    # started and the writer's loop is producing writes before we kill it.
    DELAY=$(awk -v min=800 -v max=3000 'BEGIN{srand();print int(min+rand()*(max-min))/1000}')
    sleep "$DELAY"

    # Hard kill
    kill -9 "$WRITER_PID" 2>/dev/null || true
    wait "$WRITER_PID" 2>/dev/null || true

    # Verify
    if java -cp "$CP" io.arundhaas.kvstore.BenchMark.KillTestVerifier "$WAL" "$AUDIT"; then
        PASSED=$((PASSED+1))
    else
        FAILED=$((FAILED+1))
        echo "Trial $trial FAILED at delay=${DELAY}s"
    fi
done

echo
echo "================================================"
echo "kill-9 durability: $PASSED/$TRIALS passed, $FAILED failed"
echo "================================================"
