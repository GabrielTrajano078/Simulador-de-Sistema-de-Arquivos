package filesystem;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Gerencia o log de operações (journaling) do sistema de arquivos simulado.
 * Utiliza write-ahead logging: registra a operação antes de aplicá-la.
 */
public class Journal {

    public enum Status {
        PENDING,
        COMMITTED
    }

    public static class JournalEntry {
        private final long id;
        private final String timestamp;
        private final String operation;
        private final String params;
        private Status status;

        public JournalEntry(long id, String timestamp, String operation, String params, Status status) {
            this.id = id;
            this.timestamp = timestamp;
            this.operation = operation;
            this.params = params;
            this.status = status;
        }

        public long getId() {
            return id;
        }

        public String getOperation() {
            return operation;
        }

        public String getParams() {
            return params;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public String serialize() {
            return id + "|" + timestamp + "|" + operation + "|" + params + "|" + status.name();
        }

        public static JournalEntry deserialize(String line) {
            String[] parts = line.split("\\|", 5);
            if (parts.length < 5) {
                throw new IllegalArgumentException("Linha de journal inválida: " + line);
            }
            return new JournalEntry(
                    Long.parseLong(parts[0]),
                    parts[1],
                    parts[2],
                    parts[3],
                    Status.valueOf(parts[4])
            );
        }
    }

    private final Path journalPath;
    private long nextId;

    public Journal(String journalFileName) {
        this.journalPath = Path.of(journalFileName);
        this.nextId = 1;
        initialize();
    }

    private void initialize() {
        if (!Files.exists(journalPath)) {
            return;
        }
        try {
            List<JournalEntry> entries = readAll();
            for (JournalEntry entry : entries) {
                nextId = Math.max(nextId, entry.getId() + 1);
            }
        } catch (IOException e) {
            throw new RuntimeException("Falha ao inicializar journal: " + e.getMessage(), e);
        }
    }

    /**
     * Registra uma operação como PENDING (write-ahead).
     */
    public synchronized JournalEntry logPending(String operation, String params) throws IOException {
        JournalEntry entry = new JournalEntry(
                nextId++,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                operation,
                params,
                Status.PENDING
        );
        appendEntry(entry);
        return entry;
    }

    /**
     * Marca uma entrada do journal como COMMITTED após persistência bem-sucedida.
     */
    public synchronized void commit(JournalEntry entry) throws IOException {
        entry.setStatus(Status.COMMITTED);
        updateEntry(entry);
    }

    /**
     * Limpa entradas já commitadas após checkpoint do sistema de arquivos.
     */
    public synchronized void clearCommitted() throws IOException {
        List<JournalEntry> entries = readAll();
        List<JournalEntry> pending = new ArrayList<>();
        for (JournalEntry entry : entries) {
            if (entry.getStatus() == Status.PENDING) {
                pending.add(entry);
            }
        }
        rewriteAll(pending);
    }

    public synchronized List<JournalEntry> readAll() throws IOException {
        List<JournalEntry> entries = new ArrayList<>();
        if (!Files.exists(journalPath)) {
            return entries;
        }
        try (BufferedReader reader = Files.newBufferedReader(journalPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    entries.add(JournalEntry.deserialize(line));
                }
            }
        }
        return entries;
    }

    public List<JournalEntry> getPendingEntries() throws IOException {
        List<JournalEntry> pending = new ArrayList<>();
        for (JournalEntry entry : readAll()) {
            if (entry.getStatus() == Status.PENDING) {
                pending.add(entry);
            }
        }
        return pending;
    }

    private void appendEntry(JournalEntry entry) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                journalPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            writer.write(entry.serialize());
            writer.newLine();
        }
    }

    private void updateEntry(JournalEntry updated) throws IOException {
        List<JournalEntry> entries = readAll();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getId() == updated.getId()) {
                entries.set(i, updated);
                break;
            }
        }
        rewriteAll(entries);
    }

    private void rewriteAll(List<JournalEntry> entries) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(
                journalPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            for (JournalEntry entry : entries) {
                writer.write(entry.serialize());
                writer.newLine();
            }
        }
    }
}
