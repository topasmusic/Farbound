package de.topas.farbound.client;

import de.topas.farbound.FarboundNetwork;
import de.topas.farbound.DepartureCountdown;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import java.nio.file.*;

public final class FarboundClient implements ClientModInitializer {
    public static FarboundNetwork.View view = FarboundNetwork.View.finished();
    public static boolean hud = false;
    public static int opacity = 195;
    private static final DepartureCountdown COUNTDOWN = new DepartureCountdown();
    private static final Path PREFS = FabricLoader.getInstance().getConfigDir().resolve("farbound-client.json");
    public void onInitializeClient() {
        try { if (Files.exists(PREFS)) { var prefs = FarboundNetwork.JSON.fromJson(Files.readString(PREFS), Preferences.class); hud = prefs.hud; opacity = prefs.opacity; } }
        catch (Exception ignored) { hud = false; opacity = 195; }
        ClientPlayNetworking.registerGlobalReceiver(FarboundNetwork.State.TYPE, (payload, context) -> {
            var next = FarboundNetwork.JSON.fromJson(payload.json(), FarboundNetwork.View.class);
            context.client().execute(() -> {
                boolean beginsCountdown = next.status.equals("COUNTDOWN") && !view.status.equals("COUNTDOWN");
                var cue = COUNTDOWN.update(next.status, next.phase, next.remaining, System.currentTimeMillis());
                if (cue != DepartureCountdown.Cue.NONE && context.client().player != null) {
                    var sound = cue == DepartureCountdown.Cue.ARRIVAL ? net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT : net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value();
                    context.client().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound,
                        cue == DepartureCountdown.Cue.ARRIVAL ? 1f : next.remaining <= 3 ? 1.3f : 1f,
                        cue == DepartureCountdown.Cue.ARRIVAL ? .35f : .25f));
                }
                view = next;
                if (beginsCountdown && context.client().gui.screen() instanceof FarboundScreen) context.client().gui.setScreen(null);
                else if (next.open && !(context.client().gui.screen() instanceof FarboundScreen)) context.client().gui.setScreen(new FarboundScreen());
                else if (context.client().gui.screen() instanceof FarboundScreen screen) screen.update();
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { view = FarboundNetwork.View.finished(); COUNTDOWN.reset(); });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("farbound", "departure_countdown"), (g, delta) -> {
            var mc = Minecraft.getInstance(); long now = System.currentTimeMillis(); int digit = COUNTDOWN.number(now);
            if (digit < 0 || mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() instanceof FarboundScreen) return;
            float fit = Math.min(1f,Math.min(g.guiWidth()/640f,g.guiHeight()/360f));
            g.pose().pushMatrix(); g.pose().translate(g.guiWidth()/2f,g.guiHeight()*.44f); g.pose().scale(fit);
            try {
                float pulse = COUNTDOWN.pulse(now);
                FarboundTheme.frame(g,-92,-74,184,152,0xAD081F30,0xCE4DB6DD,9);
                FarboundTheme.centered(g,"F A R B O U N D",0,-59,10,FarboundTheme.CYAN);
                FarboundTheme.centered(g,digit == 0 ? "EXPEDITION STARTED" : "DEPARTING IN",0,-38,9,FarboundTheme.MUTED);
                FarboundTheme.centered(g,Integer.toString(digit),0,-18-(int)(pulse*2),49+pulse*4,0xFFE4FAFF);
                g.fill(-70,48,70,49,0x4F4AC5E8);
                g.fill(-70,48,-70+(int)(140*(1-pulse)),49,0xFF65D8FF);
                FarboundTheme.centered(g,digit == 0 ? "A new beginning." : "Stay ready. Your journey is about to begin.",0,59,7.5f,FarboundTheme.MUTED);
            } finally { g.pose().popMatrix(); }
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("farbound", "expedition_timer"), (g, delta) -> {
            var mc = Minecraft.getInstance();
            if (!hud || mc.gui.hud.isHidden() || mc.player == null || mc.gui.screen() instanceof FarboundScreen) return;
            if (!view.phase.equals("ACTIVE") && !view.status.equals("COUNTDOWN") && !view.status.equals("SEARCHING")) return;
            int x = 12, y = 12;
            String text = view.status.equals("COUNTDOWN") ? "DEPARTURE IN " + view.remaining : view.status.equals("SEARCHING") ? "FINDING A DESTINATION…" : "FARBOUND  " + time(view.elapsedTicks);
            int w = mc.font.width(text) + 20;
            g.fill(x, y, x + w, y + 26, 0xB0082338); g.fill(x, y, x + 2, y + 26, 0xFF52DDF5);
            g.text(mc.font, text, x + 10, y + 9, 0xFFE0F8FF);
        });
    }
    public static String time(long ticks) {
        long s = Math.max(0, ticks) / 20;
        return String.format("%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60);
    }
    public static void action(String action, String value) {
        if (ClientPlayNetworking.canSend(FarboundNetwork.Action.TYPE)) { view.notice = ""; ClientPlayNetworking.send(new FarboundNetwork.Action(action, value, view.revision)); }
    }
    public static void preferences() {
        try { Files.writeString(PREFS, FarboundNetwork.JSON.toJson(new Preferences(hud, opacity))); }
        catch (Exception ignored) { }
    }
    private record Preferences(boolean hud, int opacity) {}
}

