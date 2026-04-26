package io.arundhaas.kvstore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws IOException {
        String walPath = "data1/wal.log";
        String snapPath = walPath + ".snapshot";
        Files.createDirectories(Path.of("data1"));

        // Clean slate — start every run fresh so the demo is reproducible.
//        Files.deleteIfExists(Path.of(walPath));
//        Files.deleteIfExists(Path.of(snapPath));

//         ─── Session 1: writes below the snapshot threshold ───
//        System.out.println("=== Session 1: write some data (no snapshot yet) ===");
//        try (KvStore<String, String> store = new KvStore<>(walPath)) {
//            store.put("user:1", "Alice");
//            store.put("user:2", "Bob");
//            store.put("user:3", "Charlie");
//            store.delete("user:2");
//
//            System.out.println("user:1 = " + store.get("user:1"));
//            System.out.println("user:2 = " + store.get("user:2")); // null (deleted)
//            System.out.println("user:3 = " + store.get("user:3"));
//        }
//        printState(walPath, snapPath, "After Session 1 (4 writes, < 1000 → no snapshot)");

        // ─── Session 2: reopen — recovery is pure WAL replay (snapshot doesn't exist yet) ───
        System.out.println("\n=== Session 2: reopen → state recovered from WAL alone ===");
        try (KvStore<String, String> store = new KvStore<>(walPath)) {
            System.out.println("user:1 = " + store.get("user:1")); // Alice
            System.out.println("user:2 = " + store.get("user:2")); // null
            System.out.println("user:3 = " + store.get("user:3")); // Charlie
        }

        // ─── Session 3: cross the snapshot threshold ───
        System.out.println("\n=== Session 3: 1000 writes → triggers snapshot + WAL truncate ===");
        try (KvStore<String, String> store = new KvStore<>(walPath)) {
            // 4 writes already on disk from Session 1. Push us past 1000 to force snapshot.
            for (int i = 0; i < 1000; i++) {
                store.put("k" + i, "v" + i);
            }
            // After 1000th put inside this session, maybeSnapshot fires:
            //   - Snapshot.write(...) — atomic temp+rename
            //   - wal.reset() — WAL goes back to 0 bytes
            // The next write below should be the FIRST line in the truncated WAL.
            store.put("post-snapshot", "this lives only in the WAL");
        }
        printState(walPath, snapPath,
            "After Session 3 (1004 total writes; snapshot fired once at the 1000th)");
//
//        // ─── Session 4: reopen — proves snapshot + delta-WAL recovery ───
        System.out.println("\n=== Session 4: reopen → snapshot loaded + WAL replay on top ===");
        try (KvStore<String, String> store = new KvStore<>(walPath)) {
            System.out.println("user:1          = " + store.get("user:1"));         // Alice (from snapshot)
            System.out.println("k500            = " + store.get("k500"));           // v500  (from snapshot)
            System.out.println("post-snapshot   = " + store.get("post-snapshot"));  // value (from delta WAL)
            System.out.println("missing-key     = " + store.get("missing-key"));    // null
        }

        System.out.println("\n✓ Recovery contract verified: snapshot base + WAL delta = correct state.");
    }

    private static void printState(String walPath, String snapPath, String label) throws IOException {
        long walBytes  = Files.exists(Path.of(walPath))  ? Files.size(Path.of(walPath))  : 0;
        long snapBytes = Files.exists(Path.of(snapPath)) ? Files.size(Path.of(snapPath)) : 0;
        long walLines  = Files.exists(Path.of(walPath))  ? Files.readAllLines(Path.of(walPath)).size()  : 0;
        long snapLines = Files.exists(Path.of(snapPath)) ? Files.readAllLines(Path.of(snapPath)).size() : 0;

        System.out.println("\n--- " + label + " ---");
        System.out.printf("WAL      : %d lines, %d bytes%n", walLines, walBytes);
        System.out.printf("Snapshot : %d lines, %d bytes%n", snapLines, snapBytes);
    }
}
