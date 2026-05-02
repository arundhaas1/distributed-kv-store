package io.arundhaas.kvstore.BenchMark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.arundhaas.kvstore.KvStore;

public class KillTestVerifier {
	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage: KillTestVerifier <walPath> <auditPath>");
			System.exit(2);
		}
		String walPath = args[0];
		String auditPath = args[1];

		// 1. What did the writer claim to have completed?
		List<String> auditLines = Files.exists(Path.of(auditPath)) ? Files.readAllLines(Path.of(auditPath)) : List.of();
		int lastAcked = auditLines.isEmpty() ? 0 : Integer.parseInt(auditLines.get(auditLines.size() - 1).trim());

		// 2. Open the store — triggers WAL replay + snapshot load.
		int missing = 0, corrupt = 0;
		try (KvStore<String, String> store = new KvStore<>(walPath)) {
			for (int i = 1; i <= lastAcked; i++) {
				String got = store.get("k" + i);
				String want = "v" + i;
				if (got == null) {
					missing++;
					continue;
				}
				if (!want.equals(got))
					corrupt++;
			}
		}

		if (missing == 0 && corrupt == 0) {
			System.out.println("PASS  acked=" + lastAcked + "  missing=0  corrupt=0");
		} else {
			System.out.println("FAIL  acked=" + lastAcked + "  missing=" + missing + "  corrupt=" + corrupt);
			System.exit(1);
		}
	}
}