package io.arundhaas.kvstore;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

//Write ahead Log
public class WAL implements AutoCloseable{
	private final FileOutputStream out;
	
	public WAL(String filePath) throws FileNotFoundException{
		out = new FileOutputStream(filePath, true); //Allow append
	}
	
	public void append(String method, String key, String value) throws IOException {
		String line = method + "|" + key + "|"+ (value != null ? value : "") + "\n";
		out.write(line.getBytes(StandardCharsets.UTF_8));
		out.getFD().sync(); // durability: force to disk
	}
	
	@Override
	public void close() throws IOException {
		out.close();
	}
}