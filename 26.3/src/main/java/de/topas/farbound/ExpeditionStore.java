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
    private final List<ExpeditionStore> stores;
    private final ExpeditionStore owner;
    private byte[] legacyBackup;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final class State { int schema = 2; Expedition expedition; List<Expedition> expeditions = new ArrayList<>(); List<Expedition.Position> knownStarts = new ArrayList<>(); Map<UUID, Long> cooldowns = new HashMap<>(); }
    public ExpeditionStore(Path root) throws IOException {
        this.root = root;
        owner = this; stores = new ArrayList<>(); stores.add(this);
        Files.createDirectories(root);
        Path file = root.resolve("expedition.json");
        if (Files.exists(file)) {
            try {
                State state = JSON.fromJson(Files.readString(file), State.class);
                if (state == null || (state.schema != 1 && state.schema != 2)) throw new IOException("Unknown Farbound data schema.");
                if (state.schema == 1) legacyBackup = Files.readAllBytes(file);
                if (state.knownStarts != null) knownStarts = state.knownStarts;
                if (state.cooldowns != null) cooldowns = state.cooldowns;
                List<Expedition> loaded = state.schema == 1 ? (state.expedition == null ? List.of() : List.of(state.expedition)) : state.expeditions;
                if (loaded == null) throw new IOException("Missing expedition registry.");
                Set<UUID> ids = new HashSet<>(), members = new HashSet<>();
                for (var e : loaded) {
                    if (e == null || e.id == null || e.leader == null || e.members == null || !e.members.containsKey(e.leader) || !ids.add(e.id)) throw new IOException("Invalid expedition data.");
                    for (var entry : e.members.entrySet()) {
                        if (entry.getKey() == null || entry.getValue() == null || entry.getValue().phase == null) throw new IOException("Invalid expedition member.");
                        if (!entry.getValue().phase.equals("FINISHED") && !members.add(entry.getKey())) throw new IOException("A player belongs to multiple open expeditions. Preserve the data for inspection.");
                    }
                    if (expedition == null) expedition = e;
                    else { var sibling = createStore(); sibling.expedition = e; }
                }
            } catch (RuntimeException ex) { throw new IOException("Farbound data is damaged. No automatic reset was performed.", ex); }
        }
    }
    private ExpeditionStore(ExpeditionStore owner) {
        this.owner = owner; root = owner.root; stores = owner.stores;
        knownStarts = owner.knownStarts; cooldowns = owner.cooldowns;
    }
    public ExpeditionStore createStore() { stores.removeIf(s -> s != owner && s.expedition == null); var store = new ExpeditionStore(owner); stores.add(store); return store; }
    public List<ExpeditionStore> activeStores() { return stores.stream().filter(s -> s.expedition != null).toList(); }
    public void save() throws IOException {
        State state = new State(); state.expeditions = activeStores().stream().map(s -> s.expedition).toList(); state.knownStarts = knownStarts; state.cooldowns = cooldowns;
        if (owner.legacyBackup != null) {
            Path backup = root.resolve("expedition-before-1.0.1.json");
            if (Files.exists(backup) && !Arrays.equals(Files.readAllBytes(backup), owner.legacyBackup)) throw new IOException("Migration backup already exists with different content. Preserve and inspect both files.");
            if (!Files.exists(backup)) atomicWrite(backup, owner.legacyBackup);
        }
        atomicWrite(root.resolve("expedition.json"), JSON.toJson(state).getBytes(StandardCharsets.UTF_8));
        owner.legacyBackup = null;
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

