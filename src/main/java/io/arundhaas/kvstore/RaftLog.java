package io.arundhaas.kvstore;

import io.arundhaas.kvstore.Modals.LogEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RaftLog {
	private final List<LogEntry> entries = new ArrayList<>();

	public int lastIndex() {
		return entries.size();
	}

	public int lastTerm() {
		if (entries.isEmpty())
			return 0;
		return entries.get(entries.size() - 1).getTerm();
	}

	public LogEntry get(int index) {
		if (index < 1 || index > entries.size())
			return null;
		return entries.get(index - 1);
	}

	public boolean matches(int index, int term) {
		if (index == 0)
			return term == 0;
		LogEntry entry = get(index);
		return entry != null && entry.getTerm() == term;
	}

	public void append(LogEntry entry) {
		Objects.requireNonNull(entry, "entry required");
		entries.add(entry);
	}

	public void truncateAfter(int index) {
		while (entries.size() > index) {
			entries.remove(entries.size() - 1);
		}
	}

	public List<LogEntry> from(int startIndex) {
		if (startIndex < 1 || startIndex > entries.size())
			return List.of();
		return List.copyOf(entries.subList(startIndex - 1, entries.size()));
	}
}