package io.arundhaas.kvstore.BenchMark;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.arundhaas.kvstore.KvStore;

public class KillTestWriter {
	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("Usage: KillTestWriter <walPath> <auditPath>");
			System.exit(2);
		}
		String walPath = args[0];
		String auditPath = args[1];
		Files.createDirectories(Path.of(walPath).getParent());

		try (KvStore<String, String> store = new KvStore<>(walPath); FileOutputStream audit = new FileOutputStream(auditPath, true)) {
			int i = 1;
			while (true) {
				store.put("k" + i, "v" + i);
				String line = i + "\n";
				audit.write(line.getBytes(StandardCharsets.UTF_8));
				audit.getFD().sync();
				i++;
				if (i % 100 == 0)
					Thread.sleep(1);
			}
		}
	}
}