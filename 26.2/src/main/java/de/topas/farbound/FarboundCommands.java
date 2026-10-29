package de.topas.farbound;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import static net.minecraft.commands.Commands.literal;

/** The dashboard is the sole player/operator entry point. */
public final class FarboundCommands {
    private FarboundCommands(){}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher){
        dispatcher.register(literal("farbound").executes(context->{
            var player=context.getSource().getPlayerOrException();
            if(!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,FarboundNetwork.State.TYPE)){
                ExpeditionService.message(player,"Install Farbound 1.0.1 on this client to open the dashboard with /farbound.");return 0;
            }
            if(Farbound.service!=null)Farbound.service.handle(player,"open","",-1);
            return 1;
        }));
    }
}
