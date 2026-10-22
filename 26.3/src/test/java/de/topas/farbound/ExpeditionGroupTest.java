package de.topas.farbound;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Multiple player identities through real service/journal logic, without starting a server. */
class ExpeditionGroupTest {
    @TempDir Path root;
    MinecraftServer server;ServerLevel level;ExpeditionService service;ServerPlayer leader,friend,stranger;
    final Map<UUID,ServerPlayer> online=new HashMap<>();
    MockedStatic<FarboundNetwork> network;
    @BeforeAll static void bootstrap(){SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @BeforeEach void setup() throws Exception {
        server=mock(MinecraftServer.class,RETURNS_DEEP_STUBS);level=mock(ServerLevel.class);
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(root);
        when(level.getServer()).thenReturn(server);when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(server.getLevel(Level.OVERWORLD)).thenReturn(level);when(server.overworld()).thenReturn(level);
        when(server.getPlayerList().getPlayer(any(UUID.class))).thenAnswer(c->online.get(c.getArgument(0)));
        when(server.getPlayerList().getPlayers()).thenAnswer(c->new ArrayList<>(online.values()));
        leader=player("Leader");friend=player("Friend");stranger=player("Stranger");
        service=new ExpeditionService(server);network=mockStatic(FarboundNetwork.class);
        service.open(leader);
    }
    @AfterEach void cleanup(){network.close();}
    ServerPlayer player(String name){
        var p=mock(ServerPlayer.class,withSettings().extraInterfaces(VaultCarrier.class));
        var id=UUID.randomUUID();when(p.getUUID()).thenReturn(id);when(p.getPlainTextName()).thenReturn(name);
        when(p.level()).thenReturn(level);when(p.isAlive()).thenReturn(true);when(p.gameMode()).thenReturn(GameType.SURVIVAL);
        when(p.getBoundingBox()).thenReturn(new net.minecraft.world.phys.AABB(0,0,0,1,2,1));
        var inv=mock(Inventory.class);when(inv.isEmpty()).thenReturn(true);when(p.getInventory()).thenReturn(inv);
        when(p.getEnderChestInventory()).thenReturn(new PlayerEnderChestContainer());
        when(((VaultCarrier)p).farbound$vault()).thenReturn(new CompoundTag());
        p.connection=mock(ServerGamePacketListenerImpl.class);
        online.put(id,p);when(server.getPlayerList().getPlayerByName(name)).thenReturn(p);return p;
    }
    void act(ServerPlayer p,String action,String value){service.handle(p,action,value,service.expedition().revision);}
    void joinFriend(){act(leader,"invite","Friend");act(friend,"accept","");assertTrue(service.expedition().members.containsKey(friend.getUUID()));}
    @Test void invitationsAreVoluntaryExpireAndDoNotGrantLeaderPermissions(){
        act(leader,"invite","Friend");assertTrue(service.view(friend).invited);
        assertEquals(1,service.expedition().members.size());
        act(stranger,"accept","");assertEquals(1,service.expedition().members.size());
        act(friend,"accept","");assertEquals(2,service.expedition().members.size());
        act(friend,"configure","distance 50000");assertEquals(10000,service.expedition().distance);
        assertFalse(service.view(friend).notice.isEmpty());
        act(friend,"invite","Stranger");assertFalse(service.expedition().invitations.containsKey(stranger.getUUID()));
        act(leader,"invite","Stranger");service.expedition().invitations.put(stranger.getUUID(),0L);
        act(stranger,"accept","");assertEquals(2,service.expedition().members.size());
    }
    @Test void personalGearStaysSeparateWhileRouteAndReadinessAreShared(){
        joinFriend();act(leader,"ready","");act(friend,"ready","");
        assertTrue(service.view(leader).ready);assertTrue(service.view(friend).ready);
        act(friend,"configure","inventory KEEP");act(friend,"configure","xp keep");act(friend,"configure","ender keep");
        assertEquals("STORE",service.view(leader).inventory);assertTrue(service.view(leader).xpStore);assertTrue(service.view(leader).enderStore);
        assertEquals("KEEP",service.view(friend).inventory);assertFalse(service.view(friend).xpStore);assertFalse(service.view(friend).enderStore);
        assertFalse(service.view(leader).ready);assertFalse(service.view(friend).ready);
        act(leader,"configure","distance 50000");act(leader,"configure","mode homeward");
        assertEquals(50000,service.view(friend).distance);assertTrue(service.view(friend).homeward);
    }
    @Test void staleReadyPacketCannotApproveChangedSettings(){
        joinFriend();int old=service.expedition().revision;act(leader,"configure","distance 100000");
        service.handle(friend,"ready","",old);
        assertFalse(service.view(friend).ready);assertTrue(service.view(friend).notice.contains("out of date"));
        act(friend,"ready","");assertTrue(service.view(friend).ready);
    }
    @Test void offlineOrUnreadyMemberPreventsDepartureWithoutTouchingProfiles(){
        joinFriend();act(leader,"ready","");act(leader,"launch","");
        assertEquals("DRAFT",service.expedition().status);
        act(friend,"ready","");online.remove(friend.getUUID());act(leader,"launch","");
        assertEquals("DRAFT",service.expedition().status);
        assertFalse(service.view(leader).people.stream().filter(p->p.id().equals(friend.getUUID())).findFirst().orElseThrow().online());
        assertEquals("HOME",PlayerVault.phase(leader));assertEquals("HOME",PlayerVault.phase(friend));
        assertNull(service.member(leader).nonce);assertNull(service.member(friend).nonce);
    }
    @Test void countdownLogoutCancelsForEveryoneBeforeAnyProfileTransition(){
        joinFriend();act(leader,"ready","");act(friend,"ready","");
        service.expedition().status="COUNTDOWN";online.remove(friend.getUUID());service.tick();
        assertEquals("DRAFT",service.expedition().status);
        assertFalse(service.view(leader).ready);assertFalse(service.view(friend).ready);
        assertEquals("HOME",PlayerVault.phase(leader));assertEquals("HOME",PlayerVault.phase(friend));
    }
    @Test void memberLeavingClearsReadinessAndLeaderCanThenDiscardDraft(){
        joinFriend();act(leader,"ready","");act(friend,"ready","");
        act(friend,"leave","");assertEquals(1,service.expedition().members.size());assertFalse(service.view(leader).ready);
        assertEquals("NONE",service.view(friend).status);act(leader,"leave","");assertNull(service.expedition());
        assertEquals("HOME",PlayerVault.phase(leader));assertEquals("HOME",PlayerVault.phase(friend));
    }
    @Test void ruleChangeWaitsForEveryVoteAndForbiddenDimensionToBeVacated(){
        joinFriend();var e=service.expedition();e.status="ACTIVE";e.members.values().forEach(m->m.phase="ACTIVE");
        act(leader,"configure","nether blocked");assertFalse(e.netherBlocked);assertEquals(Boolean.TRUE,e.proposedNether);
        var nether=mock(ServerLevel.class);when(nether.dimension()).thenReturn(Level.NETHER);when(friend.level()).thenReturn(nether);
        act(friend,"vote","");assertFalse(e.netherBlocked);
        when(friend.level()).thenReturn(level);
        for(int i=0;i<20;i++)service.tick();
        assertTrue(e.netherBlocked);assertNull(e.proposedNether);assertTrue(service.view(leader).rulesChanged);
    }
    @Test void onlineListTracksMembershipAndPendingInvitations(){
        assertEquals(3,service.view(leader).online.size());act(leader,"invite","Friend");
        assertTrue(service.view(leader).online.stream().filter(p->p.id().equals(friend.getUUID())).findFirst().orElseThrow().invited());
        act(friend,"accept","");assertFalse(service.view(leader).online.stream().filter(p->p.id().equals(friend.getUUID())).findFirst().orElseThrow().invited());
        assertEquals(2,service.view(leader).people.size());online.remove(stranger.getUUID());assertEquals(2,service.view(leader).online.size());
    }
    @Test void removedInventoryChoiceCannotBeSelectedButLegacyDraftRemainsIntact(){
        act(leader,"configure","inventory EMPTY");assertEquals("STORE",service.member(leader).inventory);
        service.member(leader).inventory="EMPTY";act(leader,"configure","distance 50000");assertEquals("EMPTY",service.member(leader).inventory);
        act(leader,"configure","inventory KEEP");assertEquals("KEEP",service.member(leader).inventory);
    }
    @Test void operatorActionsAreAuthorizedOnServerRatherThanTrustingVisibleControls(){
        act(friend,"admin_discard","");assertNotNull(service.expedition());assertTrue(service.view(friend).notice.contains("Operator permission"));
        var source=mock(net.minecraft.commands.CommandSourceStack.class);when(friend.createCommandSourceStack()).thenReturn(source);
        when(source.permissions()).thenReturn(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS);
        assertTrue(service.view(friend).admin);act(friend,"admin_discard","");assertNull(service.expedition());
    }
    @Test void pausedMemberDoesNotRestrictHomeTravelOrStartAnotherExpedition() throws Exception {
        joinFriend();var e=service.expedition();e.status="ACTIVE";e.netherBlocked=true;service.member(friend).phase="PAUSED";
        var nether=mock(ServerLevel.class);when(nether.dimension()).thenReturn(Level.NETHER);
        assertFalse(service.active(friend));assertTrue(service.dimensionAllowed(friend,nether));
        service.open(friend);assertSame(e,service.expedition());
        service.member(leader).phase="ACTIVE";assertFalse(service.dimensionAllowed(leader,nether));
    }
}
