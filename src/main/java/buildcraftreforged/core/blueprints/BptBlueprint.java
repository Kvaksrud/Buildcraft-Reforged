/**
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: slots hold block states; the idMap, contents and requirements sections become
 * a block state palette, an index array, a slot data list and a requirements list in NBT (plan section 13.4); id
 * mapping removed (13.3); TileEntity becomes BlockEntity.
 */

package buildcraftreforged.core.blueprints;

import buildcraftreforged.api.blueprints.IBptContext;
import buildcraftreforged.core.IBptContributor;
import buildcraftreforged.core.utils.BCLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A blueprint: every block state of a box, with block entity data and the items each block cost. Ported from 4.2.2
 * {@code buildcraft.core.blueprints.BptBlueprint}.
 *
 * <p>4.2.2 saved an {@code idMap} (block id to a name and class signature), one NBT line per slot holding {@code bId},
 * {@code meta} and the tile's data, and one line of requirements per slot. Block states are saved by name, so the
 * id map and its remapping ({@code mapWorldId}, {@code mapItemStack}, {@code storeId}) are gone (plan section 13.3);
 * a block that this game does not have makes loading fail, as 4.2.2's "can't find block of signature".
 */
public class BptBlueprint extends BptBase {

    public BptBlueprint() {
    }

    public BptBlueprint(int sizeX, int sizeY, int sizeZ) {
        super(sizeX, sizeY, sizeZ);
    }

    /**
     * Captures the block at world position (x, y, z) into the slot at its place in the context's box.
     * {@code anchorTile} is the architect, handed to an {@link IBptContributor}; the world is the context's (4.2.2
     * read the anchor tile's world, the same one).
     */
    public void readFromWorld(IBptContext context, @Nullable BlockEntity anchorTile, int x, int y, int z) {
        BptSlot slot = new BptSlot();
        Level world = context.world();
        BlockPos pos = new BlockPos(x, y, z);

        slot.x = (int) (x - context.surroundingBox().pMin().x);
        slot.y = (int) (y - context.surroundingBox().pMin().y);
        slot.z = (int) (z - context.surroundingBox().pMin().z);
        slot.state = world.getBlockState(pos);

        if (slot.state.hasBlockEntity()) {
            BlockEntity tile = world.getBlockEntity(pos);

            if (tile != null && tile instanceof IBptContributor) {
                IBptContributor contributor = (IBptContributor) tile;

                contributor.saveToBluePrint(anchorTile, this, slot);
            }
        }

        try {
            slot.initializeFromWorld(context, x, y, z);
            contents[slot.x][slot.y][slot.z] = slot;
        } catch (Throwable t) {
            // Defensive code against errors in implementers
            BCLog.logger.error("BptBlueprint.readFromWorld", t);

        }
    }

    /**
     * Writes {@code palette} (the distinct block states), {@code blocks} (one palette index per position, -1 for no
     * slot), {@code data} (slots with block entity or schematic data) and {@code requirements}.
     */
    @Override
    public void saveAttributes(CompoundTag nbt, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        Map<BlockState, Integer> paletteIds = new HashMap<BlockState, Integer>();
        ListTag palette = new ListTag();
        int[] blocks = new int[sizeX * sizeY * sizeZ];
        ListTag data = new ListTag();
        ListTag requirements = new ListTag();

        for (int x = 0; x < sizeX; ++x) {
            for (int y = 0; y < sizeY; ++y) {
                for (int z = 0; z < sizeZ; ++z) {
                    BptSlot slot = contents[x][y][z];

                    if (slot == null) {
                        blocks[index(x, y, z)] = -1;
                        continue;
                    }

                    Integer id = paletteIds.get(slot.state);

                    if (id == null) {
                        id = palette.size();
                        paletteIds.put(slot.state, id);
                        palette.add(NbtUtils.writeBlockState(slot.state));
                    }

                    blocks[index(x, y, z)] = id;

                    if (!slot.cpt.isEmpty()) {
                        CompoundTag entry = position(x, y, z);
                        entry.put("cpt", slot.cpt.copy());
                        data.add(entry);
                    }

                    if (slot.storedRequirements.size() > 0) {
                        ListTag list = new ListTag();

                        for (ItemStack stack : slot.storedRequirements) {
                            if (!stack.isEmpty()) {
                                list.add(ItemStack.CODEC.encodeStart(ops, stack).getOrThrow());
                            }
                        }

                        CompoundTag entry = position(x, y, z);
                        entry.put("items", list);
                        requirements.add(entry);
                    }
                }
            }
        }

        nbt.put("palette", palette);
        nbt.putIntArray("blocks", blocks);
        nbt.put("data", data);
        nbt.put("requirements", requirements);
    }

    @Override
    public void loadAttributes(CompoundTag nbt, HolderLookup.Provider registries) throws BptError {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        HolderGetter<Block> blockGetter = registries.lookupOrThrow(Registries.BLOCK);
        List<BlockState> palette = new ArrayList<BlockState>();

        for (Tag tag : nbt.getListOrEmpty("palette")) {
            CompoundTag stateTag = fix(References.BLOCK_STATE, tag).asCompound().orElse(new CompoundTag());
            String blockName = stateTag.getStringOr("id", "");
            Identifier blockId = Identifier.tryParse(blockName);

            if (blockId == null || blockGetter.get(ResourceKey.create(Registries.BLOCK, blockId)).isEmpty())
                throw new BptError("BLUEPRINT ERROR: can't find block " + blockName + " for " + getName());

            palette.add(NbtUtils.readBlockState(blockGetter, stateTag));
        }

        int[] blocks = nbt.getIntArray("blocks").orElse(new int[0]);

        if (blocks.length != sizeX * sizeY * sizeZ)
            throw new BptError("Blueprint " + getName() + " has " + blocks.length + " blocks for size " + sizeX + " x " + sizeY + " x " + sizeZ);

        for (int x = 0; x < sizeX; ++x) {
            for (int y = 0; y < sizeY; ++y) {
                for (int z = 0; z < sizeZ; ++z) {
                    int id = blocks[index(x, y, z)];

                    if (id == -1) {
                        continue;
                    }

                    if (id < 0 || id >= palette.size())
                        throw new BptError("Blueprint " + getName() + " has palette index " + id + " out of range");

                    setBlockState(x, y, z, palette.get(id));
                }
            }
        }

        for (Tag tag : nbt.getListOrEmpty("data")) {
            CompoundTag entry = tag.asCompound().orElse(new CompoundTag());
            BptSlot slot = slotAt(entry);
            CompoundTag cpt = entry.getCompoundOrEmpty("cpt");

            // Data saved from a block entity carries its type id; only that can go through the block entity fixers.
            if (cpt.contains("id")) {
                cpt = fix(References.BLOCK_ENTITY, cpt).asCompound().orElse(cpt);
            }

            slot.cpt = cpt;
        }

        for (Tag tag : nbt.getListOrEmpty("requirements")) {
            CompoundTag entry = tag.asCompound().orElse(new CompoundTag());
            BptSlot slot = slotAt(entry);

            for (Tag item : entry.getListOrEmpty("items")) {
                Optional<ItemStack> stack = ItemStack.CODEC.parse(ops, fix(References.ITEM_STACK, item)).result();

                if (stack.isEmpty())
                    throw new BptError("BLUEPRINT ERROR: can't read item " + item + " for " + getName());

                slot.storedRequirements.add(stack.get());
            }
        }
    }

    private static CompoundTag position(int x, int y, int z) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("x", x);
        entry.putInt("y", y);
        entry.putInt("z", z);
        return entry;
    }

    private BptSlot slotAt(CompoundTag entry) throws BptError {
        int x = entry.getIntOr("x", -1);
        int y = entry.getIntOr("y", -1);
        int z = entry.getIntOr("z", -1);

        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ || contents[x][y][z] == null)
            throw new BptError("Blueprint " + getName() + " has data for an empty slot " + x + ", " + y + ", " + z);

        return contents[x][y][z];
    }

    @Override
    public String getName() {
        if (name == null)
            return "Blueprint #" + position;
        else
            return name;
    }
}
