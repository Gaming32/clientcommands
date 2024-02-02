package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.earthcomputer.clientcommands.mixin.OptionInstanceAccessor;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class FovCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cfov")
            .then(argument("fov", integer(0, 360))
                .executes(ctx -> setFov(ctx.getSource(), getInteger(ctx, "fov"))))
            .then(literal("normal")
                .executes(ctx -> setFov(ctx.getSource(), 70)))
            .then(literal("quakePro")
                .executes(ctx -> setFov(ctx.getSource(), 110))));
    }

    private static int setFov(FabricClientCommandSource source, int fov) {
        ((OptionInstanceAccessor) (Object) source.getClient().options.fov()).forceSetValue(fov);

        Component feedback = Component.translatable("commands.cfov.success", fov);
        source.sendFeedback(feedback);

        return Command.SINGLE_SUCCESS;
    }

}
