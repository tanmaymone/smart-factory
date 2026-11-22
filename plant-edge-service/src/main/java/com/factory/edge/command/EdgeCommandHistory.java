package com.factory.edge.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Bounded ring of recent command executions (newest first). Cap 32 for M2.5 diagnostics. */
@Component
public class EdgeCommandHistory {

    public static final int CAPACITY = 32;

    public record Entry(CommandResult result, boolean ackPublished) {}

    private final Map<String, Entry> byCommandId = new LinkedHashMap<>(CAPACITY + 1, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
            return size() > CAPACITY;
        }
    };

    public synchronized void record(CommandResult result, boolean ackPublished) {
        if (result == null || result.commandId() == null) {
            return;
        }
        if (!result.terminal()) {
            byCommandId.remove(result.commandId());
            return;
        }
        byCommandId.remove(result.commandId());
        byCommandId.put(result.commandId(), new Entry(result, ackPublished));
    }

    public synchronized void markPublished(String commandId) {
        Entry existing = byCommandId.get(commandId);
        if (existing != null && !existing.ackPublished()) {
            byCommandId.put(commandId, new Entry(existing.result(), true));
        }
    }

    public synchronized Optional<Entry> find(String commandId) {
        return Optional.ofNullable(byCommandId.get(commandId));
    }

    public synchronized List<Entry> recent() {
        List<Entry> entries = new ArrayList<>(byCommandId.values());
        java.util.Collections.reverse(entries);
        return List.copyOf(entries);
    }

    public synchronized List<CommandResult> unpublishedTerminal() {
        List<CommandResult> out = new ArrayList<>();
        for (Entry entry : byCommandId.values()) {
            if (entry.result().terminal() && !entry.ackPublished()) {
                out.add(entry.result());
            }
        }
        return List.copyOf(out);
    }
}
