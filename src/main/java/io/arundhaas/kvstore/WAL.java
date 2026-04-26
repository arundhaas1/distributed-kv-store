package io.arundhaas.kvstore;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Write-ahead log: every mutation is appended + fsynced before it ever touches memtable.
public class WAL implements AutoCloseable {

    private final String filePath;
    private FileOutputStream out;

    public WAL(String filePath) throws FileNotFoundException {
        this.filePath = filePath;
        this.out = new FileOutputStream(filePath, true); // append
    }

    public void append(String method, String key, String value) throws IOException {
        String line = method + "|" + key + "|" + (value != null ? value : "") + "\n";
        out.write(line.getBytes(StandardCharsets.UTF_8));
        out.getFD().sync(); // durability: force bytes to disk before returning
    }
    
    public void reset() throws IOException {
    	out.close();
        Files.write(Path.of(filePath), new byte[0]); // overwrite to 0 bytes
        this.out = new FileOutputStream(filePath, true);
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
