package de.topas.farbound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;
import java.io.*;
import java.util.*;

public final class ExpeditionService implements AutoCloseable {
    private final MinecraftServer server;
    public final ExpeditionStore store;
    public final FarboundConfig config;
    private SpawnSearch search;
    private int ticks, remaining;
    private boolean failed;
    private final Map<UUID, String> notices = new HashMap<>();
    public ExpeditionService(MinecraftServer server) {
        this.server = server;
        try {
            store = new ExpeditionStore(server.getWorldPath(LevelResource.ROOT).resolve("farbound"));
            config = FarboundConfig.load(server.getWorldPath(LevelResource.ROOT).resolve("farbound/config.json"));
            var e = store.expedition;
            if (e != null && (e.status.equals("SEARCHING") || e.status.equals("COUNTDOWN"))) { e.status = "DRAFT"; e.changed(); store.save(); }
            if (e != null && e.status.equals("STARTING")) {
                if (e.target == null) throw new IOException("Interrupted departure has no confirmed destination.");
                e.status = "ACTIVE";
                if (!store.knownStarts.contains(e.target)) store.knownStarts.add(e.target);
                store.save();
            }
            if (e != null && e.members.values().stream().allMatch(m -> m.phase.equals("FINISHED"))) store.archive();
        } catch (IOException ex) { throw new IllegalStateException("Farbound data cannot be read. Preserve the original files; inspect them before continuing this world.", ex); }
    }
    public Expedition expedition() { return store.expedition; }
    public Expedition.Member member(ServerPlayer p) { return expedition() == null ? null : expedition().members.get(p.getUUID()); }
    public boolean active(ServerPlayer p) {
        var m = member(p);
        return m != null && Set.of("PREPARED", "ACTIVE", "RESTORING", "PAUSING", "RESUMING", "RECOVERY_REQUIRED").contains(m.phase);
    }
    public void handle(ServerPlayer p, String action, String value, int revision) {
        try {
            notices.remove(p.getUUID());
            if (failed) throw new IllegalStateException("Farbound storage error. Check the data and restart the server.");
            if (action.equals("open")) { open(p); return; }
            if(action.startsWith("admin_")){
                if(!operator(p))throw new IllegalStateException("Operator permission required.");
                if(expedition()!=null&&revision>=0&&revision!=expedition().revision)throw new IllegalStateException("Review the updated server tools first.");
                switch(action){case "admin_discard"->adminDiscardDraft();case "admin_retry"->{var target=value.isEmpty()?p:server.getPlayerList().getPlayer(UUID.fromString(value));if(target==null)throw new IllegalStateException("The player must be online.");adminRetry(target);}case "admin_force"->{var parts=value.split(" ",2);if(parts.length!=2)throw new IllegalArgumentException("Choose a dimension rule.");adminForceRule(parts[0],parts[1]);}default->throw new IllegalArgumentException("Unknown operator action.");}
                sendView(p);return;
            }
            var e = expedition();
            if (e == null) throw new IllegalStateException("Open /farbound first.");
            if (revision >= 0 && !ExpeditionPolicy.acceptRevision(revision, e.revision)) throw new IllegalStateException("This view is out of date. Please review the current settings.");
            if (action.equals("accept")) { accept(p); broadcast(); return; }
            var m = requireMember(p);
            if (m.phase.equals("RECOVERY_REQUIRED")) throw new IllegalStateException("Stored profile locked: data reconciliation required.");
            switch (action) {
                case "configure" -> configure(p, value);
                case "invite" -> invite(p, value);
                case "ready" -> {
                    requireDraft();
                    eligible(p);
                    if (m.inventory.equals("EMPTY") && !p.getInventory().isEmpty()) throw new IllegalStateException("Empty your inventory, armor and offhand first.");
                    m.ready = !m.ready; store.save();
                }
                case "launch" -> launch(p);
                case "cancel" -> {
                    requireLeader(p);
                    if (e.status.equals("ACTIVE") || e.status.equals("STARTING")) throw new IllegalStateException("Each participant must finish their own active trip.");
                    if (search != null) search.close(); search = null;
                    e.status = "DRAFT"; e.changed(); store.save();
                }
                case "vote" -> { e.vote(p.getUUID()); store.save(); applyRules(); }
                case "end" -> finish(p, false);
                case "pause" -> pause(p);
                case "resume" -> resume(p);
                case "abort" -> {
                    if (e.untouchedPreparation()) {
                        if (!e.status.equals("DRAFT")) cancelStart("Departure canceled before any profile change.");
                        leavePreparation(p);
                    } else if(m.phase.equals("PAUSED")) abandonPaused(p); else finish(p, true);
                }
                case "leave" -> {
                    leavePreparation(p);
                }
                default -> throw new IllegalStateException("Unknown Farbound action.");
            }
            broadcast();
        } catch (IllegalStateException | IllegalArgumentException ex) {
            // Once a journal intent is durable, do not release a player with a stale
            // switch snapshot. Recovery must finish before they can change either profile.
            var m=member(p);
            if(m!=null&&Set.of("PAUSING","RESUMING","ABANDONING").contains(m.phase)){storageFailure(p,ex);return;}
            notices.put(p.getUUID(), ex.getMessage()); message(p, ex.getMessage());
            var v=view(p);v.open=action.equals("open");FarboundNetwork.send(p,v);
        }
        catch (IOException ex) { storageFailure(p, ex); }
        catch (RuntimeException ex) { storageFailure(p,ex); }
    }
    public void open(ServerPlayer p) throws IOException {
        var previous=expedition();
        if (previous!=null && !previous.members.containsKey(p.getUUID()) && previous.untouchedPreparation()
            && Set.of("HOME","FINISHED").contains(PlayerVault.phase(p))
            && previous.status.equals("DRAFT") && server.isSingleplayer() && server.isSingleplayerOwner(p.nameAndId())
            && previous.members.keySet().stream().allMatch(id -> server.getPlayerList().getPlayer(id)==null)) {
            requireApproval(p); eligible(p);
            store.archive();
            message(p,"Archived an untouched preparation from an offline singleplayer profile. No items or XP were changed.");
        }
        if (expedition() == null) {
            if (!Set.of("HOME","FINISHED").contains(PlayerVault.phase(p))) throw new IllegalStateException("Stored profile has no matching expedition. Check the data.");
            long wait=store.cooldowns.getOrDefault(p.getUUID(),0L)-System.currentTimeMillis();
            if(wait>0)throw new IllegalStateException("Please wait "+((wait+999)/1000)+" seconds before starting another expedition.");
            requireApproval(p); eligible(p);
            store.expedition = new Expedition(p.getUUID());
            store.save();
        }
        var e = expedition();
        if (!e.members.containsKey(p.getUUID()) && !e.invitations.containsKey(p.getUUID())&&!operator(p)) throw new IllegalStateException(
            "Another expedition is already open ("+e.status+"). Ask its leader to invite you or leave. Operators can inspect and archive untouched preparations in Server tools.");
        var view = view(p); view.open = true; FarboundNetwork.send(p, view);
    }
    private void leavePreparation(ServerPlayer p) throws IOException {
        requireDraft(); var e=expedition(); requireMember(p);
        if (!e.untouchedPreparation()) throw new IllegalStateException("This preparation has profile transitions and cannot be discarded. Contact an operator.");
        checkUnstoredOnlineProfiles(e);
        var ids=new ArrayList<>(e.members.keySet());
        if (e.leader.equals(p.getUUID())) store.archive();
        else {e.members.remove(p.getUUID());e.changed();store.save();}
        for(var id:ids){var member=server.getPlayerList().getPlayer(id);if(member!=null)sendView(member);}
        message(p,"Preparation left. No inventory or XP was changed.");
    }
    private void configure(ServerPlayer p, String value) throws IOException {
        String[] parts = value.split(" ", 2);
        if (parts.length != 2) throw new IllegalStateException("Specify a setting and a value.");
        var e = expedition(); var m = requireMember(p);
        String key = parts[0], setting = parts[1];
        if (!Set.of("inventory", "ender", "xp").contains(key)) requireLeader(p);
        if (e.status.equals("ACTIVE") && (key.equals("nether") || key.equals("end"))) {
            boolean blocked = choice(setting, "blocked", "allowed");
            e.propose(key.equals("nether") ? blocked : e.netherBlocked, key.equals("end") ? blocked : e.endBlocked);
            e.vote(p.getUUID()); store.save(); applyRules(); return;
        }
        requireDraft();
        switch (key) {
            case "distance" -> { int distance = Integer.parseInt(setting); if (distance > config.maxDistance || !Set.of(10000, 50000, 100000, 1000000).contains(distance)) throw new IllegalStateException("Distance exceeds server limits. Presets: 10000, 50000, 100000 or 1000000."); e.distance = distance; }
            case "reference" -> e.worldSpawn = choice(setting, "spawn", "here");
            case "mode" -> e.homeward = choice(setting, "homeward", "restart");
            case "inventory" -> { if (!Set.of("STORE", "KEEP").contains(setting)) throw new IllegalStateException("Choose Store at home or Take everything."); m.inventory = setting; }
            case "ender" -> m.enderStore = choice(setting, "store", "keep");
            case "xp" -> m.xpStore = choice(setting, "store", "keep");
            case "nether" -> e.netherBlocked = choice(setting, "blocked", "allowed");
            case "end" -> e.endBlocked = choice(setting, "blocked", "allowed");
            case "countdown" -> { int seconds = Integer.parseInt(setting); if (!Set.of(0, 5, 10, 15, 30).contains(seconds)) throw new IllegalStateException("Countdown: 0, 5, 10, 15 or 30."); e.countdown = seconds; }
            default -> throw new IllegalStateException("Unknown setting.");
        }
        e.changed(); store.save();
    }
    private static boolean choice(String value, String yes, String no) {
        if (!value.equals(yes) && !value.equals(no)) throw new IllegalStateException("Allowed: " + yes + " / " + no);
        return value.equals(yes);
    }
    private void invite(ServerPlayer p, String name) throws IOException {
        requireLeader(p); requireDraft();
        var friend = server.getPlayerList().getPlayerByName(name);
        if (friend == null) throw new IllegalStateException("The player must be online.");
        var e = expedition();
        if (e.members.containsKey(friend.getUUID())) throw new IllegalStateException("The player is already a member.");
        if (e.invitations.getOrDefault(friend.getUUID(), 0L) > System.currentTimeMillis()) throw new IllegalStateException("An invitation is already pending.");
        e.invitations.put(friend.getUUID(), System.currentTimeMillis() + 300000); store.save();
        message(friend, p.getPlainTextName() + " invited you. Open /farbound to join voluntarily."); sendView(friend);
    }
    private void accept(ServerPlayer p) throws IOException {
        requireDraft(); var e = expedition();
        if (e.invitations.getOrDefault(p.getUUID(), 0L) < System.currentTimeMillis()) throw new IllegalStateException("No valid invitation.");
        if (e.members.size() >= config.maxMembers) throw new IllegalStateException("Participant limit reached.");
        requireApproval(p); eligible(p);
        if (!Set.of("HOME","FINISHED").contains(PlayerVault.phase(p))) throw new IllegalStateException("The player already has a stored profile.");
        if(store.cooldowns.getOrDefault(p.getUUID(),0L)>System.currentTimeMillis())throw new IllegalStateException("Please wait "+remainingCooldown(p)+" seconds before joining another expedition.");
        e.invitations.remove(p.getUUID()); e.members.put(p.getUUID(), new Expedition.Member()); e.changed(); store.save();
    }
    private void launch(ServerPlayer p) throws IOException {
        requireLeader(p); requireDraft(); var e = expedition();
        checkGroup();
        var origin = e.worldSpawn ? server.overworld().getRespawnData().pos() : p.blockPosition();
        e.originX = origin.getX(); e.originZ = origin.getZ();
        var protectedStarts = new ArrayList<>(store.knownStarts);
        var spawn = server.overworld().getRespawnData().pos();
        protectedStarts.add(position(server.overworld(), Vec3.atCenterOf(spawn), 0, 0));
        for (var player : server.getPlayerList().getPlayers()) protectedStarts.add(position(player));
        search = new SpawnSearch(server.overworld(), e, protectedStarts, config.protectedAreas);
        e.status = "SEARCHING"; store.save();
    }
    private void checkGroup() {
        if (expedition().distance > config.maxDistance || expedition().members.size() > config.maxMembers) throw new IllegalStateException("Departure settings exceed server limits.");
        for (var entry : expedition().members.entrySet()) {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !entry.getValue().ready) throw new IllegalStateException("All participants must be online and ready.");
            requireApproval(player); eligible(player);
            var loaded = net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().stream().map(mod -> mod.getMetadata().getId()).collect(java.util.stream.Collectors.toSet());
            if (!config.inventorySupported(entry.getValue().inventory, loaded)) throw new IllegalStateException("Extra inventory mod detected. Only KEEP is supported without an adapter.");
            if (entry.getValue().inventory.equals("EMPTY") && !player.getInventory().isEmpty()) throw new IllegalStateException(player.getPlainTextName() + ": inventory is not empty.");
        }
    }
    public static void eligible(ServerPlayer p) {
        if (!p.isAlive() || p.gameMode() != GameType.SURVIVAL || p.isSleeping() || p.isPassenger() || p.isVehicle()
            || p.hurtTime > 0 || p.getLastDamageSource(200) != null)
            throw new IllegalStateException("Start or finish alive in Survival, out of combat, awake and without a vehicle.");
        if (p.level().getServer().getWorldData().isHardcore()) throw new IllegalStateException("Hardcore is not supported in this test version.");
        if (!p.level().dimension().equals(Level.OVERWORLD)) throw new IllegalStateException("Return to the Overworld first.");
        var nearby = p.level().getEntities(p, p.getBoundingBox().inflate(12), entity -> entity instanceof net.minecraft.world.entity.Mob mob && mob.isLeashed() && mob.getLeashHolder() == p);
        if (!nearby.isEmpty()) throw new IllegalStateException("Leave leashed animals safely behind first.");
    }
    public void tick() {
        if (failed) return;
        ticks++;
        var e = expedition(); if (e == null) return;
        try {
            if (e.status.equals("SEARCHING") || e.status.equals("COUNTDOWN")) {
                try { checkGroup(); } catch (IllegalStateException ex) { cancelStart(ex.getMessage()); return; }
                if (e.status.equals("SEARCHING")) {
                    search.tick();
                    if (search.failed) { cancelStart("No safe destination found. Please review the settings."); return; }
                    if (search.result != null) {
                        e.target = position(server.overworld(), Vec3.atBottomCenterOf(search.result.getFirst()), 0, 0);
                        e.status = "COUNTDOWN"; remaining = e.countdown * 20; store.save(); broadcast();
                    }
                } else if (--remaining <= 0) startGroup();
            }
            if (e.status.equals("ACTIVE") && e.members.entrySet().stream().anyMatch(entry -> entry.getValue().phase.equals("ACTIVE") && server.getPlayerList().getPlayer(entry.getKey()) != null)) e.elapsedTicks++;
            if (ticks % 20 == 0) { applyRules(); broadcast(); }
            if (ticks % 200 == 0) store.save();
        } catch (Exception ex) { storageFailure(null, ex); }
    }
    private void cancelStart(String reason) throws IOException {
        if (search != null) search.close(); search = null;
        expedition().status = "DRAFT"; expedition().changed(); store.save();
        for (var id : expedition().members.keySet()) { var p = server.getPlayerList().getPlayer(id); if (p != null) message(p, reason); }
        broadcast();
    }
    private void startGroup() throws IOException {
        var e = expedition(); checkGroup();
        List<ServerPlayer> players = e.members.keySet().stream().map(id -> server.getPlayerList().getPlayer(id)).toList();
        if (search == null || search.result == null || search.result.stream().anyMatch(pos -> !SpawnSearch.safe(server.overworld(), pos))) { cancelStart("The destination changed. Search again before departing."); return; }
        Map<UUID, net.minecraft.nbt.CompoundTag> snapshots = new HashMap<>();
        for (var p : players) {
            p.closeContainer(); var m = member(p);
            if (m.inventory.equals("EMPTY") && !p.getInventory().isEmpty()) { cancelStart("Items remain after closing your container. Empty the inventory first."); return; }
            m.home = position(p); m.nonce = UUID.randomUUID().toString();
            snapshots.put(p.getUUID(), PlayerVault.capture(p, e, m));
        }
        e.status = "STARTING"; e.members.values().forEach(m -> m.phase = "PREPARED"); store.save();
        for (int i = 0; i < players.size(); i++) {
            var p = players.get(i); var m = member(p);
            try {
                PlayerVault.begin(p, snapshots.get(p.getUUID()));
                var pos = search.result.get(i);
                p.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, pos, 0, 0), true), false);
                if (!p.teleportTo(server.overworld(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, Set.of(), 0, 0, true)) throw new IOException("Departure teleport failed.");
                if (!m.inventory.equals("KEEP")) { p.setHealth(p.getMaxHealth()); p.getFoodData().setFoodLevel(20); p.getFoodData().setSaturation(5); p.removeAllEffects(); }
                PlayerVault.persist(p); m.phase = "ACTIVE"; store.save();
            } catch (Exception ex) { storageFailure(p, ex); return; }
        }
        e.status = "ACTIVE"; store.knownStarts.add(e.target); store.save();
        search.close(); search = null;
        broadcast();
    }
    private void finish(ServerPlayer p, boolean abort) throws IOException {
        var e = expedition(); var m = requireMember(p);
        if (!m.phase.equals("ACTIVE") || !e.status.equals("ACTIVE")) throw new IllegalStateException("No active expedition to finish.");
        eligible(p);
        if (e.homeward && !abort && (Math.hypot(p.getX() - m.home.x(), p.getZ() - m.home.z()) > config.homeRadius || !p.level().dimension().identifier().toString().equals(m.home.dimension())))
            throw new IllegalStateException("Reach your departure area within "+config.homeRadius+" horizontal blocks in the same dimension, or use Emergency return in the menu.");
        p.closeContainer();
        var tag = PlayerVault.data(p);
        if (!ExpeditionPolicy.canRestore(tag.getBooleanOr("inventoryStored", false), tag.getBooleanOr("enderStored", false), p.getInventory().isEmpty(), p.getEnderChestInventory().isEmpty()))
            throw new IllegalStateException("Empty your inventory and expedition ender chest into ordinary chests first.");
        m.phase = "RESTORING"; store.save();
        try {
            returnHome(p, m);
            completeMember(p, m);
            message(p, abort ? "Expedition abandoned. Your home profile has been restored." : "Expedition complete. Your home profile has been restored.");
        } catch (Exception ex) { storageFailure(p, ex); }
    }
    private void pause(ServerPlayer p) throws IOException {
        var m=requireMember(p);
        if(!expedition().status.equals("ACTIVE")||!m.phase.equals("ACTIVE"))throw new IllegalStateException("Only an active expedition can be paused.");
        eligible(p);
        if(!PlayerVault.data(p).getBooleanOr("inventoryStored",false)||!PlayerVault.data(p).getBooleanOr("xpStored",false))
            throw new IllegalStateException("Pause requires Store at home inventory and stored XP.");
        switchPosition(m.homeReturn==null?m.home:m.homeReturn);
        p.closeContainer();m.pausedAt=position(p);m.phase="PAUSING";store.save();
        continueSwitch(p,false);
    }
    private void resume(ServerPlayer p) throws IOException {
        var m=requireMember(p);if(!m.phase.equals("PAUSED"))throw new IllegalStateException("No paused expedition to resume.");
        eligible(p);switchPosition(m.pausedAt);
        p.closeContainer();m.homeReturn=position(p);m.phase="RESUMING";store.save();continueSwitch(p,true);
    }
    private BlockPos switchPosition(Expedition.Position position){
        if(position==null)throw new IllegalStateException("Saved position is unavailable.");
        var destination=level(position.dimension());
        var safe=destination==null?null:SpawnSearch.nearby(destination,BlockPos.containing(position.x(),position.y(),position.z()));
        if(safe==null)throw new IllegalStateException("Saved destination is unsafe. Your current profile is unchanged.");
        return safe;
    }
    private void continueSwitch(ServerPlayer p,boolean resume) throws IOException {
        var m=member(p);String target=resume?"ACTIVE":"PAUSED",intent=resume?"RESUMING":"PAUSING";
        String problem=loginProblem(p);if(problem!=null)throw new IOException(problem);
        if(!PlayerVault.phase(p).equals(target)){
            var destination=resume?m.pausedAt:m.homeReturn==null?m.home:m.homeReturn;
            var safe=switchPosition(destination);
            if(!PlayerVault.phase(p).equals(intent))PlayerVault.prepareSwitch(p,expedition(),m,resume);
            if(!p.teleportTo(level(destination.dimension()),safe.getX()+.5,safe.getY(),safe.getZ()+.5,Set.of(),destination.yaw(),destination.pitch(),true))throw new IOException("Profile switch teleport rejected.");
            PlayerVault.completeSwitch(p,resume);
        }
        m.phase=target;store.save();sendView(p);
    }
    private void abandonPaused(ServerPlayer p) throws IOException {
        var m=member(p);m.phase="ABANDONING";store.save();completePausedAbandon(p);
    }
    private void completePausedAbandon(ServerPlayer p) throws IOException {
        if(!Set.of("PAUSED","FINISHED").contains(PlayerVault.phase(p)))throw new IOException("No matching paused profile to abandon.");
        // The current home profile stays live; the inactive expedition is discarded.
        var marker=new net.minecraft.nbt.CompoundTag();
        marker.putString("expedition",expedition().id.toString());marker.putString("nonce",member(p).nonce);marker.putString("phase","FINISHED");
        ((VaultCarrier)p).farbound$vault(marker);PlayerVault.persist(p);completeMember(p,member(p));
    }
    private void returnHome(ServerPlayer p, Expedition.Member m) throws IOException {
        var destination=m.homeReturn==null?m.home:m.homeReturn;
        ServerLevel homeLevel = level(destination.dimension());
        if (homeLevel == null) throw new IOException("Home dimension is missing. Your stored profile remains locked.");
        BlockPos home = SpawnSearch.nearby(homeLevel, BlockPos.containing(destination.x(), destination.y(), destination.z()));
        if (home == null) throw new IOException("No safe home position. Your stored profile remains locked.");
        if (!p.teleportTo(homeLevel, home.getX() + .5, home.getY(), home.getZ() + .5, Set.of(), destination.yaw(), destination.pitch(), true))
            throw new IOException("Home teleport rejected. No original items or XP released.");
        // Durable home position first: FINISHED always means the original profile is at home.
        PlayerVault.persist(p);
        PlayerVault.restore(p);
    }
    private void completeMember(ServerPlayer p, Expedition.Member m) throws IOException {
        m.phase = "FINISHED"; m.finishedAt = System.currentTimeMillis();
        store.cooldowns.put(p.getUUID(), m.finishedAt + config.cooldownSeconds * 1000L); store.save();
        sendView(p);
        if (expedition().members.values().stream().allMatch(other -> other.phase.equals("FINISHED"))) store.archive();
    }
    public boolean dimensionAllowed(ServerPlayer p, ServerLevel destination) {
        var e = expedition();
        boolean allowed = ExpeditionPolicy.dimensionAllowed(active(p), e != null && e.netherBlocked, e != null && e.endBlocked, destination.dimension().identifier().toString());
        if (!allowed) message(p, "This dimension is blocked for your expedition.");
        return allowed;
    }
    public TeleportTransition respawn(ServerPlayer p, TeleportTransition vanilla, TeleportTransition.PostTeleportTransition action) {
        if (!active(p)) return vanilla;
        var config = p.getRespawnConfig();
        if (config != null && !config.forced() && !vanilla.missingRespawnBlock() && dimensionAllowed(p, vanilla.newLevel())
            && vanilla.newLevel().dimension().equals(config.respawnData().dimension())) return vanilla;
        var anchor = expedition().target;
        var level = server.overworld();
        var pos = SpawnSearch.nearby(level, BlockPos.containing(anchor.x(), anchor.y(), anchor.z()));
        if (pos == null) {
            p.connection.disconnect(Component.literal("Farbound: Expedition anchor is unsafe. Secure the start area before rejoining."));
            return new TeleportTransition(level, new Vec3(anchor.x(), anchor.y(), anchor.z()), Vec3.ZERO, 0, 0, action);
        }
        return new TeleportTransition(level, Vec3.atBottomCenterOf(pos), Vec3.ZERO, 0, 0, action);
    }
    public boolean respawnReady(ServerPlayer p) {
        if (!active(p)) return true;
        var anchor = expedition().target;
        if (anchor != null && SpawnSearch.nearby(server.overworld(), BlockPos.containing(anchor.x(), anchor.y(), anchor.z())) != null) return true;
        p.connection.disconnect(Component.literal("Farbound: Expedition anchor is unsafe. An operator must secure the area before respawning."));
        return false;
    }
    /** Runs before PlayerList releases the player to the world/network. No inventory changes here. */
    public String loginProblem(ServerPlayer p) {
        var m = member(p); String phase = PlayerVault.phase(p);
        if (failed && ((m != null && !Set.of("HOME", "FINISHED").contains(m.phase)) || !Set.of("HOME","FINISHED").contains(phase))) return "Farbound storage is suspended.";
        var tag = PlayerVault.data(p);
        boolean matching = expedition() != null && tag.getStringOr("expedition", "").equals(expedition().id.toString())
            && tag.getStringOr("nonce", "").equals(m == null || m.nonce == null ? "" : m.nonce);
        if (m == null || m.phase.equals("HOME"))
            return phase.equals("HOME") || phase.equals("FINISHED") ? null : "Active profile has no matching world state.";
        if (m.phase.equals("PREPARED") && !matching && (phase.equals("HOME") || phase.equals("FINISHED"))) return null;
        if (!matching) return "Profile identifier does not match the expedition.";
        return ExpeditionPolicy.reconcile(m.phase, phase).equals("RECOVERY_REQUIRED") ? "Conflicting saved states." : null;
    }
    public void joined(ServerPlayer p) {
        try {
            String problem = loginProblem(p);
            if (problem != null) throw new IOException(problem);
            var m = member(p);
            if (m == null || m.phase.equals("HOME")) return;
            var e = expedition();
            if(m.phase.equals("PAUSING")||m.phase.equals("RESUMING")){continueSwitch(p,m.phase.equals("RESUMING"));return;}
            if(m.phase.equals("ABANDONING")){completePausedAbandon(p);return;}
            if (m.phase.equals("PREPARED")) {
                String phase = PlayerVault.phase(p);
                if (phase.equals("HOME") || phase.equals("FINISHED")) PlayerVault.begin(p, PlayerVault.capture(p, e, m));
                else if (phase.equals("PREPARED")) PlayerVault.begin(p, PlayerVault.data(p).copy());
                var pos = SpawnSearch.nearby(server.overworld(), BlockPos.containing(e.target.x(), e.target.y(), e.target.z()));
                if (pos == null) throw new IOException("Expedition destination is unsafe.");
                p.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, pos, 0, 0), true), false);
                if (!p.teleportTo(server.overworld(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, Set.of(), 0, 0, true)) throw new IOException("Recovery teleport was rejected.");
                if (!m.inventory.equals("KEEP")) { p.setHealth(p.getMaxHealth()); p.getFoodData().setFoodLevel(20); p.getFoodData().setSaturation(5); p.removeAllEffects(); }
                PlayerVault.persist(p); m.phase = "ACTIVE"; store.save();
            } else if (m.phase.equals("RESTORING")) {
                if (PlayerVault.phase(p).equals("ACTIVE")) returnHome(p, m);
                completeMember(p, m);
                return;
            }
            if (e.status.equals("STARTING") && e.members.values().stream().allMatch(other -> other.phase.equals("ACTIVE") || other.phase.equals("FINISHED"))) {
                e.status = "ACTIVE";
                if (!store.knownStarts.contains(e.target)) store.knownStarts.add(e.target);
                store.save();
            }
            if (m.phase.equals("ACTIVE")) message(p, "Your expedition continues. /farbound");
        } catch (Exception ex) { storageFailure(p, ex); }
    }
    private void applyRules() throws IOException {
        var e = expedition(); if (e == null || e.proposedNether == null) return;
        boolean occupied = e.members.entrySet().stream().anyMatch(entry -> {
            if (entry.getValue().phase.equals("FINISHED")) return false;
            var p = server.getPlayerList().getPlayer(entry.getKey());
            if (p == null) return true;
            String dimension = p.level().dimension().identifier().toString();
            return e.proposedNether && dimension.equals("minecraft:the_nether") || e.proposedEnd && dimension.equals("minecraft:the_end");
        });
        if (e.applyProposal(occupied)) store.save();
    }
    public void sendView(ServerPlayer p) { FarboundNetwork.send(p, view(p)); }
    public FarboundNetwork.View view(ServerPlayer p) {
        var e = expedition(); var v = new FarboundNetwork.View();
        v.admin=operator(p);v.adminInfo=v.admin?adminInspect():"";v.homeRadius=config.homeRadius;
        v.cooldownSeconds=remainingCooldown(p);
        v.notice = notices.getOrDefault(p.getUUID(), "");
        if (e == null) return v;
        v.occupied=true;v.revision=e.revision;v.netherBlocked=e.netherBlocked;v.endBlocked=e.endBlocked;
        var m = member(p);
        if(v.admin)for(var entry:e.members.entrySet()){var id=entry.getKey();v.inspection.add(id+" · "+entry.getValue().phase);var online=server.getPlayerList().getPlayer(id);if(online!=null)v.recovery.add(new FarboundNetwork.Online(id,online.getPlainTextName(),false));}
        if (m == null && e.invitations.getOrDefault(p.getUUID(), 0L) < System.currentTimeMillis()) return v;
        if (m != null && m.phase.equals("FINISHED")) return v;
        v.revision = e.revision; v.status = e.status; v.leader = e.leader.equals(p.getUUID()); v.invited = m == null;
        v.notice = notices.getOrDefault(p.getUUID(), "");
        v.distance = e.distance; v.worldSpawn = e.worldSpawn; v.homeward = e.homeward;
        v.netherBlocked = e.netherBlocked; v.endBlocked = e.endBlocked; v.proposedNether = e.proposedNether; v.proposedEnd = e.proposedEnd;
        v.voted = e.votes.contains(p.getUUID()); v.countdown = e.countdown; v.remaining = Math.max(0, (remaining + 19) / 20);
        v.elapsedTicks = e.elapsedTicks; v.rulesChanged = e.rulesChanged;
        if(e.status.equals("DRAFT"))for(var candidate:server.getPlayerList().getPlayers())v.online.add(new FarboundNetwork.Online(candidate.getUUID(),candidate.getPlainTextName(),e.invitations.getOrDefault(candidate.getUUID(),0L)>System.currentTimeMillis()));
        if (m != null) { v.inventory = m.inventory; v.enderStore = m.enderStore; v.xpStore = m.xpStore; v.ready = m.ready; v.phase = m.phase;
            if (m.home != null) {v.homeDistance = Math.round(Math.hypot(p.getX() - m.home.x(), p.getZ() - m.home.z()));v.homeReached=p.level().dimension().identifier().toString().equals(m.home.dimension())&&Math.hypot(p.getX()-m.home.x(),p.getZ()-m.home.z())<=config.homeRadius;}
            v.canPause=m.phase.equals("ACTIVE")&&PlayerVault.data(p).getBooleanOr("inventoryStored",false)&&PlayerVault.data(p).getBooleanOr("xpStored",false); }
        for (var entry : e.members.entrySet()) {
            var player = server.getPlayerList().getPlayer(entry.getKey());
            v.people.add(new FarboundNetwork.Person(entry.getKey(), player == null ? entry.getKey().toString().substring(0, 8) : player.getPlainTextName(),
                player != null, e.leader.equals(entry.getKey()), entry.getValue().ready, entry.getValue().phase));
            v.members.add((player == null ? entry.getKey().toString().substring(0, 8) + " (offline)" : player.getPlainTextName())
                + " · " + (entry.getValue().phase.equals("HOME") ? entry.getValue().ready ? "READY" : "Preparing" : entry.getValue().phase));
        }
        return v;
    }
    private void broadcast() { if (expedition() != null) for (var id : expedition().members.keySet()) { var p = server.getPlayerList().getPlayer(id); if (p != null) sendView(p); } }
    private Expedition.Member requireMember(ServerPlayer p) { var m = member(p); if (m == null || m.phase.equals("FINISHED")) throw new IllegalStateException("You are not an active member."); return m; }
    private int remainingCooldown(ServerPlayer p){return (int)Math.max(0,(store.cooldowns.getOrDefault(p.getUUID(),0L)-System.currentTimeMillis()+999)/1000);}
    private void requireApproval(ServerPlayer p) { if (!config.mayStart(p.getUUID())) throw new IllegalStateException("The server operator has not enabled new expeditions for you."); }
    private void requireLeader(ServerPlayer p) { if (!expedition().leader.equals(p.getUUID())) throw new IllegalStateException("Only the expedition leader can change this setting."); }
    private boolean operator(ServerPlayer p){var source=p.createCommandSourceStack();return source!=null&&source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN);}
    private void requireDraft() { if (!expedition().status.equals("DRAFT")) throw new IllegalStateException("Departure settings are locked. Cancel the search or countdown before changing them."); }
    private ServerLevel level(String id) { return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(id))); }
    public static Expedition.Position position(ServerPlayer p) { return position(p.level(), p.position(), p.getYRot(), p.getXRot()); }
    private static Expedition.Position position(ServerLevel level, Vec3 pos, float yaw, float pitch) { return new Expedition.Position(level.dimension().identifier().toString(), pos.x, pos.y, pos.z, yaw, pitch); }
    public static void message(ServerPlayer p, String text) { p.sendSystemMessage(Component.literal("[Farbound] " + text)); }
    public String adminInspect() {
        var e = expedition();
        return e == null ? "No open expedition." : "Expedition " + e.id + " · " + e.status + " · " + e.members.entrySet().stream().map(entry -> entry.getKey() + ":" + entry.getValue().phase).toList() + (failed ? " · STORAGE LOCKED" : "");
    }
    public void adminDiscardDraft() throws IOException {
        var e=expedition();
        if (failed || e==null || !e.status.equals("DRAFT") || !e.untouchedPreparation())
            throw new IllegalStateException("Only an untouched DRAFT can be archived. Active or interrupted profiles must be recovered, never discarded.");
        checkUnstoredOnlineProfiles(e);
        var ids=new ArrayList<>(e.members.keySet()); store.archive();
        for(var id:ids){var p=server.getPlayerList().getPlayer(id);if(p!=null){message(p,"An operator archived the unstarted preparation. No profile changed.");sendView(p);}}
        Farbound.LOG.info("Operator archived untouched Farbound draft {}",e.id);
    }
    private void checkUnstoredOnlineProfiles(Expedition e) {
        for(var id:e.members.keySet()) {var p=server.getPlayerList().getPlayer(id);if(p!=null&&!Set.of("HOME","FINISHED").contains(PlayerVault.phase(p)))
            throw new IllegalStateException("A participant still has a stored profile. Draft archive refused; inspect the data.");}
    }
    public void adminRetry(ServerPlayer p) {
        if (failed) throw new IllegalStateException("After a storage error, back up and inspect the data, then restart the server. There is no release bypass.");
        String problem = loginProblem(p);
        if (problem != null) throw new IllegalStateException(problem);
        joined(p);
    }
    public void adminForceRule(String dimension, String setting) throws IOException {
        if (failed || expedition() == null || !expedition().status.equals("ACTIVE")) throw new IllegalStateException("No available active expedition.");
        if (!Set.of("nether", "end").contains(dimension)) throw new IllegalStateException("Dimension must be nether or end.");
        boolean blocked = choice(setting, "blocked", "allowed"); var e = expedition();
        e.propose(dimension.equals("nether") ? blocked : e.netherBlocked, dimension.equals("end") ? blocked : e.endBlocked);
        e.votes.addAll(e.members.keySet()); e.rulesChanged = true; store.save();
        for (var id : e.members.keySet()) { var p = server.getPlayerList().getPlayer(id); if (p != null) message(p, "An operator enforced a rule proposal: " + dimension + " " + setting + ". New blocks wait for a normal return."); }
        Farbound.LOG.info("Operator forced Farbound rule {} {} for {}", dimension, setting, e.id);
        applyRules(); broadcast();
    }
    private void storageFailure(ServerPlayer p, Exception ex) {
        failed = true; Farbound.LOG.error("Farbound operation suspended; preserve world/player files", ex);
        if (search != null) search.close();
        if (p != null) p.connection.disconnect(Component.literal("Farbound: Profile transition could not be confirmed. Contact the server operator for data inspection."));
        if (expedition() != null) for (var id : expedition().members.keySet()) { var player = server.getPlayerList().getPlayer(id); if (player != null) player.connection.disconnect(Component.literal("Farbound: Expedition suspended due to a storage error.")); }
    }
    public void close() { if (search != null) search.close(); if (!failed) try { store.save(); } catch (IOException ex) { Farbound.LOG.error("Farbound state save failed", ex); } }
}


