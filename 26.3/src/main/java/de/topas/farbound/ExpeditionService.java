package de.topas.farbound;

import java.io.IOException;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.portal.TeleportTransition;

/** Routes each player to their own solo/group expedition. All calls run on the server thread. */
public final class ExpeditionService implements AutoCloseable {
    private final MinecraftServer server;
    private final ExpeditionStore registry;
    private final FarboundConfig config;
    private final List<ExpeditionSession> sessions = new ArrayList<>();
    public ExpeditionService(MinecraftServer server) {
        this.server = server;
        try {
            var root = server.getWorldPath(LevelResource.ROOT).resolve("farbound");
            registry = new ExpeditionStore(root);
            config = FarboundConfig.load(root.resolve("config.json"));
            for (var store : registry.activeStores()) sessions.add(new ExpeditionSession(server, store, config));
        } catch (IOException ex) { throw new IllegalStateException("Farbound data cannot be read. Preserve world and player files before continuing.", ex); }
    }
    ExpeditionSession sessionFor(ServerPlayer player) {
        return sessions.stream().filter(s -> s.member(player) != null && !s.member(player).phase.equals("FINISHED")).findFirst().orElse(null);
    }
    private ExpeditionSession invitationFor(ServerPlayer player) {
        return sessions.stream().filter(s -> s.expedition() != null && s.expedition().status.equals("DRAFT")
            && s.expedition().invitations.getOrDefault(player.getUUID(), 0L) > System.currentTimeMillis()).findFirst().orElse(null);
    }
    private ExpeditionSession visible(ServerPlayer player) {
        var own = sessionFor(player); return own != null ? own : invitationFor(player);
    }
    public void handle(ServerPlayer player, String action, String value, int revision) {
        var session = visible(player);
        if (session == null && action.equals("open")) {
            session = new ExpeditionSession(server, registry.createStore(), config);
            sessions.add(session);
        }
        if (session == null) { reply(player, "Open /farbound to start your own expedition.", action.equals("open")); return; }
        if (action.equals("decline") && sessionFor(player) == null) {
            session.expedition().invitations.remove(player.getUUID());
            try { session.store.save(); sendView(player); }
            catch (IOException ex) { throw new IllegalStateException("Invitation could not be saved.", ex); }
            return;
        }
        if (action.equals("invite")) {
            var friend = server.getPlayerList().getPlayerByName(value);
            var own = friend == null ? null : sessionFor(friend);
            var pending = friend == null ? null : invitationFor(friend);
            if ((own != null && own != session) || (pending != null && pending != session)) {
                reply(player, "This player already has an expedition or invitation. They must finish or leave it first.", false); return;
            }
        }
        session.handle(player, action, value, revision);
        sessions.removeIf(s -> s.expedition() == null);
    }
    private void reply(ServerPlayer player, String notice, boolean open) {
        message(player, notice); var view = view(player); view.notice = notice; view.open = open; FarboundNetwork.send(player, view);
    }
    public FarboundNetwork.View view(ServerPlayer player) {
        var session = visible(player);
        if (session != null) return session.view(player);
        var view = new FarboundNetwork.View(); view.homeRadius = config.homeRadius;
        view.admin = ExpeditionSession.operator(player);
        view.cooldownSeconds = (int)Math.max(0, (registry.cooldowns.getOrDefault(player.getUUID(),0L) - System.currentTimeMillis() + 999)/1000);
        return view;
    }
    public void sendView(ServerPlayer player) { FarboundNetwork.send(player, view(player)); }
    public boolean dimensionAllowed(ServerPlayer player, ServerLevel destination) { var s = sessionFor(player); return s == null || s.dimensionAllowed(player, destination); }
    public TeleportTransition respawn(ServerPlayer player, TeleportTransition vanilla, TeleportTransition.PostTeleportTransition action) {
        var s = sessionFor(player); return s == null ? vanilla : s.respawn(player, vanilla, action);
    }
    public boolean respawnReady(ServerPlayer player) { var s = sessionFor(player); return s == null || s.respawnReady(player); }
    public String loginProblem(ServerPlayer player) {
        var s = sessionFor(player);
        return s != null ? s.loginProblem(player) : Set.of("HOME", "FINISHED").contains(PlayerVault.phase(player)) ? null : "Stored profile has no matching expedition. Preserve the player data for inspection.";
    }
    public void joined(ServerPlayer player) { var s = sessionFor(player); if (s != null) s.joined(player); }
    public void tick() { for (var s : List.copyOf(sessions)) s.tick(); sessions.removeIf(s -> s.expedition() == null); }
    public void close() { for (var s : sessions) s.close(); }
    public static void message(ServerPlayer player, String text) { ExpeditionSession.message(player, text); }
    public static Expedition.Position position(ServerPlayer player) { return ExpeditionSession.position(player); }
    public static void eligible(ServerPlayer player) { ExpeditionSession.eligible(player); }
}
