package buildcraftreforged.gametest;

import buildcraftreforged.api.gates.ActionManager;
import buildcraftreforged.api.gates.IAction;
import buildcraftreforged.api.gates.ITrigger;
import buildcraftreforged.api.gates.TriggerParameter;
import buildcraftreforged.api.power.IPowerReceptor;
import buildcraftreforged.api.power.PowerHandler;
import buildcraftreforged.api.transport.IPipe.WireColor;
import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.CoreRegistry;
import buildcraftreforged.core.network.InvalidPacketException;
import buildcraftreforged.core.network.PacketData;
import buildcraftreforged.core.network.PacketHandler;
import buildcraftreforged.core.utils.EnumColor;
import buildcraftreforged.transport.EnergyPulser;
import buildcraftreforged.transport.Gate;
import buildcraftreforged.transport.Gate.GateKind;
import buildcraftreforged.transport.GateSettings;
import buildcraftreforged.transport.GateVanilla;
import buildcraftreforged.transport.IPipeGateTarget;
import buildcraftreforged.transport.ItemGate;
import buildcraftreforged.transport.PipeFlow;
import buildcraftreforged.transport.PipeGateHolder;
import buildcraftreforged.transport.PipeMaterial;
import buildcraftreforged.transport.PowerMode;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.gui.ContainerGateInterface;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

/**
 * #60: gates, pipe wire and the pipe triggers and actions (docs/TRANSPORT.md 12, 14). Gates follow 4.2.2: the kinds,
 * AND/OR resolution, wire signals that lose one per pipe, and the 4.2.2 action and trigger lists per kind.
 */
final class GateTests {
    private static final BlockPos PIPE = new BlockPos(1, 1, 1);
    private static final int SETTLE = 3;

    private GateTests() {
    }

    private static BlockState pipe(PipeType kind) {
        return TransportRegistry.getPipe(kind, PipeMaterial.STONE).defaultBlockState();
    }

    private static PipeGateHolder holder(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, TilePipe.class).gateHolder;
    }

    private static ItemStack gateItem(GateKind kind, boolean autarchic) {
        return new ItemStack(TransportRegistry.getGateItem(kind, autarchic));
    }

    /** A click on the top face of the pipe centre, where a gate sits on a pipe without an upward connection. */
    private static UseOnContext clickTop(GameTestHelper helper, Player player, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        Vec3 hit = new Vec3(absolute.getX() + 0.5, absolute.getY() + 0.75, absolute.getZ() + 0.5);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, absolute, false));
    }

    private static Gate fit(GameTestHelper helper, BlockPos pos, GateKind kind, boolean autarchic) {
        PipeGateHolder holder = holder(helper, pos);
        holder.addOrStripGate(null, gateItem(kind, autarchic), Vec3.ZERO);
        helper.assertTrue(holder.gate != null && holder.gate.kind == kind, "no " + kind + " gate fitted at " + pos);
        return holder.gate;
    }

    private static void wire(GameTestHelper helper, BlockPos pos, WireColor... colors) {
        PipeGateHolder holder = holder(helper, pos);
        for (WireColor color : colors)
            holder.addOrStripWire(null, new ItemStack(TransportRegistry.getPipeWire(color)), color);
    }

    private static List<ItemEntity> drops(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(5));
    }

    private static void clearDrops(GameTestHelper helper) {
        for (ItemEntity entity : drops(helper))
            entity.discard();
    }

    /**
     * The 14 gate items and four wires; fitting by using the item, taking off by sneak-using it on the gate (the
     * gate's programming goes with the item and comes back when it is fitted again), wires by use and sneak-use, and
     * sneaking with an empty hand strips wires before the gate.
     */
    static void itemsAndFitting(GameTestHelper helper) {
        for (boolean autarchic : new boolean[] {false, true}) {
            for (GateKind kind : GateKind.values()) {
                if (kind == GateKind.None)
                    continue;
                ItemGate item = TransportRegistry.getGateItem(kind, autarchic);
                helper.assertTrue(item.kind == kind && item.autarchic == autarchic, "wrong gate item for " + kind);
                helper.assertValueEqual(item.getDescriptionId(),
                        (autarchic ? "item.pipeGateAutarchic." : "item.pipeGate.") + (kind.ordinal() - 1),
                        "translation key of " + kind);
            }
        }
        helper.assertValueEqual(TransportRegistry.redPipeWire.getId().getPath(), "red_pipe_wire", "wire name");

        helper.setBlock(PIPE, pipe(PipeType.ITEM));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        PipeGateHolder holder = holder(helper, PIPE);

        ItemStack gold = gateItem(GateKind.AND_3, false).copyWithCount(2);
        player.setItemInHand(InteractionHand.MAIN_HAND, gold);
        helper.assertTrue(gold.useOn(clickTop(helper, player, PIPE)).consumesAction(), "gate not fitted");
        helper.assertTrue(holder.gate != null && holder.gate.kind == GateKind.AND_3, "wrong gate fitted");
        helper.assertValueEqual(gold.getCount(), 1, "gate items left after fitting");
        helper.assertFalse(gold.useOn(clickTop(helper, player, PIPE)).consumesAction(),
                "a second gate went on the pipe");

        // Program it, then take it off: the dropped item keeps the program.
        holder.gate.setTrigger(0, CoreRegistry.triggerRedstoneActive.get());
        holder.gate.setAction(0, CoreRegistry.actionRedstone.get());
        holder.gate.setTriggerParameter(2, TriggerParameter.of(new ItemStack(Items.DIAMOND)));
        GateSettings program = holder.gate.getSettings();
        clearDrops(helper);
        player.setShiftKeyDown(true);
        helper.assertTrue(gold.useOn(clickTop(helper, player, PIPE)).consumesAction(), "gate not taken off");
        helper.assertTrue(holder.gate == null, "gate still on the pipe");
        List<ItemEntity> dropped = drops(helper);
        helper.assertTrue(dropped.size() == 1 && dropped.get(0).getItem().is(gold.getItem()), "gate item not dropped");
        ItemStack droppedGate = dropped.get(0).getItem();
        helper.assertValueEqual(droppedGate.get(TransportRegistry.gateSettings.get()), program, "program on the item");
        helper.assertFalse(ItemStack.isSameItemSameComponents(droppedGate, gold), "a programmed gate stacks with a blank");

        // Fit the dropped gate again: the program is back.
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, droppedGate);
        droppedGate.useOn(clickTop(helper, player, PIPE));
        helper.assertTrue(holder.gate != null
                && holder.gate.getTrigger(0) == CoreRegistry.triggerRedstoneActive.get()
                && holder.gate.getAction(0) == CoreRegistry.actionRedstone.get()
                && holder.gate.getTriggerParameter(2).getItemStack().is(Items.DIAMOND), "program not restored");

        // Wires: use lays one, a second use does nothing, sneak-use takes it off.
        ItemStack red = new ItemStack(TransportRegistry.redPipeWire.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, red);
        helper.assertTrue(red.useOn(clickTop(helper, player, PIPE)).consumesAction(), "wire not laid");
        helper.assertTrue(holder.wireSet[WireColor.Red.ordinal()] && red.getCount() == 2, "red wire missing");
        helper.assertFalse(red.useOn(clickTop(helper, player, PIPE)).consumesAction(), "wire laid twice");
        player.setShiftKeyDown(true);
        clearDrops(helper);
        helper.assertTrue(red.useOn(clickTop(helper, player, PIPE)).consumesAction(), "wire not taken off");
        helper.assertFalse(holder.wireSet[WireColor.Red.ordinal()], "red wire still laid");
        helper.assertTrue(drops(helper).size() == 1, "taken-off wire not dropped");

        // Empty hand, sneaking: wires go first, then the gate.
        wire(helper, PIPE, WireColor.Blue);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos absolute = helper.absolutePos(PIPE);
        BlockHitResult top = new BlockHitResult(new Vec3(absolute.getX() + 0.5, absolute.getY() + 0.75,
                absolute.getZ() + 0.5), Direction.UP, absolute, false);
        BlockState state = helper.getBlockState(PIPE);
        state.useWithoutItem(helper.getLevel(), player, top);
        helper.assertTrue(!holder.wireSet[WireColor.Blue.ordinal()] && holder.gate != null, "wire not stripped first");
        state.useWithoutItem(helper.getLevel(), player, top);
        helper.assertTrue(holder.gate == null, "gate not stripped after the wires");

        // Breaking the pipe drops what is on it.
        clearDrops(helper);
        fit(helper, PIPE, GateKind.OR_4, true);
        wire(helper, PIPE, WireColor.Green, WireColor.Yellow);
        helper.getLevel().destroyBlock(absolute, false);
        List<ItemStack> broken = new ArrayList<>();
        for (ItemEntity entity : drops(helper))
            broken.add(entity.getItem());
        helper.assertTrue(broken.stream().anyMatch(s -> s.is(TransportRegistry.getGateItem(GateKind.OR_4, true)))
                && broken.stream().anyMatch(s -> s.is(TransportRegistry.greenPipeWire.get()))
                && broken.stream().anyMatch(s -> s.is(TransportRegistry.yellowPipeWire.get())),
                "breaking the pipe dropped " + broken);
        clearDrops(helper);
        helper.succeed();
    }

    /**
     * Every kind resolves its positions as 4.2.2: one action fed by several triggers is on when all of them are
     * (AND) or any of them is (OR); a single gate has one position. Then the redstone input follows a redstone block
     * and the redstone output lights a lamp.
     *
     * <p>A gate emitting redstone powers the solid blocks around its pipe, and the pipe sees that as a redstone input,
     * as in 4.2.2; so the logic part uses the Pipe Contents triggers of an idle item pipe ("Empty" true, "Items
     * Traversing" false), and the input and output parts are separate.
     */
    static void logicKinds(GameTestHelper helper) {
        helper.setBlock(PIPE, pipe(PipeType.ITEM));
        PipeGateHolder holder = holder(helper, PIPE);
        ITrigger isTrue = TransportRegistry.triggerPipeEmpty.get();
        ITrigger isFalse = TransportRegistry.triggerPipeItems.get();
        IAction redstone = CoreRegistry.actionRedstone.get();

        for (GateKind kind : GateKind.values()) {
            if (kind == GateKind.None)
                continue;
            holder.resetGate();
            Gate gate = fit(helper, PIPE, kind, false);
            int positions = kind.getPositions();
            helper.assertValueEqual(positions, switch (kind) {
                case Single -> 1;
                case AND_2, OR_2 -> 2;
                case AND_3, OR_3 -> 4;
                default -> 8;
            }, "positions of " + kind);
            boolean and = kind.name().startsWith("AND");
            boolean or = kind.name().startsWith("OR");
            helper.assertValueEqual(gate.getConditional(), and ? Gate.GateConditional.AND
                    : or ? Gate.GateConditional.OR : Gate.GateConditional.None, "conditional of " + kind);

            // All positions true: every kind emits.
            for (int i = 0; i < positions; i++) {
                gate.setTrigger(i, isTrue);
                gate.setAction(i, redstone);
            }
            gate.resolveActions();
            helper.assertTrue(gate.broadcastRedstone, kind + " with all triggers true did not emit");

            // One position false: AND stops, OR goes on, a single gate (whose only position it is) stops.
            gate.setTrigger(positions - 1, isFalse);
            gate.resolveActions();
            helper.assertValueEqual(gate.broadcastRedstone, or, kind + " with one false trigger");

            // A position with a trigger and no action takes no part.
            gate.setAction(positions - 1, null);
            gate.resolveActions();
            helper.assertValueEqual(gate.broadcastRedstone, positions > 1, kind + " counted a position without action");
        }
        holder.resetGate();

        // Redstone input: a redstone block next to the pipe.
        Gate gate = fit(helper, PIPE, GateKind.Single, false);
        BlockPos power = PIPE.north();
        BlockPos lamp = PIPE.south();
        ITrigger on = CoreRegistry.triggerRedstoneActive.get();
        ITrigger off = CoreRegistry.triggerRedstoneInactive.get();
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(!gate.isNearbyTriggerActive(on, null) && gate.isNearbyTriggerActive(off, null),
                    "Redstone Signal On/Off without power");
            helper.setBlock(power, Blocks.REDSTONE_BLOCK);
            helper.runAfterDelay(SETTLE, () -> {
                helper.assertTrue(gate.isNearbyTriggerActive(on, null) && !gate.isNearbyTriggerActive(off, null),
                        "Redstone Signal On/Off with a redstone block");
                helper.setBlock(power, Blocks.AIR);

                // Redstone output: "Pipe Empty -> Redstone Signal" lights a lamp until items flow.
                helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
                gate.setTrigger(0, isTrue);
                gate.setAction(0, redstone);
                helper.runAfterDelay(SETTLE, () -> {
                    helper.assertTrue(gate.isEmittingRedstone(), "gate does not emit");
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true);
                    holder.flow = new Flow(true, new ItemStack(Items.STONE), FluidStack.EMPTY, false, false);
                    helper.runAfterDelay(SETTLE + 4, () -> {
                        helper.assertFalse(gate.isEmittingRedstone(), "gate still emits");
                        helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false);
                        helper.succeed();
                    });
                });
            });
        });
    }

    /**
     * Pipe wire: a gate broadcasting a colour puts 255 on its pipe's wire, and each wired pipe along the line takes
     * one less. Colours are independent, a pipe without a colour stops it, and a pipe that is not connected (another
     * kind) never gets the signal. Signal triggers on the far gate read it; the signal dies when the source stops.
     */
    static void wireSignals(GameTestHelper helper) {
        BlockPos[] line = new BlockPos[5];
        for (int x = 0; x < 5; x++) {
            line[x] = new BlockPos(x, 1, 1);
            helper.setBlock(line[x], pipe(PipeType.ITEM));
        }
        BlockPos fluidPipe = new BlockPos(2, 2, 1);
        helper.setBlock(fluidPipe, pipe(PipeType.FLUID));

        for (int x = 0; x < 5; x++) {
            if (x == 2)
                wire(helper, line[x], WireColor.Red, WireColor.Blue);
            else
                wire(helper, line[x], WireColor.values());
        }
        wire(helper, fluidPipe, WireColor.Red);

        // Source: a diamond gate turns "Redstone Signal On" into all four colours.
        Gate source = fit(helper, line[0], GateKind.OR_4, false);
        LinkedList<IAction> sourceActions = new LinkedList<>();
        source.addActions(sourceActions);
        IAction[] signals = {TransportRegistry.actionRedSignal.get(), TransportRegistry.actionBlueSignal.get(),
                TransportRegistry.actionGreenSignal.get(), TransportRegistry.actionYellowSignal.get()};
        for (int i = 0; i < 4; i++) {
            helper.assertTrue(sourceActions.contains(signals[i]), "diamond gate does not offer " + signals[i]);
            source.setTrigger(i, CoreRegistry.triggerRedstoneActive.get());
            source.setAction(i, signals[i]);
        }

        // Sink: an iron gate reads "Red Pipe Signal On" and emits redstone into a lamp.
        Gate sink = fit(helper, line[4], GateKind.AND_2, false);
        LinkedList<IAction> ironActions = new LinkedList<>();
        sink.addActions(ironActions);
        helper.assertTrue(ironActions.contains(signals[0]) && !ironActions.contains(signals[1])
                && !ironActions.contains(signals[2]), "iron gate signal actions: " + ironActions);
        LinkedList<ITrigger> ironTriggers = new LinkedList<>();
        sink.addTrigger(ironTriggers);
        helper.assertTrue(ironTriggers.contains(TransportRegistry.triggerRedSignalActive.get())
                && !ironTriggers.contains(TransportRegistry.triggerBlueSignalActive.get()),
                "iron gate signal triggers: " + ironTriggers);
        sink.setTrigger(0, TransportRegistry.triggerRedSignalActive.get());
        sink.setAction(0, CoreRegistry.actionRedstone.get());
        BlockPos lamp = new BlockPos(4, 1, 2);
        helper.setBlock(lamp, Blocks.REDSTONE_LAMP);
        BlockPos power = new BlockPos(0, 2, 1);

        helper.runAfterDelay(SETTLE, () -> {
            for (int x = 0; x < 5; x++)
                helper.assertValueEqual(holder(helper, line[x]).signalStrength[WireColor.Red.ordinal()], 0,
                        "red signal before the source runs, pipe " + x);
            helper.setBlock(power, Blocks.REDSTONE_BLOCK);
            helper.runAfterDelay(20, () -> {
                int[][] expected = {
                        {255, 254, 253, 252, 251}, // red
                        {255, 254, 253, 252, 251}, // blue
                        {255, 254, 0, 0, 0}, // green stops at the pipe without green
                        {255, 254, 0, 0, 0}}; // yellow too
                for (WireColor color : WireColor.values()) {
                    for (int x = 0; x < 5; x++) {
                        helper.assertValueEqual(holder(helper, line[x]).signalStrength[color.ordinal()],
                                expected[color.ordinal()][x], color + " signal at pipe " + x);
                    }
                }
                helper.assertValueEqual(holder(helper, fluidPipe).signalStrength[WireColor.Red.ordinal()], 0,
                        "red signal crossed into an unconnected pipe");
                helper.assertTrue(TransportRegistry.triggerRedSignalActive.get() instanceof
                        buildcraftreforged.transport.ITriggerPipe trigger
                        && trigger.isTriggerActive(helper.getBlockEntity(line[2], TilePipe.class), null),
                        "Red Pipe Signal On is false on a lit pipe");
                helper.assertTrue(holder(helper, line[4]).isWireLit(WireColor.Red), "red wire not drawn lit");
                helper.assertTrue(holder(helper, line[1]).isWireConnected(WireColor.Red, Direction.EAST)
                        && !holder(helper, line[2]).isWireConnected(WireColor.Green, Direction.WEST),
                        "wire render connections");
                helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, true);

                helper.setBlock(power, Blocks.AIR);
                helper.runAfterDelay(30, () -> {
                    for (WireColor color : WireColor.values()) {
                        for (int x = 0; x < 5; x++)
                            helper.assertValueEqual(holder(helper, line[x]).signalStrength[color.ordinal()], 0,
                                    color + " signal left at pipe " + x);
                    }
                    helper.assertTrue(TransportRegistry.triggerRedSignalInactive.get() instanceof
                            buildcraftreforged.transport.ITriggerPipe trigger
                            && trigger.isTriggerActive(helper.getBlockEntity(line[4], TilePipe.class), null),
                            "Red Pipe Signal Off is false on a dark pipe");
                    helper.assertBlockProperty(lamp, RedstoneLampBlock.LIT, false);
                    helper.succeed();
                });
            });
        });
    }

    private record Flow(boolean isFlowing, ItemStack itemContent, FluidStack fluidContent, boolean requestsEnergy,
            boolean isOverloaded) implements PipeFlow {
    }

    private static boolean active(GameTestHelper helper, BlockPos pos, ITrigger trigger, @Nullable ItemStack parameter) {
        Gate gate = holder(helper, pos).gate;
        return gate.isNearbyTriggerActive(trigger, parameter == null ? null : TriggerParameter.of(parameter));
    }

    /**
     * Pipe Contents (docs/TRANSPORT.md 12) on each kind, read from the pipe's flow, and the triggers each kind
     * offers. The flow is stubbed: the records (#59) and power phases (#52) are not in yet.
     */
    static void pipeContents(GameTestHelper helper) {
        BlockPos item = new BlockPos(0, 1, 1);
        BlockPos fluid = new BlockPos(2, 1, 1);
        BlockPos power = new BlockPos(4, 1, 1);
        helper.setBlock(item, pipe(PipeType.ITEM));
        helper.setBlock(fluid, pipe(PipeType.FLUID));
        helper.setBlock(power, pipe(PipeType.POWER));
        for (BlockPos pos : List.of(item, fluid, power))
            fit(helper, pos, GateKind.AND_3, false);

        ITrigger empty = TransportRegistry.triggerPipeEmpty.get();
        ITrigger items = TransportRegistry.triggerPipeItems.get();
        ITrigger fluids = TransportRegistry.triggerPipeFluids.get();
        ITrigger energy = TransportRegistry.triggerPipeContainsEnergy.get();
        ITrigger requests = TransportRegistry.triggerPipeRequestsEnergy.get();
        ITrigger overload = TransportRegistry.triggerPipeTooMuchEnergy.get();

        // Offered per kind (4.2.2 PipeTriggerProvider, Power Requested on every power pipe).
        helper.assertValueEqual(ActionManager.getPipeTriggers(holder(helper, item)).stream()
                .filter(t -> List.of(empty, items, fluids, energy, requests, overload).contains(t)).toList(),
                List.of(empty, items), "item pipe triggers");
        helper.assertValueEqual(ActionManager.getPipeTriggers(holder(helper, fluid)).stream()
                .filter(t -> List.of(empty, items, fluids, energy, requests, overload).contains(t)).toList(),
                List.of(empty, fluids), "fluid pipe triggers");
        helper.assertValueEqual(ActionManager.getPipeTriggers(holder(helper, power)).stream()
                .filter(t -> List.of(empty, items, fluids, energy, requests, overload).contains(t)).toList(),
                List.of(empty, energy, overload, requests), "power pipe triggers");
        helper.assertTrue(items.hasParameter() && fluids.hasParameter() && !empty.hasParameter(), "parameters");

        // Nothing flows: Empty only.
        for (BlockPos pos : List.of(item, fluid, power)) {
            helper.assertTrue(active(helper, pos, empty, null), "Empty false on an idle pipe at " + pos);
            for (ITrigger trigger : List.of(items, fluids, energy, requests, overload))
                helper.assertFalse(active(helper, pos, trigger, null), trigger.getUniqueTag() + " true on " + pos);
        }

        holder(helper, item).flow = new Flow(true, new ItemStack(Items.COBBLESTONE), FluidStack.EMPTY, false, false);
        helper.assertFalse(active(helper, item, empty, null), "Empty true while items flow");
        helper.assertTrue(active(helper, item, items, null), "Items Traversing false");
        helper.assertTrue(active(helper, item, items, new ItemStack(Items.COBBLESTONE)), "Items Traversing (cobble)");
        helper.assertFalse(active(helper, item, items, new ItemStack(Items.DIRT)), "Items Traversing (dirt)");

        holder(helper, fluid).flow = new Flow(true, ItemStack.EMPTY, new FluidStack(Fluids.WATER, 100), false, false);
        helper.assertFalse(active(helper, fluid, empty, null), "Empty true while fluid flows");
        helper.assertTrue(active(helper, fluid, fluids, null), "Fluid Traversing false");
        helper.assertTrue(active(helper, fluid, fluids, new ItemStack(Items.WATER_BUCKET)), "Fluid Traversing (water)");
        helper.assertFalse(active(helper, fluid, fluids, new ItemStack(Items.LAVA_BUCKET)), "Fluid Traversing (lava)");
        helper.assertFalse(active(helper, fluid, items, null), "Items Traversing on a fluid pipe");

        holder(helper, power).flow = new Flow(true, ItemStack.EMPTY, FluidStack.EMPTY, false, false);
        helper.assertTrue(active(helper, power, energy, null) && !active(helper, power, empty, null), "power flow");
        helper.assertFalse(active(helper, power, requests, null) || active(helper, power, overload, null),
                "requests or overload without cause");
        holder(helper, power).flow = new Flow(false, ItemStack.EMPTY, FluidStack.EMPTY, true, true);
        helper.assertTrue(active(helper, power, requests, null) && active(helper, power, overload, null),
                "Power Requested / Power Overloaded");
        helper.succeed();
    }

    /** Records what the gate hands the module: 4.2.2 pipes overrode getActions and actionsActivated. */
    private static final class TestTarget implements IPipeGateTarget {
        final List<IAction> offered;
        final Map<IAction, Boolean> received = new HashMap<>();

        TestTarget(List<IAction> offered) {
            this.offered = offered;
        }

        @Override
        public void addActions(LinkedList<IAction> list) {
            list.addAll(offered);
        }

        @Override
        public void actionsActivated(Map<IAction, Boolean> actions) {
            received.clear();
            received.putAll(actions);
        }
    }

    /**
     * Pipe Direction, Pipe Color, Extraction Preset and Power Limiter (docs/TRANSPORT.md 12) are registered with their
     * 4.2.2 tags, offered through the pipe's module and handed to it with their state. The modules that act on them
     * are #55–#57; the Energy Pulser actions come with autarchic gates and pulse a power receptor every 10 ticks.
     */
    static void moduleActionsAndPulser(GameTestHelper helper) {
        helper.assertValueEqual(TransportRegistry.actionPipeColor.size(), 16, "Pipe Color actions");
        helper.assertValueEqual(TransportRegistry.actionPipeDirection.size(), 6, "Pipe Direction actions");
        helper.assertValueEqual(TransportRegistry.actionPowerLimiter.size(), 7, "Power Limiter actions");
        helper.assertValueEqual(TransportRegistry.actionPipeColor.get(EnumColor.RED.ordinal()).get().getUniqueTag(),
                "buildcraft.pipe.color.red", "Pipe Color tag");
        helper.assertValueEqual(TransportRegistry.actionPipeDirection.get(Direction.EAST.ordinal()).get().getUniqueTag(),
                "buildcraft.pipe.dir.east", "Pipe Direction tag");
        helper.assertValueEqual(TransportRegistry.actionPowerLimiter.get(PowerMode.M128.ordinal()).get().getDescription(),
                "1280 FE/t Limit", "Power Limiter text");
        helper.assertValueEqual(TransportRegistry.actionExtractionPresetBlue.get().getUniqueTag(),
                "buildcraft.extraction.preset.color.blue", "Extraction Preset tag");
        helper.assertTrue(ActionManager.actions.get("buildcraft.pulser.constant") == TransportRegistry.actionEnergyPulser.get()
                && ActionManager.triggers.get("buildcraft.redtone.input.active") == CoreRegistry.triggerRedstoneActive.get(),
                "4.2.2 tags do not find the entries");

        helper.setBlock(PIPE, pipe(PipeType.POWER));
        PipeGateHolder holder = holder(helper, PIPE);
        IAction east = TransportRegistry.actionPipeDirection.get(Direction.EAST.ordinal()).get();
        IAction west = TransportRegistry.actionPipeDirection.get(Direction.WEST.ordinal()).get();
        IAction red = TransportRegistry.actionPipeColor.get(EnumColor.RED.ordinal()).get();
        IAction limit = TransportRegistry.actionPowerLimiter.get(PowerMode.M2.ordinal()).get();
        IAction preset = TransportRegistry.actionExtractionPresetRed.get();
        TestTarget target = new TestTarget(List.of(east, west, red, limit, preset));
        holder.target = target;

        Gate gate = fit(helper, PIPE, GateKind.OR_4, true);
        LinkedList<IAction> offered = holder.getActions();
        helper.assertTrue(offered.containsAll(target.offered), "module actions not offered: " + offered);
        helper.assertTrue(offered.contains(TransportRegistry.actionEnergyPulser.get())
                && offered.contains(TransportRegistry.actionSingleEnergyPulse.get()), "autarchic gate offers no pulser");

        // Unpowered pipe: "Redstone Signal Off" is true, "On" false.
        holder.redstonePowered = false;
        gate.setTrigger(0, CoreRegistry.triggerRedstoneInactive.get());
        gate.setAction(0, east);
        gate.setTrigger(1, CoreRegistry.triggerRedstoneActive.get());
        gate.setAction(1, west);
        gate.setTrigger(2, CoreRegistry.triggerRedstoneInactive.get());
        gate.setAction(2, red);
        gate.setTrigger(3, CoreRegistry.triggerRedstoneInactive.get());
        gate.setAction(3, limit);
        gate.setTrigger(4, CoreRegistry.triggerRedstoneActive.get());
        gate.setAction(4, preset);
        gate.resolveActions();
        helper.assertValueEqual(target.received, Map.of(east, true, west, false, red, true, limit, true, preset, false),
                "actions handed to the module");

        // A pipe gate's pulser has no receptor (pipes need no power), but lights the gate while active.
        gate.setTrigger(5, CoreRegistry.triggerRedstoneInactive.get());
        gate.setAction(5, TransportRegistry.actionEnergyPulser.get());
        gate.resolveActions();
        helper.assertTrue(((GateVanilla) gate).getPulser().isActive() && ((GateVanilla) gate).isLit(false),
                "pulser not running");

        // The pulser on a power receptor: min(2^(n-1), 64) x 1.01 MJ, as FE, every 10 ticks.
        helper.assertValueEqual(EnergyPulser.pulseEnergy(1), 10L, "one pulser action");
        helper.assertValueEqual(EnergyPulser.pulseEnergy(2), 20L, "two pulser actions");
        helper.assertValueEqual(EnergyPulser.pulseEnergy(8), 646L, "eight pulser actions");
        Receptor receptor = new Receptor(helper.getLevel());
        EnergyPulser pulser = new EnergyPulser(receptor);
        pulser.enablePulse(1);
        for (int tick = 0; tick < 30; tick++)
            pulser.update();
        helper.assertValueEqual(receptor.handler.getEnergyStored(), 30L, "three pulses in 30 ticks");
        pulser.disablePulse();
        pulser.update();
        pulser.enableSinglePulse(2);
        for (int tick = 0; tick < 40; tick++)
            pulser.update();
        helper.assertValueEqual(receptor.handler.getEnergyStored(), 50L, "a single pulse pulses once");
        helper.succeed();
    }

    private static final class Receptor implements IPowerReceptor {
        final PowerHandler handler;
        private final Level level;

        Receptor(Level level) {
            this.level = level;
            handler = new PowerHandler(this, PowerHandler.Type.MACHINE);
            handler.configure(1, 100000, 1000000, 1000000);
        }

        @Override
        public PowerHandler.PowerReceiver getPowerReceiver(@Nullable Direction side) {
            return handler.getPowerReceiver();
        }

        @Override
        public void doWork(PowerHandler workProvider) {
        }

        @Override
        public Level getWorld() {
            return level;
        }
    }

    /**
     * The gate menu on a server: the offered lists (pipe, gate, neighbour), a client copy filled from the widget's
     * bytes, a selection from the client applied and saved, and selections the server did not offer refused.
     */
    static void menu(GameTestHelper helper) {
        helper.setBlock(PIPE, pipe(PipeType.ITEM));
        helper.setBlock(PIPE.east(), Blocks.CHEST);
        PipeGateHolder holder = holder(helper, PIPE);
        wire(helper, PIPE, WireColor.Red);
        TilePipe tile = helper.getBlockEntity(PIPE, TilePipe.class);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(PIPE);
        player.snapTo(Vec3.atBottomCenterOf(pos.above()));

        helper.runAfterDelay(SETTLE, () -> {
            fit(helper, PIPE, GateKind.AND_2, false);
            ContainerGateInterface menu = new ContainerGateInterface(1, player.getInventory(), tile);
            player.containerMenu = menu;

            List<ITrigger> triggers = new ArrayList<>(menu.getPotentialTriggers());
            List<IAction> actions = new ArrayList<>(menu.getPotentialActions());
            for (ITrigger expected : List.of(CoreRegistry.triggerRedstoneActive.get(),
                    TransportRegistry.triggerPipeEmpty.get(), TransportRegistry.triggerRedSignalActive.get(),
                    CoreRegistry.triggerEmptyInventory.get()))
                helper.assertTrue(triggers.contains(expected), "menu does not offer " + expected.getUniqueTag());
            helper.assertFalse(triggers.contains(CoreRegistry.triggerInventoryBelow25.get()),
                    "an iron gate offers a trigger that needs a parameter");
            helper.assertTrue(actions.contains(CoreRegistry.actionRedstone.get())
                    && actions.contains(TransportRegistry.actionRedSignal.get()), "menu actions " + actions);

            // Server to client.
            RegistryAccess access = helper.getLevel().registryAccess();
            ContainerGateInterface client = new ContainerGateInterface(1, player.getInventory(), tile);
            holder.gate.setTrigger(1, TransportRegistry.triggerPipeEmpty.get());
            holder.gate.setAction(1, CoreRegistry.actionRedstone.get());
            byte[] data = menu.widget.getData(access, true);
            client.handleWidgetClientData(menu.getWidgets().indexOf(menu.widget), PacketData.wrap(data, access));
            helper.assertValueEqual(new ArrayList<>(client.getPotentialTriggers()), triggers, "client trigger list");
            helper.assertValueEqual(new ArrayList<>(client.getPotentialActions()), actions, "client action list");
            helper.assertTrue(client.triggerState[1], "Pipe Empty not shown active on the client");

            // Client to server: one position, checked.
            String reason = PacketHandler.checkMenuAccess(PacketHandler.Sender.of(player), menu.containerId, pos);
            helper.assertTrue(reason == null, "GUI return refused: " + reason);
            sendSelection(helper, menu, player, 0, CoreRegistry.triggerRedstoneInactive.get(),
                    TransportRegistry.actionRedSignal.get(), ContainerGateInterface.PARAMETER_KEEP, true);
            helper.assertTrue(holder.gate.getTrigger(0) == CoreRegistry.triggerRedstoneInactive.get()
                    && holder.gate.getAction(0) == TransportRegistry.actionRedSignal.get(), "selection not applied");
            sendSelection(helper, menu, player, 2, CoreRegistry.triggerRedstoneActive.get(), null,
                    ContainerGateInterface.PARAMETER_KEEP, false);
            sendSelection(helper, menu, player, 0, CoreRegistry.triggerInventoryBelow25.get(), null,
                    ContainerGateInterface.PARAMETER_KEEP, false);
            sendSelection(helper, menu, player, 0, null, TransportRegistry.actionBlueSignal.get(),
                    ContainerGateInterface.PARAMETER_KEEP, false);
            sendSelection(helper, menu, player, 0, CoreRegistry.triggerRedstoneActive.get(), null,
                    ContainerGateInterface.PARAMETER_FROM_CARRIED, false);

            // The selection survives a save and load.
            CompoundTag saved = tile.saveWithoutMetadata(access);
            TilePipe copy = new TilePipe(tile.getBlockPos(), tile.getBlockState());
            copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, access, saved));
            helper.assertTrue(copy.gateHolder.gate != null && copy.gateHolder.gate.kind == GateKind.AND_2
                    && copy.gateHolder.gate.getTrigger(0) == CoreRegistry.triggerRedstoneInactive.get()
                    && copy.gateHolder.gate.getAction(1) == CoreRegistry.actionRedstone.get()
                    && copy.gateHolder.wireSet[WireColor.Red.ordinal()], "gate or wire lost in save and load");

            // Taking the gate off closes the menu.
            holder.stripGate();
            helper.assertFalse(menu.stillValid(player), "menu still open without its gate");
            player.containerMenu = player.inventoryMenu;
            clearDrops(helper);
            helper.succeed();
        });
    }

    private static void sendSelection(GameTestHelper helper, ContainerGateInterface menu, Player player, int position,
            @Nullable ITrigger trigger, @Nullable IAction action, int parameterMode, boolean valid) {
        RegistryAccess access = helper.getLevel().registryAccess();
        byte[] bytes = PacketData.write(access,
                data -> ContainerGateInterface.writeSelection(data, position, trigger, action, parameterMode));
        RegistryFriendlyByteBuf data = PacketData.wrap(bytes, access);
        boolean refused = false;
        try {
            menu.getGuiReturnHandler().readGuiData(data, player);
        } catch (InvalidPacketException e) {
            refused = true;
        } finally {
            data.release();
        }
        helper.assertTrue(refused != valid, (valid ? "valid selection refused" : "invalid selection accepted")
                + " at position " + position);
    }
}
