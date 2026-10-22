package de.topas.farbound;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
import java.util.*;

public final class ExpeditionStore {
    public Expedition expedition;
    public List<Expedition.Position> knownStarts = new ArrayList<>();
    public Map<UUID, Long> cooldowns = new HashMap<>();
    private final Path root;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final class State { int schema = 1; Expedition expedition; List<Expedition.Position> knownStarts = new ArrayList<>(); Map<UUID, Long> cooldowns = new HashMap<>(); }
    public ExpeditionStore(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root);
        Path file = root.resolve("expedition.json");
        if (Files.exists(file)) {
            try {
                State state = JSON.fromJson(Files.readString(file), State.class);
                if (state == null || state.schema != 1) throw new IOException("Unknown Farbound data schema.");
                expedition = state.expedition;
                if (state.knownStarts != null) knownStarts = state.knownStarts;
                if (state.cooldowns != null) cooldowns = state.cooldowns;
                if (expedition != null && (expedition.id == null || expedition.leader == null || expedition.members == null
                    || !expedition.members.containsKey(expedition.leader))) throw new IOException("Invalid expedition data.");
            } catch (RuntimeException ex) { throw new IOException("Farbound data is damaged. No automatic reset was performed.", ex); }
        }
    }
    public void save() throws IOException {
        State state = new State(); state.expedition = expedition; state.knownStarts = knownStarts; state.cooldowns = cooldowns;
        atomicWrite(root.resolve("expedition.json"), JSON.toJson(state).getBytes(StandardCharsets.UTF_8));
    }
    public static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "farbound-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    public void archive() throws IOException {
        if (expedition != null) atomicWrite(root.resolve("history").resolve(expedition.id + ".json"), JSON.toJson(expedition).getBytes(StandardCharsets.UTF_8));
        expedition = null;
        save();
    }
}

