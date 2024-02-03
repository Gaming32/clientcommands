package net.earthcomputer.clientcommands.features;

import com.mojang.logging.LogUtils;
import net.earthcomputer.clientcommands.Configs;
import net.earthcomputer.clientcommands.MultiVersionCompat;
import net.earthcomputer.clientcommands.task.LongTask;
import net.earthcomputer.clientcommands.task.LongTaskList;
import net.earthcomputer.clientcommands.task.OneTickTask;
import net.earthcomputer.clientcommands.task.SimpleTask;
import net.earthcomputer.clientcommands.task.TaskManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class EnchantmentCracker {

    /*
     * The enchantment cracker works as follows:
     *
     * First, crack the first few XP seeds. When you open an enchantment table GUI,
     * the server gives you 12 bits of the 32-bit enchantment seed. Vanilla uses
     * this masked version of the seed to generate the galactic alphabet text in the
     * GUI. We use brute force to guess the other 20 bits, matching each possibility
     * and what it would generate with certain things the server tells us, such as
     * the enchantment hints. We can narrow down the possibilities to 1 after
     * putting a few items into the enchantment table.
     *
     * Second, we know that the above XP seeds are generated by calling the player
     * entity's RNG's unbounded nextInt() method. This means that after a doing the
     * above a few times, enchanting an item after each time, we have a few
     * consecutive values of nextInt(). Each time an item is enchanted, we narrow
     * down the possibilities of what the player RNG's state could be. The first
     * value of nextInt() gives us 32 bits of its 48-bit internal state. Each time
     * nextInt() is next called, we narrow down its internal state by brute force.
     * It usually only takes two values of nextInt() to guess the internal state.
     *
     * There's one small catch: for this to work, we have to know that the values of
     * nextInt() are indeed consecutive. The first XP seed, if it's cracked, cannot
     * be used as one of these values since it was generated an unknown length of
     * time in the past, possibly even before a server restart - so we have to
     * ignore that. More obviously, there are many, many other things which use the
     * player's RNG and hence affect its internal state. We have to detect on the
     * client side when one of these things is likely to be happening. This is only
     * possible to do for certain if the server is running vanilla because some mod
     * could use the player's RNG for some miscellaneous task.
     *
     * Third, we can take advantage of the fact that generating XP seeds is not the
     * only thing that the player RNG does, to manipulate the RNG to produce an XP
     * seed which we want. The /cenchant command, which calls the
     * manipulateEnchantments method of this class, does this. We change the state
     * of the player RNG in a predictable way by throwing out items of the player's
     * inventory. Each time the player throws out an item, rand.nextFloat() gets
     * called 4 times to determine the velocity of the item which is thrown out. If
     * we throw out n items before we then do a dummy enchantment to generate our
     * new enchantment seed, then we can change n to change the enchantment seed. By
     * simulating which XP seed each n (up to a limit) will generate, and which
     * enchantments that XP seed will generate, we can filter out which enchantments
     * we want and determine n.
     */

    public static final Logger LOGGER = LogUtils.getLogger();

    // RENDERING
    /*
     * This section is in charge of rendering the overlay on the enchantment GUI
     */

    public static void drawEnchantmentGUIOverlay(GuiGraphics graphics) {
        CrackState crackState = Configs.enchCrackState;

        List<String> lines = new ArrayList<>();

        lines.add(I18n.get("enchCrack.state", I18n.get("enchCrack.state." + crackState.getSerializedName())));
        lines.add(I18n.get("playerManip.state", I18n.get("playerManip.state." + Configs.playerCrackState.getSerializedName())));

        lines.add("");

        if (crackState == CrackState.CRACKED) {
            lines.add(I18n.get("enchCrack.xpSeed.one", possibleXPSeeds.iterator().next()));
        } else if (crackState == CrackState.CRACKING) {
            lines.add(I18n.get("enchCrack.xpSeed.many", possibleXPSeeds.size()));
        }

        lines.add("");

        if (enchantingTablePos != null) {
            lines.add(I18n.get("enchCrack.bookshelfCount", getEnchantPower(Minecraft.getInstance().level, enchantingTablePos)));
            lines.add("");
        }

        if (crackState == CrackState.CRACKED) {
            lines.add(I18n.get("enchCrack.enchantments"));
        } else {
            lines.add(I18n.get("enchCrack.clues"));
        }

        for (int slot = 0; slot < 3; slot++) {
            lines.add(I18n.get("enchCrack.slot", slot + 1));
            List<EnchantmentInstance> enchs = getEnchantmentsInTable(slot);
            if (enchs != null) {
                for (EnchantmentInstance ench : enchs) {
                    lines.add("   " + ench.enchantment.getFullname(ench.level).getString());
                }
            }
        }

        Font font = Minecraft.getInstance().font;
        int y = 0;
        for (String line : lines) {
            graphics.drawString(font, line, 0, y, 0xffffff, false);
            y += font.lineHeight;
        }
    }

    // LOGIC
    /*
     * This section is in charge of the logic of the cracking
     */

    static Set<Integer> possibleXPSeeds = new HashSet<>(1 << 20);
    private static int firstXpSeed;
    public static BlockPos enchantingTablePos = null;
    private static boolean doneEnchantment = false;

    public static void resetCracker() {
        Configs.enchCrackState = CrackState.UNCRACKED;
        possibleXPSeeds.clear();
    }

    private static void prepareForNextEnchantmentSeedCrack(int serverReportedXPSeed) {
        serverReportedXPSeed &= 0x0000fff0;
        for (int highBits = 0; highBits < 65536; highBits++) {
            for (int low4Bits = 0; low4Bits < 16; low4Bits++) {
                possibleXPSeeds.add((highBits << 16) | serverReportedXPSeed | low4Bits);
            }
        }
    }

    public static void addEnchantmentSeedInfo(Level level, EnchantmentMenu menu) {
        CrackState crackState = Configs.enchCrackState;
        if (crackState == CrackState.CRACKED) {
            return;
        }

        ItemStack itemToEnchant = menu.getSlot(0).getItem();
        if (itemToEnchant.isEmpty() || !itemToEnchant.isEnchantable()) {
            return;
        }

        if (enchantingTablePos == null) {
            return;
        }
        BlockPos tablePos = enchantingTablePos;

        if (crackState == CrackState.UNCRACKED) {
            Configs.enchCrackState = CrackState.CRACKING;
            prepareForNextEnchantmentSeedCrack(menu.getEnchantmentSeed());
        }
        int power = getEnchantPower(level, tablePos);

        RandomSource rand = RandomSource.create();
        int[] actualEnchantLevels = menu.costs;
        int[] actualEnchantmentClues = menu.enchantClue;
        int[] actualLevelClues = menu.levelClue;

        // brute force the possible seeds
        Iterator<Integer> xpSeedItr = possibleXPSeeds.iterator();
        seedLoop: while (xpSeedItr.hasNext()) {
            int xpSeed = xpSeedItr.next();
            rand.setSeed(xpSeed);

            // check enchantment levels match
            for (int slot = 0; slot < 3; slot++) {
                int cost = EnchantmentHelper.getEnchantmentCost(rand, slot, power, itemToEnchant);
                if (cost < slot + 1) {
                    cost = 0;
                }
                if (cost != actualEnchantLevels[slot]) {
                    xpSeedItr.remove();
                    continue seedLoop;
                }
            }

            // generate enchantment clues and see if they match
            for (int slot = 0; slot < 3; slot++) {
                if (actualEnchantLevels[slot] > 0) {
                    List<EnchantmentInstance> enchantments = getEnchantmentList(rand, xpSeed, itemToEnchant, slot,
                            actualEnchantLevels[slot]);
                    if (enchantments == null || enchantments.isEmpty()) {
                        // check that there is indeed no enchantment clue
                        if (actualEnchantmentClues[slot] != -1 || actualLevelClues[slot] != -1) {
                            xpSeedItr.remove();
                            continue seedLoop;
                        }
                    } else {
                        // check the right enchantment clue was generated
                        EnchantmentInstance clue = enchantments.get(rand.nextInt(enchantments.size()));
                        if (BuiltInRegistries.ENCHANTMENT.getId(clue.enchantment) != actualEnchantmentClues[slot]
                                || clue.level != actualLevelClues[slot]) {
                            xpSeedItr.remove();
                            continue seedLoop;
                        }
                    }
                }
            }
        }

        // test the outcome, see if we need to change state
        if (possibleXPSeeds.size() == 0) {
            Configs.enchCrackState = CrackState.UNCRACKED;
            LOGGER.warn(
                    "Invalid enchantment seed information. Has the server got unknown mods, is there a desync, or is the client just bugged?");
        } else if (possibleXPSeeds.size() == 1) {
            Configs.enchCrackState = CrackState.CRACKED;
            addPlayerRNGInfo(possibleXPSeeds.iterator().next());
        }
    }

    private static void addPlayerRNGInfo(int enchantmentSeed) {
        if (Configs.playerCrackState == PlayerRandCracker.CrackState.ENCH_CRACKING_1) {
            firstXpSeed = enchantmentSeed;
            Configs.playerCrackState = PlayerRandCracker.CrackState.HALF_CRACKED;
        } else if (Configs.playerCrackState == PlayerRandCracker.CrackState.ENCH_CRACKING_2) {
            // lattispaghetti
            long max_1 = Integer.toUnsignedLong(firstXpSeed) + 1;
            long min_1 = Integer.toUnsignedLong(firstXpSeed);
            long max_2 = Integer.toUnsignedLong(enchantmentSeed) + 1;
            long a = (24667315 * max_1 + 18218081 * max_2) >> 32;
            long b = (-4824621 * min_1 + 7847617 * max_2) >> 32;

            boolean valid = true;
            long seed = (7847617 * a - 18218081 * b) & PlayerRandCracker.MASK;
            if ((int) (seed >>> 16) != firstXpSeed) {
                valid = false;
            }
            seed = (seed * PlayerRandCracker.MULTIPLIER + PlayerRandCracker.ADDEND) & PlayerRandCracker.MASK;
            if ((int) (seed >>> 16) != enchantmentSeed) {
                valid = false;
            }
            if (valid) {
                PlayerRandCracker.setSeed(seed);
                Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
            } else {
                Configs.playerCrackState = PlayerRandCracker.CrackState.UNCRACKED;
                LOGGER.warn(
                        "Invalid player RNG information. Has the server got unknown mods, is there a desync, has an operator used /give, or is the client just bugged?");
            }
        }
    }

    public static void onEnchantedItem() {
        if (Configs.playerCrackState == PlayerRandCracker.CrackState.UNCRACKED && !isEnchantingPredictionEnabled()) {
            return;
        }
        if (Configs.playerCrackState.knowsSeed()) {
            possibleXPSeeds.clear();
            possibleXPSeeds.add(PlayerRandCracker.nextInt());
            Configs.playerCrackState = PlayerRandCracker.CrackState.CRACKED;
            Configs.enchCrackState = CrackState.CRACKED;
        } else if (Configs.playerCrackState == PlayerRandCracker.CrackState.HALF_CRACKED) {
            possibleXPSeeds.clear();
            Configs.playerCrackState = PlayerRandCracker.CrackState.ENCH_CRACKING_2;
            Configs.enchCrackState = CrackState.UNCRACKED;
        } else if ((Configs.playerCrackState == PlayerRandCracker.CrackState.UNCRACKED
                || Configs.playerCrackState == PlayerRandCracker.CrackState.ENCH_CRACKING_1
                || Configs.playerCrackState == PlayerRandCracker.CrackState.ENCH_CRACKING_2)) {
            possibleXPSeeds.clear();
            Configs.playerCrackState = PlayerRandCracker.CrackState.ENCH_CRACKING_1;
            Configs.enchCrackState = CrackState.UNCRACKED;
        } else {
            PlayerRandCracker.onUnexpectedItemEnchant();
            Configs.enchCrackState = CrackState.UNCRACKED;
        }
        doneEnchantment = true;
    }

    // ENCHANTMENT MANIPULATION
    /*
     * This section is involved in actually manipulating the enchantments and the XP
     * seed
     */

    public static ManipulateResult manipulateEnchantments(Item item, Predicate<List<EnchantmentInstance>> enchantmentsPredicate, boolean simulate) {
        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;

        ItemStack stack = new ItemStack(item);
        long seed = PlayerRandCracker.getSeed();
        // -2: not found; -1: no dummy enchantment needed; >= 0: number of times needed
        // to throw out item before dummy enchantment
        int timesNeeded = -2;
        int bookshelvesNeeded = 0;
        int slot = 0;
        List<EnchantmentInstance> enchantments = null;
        int[] enchantLevels = new int[3];
        outerLoop:
        for (int i = Configs.enchCrackState == CrackState.CRACKED ? -1 : 0;
             i < (Configs.playerCrackState.knowsSeed() ? Configs.getMaxEnchantItemThrows() : 0);
             i++) {
            int xpSeed = i == -1 ?
                    possibleXPSeeds.iterator().next()
                    : (int) (((seed * PlayerRandCracker.MULTIPLIER + PlayerRandCracker.ADDEND) & PlayerRandCracker.MASK) >>> 16);
            RandomSource rand = RandomSource.create();
            for (bookshelvesNeeded = 0; bookshelvesNeeded <= 15; bookshelvesNeeded++) {
                rand.setSeed(xpSeed);
                for (slot = 0; slot < 3; slot++) {
                    int level = EnchantmentHelper.getEnchantmentCost(rand, slot, bookshelvesNeeded, stack);
                    if (level < slot + 1) {
                        level = 0;
                    }
                    enchantLevels[slot] = level;
                }
                for (slot = 0; slot < 3; slot++) {
                    enchantments = getEnchantmentList(rand, xpSeed, stack, slot,
                            enchantLevels[slot]);
                    if (enchantmentsPredicate.test(enchantments)) {
                        timesNeeded = i;
                        break outerLoop;
                    }
                }
            }

            if (i != -1) {
                for (int j = 0; j < 4; j++) {
                    seed = (seed * PlayerRandCracker.MULTIPLIER + PlayerRandCracker.ADDEND) & PlayerRandCracker.MASK;
                }
            }
        }
        if (timesNeeded == -2) {
            return null;
        }

        if (simulate) {
            return new ManipulateResult(timesNeeded, bookshelvesNeeded, slot, enchantments);
        }

        LongTaskList taskList = new LongTaskList();
        if (timesNeeded != -1) {
            if (timesNeeded != 0) {
                player.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 90);
                // sync rotation to server before we throw any items
                player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), 90, player.onGround()));
                Configs.playerCrackState = PlayerRandCracker.CrackState.MANIPULATING_ENCHANTMENTS;
            }
            for (int i = 0; i < timesNeeded; i++) {
                // throw the item once it's in the inventory
                taskList.addTask(new SimpleTask() {
                    @Override
                    public boolean condition() {
                        if (Configs.playerCrackState != PlayerRandCracker.CrackState.MANIPULATING_ENCHANTMENTS) {
                            taskList._break();
                            return false;
                        }

                        Slot slot = PlayerRandCracker.getBestItemThrowSlot(Minecraft.getInstance().player.containerMenu.slots);
                        //noinspection RedundantIfStatement
                        if (slot == null) {
                            return true; // keep waiting
                        } else {
                            return false; // ready to throw an item
                        }
                    }

                    @Override
                    protected void onTick() {
                    }

                    @Override
                    public void onCompleted() {
                        PlayerRandCracker.throwItem();
                        scheduleDelay();
                    }
                });
            }
            // dummy enchantment
            taskList.addTask(new LongTask() {
                @Override
                public void initialize() {
                    Configs.playerCrackState = PlayerRandCracker.CrackState.WAITING_DUMMY_ENCHANT;
                    Minecraft.getInstance().gui.getChat().addMessage(Component.translatable("enchCrack.insn.dummy"));
                    doneEnchantment = false;
                }

                @Override
                public boolean condition() {
                    return Configs.playerCrackState == PlayerRandCracker.CrackState.WAITING_DUMMY_ENCHANT;
                }

                @Override
                public void increment() {
                }

                @Override
                public void body() {
                    scheduleDelay();
                }
            });
        }
        final int bookshelvesNeeded_f = bookshelvesNeeded;
        final int slot_f = slot;
        doneEnchantment = true;
        taskList.addTask(new OneTickTask() {
            @Override
            public void run() {
                if (Configs.enchCrackState == CrackState.CRACKED && doneEnchantment) {
                    ChatComponent chat = Minecraft.getInstance().gui.getChat();
                    chat.addMessage(Component.translatable("enchCrack.insn.ready").withStyle(ChatFormatting.BOLD));
                    chat.addMessage(Component.translatable("enchCrack.insn.bookshelves", bookshelvesNeeded_f));
                    chat.addMessage(Component.translatable("enchCrack.insn.slot", slot_f + 1));
                }
            }
        });

        TaskManager.addTask("enchantmentCracker", taskList);

        return new ManipulateResult(timesNeeded, bookshelvesNeeded, slot, enchantments);
    }

    // MISCELLANEOUS HELPER METHODS & ENCHANTING SIMULATION

    public static boolean isEnchantingPredictionEnabled() {
        return Configs.getEnchantingPrediction();
    }

    private static int getEnchantPower(Level level, BlockPos tablePos) {
        int power = 0;

        int protocolVersion = MultiVersionCompat.INSTANCE.getProtocolVersion();

        for (BlockPos bookshelfOffset : EnchantmentTableBlock.BOOKSHELF_OFFSETS) {
            if (protocolVersion <= MultiVersionCompat.V1_18) {
                // old bookshelf detection method
                BlockPos obstructionPos = tablePos.offset(Mth.clamp(bookshelfOffset.getX(), -1, 1), 0, Mth.clamp(bookshelfOffset.getZ(), -1, 1));
                if (level.getBlockState(tablePos.offset(bookshelfOffset)).is(Blocks.BOOKSHELF) && level.getBlockState(obstructionPos).isAir()) {
                    power++;
                }
            } else if (protocolVersion < MultiVersionCompat.V1_20) {
                // any non-air block used to obstruct bookshelves
                BlockPos obstructionPos = tablePos.offset(bookshelfOffset.getX() / 2, bookshelfOffset.getY(), bookshelfOffset.getZ() / 2);
                if (level.getBlockState(tablePos.offset(bookshelfOffset)).is(Blocks.BOOKSHELF) && level.getBlockState(obstructionPos).isAir()) {
                    power++;
                }
            } else {
                if (EnchantmentTableBlock.isValidBookShelf(level, tablePos, bookshelfOffset)) {
                    power++;
                }
            }
        }

        return power;
    }

    private static List<EnchantmentInstance> getEnchantmentList(RandomSource rand, int xpSeed, ItemStack stack, int enchantSlot,
                                                                  int level) {
        rand.setSeed(xpSeed + enchantSlot);
        List<EnchantmentInstance> list = EnchantmentHelper.selectEnchantment(rand, stack, level, false);

        if (stack.getItem() == Items.BOOK && list.size() > 1) {
            list.remove(rand.nextInt(list.size()));
        }

        return list;
    }

    // Same as above method, except does not assume the seed has been cracked. If it
    // hasn't returns the clue given by the server
    public static List<EnchantmentInstance> getEnchantmentsInTable(int slot) {
        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;
        CrackState crackState = Configs.enchCrackState;
        EnchantmentMenu enchContainer = (EnchantmentMenu) player.containerMenu;

        if (crackState != CrackState.CRACKED) {
            if (enchContainer.enchantClue[slot] == -1) {
                // if we haven't cracked it, and there's no clue, then we can't give any
                // information about the enchantment
                return null;
            } else {
                // return a list containing the clue
                Enchantment enchantment = Enchantment.byId(enchContainer.enchantClue[slot]);
                if (enchantment == null) {
                    return null;
                }
                return Collections.singletonList(new EnchantmentInstance(enchantment, enchContainer.levelClue[slot]));
            }
        } else {
            // return the enchantments using our cracked seed
            RandomSource rand = RandomSource.create();
            int xpSeed = possibleXPSeeds.iterator().next();
            ItemStack enchantingStack = enchContainer.getSlot(0).getItem();
            int enchantLevels = enchContainer.costs[slot];
            return getEnchantmentList(rand, xpSeed, enchantingStack, slot, enchantLevels);
        }
    }

    public record ManipulateResult(int itemThrows, int bookshelves, int slot, List<EnchantmentInstance> enchantments) {}

    public enum CrackState implements StringRepresentable {
        UNCRACKED("uncracked"), CRACKED("cracked"), CRACKING("cracking");

        private final String name;
        CrackState(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

}
