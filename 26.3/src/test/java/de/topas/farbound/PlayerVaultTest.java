package de.topas.farbound;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerVaultTest {
    @BeforeAll static void registries() { SharedConstants.tryDetectVersion(); Farbound.SEARCH_TICKET.toString(); Bootstrap.bootStrap(); }
    @Test void synchronizationPreservesExactFractionAndTotal() throws Exception {
        var p = mock(ServerPlayer.class);
        when(p.getInventory()).thenReturn(mock(Inventory.class));
        var field = net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");
        field.setAccessible(true); field.set(p, mock(InventoryMenu.class));
        p.connection = mock(ServerGamePacketListenerImpl.class);
        when(p.getXpNeededForNextLevel()).thenReturn(121);
        doCallRealMethod().when(p).setExperiencePoints(anyInt());
        doCallRealMethod().when(p).setExperienceLevels(anyInt());
        p.experienceLevel = 37; p.totalExperience = 2498; p.experienceProgress = .456789f;
        PlayerVault.sync(p);
        assertEquals(.456789f, p.experienceProgress);
        assertEquals(37, p.experienceLevel);
        assertEquals(2498, p.totalExperience);
    }
    @Test void roundtripPreservesComponentsEquipmentEnderAndExactXpAfterSlotChange() throws Exception {
        var p = mock(ServerPlayer.class, withSettings().extraInterfaces(VaultCarrier.class));
        var equipment = new net.minecraft.world.entity.EntityEquipment();
        var inventory = new Inventory(p, equipment);
        var ender = new net.minecraft.world.inventory.PlayerEnderChestContainer();
        var tag = new net.minecraft.nbt.CompoundTag[]{new net.minecraft.nbt.CompoundTag()};
        when(((VaultCarrier)p).farbound$vault()).thenAnswer(call -> tag[0]);
        doAnswer(call -> { tag[0] = call.getArgument(0); return null; }).when((VaultCarrier)p).farbound$vault(any());
        when(p.registryAccess()).thenReturn(net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
        when(p.getInventory()).thenReturn(inventory);
        when(p.getEnderChestInventory()).thenReturn(ender);
        when(p.getItemBySlot(any())).thenAnswer(call -> {
            net.minecraft.world.entity.EquipmentSlot slot = call.getArgument(0);
            return slot == net.minecraft.world.entity.EquipmentSlot.MAINHAND ? inventory.getSelectedItem() : equipment.get(slot);
        });
        doAnswer(call -> { net.minecraft.world.entity.EquipmentSlot slot = call.getArgument(0); net.minecraft.world.item.ItemStack stack = call.getArgument(1);
            if (slot == net.minecraft.world.entity.EquipmentSlot.MAINHAND) inventory.setItem(inventory.getSelectedSlot(), stack);
            else equipment.set(slot, stack); return null;
        }).when(p).setItemSlot(any(), any());
        for (var item : new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.AIR, net.minecraft.world.item.Items.DIAMOND, net.minecraft.world.item.Items.EMERALD, net.minecraft.world.item.Items.DIAMOND_HELMET, net.minecraft.world.item.Items.SHIELD, net.minecraft.world.item.Items.GOLD_INGOT}) { var holder = (net.minecraft.core.Holder.Reference<net.minecraft.world.item.Item>) net.minecraft.core.registries.BuiltInRegistries.ITEM.wrapAsHolder(item); if (!holder.areComponentsBound()) holder.bindComponents(net.minecraft.core.component.DataComponentMap.builder().set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build()); }
        var named = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Home gem"));
        inventory.setItem(0, named); inventory.setItem(4, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 13));
        equipment.set(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
        equipment.set(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
        ender.setItem(26, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 21));
        p.experienceLevel = 37; p.totalExperience = 2498; p.experienceProgress = .456789f;
        var e = new Expedition(java.util.UUID.randomUUID()); var m = e.members.get(e.leader); m.nonce = "test";
        var snapshot = PlayerVault.capture(p, e, m);
        try (var disk = mockStatic(PlayerVault.class, CALLS_REAL_METHODS)) {
            disk.when(() -> PlayerVault.persist(p)).thenAnswer(call -> null);
            disk.when(() -> PlayerVault.sync(p)).thenAnswer(call -> null);
            PlayerVault.begin(p, snapshot);
            assertTrue(inventory.isEmpty()); assertTrue(ender.isEmpty()); assertEquals(0, p.experienceLevel);
            inventory.setSelectedSlot(4);
            inventory.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 3));
            assertThrows(IllegalStateException.class, () -> PlayerVault.restore(p));
            assertEquals(3, inventory.getItem(2).getCount());
            assertEquals("ACTIVE", PlayerVault.phase(p));
            inventory.clearContent();
            PlayerVault.restore(p);
            assertEquals(net.minecraft.world.item.Items.EMERALD, inventory.getItem(4).getItem());
            assertEquals(13, inventory.getItem(4).getCount());
            assertEquals("Home gem", inventory.getItem(0).get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString());
            assertEquals(net.minecraft.world.item.Items.DIAMOND_HELMET, equipment.get(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem());
            assertEquals(net.minecraft.world.item.Items.SHIELD, equipment.get(net.minecraft.world.entity.EquipmentSlot.OFFHAND).getItem());
            assertEquals(21, ender.getItem(26).getCount());
            assertEquals(37, p.experienceLevel); assertEquals(2498, p.totalExperience);
            assertEquals(.456789f, p.experienceProgress);
            assertThrows(IllegalStateException.class, () -> PlayerVault.restore(p));
        }
    }
}




