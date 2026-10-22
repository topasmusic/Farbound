package de.topas.farbound;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Per-world operator policy; changing it never changes an active player's stored profile. */
public final class FarboundConfig {
    public boolean enabled = true;
    public int schema=2;
    public int maxDistance = 1000000, maxMembers = 8, cooldownSeconds = 60, homeRadius = 100;
    public Set<UUID> allowedPlayers = new HashSet<>();
    public Set<String> extraInventoryMods = new HashSet<>(Set.of("trinkets", "accessories", "curios", "inventorio", "rpginventory"));
    public List<Circle> protectedAreas = new ArrayList<>();
    public record Circle(double x, double z, double radius) {}
    public static FarboundConfig load(Path file) throws IOException {
        var json = new GsonBuilder().setPrettyPrinting().create();
        try {
            FarboundConfig config = Files.exists(file) ? json.fromJson(Files.readString(file), FarboundConfig.class) : new FarboundConfig();
            if (config == null || config.maxDistance < 10000 || config.maxDistance > 1000000 || config.maxMembers < 1 || config.maxMembers > 8
                || config.cooldownSeconds < 0 || config.cooldownSeconds > 86400 || config.homeRadius < 1 || config.homeRadius > 256
                || config.allowedPlayers == null || config.extraInventoryMods == null || config.protectedAreas == null
                || config.protectedAreas.stream().anyMatch(c -> c == null || !Double.isFinite(c.x) || !Double.isFinite(c.z) || !Double.isFinite(c.radius) || c.radius < 1))
                throw new IOException("Invalid Farbound configuration. Inspect the file; no automatic reset was performed.");
            if (!Files.exists(file)) ExpeditionStore.atomicWrite(file, json.toJson(config).getBytes(StandardCharsets.UTF_8));
            else {
                var document=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if(!document.has("schema")){
                    if(config.homeRadius==32){config.homeRadius=100;document.addProperty("homeRadius",100);}
                    document.addProperty("schema",2);ExpeditionStore.atomicWrite(file,json.toJson(document).getBytes(StandardCharsets.UTF_8));
                }
            }
            return config;
        } catch (RuntimeException ex) { throw new IOException("Farbound configuration cannot be read.", ex); }
    }
    public boolean mayStart(UUID player) { return enabled && (allowedPlayers.isEmpty() || allowedPlayers.contains(player)); }
    public boolean inventorySupported(String mode, Set<String> loadedMods) {
        return mode.equals("KEEP") || Collections.disjoint(extraInventoryMods, loadedMods);
    }
}

