package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

import static net.earthcomputer.clientcommands.command.ClientCommandHelper.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public class ChatCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("chat").executes(ctx -> execute()));
    }

    private static int execute() {
        MinecraftClient.getInstance().send(() -> {
            MinecraftClient.getInstance().openChatScreen("");
            sendFeedback("commands.chat.success");
        });
        return Command.SINGLE_SUCCESS;
    }
}
