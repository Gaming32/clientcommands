package net.earthcomputer.clientcommands.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.minecraft.commands.SharedSuggestionProvider.suggest;

public class VarCommand {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("%([^%]+)%");

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final SimpleCommandExceptionType SAVE_FAILED_EXCEPTION = new SimpleCommandExceptionType(Component.translatable("commands.cvar.saveFile.failed"));
    private static final DynamicCommandExceptionType ALREADY_EXISTS_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.cvar.add.alreadyExists", arg));
    private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(arg -> Component.translatable("commands.cvar.notFound", arg));

    private static final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("clientcommands");

    private static final Map<String, String> variables = new HashMap<>();

    static {
        try {
            loadFile();
        } catch (IOException e) {
            LOGGER.error("Could not load vars file, hence /cvar will not work!", e);
        }
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("cvar")
                .then(literal("add")
                        .then(argument("variable", word())
                                .then(argument("value", greedyString())
                                        .executes(ctx -> addVariable(ctx.getSource(), getString(ctx, "variable"), getString(ctx, "value"))))))
                .then(literal("remove")
                        .then(argument("variable", word())
                                .suggests((ctx, builder) -> suggest(variables.keySet(), builder))
                                .executes(ctx -> removeVariable(ctx.getSource(), getString(ctx, "variable")))))
                .then(literal("edit")
                        .then(argument("variable", word())
                                .suggests((ctx, builder) -> suggest(variables.keySet(), builder))
                                .then(argument("value", greedyString())
                                        .executes(ctx -> editVariable(ctx.getSource(), getString(ctx, "variable"), getString(ctx, "value"))))))
                .then(literal("parse")
                        .then(argument("variable", word())
                                .suggests((ctx, builder) -> suggest(variables.keySet(), builder))
                                .executes(ctx -> parseVariable(ctx.getSource(), getString(ctx, "variable")))))
                .then(literal("list")
                        .executes(ctx -> listVariables(ctx.getSource()))));
    }

    private static int addVariable(FabricClientCommandSource source, String variable, String value) throws CommandSyntaxException {
        if (variables.containsKey(variable)) {
            throw ALREADY_EXISTS_EXCEPTION.create(variable);
        }
        variables.put(variable, value);
        saveFile();
        source.sendFeedback(Component.translatable("commands.cvar.add.success", variable));
        return Command.SINGLE_SUCCESS;
    }

    private static int removeVariable(FabricClientCommandSource source, String variable) throws CommandSyntaxException {
        if (variables.remove(variable) == null) {
            throw NOT_FOUND_EXCEPTION.create(variable);
        }
        saveFile();
        source.sendFeedback(Component.translatable("commands.cvar.remove.success", variable));
        return Command.SINGLE_SUCCESS;
    }

    private static int editVariable(FabricClientCommandSource source, String variable, String value) throws CommandSyntaxException {
        if (!variables.containsKey(variable)) {
            throw NOT_FOUND_EXCEPTION.create(variable);
        }
        variables.put(variable, value);
        saveFile();
        source.sendFeedback(Component.translatable("commands.cvar.edit.success", variable));
        return Command.SINGLE_SUCCESS;
    }

    private static int parseVariable(FabricClientCommandSource source, String variable) throws CommandSyntaxException {
        String value = variables.get(variable);
        if (value == null) {
            throw NOT_FOUND_EXCEPTION.create(variable);
        }
        source.sendFeedback(Component.translatable("commands.cvar.parse.success", variable, value));
        return Command.SINGLE_SUCCESS;
    }

    private static int listVariables(FabricClientCommandSource source) {
        if (variables.isEmpty()) {
            source.sendFeedback(Component.translatable("commands.cvar.list.empty"));
        } else {
            String list = "%" + String.join("%, %", variables.keySet()) + "%";
            source.sendFeedback(Component.translatable("commands.cvar.list", list));
        }
        return variables.size();
    }

    private static void saveFile() throws CommandSyntaxException {
        try {
            CompoundTag rootTag = new CompoundTag();
            variables.forEach(rootTag::putString);
            Path newFile = File.createTempFile("vars", ".dat", configPath.toFile()).toPath();
            NbtIo.write(rootTag, newFile);
            Path backupFile = configPath.resolve("vars.dat_old");
            Path currentFile = configPath.resolve("vars.dat");
            Util.safeReplaceFile(currentFile, newFile, backupFile);
        } catch (IOException e) {
            throw SAVE_FAILED_EXCEPTION.create();
        }
    }

    private static void loadFile() throws IOException {
        variables.clear();
        CompoundTag rootTag = NbtIo.read(configPath.resolve("vars.dat"));
        if (rootTag == null) {
            return;
        }
        rootTag.getAllKeys().forEach(key -> variables.put(key, rootTag.getString(key)));
    }

    public static String replaceVariables(String originalString) {
        Matcher matcher = VARIABLE_PATTERN.matcher(originalString);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String group = matcher.group();
            matcher.appendReplacement(builder, variables.getOrDefault(group.substring(1, group.length() - 1), group).replace("\\", "\\\\").replace("$", "\\$"));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    public static boolean containsVars(String command) {
        return VARIABLE_PATTERN.matcher(command).find();
    }
}
