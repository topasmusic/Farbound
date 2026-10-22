package de.topas.farbound;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExpeditionServiceTest {
    @TempDir Path root;
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private class Fixture {
        final MinecraftServer server = mock(MinecraftServer.class, RETURNS_DEEP_STUBS);
        final ServerLevel level = mock(ServerLevel.class);
        final ServerPlayer p = mock(ServerPlayer.class, withSettings().extraInterfaces(VaultCarrier.class));
        CompoundTag tag = new CompoundTag();
        final ExpeditionService service;
        final Expedition e;
        final Expedition.Member m;
        Fixture() {
            when(server.getWorldPath(LevelResource.ROOT)).thenReturn(root);
            when(level.getServer()).thenReturn(server); when(level.dimension()).thenReturn(Level.OVERWORLD);
            when(server.getLevel(Level.OVERWORLD)).thenReturn(level); when(server.overworld()).thenReturn(level);
            p.connection = mock(ServerGamePacketListenerImpl.class);
            when(((VaultCarrier)p).farbound$vault()).thenAnswer(call -> tag);
            doAnswer(call -> { tag = call.getArgument(0); return null; }).when((VaultCarrier)p).farbound$vault(any());
            when(p.registryAccess()).thenReturn(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            when(p.getUUID()).thenReturn(UUID.randomUUID()); when(p.level()).thenReturn(level);
            when(p.isAlive()).thenReturn(true); when(p.gameMode()).thenReturn(GameType.SURVIVAL);
            when(p.getBoundingBox()).thenReturn(new net.minecraft.world.phys.AABB(0,0,0,1,2,1));
            var inventory = mock(Inventory.class); when(inventory.isEmpty()).thenReturn(true); when(p.getInventory()).thenReturn(inventory);
            when(p.getEnderChestInventory()).thenReturn(new PlayerEnderChestContainer());
            service = new ExpeditionService(server); e = new Expedition(p.getUUID()); service.store.expedition = e;
            e.status = "ACTIVE"; m = e.members.get(e.leader); m.phase = "ACTIVE"; m.nonce = "transaction";
            m.home = new Expedition.Position("minecraft:overworld", 100, 70, 100, 0, 0); e.target = m.home;
            tag.putString("expedition", e.id.toString()); tag.putString("nonce", m.nonce); tag.putString("phase", "ACTIVE");
            tag.putBoolean("xpStored", true); tag.putInt("xpLevel", 37); tag.putInt("xpTotal", 2498); tag.putFloat("xpProgress", .456789f);
            p.experienceLevel = 12; when(server.getPlayerList().getPlayer(p.getUUID())).thenReturn(p);
        }
    }
    private void returnScenario(boolean success, boolean recovery, boolean alreadyFinished) throws Exception {
        var f = new Fixture();
        if (recovery) f.m.phase = "RESTORING";
        if (alreadyFinished) { f.tag.putString("phase", "FINISHED"); f.p.experienceLevel = 37; }
        when(f.p.teleportTo(eq(f.level), anyDouble(), anyDouble(), anyDouble(), anySet(), anyFloat(), anyFloat(), anyBoolean())).thenReturn(success);
        var saves = new ArrayList<CompoundTag>();
        try (var terrain = mockStatic(SpawnSearch.class); var disk = mockStatic(PlayerVault.class, CALLS_REAL_METHODS);
             var network = mockStatic(FarboundNetwork.class)) {
            terrain.when(() -> SpawnSearch.nearby(eq(f.level), any())).thenReturn(new BlockPos(100,70,100));
            disk.when(() -> PlayerVault.persist(f.p)).thenAnswer(call -> { saves.add(f.tag.copy()); return null; });
            disk.when(() -> PlayerVault.sync(f.p)).thenAnswer(call -> null);
            if (recovery) f.service.joined(f.p); else f.service.handle(f.p, "end", "", -1);
            if (!success) {
                assertEquals(12, f.p.experienceLevel, "Home XP cannot be released at expedition position");
                assertEquals("ACTIVE", f.tag.getStringOr("phase", "")); assertEquals("RESTORING", f.m.phase); assertTrue(saves.isEmpty());
                assertNotNull(f.service.loginProblem(f.p));
                f.service.store.expedition = null; f.tag.putString("phase", "HOME");
                assertNull(f.service.loginProblem(f.p), "Unrelated home profiles must still be allowed to join");
            } else {
                assertEquals(37, f.p.experienceLevel); assertEquals("FINISHED", f.tag.getStringOr("phase", ""));
                assertEquals("FINISHED", f.m.phase); assertNull(f.service.expedition());
                if (alreadyFinished) assertTrue(saves.isEmpty());
                else { assertEquals(2, saves.size()); assertEquals("ACTIVE", saves.getFirst().getStringOr("phase", "")); assertEquals("FINISHED", saves.getLast().getStringOr("phase", "")); }
                f.service.joined(f.p); assertEquals(37, f.p.experienceLevel);
            }
        }
    }
    @Test void rejectedHomeTeleportMustNotReleaseStoredProfile() throws Exception { returnScenario(false, false, false); }
    @Test void successfulReturnPersistsPositionBeforeGivingOriginalXp() throws Exception { returnScenario(true, false, false); }
    @Test void crashBeforeRestoreResumesExactlyOnce() throws Exception { returnScenario(true, true, false); }
    @Test void crashAfterRestoreNeverGivesOriginalsAgain() throws Exception { returnScenario(true, true, true); }
    @Test void mismatchedNonceAndOrphanProfilesAreBlockedBeforeRelease() {
        var f = new Fixture(); assertNull(f.service.loginProblem(f.p));
        f.tag.putString("nonce", "other"); assertNotNull(f.service.loginProblem(f.p));
        f.service.store.expedition = null; assertNotNull(f.service.loginProblem(f.p));
        f.tag.putString("phase", "FINISHED"); assertNull(f.service.loginProblem(f.p));
    }
    @Test void partiallyStartedGroupDoesNotHoldActiveMembersHostageAfterRestart() throws Exception {
        var f = new Fixture(); f.e.status = "STARTING";
        var offline = new Expedition.Member(); offline.phase = "PREPARED"; offline.nonce = "offline"; offline.home = f.m.home;
        f.e.members.put(UUID.randomUUID(), offline); f.service.store.save();
        var resumed = new ExpeditionService(f.server);
        assertEquals("ACTIVE", resumed.expedition().status);
        assertEquals("PREPARED", resumed.expedition().members.values().stream().filter(m -> m.nonce.equals("offline")).findFirst().orElseThrow().phase);
    }
    @Test void interruptedStartResumesFromEachDurablePlayerBoundary() throws Exception {
        for (String phase : List.of("HOME", "PREPARED", "ACTIVE")) {
            var f = new Fixture(); f.e.status = "STARTING"; f.m.phase = "PREPARED";
            f.m.inventory = "KEEP"; f.m.enderStore = false; f.m.xpStore = false;
            f.tag.putString("phase", phase); f.tag.putBoolean("xpStored", false);
            when(f.p.teleportTo(eq(f.level), anyDouble(), anyDouble(), anyDouble(), anySet(), anyFloat(), anyFloat(), anyBoolean())).thenReturn(true);
            try (var terrain = mockStatic(SpawnSearch.class); var disk = mockStatic(PlayerVault.class, CALLS_REAL_METHODS)) {
                terrain.when(() -> SpawnSearch.nearby(eq(f.level), any())).thenReturn(new BlockPos(100,70,100));
                disk.when(() -> PlayerVault.persist(f.p)).thenAnswer(call -> null);
                disk.when(() -> PlayerVault.sync(f.p)).thenAnswer(call -> null);
                assertNull(f.service.loginProblem(f.p)); f.service.joined(f.p);
                assertEquals("ACTIVE", f.m.phase, phase); assertEquals("ACTIVE", f.e.status, phase);
                assertEquals("ACTIVE", f.tag.getStringOr("phase", ""), phase);
                assertEquals(12, f.p.experienceLevel, "Already active KEEP profile must not be reset");
            }
        }
    }
    private void prepareDraft(Fixture f) {
        f.e.status="DRAFT"; f.m.phase="HOME"; f.m.home=null; f.m.nonce=null; f.e.target=null;
        f.tag=new CompoundTag();
    }
    private void preparePause(Fixture f) {
        when(f.p.getInventory().getNonEquipmentItems()).thenReturn(net.minecraft.core.NonNullList.create());
        when(f.p.getItemBySlot(any())).thenReturn(net.minecraft.world.item.ItemStack.EMPTY);
        when(f.p.getFoodData()).thenReturn(mock(net.minecraft.world.food.FoodData.class));
        when(f.p.position()).thenReturn(new net.minecraft.world.phys.Vec3(10000,70,0));
        f.p.experienceLevel=37;f.tag=PlayerVault.capture(f.p,f.e,f.m);f.tag.putString("phase","ACTIVE");f.p.experienceLevel=12;
        when(f.p.teleportTo(eq(f.level),anyDouble(),anyDouble(),anyDouble(),anySet(),anyFloat(),anyFloat(),anyBoolean())).thenReturn(true);
    }
    @Test void pauseAndResumeSwapExactXpWithoutCompletingTheExpedition() {
        var f=new Fixture();preparePause(f);
        try(var terrain=mockStatic(SpawnSearch.class);var disk=mockStatic(PlayerVault.class,CALLS_REAL_METHODS);var network=mockStatic(FarboundNetwork.class)){
            terrain.when(()->SpawnSearch.nearby(eq(f.level),any())).thenReturn(new BlockPos(100,70,100));
            disk.when(()->PlayerVault.persist(f.p)).thenAnswer(c->null);disk.when(()->PlayerVault.sync(f.p)).thenAnswer(c->null);
            f.service.handle(f.p,"pause","",f.e.revision);
            assertEquals("PAUSED",f.m.phase);assertEquals("PAUSED",PlayerVault.phase(f.p));assertEquals(37,f.p.experienceLevel);
            assertSame(f.e,f.service.expedition());assertNull(f.service.loginProblem(f.p));
            f.p.experienceLevel=40;f.service.handle(f.p,"resume","",f.e.revision);
            assertEquals("ACTIVE",f.m.phase);assertEquals(12,f.p.experienceLevel);assertNull(f.service.loginProblem(f.p));
            f.p.experienceLevel=18;f.service.handle(f.p,"pause","",f.e.revision);
            assertEquals("PAUSED",f.m.phase);assertEquals(40,f.p.experienceLevel);
            f.service.handle(f.p,"end","",f.e.revision);assertEquals("PAUSED",f.m.phase);
            f.service.handle(f.p,"abort","",f.e.revision);assertNull(f.service.expedition());assertEquals(40,f.p.experienceLevel);
        }
    }
    @Test void cooldownMessageReportsCurrentRemainingSeconds() {
        var f=new Fixture();prepareDraft(f);f.service.store.expedition=null;
        f.service.store.cooldowns.put(f.p.getUUID(),System.currentTimeMillis()+26500);
        try(var network=mockStatic(FarboundNetwork.class)){
            f.service.handle(f.p,"open","",-1);assertTrue(f.service.view(f.p).notice.contains("27 seconds"));
            f.service.store.cooldowns.put(f.p.getUUID(),System.currentTimeMillis()+12500);
            f.service.handle(f.p,"open","",-1);assertTrue(f.service.view(f.p).notice.contains("13 seconds"));
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"ACTIVE","PAUSING","PAUSED","RESUME_PAUSED","RESUMING","RESUME_ACTIVE"})
    void interruptedProfileSwitchReconcilesFromEveryDurableBoundary(String boundary) throws Exception {
        var f=new Fixture();preparePause(f);boolean resume=boundary.startsWith("RESUME")||boundary.equals("RESUMING");
        try(var terrain=mockStatic(SpawnSearch.class);var disk=mockStatic(PlayerVault.class,CALLS_REAL_METHODS);var network=mockStatic(FarboundNetwork.class)){
            terrain.when(()->SpawnSearch.nearby(eq(f.level),any())).thenReturn(new BlockPos(100,70,100));
            disk.when(()->PlayerVault.persist(f.p)).thenAnswer(c->null);disk.when(()->PlayerVault.sync(f.p)).thenAnswer(c->null);
            if(resume){PlayerVault.prepareSwitch(f.p,f.e,f.m,false);PlayerVault.completeSwitch(f.p,false);f.p.experienceLevel=40;}
            f.m.pausedAt=new Expedition.Position("minecraft:overworld",10000,70,0,0,0);f.m.phase=resume?"RESUMING":"PAUSING";
            if(!boundary.equals("ACTIVE")&&!boundary.equals("RESUME_PAUSED"))PlayerVault.prepareSwitch(f.p,f.e,f.m,resume);
            if(boundary.equals("PAUSED")||boundary.equals("RESUME_ACTIVE"))PlayerVault.completeSwitch(f.p,resume);
            f.service.store.save();var reloaded=new ExpeditionService(f.server);
            assertNull(reloaded.loginProblem(f.p));reloaded.joined(f.p);
            assertEquals(resume?"ACTIVE":"PAUSED",reloaded.member(f.p).phase);
            assertEquals(resume?12:37,f.p.experienceLevel);
            reloaded.joined(f.p);assertEquals(resume?12:37,f.p.experienceLevel,"Repeated login cannot apply a profile twice");
            assertNull(reloaded.loginProblem(f.p));assertEquals("ACTIVE",reloaded.expedition().status);
        }
    }
    @Test void destinationFailureBeforeIntentLeavesProfileUsableButAfterIntentLocksIt() {
        var f=new Fixture();preparePause(f);
        try(var terrain=mockStatic(SpawnSearch.class);var disk=mockStatic(PlayerVault.class,CALLS_REAL_METHODS);var network=mockStatic(FarboundNetwork.class)){
            terrain.when(()->SpawnSearch.nearby(eq(f.level),any())).thenReturn(null);
            f.service.handle(f.p,"pause","",f.e.revision);assertEquals("ACTIVE",f.m.phase);assertNull(f.service.loginProblem(f.p));
            terrain.when(()->SpawnSearch.nearby(eq(f.level),any())).thenReturn(new BlockPos(100,70,100)).thenReturn(null);
            f.service.handle(f.p,"pause","",f.e.revision);assertEquals("PAUSING",f.m.phase);
            assertEquals(12,f.p.experienceLevel);assertNotNull(f.service.loginProblem(f.p));
            verify(f.p.connection,atLeastOnce()).disconnect(any(net.minecraft.network.chat.Component.class));
        }
    }
    @Test void rejectedPauseTeleportCannotReleaseHomeItemsOrXp() {
        var f=new Fixture();preparePause(f);
        when(f.p.teleportTo(eq(f.level),anyDouble(),anyDouble(),anyDouble(),anySet(),anyFloat(),anyFloat(),anyBoolean())).thenReturn(false);
        try(var terrain=mockStatic(SpawnSearch.class);var disk=mockStatic(PlayerVault.class,CALLS_REAL_METHODS);var network=mockStatic(FarboundNetwork.class)){
            terrain.when(()->SpawnSearch.nearby(eq(f.level),any())).thenReturn(new BlockPos(100,70,100));
            disk.when(()->PlayerVault.persist(f.p)).thenAnswer(c->null);
            f.service.handle(f.p,"pause","",f.e.revision);assertEquals(12,f.p.experienceLevel);assertEquals("PAUSING",PlayerVault.phase(f.p));assertNotNull(f.service.loginProblem(f.p));
        }
    }
    @Test void homewardFinishUsesActualRadiusAndPauseDoesNotCompleteTheWalk() {
        var f=new Fixture();f.e.homeward=true;when(f.p.getX()).thenReturn(201d);when(f.p.getZ()).thenReturn(100d);
        try(var terrain=mockStatic(SpawnSearch.class);var disk=mockStatic(PlayerVault.class,CALLS_REAL_METHODS);var network=mockStatic(FarboundNetwork.class)){
            f.service.handle(f.p,"end","",f.e.revision);assertEquals("ACTIVE",f.m.phase);assertTrue(f.service.view(f.p).notice.contains("100 horizontal blocks"));
            assertFalse(f.service.view(f.p).homeReached);when(f.p.getX()).thenReturn(200d);assertTrue(f.service.view(f.p).homeReached);
            terrain.when(()->SpawnSearch.nearby(eq(f.level),any())).thenReturn(new BlockPos(100,70,100));
            when(f.p.teleportTo(eq(f.level),anyDouble(),anyDouble(),anyDouble(),anySet(),anyFloat(),anyFloat(),anyBoolean())).thenReturn(true);
            disk.when(()->PlayerVault.persist(f.p)).thenAnswer(c->null);disk.when(()->PlayerVault.sync(f.p)).thenAnswer(c->null);
            f.service.handle(f.p,"end","",f.e.revision);assertEquals("FINISHED",f.m.phase);
        }
    }
    @Test void singleplayerOwnerCanReplaceAnUntouchedOfflineTestProfileDraft() throws Exception {
        var f=new Fixture(); prepareDraft(f); var old=f.e.leader; var current=UUID.randomUUID();
        when(f.p.getUUID()).thenReturn(current); when(f.server.getPlayerList().getPlayer(old)).thenReturn(null);
        when(f.server.isSingleplayer()).thenReturn(true); when(f.server.isSingleplayerOwner(f.p.nameAndId())).thenReturn(true);
        try(var network=mockStatic(FarboundNetwork.class)) {
            f.service.open(f.p);
            assertEquals(current,f.service.expedition().leader);
            assertTrue(java.nio.file.Files.exists(root.resolve("farbound/history").resolve(f.e.id+".json")));
            assertEquals(12,f.p.experienceLevel); assertEquals("HOME",PlayerVault.phase(f.p));
        }
    }
    @Test void foreignDraftOnDedicatedServerIsNeverAutomaticallyDiscarded() {
        var f=new Fixture(); prepareDraft(f); when(f.p.getUUID()).thenReturn(UUID.randomUUID());
        when(f.server.isSingleplayer()).thenReturn(false);
        assertThrows(IllegalStateException.class,()->f.service.open(f.p)); assertSame(f.e,f.service.expedition());
    }
    @Test void abortConfirmAlsoDiscardsOwnUnstartedPreparation() {
        var f=new Fixture(); prepareDraft(f);
        try(var network=mockStatic(FarboundNetwork.class)) {
            f.tag.putString("phase","ACTIVE");f.service.handle(f.p,"abort","",-1);
            assertSame(f.e,f.service.expedition());assertEquals("ACTIVE",PlayerVault.phase(f.p));
            f.tag=new CompoundTag();
            f.service.handle(f.p,"abort","",-1);
            assertNull(f.service.expedition()); assertEquals(12,f.p.experienceLevel); assertEquals("HOME",PlayerVault.phase(f.p));
        }
    }
    @Test void operatorCanArchivePureDraftButCannotDeleteAnyTransition() throws Exception {
        var f=new Fixture(); prepareDraft(f);
        for(String phase:List.of("PREPARED","ACTIVE","RESTORING","RECOVERY_REQUIRED")){
            f.m.phase=phase;assertThrows(IllegalStateException.class,()->f.service.adminDiscardDraft());assertSame(f.e,f.service.expedition());
        }
        f.m.phase="HOME";f.m.nonce="transition";assertThrows(IllegalStateException.class,()->f.service.adminDiscardDraft());f.m.nonce=null;
        f.tag.putString("phase","ACTIVE");assertThrows(IllegalStateException.class,()->f.service.adminDiscardDraft());f.tag=new CompoundTag();
        try(var network=mockStatic(FarboundNetwork.class)){
            f.service.adminDiscardDraft();assertNull(f.service.expedition());assertEquals(12,f.p.experienceLevel);
            assertTrue(java.nio.file.Files.exists(root.resolve("farbound/history").resolve(f.e.id+".json")));
        }
    }
    @Test void singleplayerRecoveryCannotReplaceAnActiveOrStillOnlineOwnersExpedition() {
        var f=new Fixture();var old=f.e.leader;when(f.p.getUUID()).thenReturn(UUID.randomUUID());
        when(f.server.isSingleplayer()).thenReturn(true);when(f.server.isSingleplayerOwner(f.p.nameAndId())).thenReturn(true);
        assertThrows(IllegalStateException.class,()->f.service.open(f.p));assertSame(f.e,f.service.expedition());
        prepareDraft(f);when(f.server.getPlayerList().getPlayer(old)).thenReturn(f.p);
        assertThrows(IllegalStateException.class,()->f.service.open(f.p));assertSame(f.e,f.service.expedition());
    }
}

