package de.topas.farbound;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionSet;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CommandPermissionTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void dashboardIsTheOnlyRegisteredCommand() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>(); FarboundCommands.register(dispatcher);
        var root=dispatcher.getRoot().getChild("farbound");
        assertNotNull(root.getCommand());assertTrue(root.getChildren().isEmpty());
    }
    @Test void missingClientGetsAnActionableMessageWithoutCreatingADraft() throws Exception {
        var dispatcher=new CommandDispatcher<CommandSourceStack>();FarboundCommands.register(dispatcher);
        var source=mock(CommandSourceStack.class);var player=mock(net.minecraft.server.level.ServerPlayer.class);
        when(source.getPlayerOrException()).thenReturn(player);
        try(var network=mockStatic(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.class)){
            assertEquals(0,dispatcher.execute("farbound",source));
            var message=org.mockito.ArgumentCaptor.forClass(net.minecraft.network.chat.Component.class);
            verify(player).sendSystemMessage(message.capture());
            assertTrue(message.getValue().getString().contains("Install Farbound"));
            assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,()->dispatcher.execute("farbound abort",source));
        }
    }
}
