package de.topas.farbound;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real inventory and item codecs, durable player-file boundaries replaced with NBT copies. */
class ProfileSwitchTest {
    @BeforeAll static void bootstrap(){
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        for(var item:new Item[]{Items.AIR,Items.DIAMOND,Items.EMERALD,Items.SHIELD}){
            var holder=(net.minecraft.core.Holder.Reference<Item>)net.minecraft.core.registries.BuiltInRegistries.ITEM.wrapAsHolder(item);
            if(!holder.areComponentsBound())holder.bindComponents(net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,64).build());
        }
    }
    static class Profile {
        final ServerPlayer p=mock(ServerPlayer.class,withSettings().extraInterfaces(VaultCarrier.class));
        final EntityEquipment equipment=new EntityEquipment();
        final Inventory inventory=new Inventory(p,equipment);
        final PlayerEnderChestContainer ender=new PlayerEnderChestContainer();
        final Expedition e=new Expedition(java.util.UUID.randomUUID());
        final Expedition.Member m=e.members.get(e.leader);
        CompoundTag tag=new CompoundTag();
        Profile(){
            m.nonce="switch-test";
            when(((VaultCarrier)p).farbound$vault()).thenAnswer(c->tag);
            doAnswer(c->{tag=c.getArgument(0);return null;}).when((VaultCarrier)p).farbound$vault(any());
            when(p.registryAccess()).thenReturn(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
            when(p.getInventory()).thenReturn(inventory);when(p.getEnderChestInventory()).thenReturn(ender);
            when(p.getItemBySlot(any())).thenAnswer(c->{EquipmentSlot s=c.getArgument(0);return s==EquipmentSlot.MAINHAND?inventory.getSelectedItem():equipment.get(s);});
            doAnswer(c->{EquipmentSlot s=c.getArgument(0);ItemStack stack=c.getArgument(1);if(s==EquipmentSlot.MAINHAND)inventory.setItem(inventory.getSelectedSlot(),stack);else equipment.set(s,stack);return null;}).when(p).setItemSlot(any(),any());
        }
        void gear(Item item,int count,int xp){inventory.clearContent();inventory.setItem(3,new ItemStack(item,count));p.experienceLevel=xp;p.totalExperience=xp*100;p.experienceProgress=.456789f;}
        void assertGear(Item item,int count,int xp){assertEquals(item,inventory.getItem(3).getItem());assertEquals(count,inventory.getItem(3).getCount());assertEquals(xp,p.experienceLevel);assertEquals(xp*100,p.totalExperience);assertEquals(.456789f,p.experienceProgress);}
        void begin(){gear(Items.DIAMOND,7,37);ender.setItem(26,new ItemStack(Items.DIAMOND,11));PlayerVaultBegin();gear(Items.EMERALD,13,12);ender.setItem(26,new ItemStack(Items.EMERALD,21));}
        void PlayerVaultBegin(){try{PlayerVault.begin(p,PlayerVault.capture(p,e,m));}catch(Exception ex){throw new AssertionError(ex);}}
    }
    @Test void repeatedPauseResumePreservesChangedHomeAndExpeditionProfiles() throws Exception {
        var f=new Profile();
        try(var disk=mockStatic(PlayerVault.class,CALLS_REAL_METHODS)){
            disk.when(()->PlayerVault.persist(f.p)).thenAnswer(c->null);disk.when(()->PlayerVault.sync(f.p)).thenAnswer(c->null);
            f.begin();
            for(int i=0;i<3;i++){
                PlayerVault.prepareSwitch(f.p,f.e,f.m,false);
                assertEquals("PAUSING",PlayerVault.phase(f.p));f.assertGear(Items.EMERALD,13+i,12+i);
                PlayerVault.completeSwitch(f.p,false);f.assertGear(Items.DIAMOND,7+i,37+i);
                assertEquals(11+i,f.ender.getItem(26).getCount());
                assertThrows(IllegalStateException.class,()->PlayerVault.completeSwitch(f.p,false));
                f.gear(Items.DIAMOND,8+i,38+i);f.ender.setItem(26,new ItemStack(Items.DIAMOND,12+i));
                PlayerVault.prepareSwitch(f.p,f.e,f.m,true);PlayerVault.completeSwitch(f.p,true);
                f.assertGear(Items.EMERALD,13+i,12+i);assertEquals(21+i,f.ender.getItem(26).getCount());
                f.gear(Items.EMERALD,14+i,13+i);f.ender.setItem(26,new ItemStack(Items.EMERALD,22+i));
            }
            f.inventory.clearContent();f.ender.clearContent();PlayerVault.restore(f.p);f.assertGear(Items.DIAMOND,10,40);
        }
    }
    @Test void savedSwitchIntentCanReplayAfterCrashWithoutDuplicatingOrMerging() throws Exception {
        var f=new Profile();
        try(var disk=mockStatic(PlayerVault.class,CALLS_REAL_METHODS)){
            disk.when(()->PlayerVault.persist(f.p)).thenAnswer(c->null);disk.when(()->PlayerVault.sync(f.p)).thenAnswer(c->null);
            f.begin();PlayerVault.prepareSwitch(f.p,f.e,f.m,false);var saved=f.tag.copy();
            PlayerVault.completeSwitch(f.p,false);f.tag=saved.copy();
            PlayerVault.completeSwitch(f.p,false);f.assertGear(Items.DIAMOND,7,37);
            PlayerVault.prepareSwitch(f.p,f.e,f.m,true);saved=f.tag.copy();
            PlayerVault.completeSwitch(f.p,true);f.tag=saved.copy();
            PlayerVault.completeSwitch(f.p,true);f.assertGear(Items.EMERALD,13,12);
            assertFalse(f.tag.contains("switchFrom"));assertFalse(f.tag.contains("switchTo"));
        }
    }
    @Test void keepEnderChestTravelsAndKeepInventoryOrXpCannotPause() throws Exception {
        var f=new Profile();f.m.enderStore=false;
        try(var disk=mockStatic(PlayerVault.class,CALLS_REAL_METHODS)){
            disk.when(()->PlayerVault.persist(f.p)).thenAnswer(c->null);disk.when(()->PlayerVault.sync(f.p)).thenAnswer(c->null);
            f.begin();PlayerVault.prepareSwitch(f.p,f.e,f.m,false);PlayerVault.completeSwitch(f.p,false);
            assertEquals(21,f.ender.getItem(26).getCount());f.ender.setItem(26,new ItemStack(Items.DIAMOND,31));
            PlayerVault.prepareSwitch(f.p,f.e,f.m,true);PlayerVault.completeSwitch(f.p,true);assertEquals(31,f.ender.getItem(26).getCount());
            for(String flag:java.util.List.of("inventoryStored","xpStored")){
                var saved=f.tag.copy();f.tag.putBoolean(flag,false);
                assertThrows(IllegalStateException.class,()->PlayerVault.prepareSwitch(f.p,f.e,f.m,false));
                f.assertGear(Items.EMERALD,13,12);f.tag=saved;
            }
        }
    }
    @Test void corruptTargetIsDecodedBeforeLiveInventoryChanges() throws Exception {
        var f=new Profile();
        try(var disk=mockStatic(PlayerVault.class,CALLS_REAL_METHODS)){
            disk.when(()->PlayerVault.persist(f.p)).thenAnswer(c->null);disk.when(()->PlayerVault.sync(f.p)).thenAnswer(c->null);
            f.begin();PlayerVault.prepareSwitch(f.p,f.e,f.m,false);
            f.tag.getCompound("switchTo").orElseThrow().remove("equipment");
            assertThrows(RuntimeException.class,()->PlayerVault.completeSwitch(f.p,false));
            f.assertGear(Items.EMERALD,13,12);assertEquals("PAUSING",PlayerVault.phase(f.p));
        }
    }
}
