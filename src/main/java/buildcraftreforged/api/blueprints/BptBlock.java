/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: keyed by Block, slots hold a BlockState, rotation by BlockState.rotate, block
 * entity data through the block entity save API, signatures removed (plan section 13); mirror added (#75).
 */

package buildcraftreforged.api.blueprints;

import java.util.LinkedList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;

/**
 * This class allow to specify specific behavior for blocks stored in blueprints:
 *
 * - what items needs to be used to create that block - how the block has to be built on the world - how to rotate the block - what extra data to store / load
 * in the blueprint
 *
 * Default implementations of this will be in the package buildcraftreforged.api.bptblocks (builders phase).
 *
 * <p>Ported from 4.2.2 {@code buildcraft.api.blueprints.BptBlock}, the base of the 18 {@code bptblocks} schematics.
 * Plan section 13 changes three things:
 * <ul>
 * <li>One instance per {@link Block} instead of per block id; see {@link BlueprintManager}.</li>
 * <li>The default {@link #rotateLeft} rotates the slot's block state, so most 4.2.2 metadata subclasses become
 * unnecessary (13.4).</li>
 * <li>4.2.2's {@code getSignature} and {@code match} translated block ids between installations. Blocks are saved by
 * name now, so they are gone; {@link BlockSignature} remains for the 1.6.4 importer (13.5).</li>
 * </ul>
 */
public class BptBlock {

    /** The block this schematic handles (4.2.2: {@code blockId}). */
    public final Block block;

    public BptBlock(Block block) {
        this.block = block;

        BlueprintManager.blockBptProps.put(block, this);
    }

    /**
     * Returns the requirements needed to build this block. When the requirements are met, they will be removed all at once from the builder, before calling
     * buildBlock.
     */
    public void addRequirements(BptSlotInfo slot, IBptContext context, LinkedList<ItemStack> requirements) {
        if (!slot.state.isAir()) {
            if (slot.storedRequirements.size() != 0) {
                requirements.addAll(slot.storedRequirements);
            } else {
                ItemStack stack = new ItemStack(slot.state.getBlock().asItem());

                // 4.2.2 added the block's item unconditionally; a block without an item would ask for nothing forever.
                if (!stack.isEmpty()) {
                    requirements.add(stack);
                }
            }
        }
    }

    /**
     * This is called each time an item matches a reqquirement, that is: (req id == stack id) for damageable items (req id == stack id && req dmg == stack dmg)
     * for other items by default, it will increase damage of damageable items by the amount of damage of the requirement, and remove the intended amount of non
     * damageable item.
     *
     * Client may override this behavior for default items. Note that this subprogram may be called twice with the same parameters, once with a copy of
     * requirements and stack to check if the entire requirements can be fullfilled, and once with the real inventory. Implementer is responsible for updating
     * req (with the remaining requirements if any) and stack (after usage)
     *
     * returns: what was used (similer to req, but created from stack, so that any NBT based differences are drawn from the correct source)
     *
     * <p>4.2.2 then turned an emptied stack into its container item (a bucket) in place. An ItemStack's item cannot change
     * now, so the caller replaces an emptied stack with its crafting remainder.
     */
    public ItemStack useItem(BptSlotInfo slot, IBptContext context, ItemStack req, ItemStack stack) {
        ItemStack result = stack.copy();
        if (stack.isDamageableItem()) {
            if (req.getDamageValue() + stack.getDamageValue() <= stack.getMaxDamage()) {
                stack.setDamageValue(req.getDamageValue() + stack.getDamageValue());
                result.setDamageValue(req.getDamageValue());
                req.setCount(0);
            }

            if (stack.getDamageValue() >= stack.getMaxDamage()) {
                stack.setCount(0);
            }
        } else {
            if (stack.getCount() >= req.getCount()) {
                result.setCount(req.getCount());
                stack.setCount(stack.getCount() - req.getCount());
                req.setCount(0);
            } else {
                req.setCount(req.getCount() - stack.getCount());
                stack.setCount(0);
            }
        }

        return result;
    }

    /**
     * Return true if the block on the world correspond to the block stored in the blueprint at the location given by the slot. By default, this subprogram is
     * permissive and doesn't take into account metadata.
     *
     * Added metadata sensitivity //Krapht
     *
     * <p>Block id and metadata are one block state now, so the whole state must match.
     */
    public boolean isValid(BptSlotInfo slot, IBptContext context) {
        return slot.state == context.world().getBlockState(slot.pos());
    }

    /**
     * Perform a 90 degree rotation to the slot.
     *
     * <p>4.2.2 did nothing here and left rotation to metadata subclasses. The default now rotates the block state
     * (plan section 13.4). "Left" is clockwise seen from above, the direction 4.2.2's {@code BptBlockRotateMeta} turned
     * ladders, furnaces and chests (north, east, south, west).
     */
    public void rotateLeft(BptSlotInfo slot, IBptContext context) {
        slot.state = slot.state.rotate(context.world(), slot.pos(), Rotation.CLOCKWISE_90);
    }

    /**
     * Mirrors the slot. New: 4.2.2 could not mirror a blueprint. The default mirrors the block state (#75).
     */
    public void mirror(BptSlotInfo slot, IBptContext context, Mirror mirror) {
        slot.state = slot.state.mirror(mirror);
    }

    /**
     * Places the block in the world, at the location specified in the slot.
     */
    public void buildBlock(BptSlotInfo slot, IBptContext context) {
        Level world = context.world();
        BlockPos pos = slot.pos();

        world.setBlock(pos, slot.state, 3);

        if (slot.state.hasBlockEntity()) {
            BlockEntity tile = world.getBlockEntity(pos);

            slot.cpt.putInt("x", slot.x);
            slot.cpt.putInt("y", slot.y);
            slot.cpt.putInt("z", slot.z);

            if (tile != null) {
                tile.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), slot.cpt));
                // New (#76): 4.2.2 tiles synced themselves; a loaded block entity is saved and sent to clients here.
                tile.setChanged();
                world.sendBlockUpdated(pos, slot.state, slot.state, 3);
            }
        }
    }

    /**
     * Return true if the block should not be placed to the world. Requirements will not be asked on such a block, and building will not be called.
     */
    public boolean ignoreBuilding(BptSlotInfo slot) {
        return false;
    }

    /**
     * Initializes a slot from the blueprint according to an objet placed on {x, y, z} on the world. This typically means adding entries in slot.cpt. Note that
     * the block state will be set automatically.
     *
     * By default, if the block has a block entity, its saved data is stored to save / load the block.
     */
    public void initializeFromWorld(BptSlotInfo slot, IBptContext context, int x, int y, int z) {
        Level world = context.world();
        BlockPos pos = new BlockPos(x, y, z);
        BlockEntity tile = null;

        if (slot.state.hasBlockEntity()) {
            tile = world.getBlockEntity(pos);

            if (tile != null) {
                slot.cpt.merge(tile.saveWithFullMetadata(world.registryAccess()));
            }
        }

        if (!slot.state.isAir() && world instanceof ServerLevel serverLevel) {
            slot.storedRequirements.addAll(Block.getDrops(slot.state, serverLevel, pos, tile));
        }
    }

    /**
     * Called on a block when the blueprint has finished to place all the blocks. This may be useful to adjust variable depending on surrounding blocks that may
     * not be there already at initial building.
     */
    public void postProcessing(BptSlotInfo slot, IBptContext context) {

    }
}
