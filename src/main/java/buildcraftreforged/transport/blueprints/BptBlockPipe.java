/**
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: one block per pipe kind and tier, so the pipe id is the block; the pipe's
 * whole block entity (module, gate, wires, facades, plugs) is kept and its sides are turned here, where 4.2.2 asked
 * the per-pipe BptItemPipe* classes; requirements are read from the pipe at capture; the signature override is gone
 * with signatures (#76).
 */

package buildcraftreforged.transport.blueprints;

import buildcraftreforged.api.blueprints.BptBlock;
import buildcraftreforged.api.blueprints.BptSlotInfo;
import buildcraftreforged.api.blueprints.IBptContext;
import buildcraftreforged.api.transport.IPipe;
import buildcraftreforged.transport.Gate;
import buildcraftreforged.transport.GateVanilla;
import buildcraftreforged.transport.ItemFacade;
import buildcraftreforged.transport.PipeModuleType;
import buildcraftreforged.transport.TilePipe;
import buildcraftreforged.transport.TransportRegistry;
import buildcraftreforged.transport.module.ModuleSorter;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * A pipe of any kind and tier (docs/TRANSPORT.md).
 *
 * <p>4.2.2 stored the pipe's item id, its wires and its gate kind in {@code cpt} (gate triggers and actions were
 * commented out), placed a new pipe of that id with those parts, and let a {@code BptItem} per pipe item add the rest:
 * the wooden and iron pipes' facing ({@code BptItemPipeWooden}, {@code BptItemPipeIron}), the diamond pipe's six
 * filter rows ({@code BptItemPipeDiamond}) and the emerald pipe's filters ({@code BptItemPipeEmerald}). Pipes are one
 * block per kind and tier now, with what those pipes did as modules, so:
 * <ul>
 * <li>The block is the pipe id, and any state of the same block counts as built.</li>
 * <li>The pipe's block entity is kept whole, as the default does: module and its settings, gate with its triggers
 * and actions, wires, facades and plugs. Building loads it back, without calling the module's install hook, so the
 * captured facing stays.</li>
 * <li>Turning and mirroring move every per-side datum: the plug and disconnection masks, the facades, the Director's
 * and Extractor's facing (the old wooden and iron pipes), the Sorter's filter rows (the old diamond pipe) and gate
 * actions that point a Director to a side.</li>
 * <li>Once everything is built ({@link #postProcessing}) the module's settings are loaded again, as the wooden and
 * iron pipes set their facing again.</li>
 * <li>Requirements are the pipe's item plus the module, gate, wires, facades and plugs on it, read from the pipe at
 * capture into the slot's stored requirements (4.2.2 rebuilt them from {@code cpt} each time). The module and gate
 * are asked for as plain items: their settings come from the blueprint.</li>
 * </ul>
 */
public class BptBlockPipe extends BptBlock {

    public BptBlockPipe(Block block) {
        super(block);
    }

    @Override
    public boolean isValid(BptSlotInfo slot, IBptContext context) {
        return slot.state.getBlock() == context.world().getBlockState(slot.pos()).getBlock();
    }

    @Override
    public void rotateLeft(BptSlotInfo slot, IBptContext context) {
        super.rotateLeft(slot, context);

        turnSides(slot.cpt, Rotation.CLOCKWISE_90::rotate);
    }

    @Override
    public void mirror(BptSlotInfo slot, IBptContext context, Mirror mirror) {
        super.mirror(slot, context, mirror);

        turnSides(slot.cpt, mirror::mirror);
    }

    @Override
    public void initializeFromWorld(BptSlotInfo bptSlot, IBptContext context, int x, int y, int z) {
        super.initializeFromWorld(bptSlot, context, x, y, z);

        BlockEntity tile = context.world().getBlockEntity(new BlockPos(x, y, z));

        if (tile instanceof TilePipe pipe) {
            PipeModuleType module = pipe.getModuleType();

            if (module != null) {
                addItem(bptSlot, TransportRegistry.getModuleItem(module));
            }

            for (IPipe.WireColor color : IPipe.WireColor.values()) {
                if (pipe.gateHolder.wireSet[color.ordinal()]) {
                    addItem(bptSlot, TransportRegistry.getPipeWire(color));
                }
            }

            Gate gate = pipe.gateHolder.gate;

            if (gate != null) {
                boolean pulser = gate instanceof GateVanilla vanilla && vanilla.hasPulser();
                addItem(bptSlot, TransportRegistry.getGateItem(gate.kind, pulser));
            }

            for (Direction side : Direction.values()) {
                BlockState facade = pipe.getFacade(side);

                if (facade != null) {
                    bptSlot.storedRequirements.add(ItemFacade.getStack(facade));
                }

                if (pipe.hasPlug(side)) {
                    addItem(bptSlot, TransportRegistry.plugItem.get());
                }
            }
        }
    }

    /**
     * 4.2.2's {@code BptItemPipeWooden} and {@code BptItemPipeIron} set the pipe's facing again once the whole
     * blueprint was built, since neighbours placed after the pipe made it look for a new side. The module's settings
     * are loaded again here for the same reason.
     */
    @Override
    public void postProcessing(BptSlotInfo slot, IBptContext context) {
        if (context.world().getBlockEntity(slot.pos()) instanceof TilePipe pipe && pipe.getModule() != null) {
            CompoundTag settings = slot.cpt.getCompoundOrEmpty("module_settings");

            pipe.getModule().load(TagValueInput.create(ProblemReporter.DISCARDING, context.world().registryAccess(), settings));
            pipe.setChanged();
            pipe.sendNetworkUpdate();
        }
    }

    private static void addItem(BptSlotInfo slot, Item item) {
        if (item != null) {
            slot.storedRequirements.add(new ItemStack(item));
        }
    }

    // Per-side data in TilePipe's saved form (TilePipe.saveAdditional, PipeGateHolder.writeToNBT, the modules' save)

    /** Moves each per-side datum of a pipe's saved data from side {@code d} to {@code turn(d)}. */
    public static void turnSides(CompoundTag cpt, UnaryOperator<Direction> turn) {
        turnMask(cpt, "plugs", turn);
        turnMask(cpt, "disconnected", turn);

        Tag[] facades = new Tag[6];

        for (int i = 0; i < 6; ++i) {
            facades[i] = cpt.get("facade[" + i + "]");
            cpt.remove("facade[" + i + "]");
        }

        for (int i = 0; i < 6; ++i) {
            if (facades[i] != null) {
                cpt.put("facade[" + side(turn, i) + "]", facades[i]);
            }
        }

        cpt.getCompound("module_settings").ifPresent(settings -> {
            // ModuleDirector and ModuleExtractor (and their subclasses): -1 for none, else get3DDataValue.
            int facing = settings.getIntOr("facing", -1);

            if (facing >= 0 && facing < 6) {
                settings.putInt("facing", side(turn, facing));
            }

            if (PipeModuleType.SORTER.getSerializedName().equals(cpt.getStringOr("module", ""))) {
                // ModuleSorter: row d holds slots d * ROW to d * ROW + ROW - 1.
                for (Tag entry : settings.getListOrEmpty("filters")) {
                    if (entry instanceof CompoundTag stack) {
                        int slot = stack.getByteOr("Slot", (byte) 0) & 255;
                        int row = slot / ModuleSorter.ROW;

                        if (row < 6) {
                            stack.putByte("Slot", (byte) (side(turn, row) * ModuleSorter.ROW + slot % ModuleSorter.ROW));
                        }
                    }
                }
            }
        });

        cpt.getCompound("Gate").ifPresent(gate -> {
            for (int i = 0; i < 8; ++i) {
                String key = "action[" + i + "]";
                String action = gate.getStringOr(key, "");

                for (Direction side : Direction.values()) {
                    if (action.equals(pipeDirectionAction(side))) {
                        gate.putString(key, pipeDirectionAction(turn.apply(side)));
                        break;
                    }
                }
            }
        });
    }

    private static void turnMask(CompoundTag cpt, String key, UnaryOperator<Direction> turn) {
        if (!cpt.contains(key))
            return;

        int mask = cpt.getByteOr(key, (byte) 0);
        int turned = 0;

        for (int i = 0; i < 6; ++i) {
            if ((mask & (1 << i)) != 0) {
                turned |= 1 << side(turn, i);
            }
        }

        cpt.putByte(key, (byte) turned);
    }

    private static int side(UnaryOperator<Direction> turn, int side) {
        return turn.apply(Direction.from3DDataValue(side)).get3DDataValue();
    }

    private static String pipeDirectionAction(Direction side) {
        return TransportRegistry.actionPipeDirection.get(side.ordinal()).getId().toString();
    }
}
