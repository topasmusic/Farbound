package de.topas.farbound;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;
import net.minecraft.util.ProblemReporter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import de.topas.farbound.mixin.PlayerListAccess;

/** The inactive profile and transition marker are saved in the SAME player file as live inventory. */
public final class PlayerVault {
    public static final String KEY = "FarboundVault";
    private PlayerVault() {}
    public static CompoundTag data(ServerPlayer p) { return ((VaultCarrier)p).farbound$vault(); }
    public static String phase(ServerPlayer p) { return data(p).getStringOr("phase", "HOME"); }
    public static CompoundTag capture(ServerPlayer p, Expedition e, Expedition.Member member) {
        var problems = new ProblemReporter.Collector();
        var out = TagValueOutput.createWithContext(problems, p.registryAccess());
        out.putString("expedition", e.id.toString());
        out.putString("phase", "PREPARED");
        out.putString("nonce", member.nonce);
        out.putBoolean("inventoryStored", member.inventory.equals("STORE"));
        out.putBoolean("enderStored", member.enderStore);
        out.putBoolean("xpStored", member.xpStore);
        if (member.inventory.equals("STORE")) {
            out.store("items", ItemStack.OPTIONAL_CODEC.listOf(), copyInventory(p));
            var equipment = out.child("equipment");
            for (var slot : EquipmentSlot.values()) if (slot != EquipmentSlot.MAINHAND) equipment.store(slot.getName(), ItemStack.OPTIONAL_CODEC, p.getItemBySlot(slot).copy());
            out.putInt("selected", p.getInventory().getSelectedSlot());
        }
        if (member.enderStore) {
            var items = new ArrayList<ItemStack>();
            for (int i = 0; i < p.getEnderChestInventory().getContainerSize(); i++) items.add(p.getEnderChestInventory().getItem(i).copy());
            out.store("ender", ItemStack.OPTIONAL_CODEC.listOf(), items);
        }
        if (member.xpStore) {
            out.putInt("xpLevel", p.experienceLevel);
            out.putInt("xpTotal", p.totalExperience);
            out.putFloat("xpProgress", p.experienceProgress);
        }
        out.storeNullable("respawn", ServerPlayer.RespawnConfig.CODEC, p.getRespawnConfig());
        if(p.getFoodData()!=null){
            out.putFloat("health",p.getHealth());out.putInt("food",p.getFoodData().getFoodLevel());
            out.putFloat("saturation",p.getFoodData().getSaturationLevel());
            out.store("effects",net.minecraft.world.effect.MobEffectInstance.CODEC.listOf(),new ArrayList<>(p.getActiveEffects()));
        }
        if (!problems.isEmpty()) throw new IllegalStateException("Player data cannot be saved without loss: " + problems.getReport());
        return out.buildResult();
    }
    private static List<ItemStack> copyInventory(ServerPlayer p) {
        return p.getInventory().getNonEquipmentItems().stream().map(ItemStack::copy).toList();
    }
    public static void begin(ServerPlayer p, CompoundTag snapshot) throws IOException {
        ((VaultCarrier)p).farbound$vault(snapshot.copy());
        persist(p); // Original profile still active, durable snapshot before clearing anything.
        if (snapshot.getBooleanOr("inventoryStored", false)) p.getInventory().clearContent();
        if (snapshot.getBooleanOr("enderStored", false)) p.getEnderChestInventory().clearContent();
        if (snapshot.getBooleanOr("xpStored", false)) {
            p.experienceLevel = 0; p.totalExperience = 0; p.experienceProgress = 0;
        }
        data(p).putString("phase", "ACTIVE");
        persist(p);
        sync(p);
    }
    public static void restore(ServerPlayer p) throws IOException {
        CompoundTag tag = data(p);
        if (!phase(p).equals("ACTIVE")) throw new IllegalStateException("No active stored profile.");
        if (!ExpeditionPolicy.canRestore(tag.getBooleanOr("inventoryStored", false), tag.getBooleanOr("enderStored", false),
                p.getInventory().isEmpty(), p.getEnderChestInventory().isEmpty()))
            throw new IllegalStateException("Empty your inventory and expedition ender chest into chests first.");
        applyProfile(p,tag,false);
        tag.putString("phase", "FINISHED");
        persist(p);
        sync(p);
    }
    /** Switch intent and both profiles live atomically beside the active inventory. */
    public static void prepareSwitch(ServerPlayer p,Expedition e,Expedition.Member member,boolean resume) throws IOException {
        String source=resume?"PAUSED":"ACTIVE",intent=resume?"RESUMING":"PAUSING";
        if(!phase(p).equals(source))throw new IllegalStateException("Profile is not ready to switch.");
        var target=data(p).copy();
        if(!target.getBooleanOr("inventoryStored",false)||!target.getBooleanOr("xpStored",false))
            throw new IllegalStateException("Pause requires Store at home inventory and stored XP.");
        var from=capture(p,e,member);
        var pending=target.copy();pending.put("switchFrom",from);pending.put("switchTo",target);pending.putString("phase",intent);
        ((VaultCarrier)p).farbound$vault(pending);persist(p);
    }
    public static void completeSwitch(ServerPlayer p,boolean resume) throws IOException {
        if(!phase(p).equals(resume?"RESUMING":"PAUSING"))throw new IllegalStateException("No matching profile switch.");
        var from=data(p).getCompound("switchFrom").orElseThrow().copy();
        var target=data(p).getCompound("switchTo").orElseThrow();
        applyProfile(p,target,true);
        from.putString("phase",resume?"ACTIVE":"PAUSED");
        ((VaultCarrier)p).farbound$vault(from);persist(p);sync(p);
    }
    private static void applyProfile(ServerPlayer p,CompoundTag tag,boolean vitals) {
        var problems = new ProblemReporter.Collector();
        var input = TagValueInput.create(problems, p.registryAccess(), tag);
        // Decode every item before changing ANY live slot. Missing/unknown components fail closed.
        List<ItemStack> items = tag.getBooleanOr("inventoryStored", false)
            ? input.read("items", ItemStack.OPTIONAL_CODEC.listOf()).orElseThrow() : null;
        List<ItemStack> ender = tag.getBooleanOr("enderStored", false)
            ? input.read("ender", ItemStack.OPTIONAL_CODEC.listOf()).orElseThrow() : null;
        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        if (items != null) {
            if (items.size() != p.getInventory().getNonEquipmentItems().size()) throw new IllegalStateException("Inventory size changed.");
            for (var slot : EquipmentSlot.values()) if (slot != EquipmentSlot.MAINHAND) equipment.put(slot, input.childOrEmpty("equipment").read(slot.getName(), ItemStack.OPTIONAL_CODEC).orElseThrow());
        }
        if (ender != null && ender.size() != p.getEnderChestInventory().getContainerSize()) throw new IllegalStateException("Ender chest size changed.");
        var respawn = input.read("respawn", ServerPlayer.RespawnConfig.CODEC).orElse(null);
        var effects=vitals&&tag.contains("health")?input.read("effects",net.minecraft.world.effect.MobEffectInstance.CODEC.listOf()).orElseThrow():null;
        if (!problems.isEmpty()) throw new IllegalStateException("Stored profile cannot be read safely.");
        if (items != null) {
            for (int i = 0; i < items.size(); i++) p.getInventory().setItem(i, items.get(i).copy());
            equipment.forEach((slot, stack) -> p.setItemSlot(slot, stack.copy()));
            p.getInventory().setSelectedSlot(tag.getIntOr("selected", 0));
        }
        if (ender != null) for (int i = 0; i < ender.size(); i++) p.getEnderChestInventory().setItem(i, ender.get(i).copy());
        if (tag.getBooleanOr("xpStored", false)) {
            p.experienceLevel = tag.getIntOr("xpLevel", 0);
            p.totalExperience = tag.getIntOr("xpTotal", 0);
            p.experienceProgress = tag.getFloatOr("xpProgress", 0);
        }
        p.setRespawnPosition(respawn, false);
        if(effects!=null){
            p.removeAllEffects();for(var effect:effects)p.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect));
            p.setHealth(tag.getFloatOr("health",p.getHealth()));p.getFoodData().setFoodLevel(tag.getIntOr("food",20));
            p.getFoodData().setSaturation(tag.getFloatOr("saturation",5));
        }
    }
    public static void persist(ServerPlayer p) throws IOException {
        String saveId = UUID.randomUUID().toString();
        data(p).putString("saveId", saveId);
        var server = p.level().getServer();
        ((PlayerListAccess)server.getPlayerList()).farbound$save(p);
        Path path = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID() + ".dat");
        var saved = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()).getCompound(KEY).orElseThrow();
        if (!saved.equals(data(p))) throw new IOException("Farbound player data could not be confirmed.");
        try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); }
    }
    public static void sync(ServerPlayer p) {
        p.getInventory().setChanged();
        p.inventoryMenu.broadcastChanges();
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetExperiencePacket(
            p.experienceProgress, p.totalExperience, p.experienceLevel));
    }
}

