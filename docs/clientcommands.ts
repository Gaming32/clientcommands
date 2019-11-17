
/**
 * The player which the user has control over
 */
declare const player: ControllablePlayer;
/**
 * The client-side world which the player is in
 */
declare const world: World;

/**
 * Runs a client-side command and returns the result. Can also be an entity selector.
 * @param command The command or entity selector to run.
 * @return The integer result of the command, or list of matching entities in the case of an entity selector.
 */
declare function $(command: string): number | Array<Entity>;

/**
 * Prints a string to client-side chat
 * @param x The string to print
 */
declare function print(x: string): void;

/**
 * Allows the game to run a tick. Pauses script execution until the next tick
 */
declare function tick(): void;

/**
 * Gets information about the given block. Note: this will return information about the default block
 * state of the block. You may sometimes get more accurate information by using {@link World.getBlockInfo}
 * @param block The block to get the information about
 */
declare function getBlockInfo(block: string): BlockInfo;

/**
 * Represents a generic entity
 */
declare class Entity {
    /**
     * The type of the entity, as used in commands. If the prefix, would be "minecraft:", then that prefix is stripped
     */
    readonly type: string;
    /**
     * The x-position of the entity
     */
    readonly x: number;
    /**
     * The y-position of the entity
     */
    readonly y: number;
    /**
     * The z-position of the entity
     */
    readonly z: number;
    /**
     * The yaw of the entity in degrees. 0 degrees is to the south, increasing clockwise
     */
    readonly yaw: number;
    /**
     * The pitch of the entity in degrees. 0 degrees is forwards, increasing downwards
     */
    readonly pitch: number;
    /**
     * The x-velocity of the entity
     */
    readonly motionX: number;
    /**
     * The y-velocity of the entity
     */
    readonly motionY: number;
    /**
     * The z-velocity of the entity
     */
    readonly motionZ: number;
    /**
     * The NBT of the entity
     */
    readonly nbt: object;

    /**
     * Returns whether this entity is the same entity as the other entity
     * @param other The other entity
     */
    equals(other: Entity): boolean;
}

/**
 * A living entity, e.g. mobs, players
 */
declare class LivingEntity extends Entity {
    /**
     * The eye height of the entity in its current pose
     */
    readonly eyeHeight: number;
    /**
     * The eye height of the entity in its standing pose
     */
    readonly standingEyeHeight: number;
}

/**
 * A player
 */
declare class Player extends LivingEntity {

}

/**
 * A player which the user has control over
 */
declare class ControllablePlayer extends Player {

    /**
     * Teleports the player a limited distance. This function cannot teleport the player more than 0.5 blocks,
     * and is meant for alignment rather than movement. Use properties like {@link pressingForward} and
     * {@link sprinting} for movement. Returns whether successful (the player was close enough to the
     * given position).
     * @param x The x-position to snap the player to
     * @param y The y-position to snap the player to
     * @param z The z-position to snap the player to
     * @param sync Whether to sync the position with the server immediately after the teleport, rather than
     * at the start of the next tick. If absent, defaults to false
     */
    snapTo(x: number, y: number, z: number, sync?: boolean): boolean;

    /**
     * Moves the player in a straight line to the specified target position. Blocks until it reaches there.
     * Returns whether successful
     * @param x The x-position of the target
     * @param z The z-position of the target
     * @param smart Default true, whether to jump up single block gaps
     */
    moveTo(x: number, z: number, smart?: boolean): boolean;

    /**
     * The yaw of the player in degrees. 0 degrees is to the south, increasing clockwise
     */
    yaw: number;
    /**
     * The pitch of the player in degrees. 0 degrees is forwards, increasing downwards
     */
    pitch: number;

    /**
     * Causes the player to look towards a point in space.
     * @param x The x-position of the point to look at
     * @param y The y-position of the point to look at
     * @param z The z-position of the point to look at
     */
    lookAt(x: number, y: number, z: number): void;
    /**
     * Causes the player to look towards an entity. If <tt>target</tt> is a {@link LivingEntity}, then
     * the player will look at the eye height of that entity. Otherwise, it will look at the bottom
     * of the entity.
     * @param target The entity to look at
     */
    lookAt(target: Entity): void;

    /**
     * Forces a synchronization of the player's look angles with the server. By default, the player's
     * new rotation is only sent to the server at the start of the next tick, meaning that if you
     * perform certain actions that depend on the player's rotation, such as dropping an item, it will
     * happen as if the player was still facing in the previous direction. Calling this function will
     * immediately send the player's rotation to the server, so that further actions (like dropping an
     * item) will be performed with the correct rotation.
     */
    syncRotation(): void;

    /**
     * The currently selected hotbar slot
     */
    selectedSlot: number;
    /**
     * The player's inventory. Slot numbers are as follows:
     * <table>
     *     <tr><th>Number</th><th>Slot</th></tr>
     *     <tr><td>0-8</td><td>Hotbar</td></tr>
     *     <tr><td>9-35</td><td>The rest of the main inventory</td></tr>
     *     <tr><td>36-39</td><td>Armor (head, torso, legs, feet)</td></tr>
     *     <tr><td>40</td><td>Offhand</td></tr>
     *     <tr><td>41</td><td>Crafting result slot</td></tr>
     *     <tr><td>42-45</td><td>Crafting grid</td></tr>
     * </table>
     * Slots beyond the hotbar and main inventory will be inaccessible while the player is looking into
     * a container.
     */
    readonly inventory: Inventory;
    /**
     * The inventory of the container the player is looking in, or <tt>null</tt> if the player isn't looking
     * in any container
     */
    readonly openContainer: Inventory | null;

    /**
     * Closes the currently opened container, if any is open
     */
    closeContainer(): void;

    /**
     * "picks" an item from the player's inventory, and selects it in the hotbar, in a similar fashion to the
     * vanilla pick block feature.
     * @param item The item to pick, using the item name specified in commands
     * @return Whether a matching item could be found in the inventory
     */
    pick(item: string): boolean;
    /**
     * "picks" an item from the player's inventory, and selects it in the hotbar, in a similar fashion to the
     * vanilla pick block feature. This overloaded function only makes sense when you care about other parts
     * of the item stack NBT, such as stack size, item damage and NBT.
     * @param item The item stack NBT to match against
     * @return Whether a matching item could be found in the inventory
     */
    pick(item: object): boolean;
    /**
     * "picks" an item from the player's inventory, and selects it in the hotbar, in a similar fashion to the
     * vanilla pick block feature. This overloaded function takes a predicate function as a parameter, which
     * takes in an item stack NBT and returns a boolean whether that item stack is eligible to be picked.
     * @param itemPredicate The predicate function which tests whether item stacks are eligible to be picked
     * @return Whether a matching item could be found in the inventory
     */
    pick(itemPredicate: (itemNbt: object) => boolean): boolean;

    /**
     * Right clicks the currently held item in air
     *
     * Warning: right-clicking some items is a continuous action. This function on its own
     * will not work for this! For an easy way to eat food, see {@link longUseItem}
     * @return Whether the item use was considered successful
     */
    rightClick(): boolean;
    /**
     * Right clicks a block. Will click on the closest part of the block at the given position.
     * This function also modifies the player rotation to look at where they clicked, if they are
     * not already hovering over the block.
     * @param x The x-position of the block to right click
     * @param y The y-position of the block to right click
     * @param z The z-position of the block to right click
     * @param side The side of the block to click on. If not specified, will click on the closest side.
     * @return Whether the right click was successful
     */
    rightClick(x: number, y: number, z: number, side?: string): boolean;
    /**
     * Right clicks an entity. This function also modifies the player rotation to look at the entity they
     * clicked on.
     * @param entity The entity to right click
     * @return Whether the right click was successful
     */
    rightClick(entity: Entity): boolean;

    /**
     * Left clicks on a block. Will click on the closest part of the block at the given position.
     * This function also modifies the player rotation to look at where they clicked, if they are
     * not already hovering over the block.
     *
     * Warning: left-clicking many blocks is a continuous mining action. This function on its own
     * will not work for this! For an easy way to mine blocks, see {@link longMineBlock}
     * @param x The x-position of the block to right click
     * @param y The y-position of the block to right click
     * @param z The z-position of the block to right click
     * @param side The side of the block to click on. If not specified, will click on the closest side.
     * @return Whether the left click was successful
     */
    leftClick(x: number, y: number, z: number, side?: string): boolean;
    /**
     * Left clicks on an entity (i.e. usually attacking it). This function also modifies the player rotation to
     * look at the entity they clicked on.
     * @param entity The entity to left click
     * @return Whether the left click was successful
     */
    leftClick(entity: Entity): boolean;

    /**
     * Blocks user input until either this script terminates or {@link unblockInput} is called (and so long as
     * no other scripts are also blocking input).
     */
    blockInput(): void;

    /**
     * Stops this script blocking user input
     */
    unblockInput(): void;

    /**
     * Holds down right click until the item can be used no more! For food, this has the effect of eating a single
     * food item. Pauses execution of the script until the action is finished. Also blocks input for the duration
     * of item use.
     */
    longUseItem(): boolean;

    /**
     * Mines a block with the currently held item until it is broken. Pauses execution of the script until the
     * action is finished. Also blocks input for the duration of the block breaking.
     * @param x The x-position of the block to mine
     * @param y The y-position of the block to mine
     * @param z The z-position of the block to mine
     */
    longMineBlock(x: number, y: number, z: number): boolean;

    /**
     * Whether the script is pressing forward for the player. This shouldn't be used to get whether forward is
     * being pressed, it will produce inconsistent results. It should be used to <b>set</b> whether forward
     * is pressed by the current script.
     */
    pressingForward: boolean;
    /**
     * Whether the script is pressing back for the player. This shouldn't be used to get whether back is
     * being pressed, it will produce inconsistent results. It should be used to <b>set</b> whether back
     * is pressed by the current script.
     */
    pressingBack: boolean;
    /**
     * Whether the script is pressing left for the player. This shouldn't be used to get whether left is
     * being pressed, it will produce inconsistent results. It should be used to <b>set</b> whether left
     * is pressed by the current script.
     */
    pressingLeft: boolean;
    /**
     * Whether the script is pressing right for the player. This shouldn't be used to get whether right is
     * being pressed, it will produce inconsistent results. It should be used to <b>set</b> whether right
     * is pressed by the current script.
     */
    pressingRight: boolean;
    /**
     * Whether the script is pressing the jump key for the player. This shouldn't be used to get whether jump is
     * being pressed, it will produce inconsistent results. It should be used to <b>set</b> whether jump
     * is pressed by the current script.
     */
    jumping: boolean;
    /**
     * Whether the script is pressing the sneak key for the player. This shouldn't be used to get whether sneak is
     * being pressed, it will produce inconsistent results. It should be used to <b>set</b> whether sneak
     * is pressed by the current script.
     */
    sneaking: boolean;
    /**
     * Whether the script is pressing the sprint key for the player. This shouldn't be used to get whether sprint is
     * being pressed, it will produce inconsistent results. It should be used to <b>set</b> whether sprint
     * is pressed by the current script.
     */
    sprinting: boolean;
}

/**
 * The options for an inventory click
 */
interface InventoryClickOptions {
    /**
     * The click type, one of:
     * <table>
     *     <tr><th>Value</th><th>Description</th></tr>
     *     <tr><td><tt>"pickup"</tt></td><td>Swaps the item held by the cursor with the item in the given slot. This is the default click type.</td></tr>
     *     <tr><td><tt>"quick_move"</tt></td><td>"Shift-clicks" on the given slot, which is commonly a quick way of moving items into and out of a container</td></tr>
     *     <tr><td><tt>"swap"</tt></td><td>Swaps the given slot with a hotbar slot, specified by also setting the {@link hotbarSlot} property on this object</td></tr>
     *     <tr><td><tt>"clone"</tt></td><td>(Creative-mode-only) clones the item in the given slot, equivalent to a middle-click on it</td></tr>
     *     <tr><td><tt>"throw"</tt></td><td>Throws the item in the given slot out of the inventory</td></tr>
     *     <tr><td><tt>"quick_craft"</tt></td><td>Performs a quick-craft. Must be performed in multiple stages, specified by also setting the {@link quickCraftStage} property in this object</td></tr>
     *     <tr><td><tt>"pickup_all"</tt></td><td>Picks up all items of the type in the slot</td></tr>
     * </table>
     */
    type?: string;
    /**
     * Whether to simulate a right click rather than a left click (which is the default)
     */
    rightClick?: boolean;
    /**
     * When {@link type} is <tt>"swap"</tt>, specifies the hotbar slot to swap with
     */
    hotbarSlot?: number;
    /**
     * When {@link type} is <tt>"quick_craft"</tt>, specifies the quick craft stage (0-3)
     */
    quickCraftStage?: number;
}

/**
 * Represents an inventory/container of items
 */
declare class Inventory {
    /**
     * The type of inventory. Returns either "player", "creative", "horse" or the container ID.
     * Note that the container ID may be more generic than you might expect, e.g. "generic_9x3"
     * for different types of chests.
     */
    readonly type: string;
    /**
     * The items in the inventory. This is an array of item stack NBT objects.
     */
    readonly items: Array<object>;

    /**
     * Simulates a click in an inventory slot. This function covers most inventory actions.
     * @param slot The slot ID to click on. If <tt>null</tt>, this represents clicking outside
     * the window (usually with the effect of dropping the stack under the cursor).
     * @param options The options of the inventory action. See {@link InventoryClickOptions}.
     */
    click(slot: number | null, options?: InventoryClickOptions): void;

    /**
     * Return whether this inventory is the same as another inventory
     * @param other The other inventory
     */
    equals(other: Inventory): boolean;
}

/**
 * The type of the global <tt>world</tt> variable, the client-side world.
 */
declare class World {
    /**
     * The dimension ID of the current dimension. In vanilla, this can either be <tt>"overworld"</tt>,
     * <tt>"the_nether"</tt> or <tt>"the_end"</tt>; in modded this may take other values.
     */
    readonly dimension: string;

    /**
     * Gets the name of the block at the given position in the client-side world.
     * @param x The x-position of the block to query
     * @param y The y-position of the block to query
     * @param z The z-position of the block to query
     * @return The block name, as used in commands. If there would be a "minecraft:" prefix, the prefix is removed.
     */
    getBlock(x: number, y: number, z: number): string;

    /**
     * Gets the block state property with the given name at the given position.
     * @param x The x-position of the block state to query
     * @param y The y-position of the block state to query
     * @param z The z-position of the block state to query
     * @param property The property name to query. E.g. could be <tt>"power"</tt> for redstone dust, or <tt>"bed_part"</tt> for beds.
     * @return A value representing the property value. If it is a boolean property, returns a boolean. If it is a
     * numeric property, returns a number. Otherwise, returns the string representation of the property value (e.g. if it's a north/south/east/west property).
     */
    getBlockProperty(x: number, y: number, z: number, property: string): boolean | number | string;

    /**
     * Gets information about the block state at the given position.
     * @param x The x-position of the block to get information about
     * @param y The y-position of the block to get information about
     * @param z The z-position of the block to get information about
     */
    getBlockInfo(x: number, y: number, z: number): BlockInfo;

    /**
     * Gets the client-side block entity NBT at the given coordinates
     * @param x The x-position of the block entity whose NBT to get
     * @param y The y-position of the block entity whose NBT to get
     * @param z The z-position of the block entity whose NBT to get
     * @return The NBT object of the block entity, or <tt>null</tt> if there was no block entity
     */
    getBlockEntityNbt(x: number, y: number, z: number): object | null;

    /**
     * Gets the block light at the given position, 0-15
     * @param x The x-coordinate of the position to get the block light of
     * @param y The y-coordinate of the position to get the block light of
     * @param z The z-coordinate of the position to get the block light of
     */
    getBlockLight(x: number, y: number, z: number): number;

    /**
     * Gets the sky light at the given position, 0-15
     * @param x The x-coordinate of the position to get the sky light of
     * @param y The y-coordinate of the position to get the sky light of
     * @param z The z-coordinate of the position to get the sky light of
     */
    getSkyLight(x: number, y: number, z: number): number;
}

/**
 * Defines a "thread", which is an action which can run "concurrently" with other threads.
 * This is not concurrency in the sense you may be used to as a programmer. Only one thread
 * can run at a time. When you start another thread, that thread gains control immediately,
 * until either it stops or it calls the {@link tick} function, which allows other script
 * threads to run their code, and the game to run a tick. Therefore, you do not have to
 * worry about thread safety, as all operations are thread safe.
 */
declare class Thread {
    /**
     * The currently executing thread
     */
    static readonly current: Thread;

    /**
     * Whether the thread is currently running. Note this does not necessarily mean this
     * thread is the currently executing thread. To test that, use <tt>thread === Thread.current</tt>
     */
    readonly running: boolean;

    /**
     * Whether the thread has been paused by {@link pause}. To pause an unpause threads, use the methods
     */
    readonly paused: boolean;

    /**
     * Whether the thread is a daemon thread. If true, this thread will be killed when the parent thread
     * stops; if false, this thread may outlive its parent
     */
    readonly daemon: boolean;

    /**
     * The thread which started this thread. If null, either this thread was not started by a script, or
     * this thread is not a daemon and the parent thread has died
     */
    readonly parent: Thread | null;

    /**
     * An array view of running threads started by this thread
     */
    readonly children: Array<Thread>;

    /**
     * Creates a thread which will execute the given function on it when run. Does not
     * run automatically, remember to explicitly call the {@link run} function.
     * @param action The function to be executed on this thread
     * @param daemon Whether this thread will be killed when the thread which started it
     * is terminated. Defaults to true. If false, the thread can outlive the thread which
     * started it.
     */
    constructor(action: () => void, daemon?: boolean);

    /**
     * Starts the thread. Does nothing if the thread has already started.
     */
    run(): void;

    /**
     * Pauses the thread. The thread will not return from the {@link tick} function until it
     * has been unpaused. If a thread pauses itself, it will continue execution until the next
     * time it calls the {@link tick} function.
     */
    pause(): void;

    /**
     * Unpauses the thread
     */
    unpause(): void;

    /**
     * Kills the thread, which stops it running any more code. If a thread tries to kill itself,
     * it will continue running until it calls the {@link tick} function, at which point it will
     * be killed
     */
    kill(): void;

    /**
     * Blocks the currently executing thread until this thread has finished executing. An exception
     * is thrown if a thread tries to wait for itself
     */
    waitFor(): void;
}

/**
 * Contains information about a block or block state
 */
declare class BlockInfo {
    /**
     * The light level emitted, 0-15
     */
    luminance: number;

    /**
     * The hardness of the block, proportional to how long it takes to mine. For example:
     * <ul>
     * <li>Tall grass: 0</li>
     * <li>Dirt: 0.5</li>
     * <li>Stone: 1.5</li>
     * <li>Obsidian: 50</li>
     * <li>Bedrock: -1</li>
     * </ul>
     */
    hardness: number;

    /**
     * How resistant this block is to explosions. For example:
     * <ul>
     * <li>Stone: 6</li>
     * <li>Obsidian: 1200</li>
     * <li>Bedrock: 3600000</li>
     * </ul>
     * Note: the wiki has these values 5 times larger than they should be
     */
    blastResistance: number;

    /**
     * Whether this block responds to random ticks
     */
    randomTickable: boolean;

    /**
     * A value between 0-1 indicating how slippery a block is. A value of 1 means no friction at all
     * (as if an entity was moving sideways in air), a value of 0 will stop an entity instantly. Most
     * blocks have a slipperiness of 0.6, while ice has a slipperiness of 0.98
     */
    slipperiness: number;

    /**
     * A list of block state properties supported by this block, i.e. those that can be used
     * in {@link World.getBlockProperty}
     */
    stateProperties: Array<string>;

    /**
     * The loot table used to drop items after this block is mined
     */
    lootTable: string;

    /**
     * The translation key used to get the name of this block
     */
    translationKey: string;

    /**
     * The item corresponding to this block, or null if the block has no corresponding item
     */
    item: string | null;

    /**
     * A unique ID of the material of the block, may change across Minecraft versions or when other mods
     * add materials. It's safest to compare against the material ID of a block with a known material
     */
    materialId: number;

    /**
     * The map color of this block, in packed 0xRRGGBB format
     */
    mapColor: number;

    /**
     * How this block reacts to being pushed by a piston.
     * <table>
     *     <tr><th>Value</th><th>Example</th><th>Description</th></tr>
     *     <tr><td><tt>"normal"</tt></td><td>Stone</td><td>Piston will move the block</td></tr>
     *     <tr><td><tt>"destroy"</tt></td><td>Torch</td><td>Piston will destroy the block (it will "pop off")</td></tr>
     *     <tr><td><tt>"block"</tt></td><td>Obsidian</td><td>Piston cannot pull the block and block will prevent piston from pushing</td></tr>
     *     <tr><td><tt>"push_only"</tt></td><td>Glazed terracotta</td><td>Block can only be pushed, does not stick to slime</td></tr>
     * </table>
     */
    pistonBehavior: string;

    /**
     * Whether this block is flammable
     */
    flammable: boolean;

    /**
     * Whether this block will drop without using the correct tool
     */
    canBreakByHand: boolean;

    /**
     * Whether this block is a liquid
     */
    liquid: boolean;

    /**
     * Whether this block blocks light TODO: investigate
     */
    blocksLight: boolean;

    /**
     * Whether this block is replaced when placing a block, e.g. tall grass
     */
    replaceable: boolean;

    /**
     * Whether this is a solid block TODO: investigate
     */
    solid: boolean;

    /**
     * The burn chance, related to how quickly the block burns once it has caught fire
     */
    burnChance: number;

    /**
     * The spread chance, related to how quickly a block catches fire in response to nearby fire
     */
    spreadChance: number;

    /**
     * Whether this block will fall like sand when unsupported
     */
    fallable: boolean;
}