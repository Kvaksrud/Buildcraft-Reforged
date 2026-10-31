/**
 * Copyright (c) SpaceToad, 2011 http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public License
 * 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: fill, empty and flatten take the filler's block entity where they took its
 * World, so they break and place as the owner's fake player (#27); a block the owner may not break, or a spot where
 * the placement does nothing (refused by a claim, or an item that places no block), is skipped for the rest of the
 * pass instead of being tried forever; the item is used on the target's top face, so it lands on the target even
 * when the block below is air or replaceable; fillerDestroy asks the owner too; the icon is a GUI sprite id and the
 * display name a Component; the world's bottom and top are the level's minimum and maximum Y.
 */
package buildcraftreforged.builders.filler.pattern;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.api.core.IBox;
import buildcraftreforged.api.filler.IFillerPattern;
import buildcraftreforged.api.filler.IPatternIterator;
import buildcraftreforged.builders.TileFiller;
import buildcraftreforged.core.BuildCraftConfiguration;
import buildcraftreforged.core.DefaultProps;
import buildcraftreforged.core.TileBuildCraft;
import buildcraftreforged.core.utils.BlockUtil;
import buildcraftreforged.core.utils.BuildCraftPlayer;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

public abstract class FillerPattern implements IFillerPattern {

    public static final Set<FillerPattern> patterns = new HashSet<FillerPattern>();
    private final String tag;
    private final Identifier icon;

    public FillerPattern(String tag) {
        this.tag = tag;
        // 4.2.2 registered "buildcraft:fillerPatterns/<tag>" in the block atlas; the GUI and gate draw a GUI sprite.
        this.icon = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "filler/" + tag);
        patterns.add(this);
    }

    /**
     * stackToPlace contains the next item that can be place in the world. Null
     * if there is none. IteratePattern is responsible to decrementing the stack
     * size if needed. Return true when the iteration process is finished.
     */
    public boolean iteratePattern(BlockEntity tile, IBox box, @Nullable ItemStack stackToPlace) {
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("fillerpattern." + tag);
    }

    @Override
    public String getUniqueTag() {
        return "buildcraft:" + tag;
    }

    /** The 4.2.2 tag ("fill", "box", ...), which also names the block's side texture. */
    public String getTag() {
        return tag;
    }

    @Override
    public Identifier getIcon() {
        return icon;
    }

    @Override
    public String toString() {
        return "Pattern: " + getUniqueTag();
    }

    @Override
    public IPatternIterator createPatternIterator(BlockEntity tile, IBox box, Direction orientation) {
        return new PatternIterator(tile, box);
    }

    protected class PatternIterator implements IPatternIterator {

        private final IBox box;
        private final BlockEntity tile;

        public PatternIterator(BlockEntity tile, IBox box) {
            this.box = box;
            this.tile = tile;
        }

        @Override
        public boolean iteratePattern(@Nullable ItemStack stackToPlace) {
            return FillerPattern.this.iteratePattern(tile, box, stackToPlace);
        }
    }

    /**
     * Attempt to fill blocks in the area.
     *
     * Return false if the process failed.
     *
     */
    public static boolean fill(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, @Nullable ItemStack stackToPlace,
            BlockEntity tile) {
        Level world = tile.getLevel();
        boolean found = false;
        int lastX = 0, lastY = 0, lastZ = 0;

        for (int y = yMin; y <= yMax && !found; ++y) {
            for (int x = xMin; x <= xMax && !found; ++x) {
                for (int z = zMin; z <= zMax && !found; ++z) {
                    if (!BlockUtil.canChangeBlock(world, x, y, z))
                        return false;
                    if (BlockUtil.isSoftBlock(world, x, y, z) && !isSkipped(tile, x, y, z)) {
                        lastX = x;
                        lastY = y;
                        lastZ = z;

                        found = true;
                    }
                }
            }
        }

        if (found && stackToPlace != null) {
            place(tile, lastX, lastY, lastZ, stackToPlace);
        } else if (found) {
            missingMaterial(tile);
        }

        return found;
    }

    /**
     * Attempt to remove the blocks in the area.
     *
     * Return false if is the process failed.
     *
     */
    public static boolean empty(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockEntity tile) {
        Level world = tile.getLevel();
        int lastX = Integer.MAX_VALUE, lastY = Integer.MAX_VALUE, lastZ = Integer.MAX_VALUE;

        for (int y = yMax; y >= yMin; y--) {
            boolean found = false;
            for (int x = xMin; x <= xMax; ++x) {
                for (int z = zMin; z <= zMax; ++z) {
                    if (!BlockUtil.canChangeBlock(world, x, y, z))
                        return false;
                    if (!BlockUtil.isSoftBlock(world, x, y, z) && !isSkipped(tile, x, y, z)) {
                        found = true;
                        lastX = x;
                        lastY = y;
                        lastZ = z;
                    }
                }
            }

            if (found) {
                break;
            }
        }

        if (lastX != Integer.MAX_VALUE) {
            if (!breakBlock(tile, lastX, lastY, lastZ))
                skip(tile, lastX, lastY, lastZ);
            return true;
        }

        return false;
    }

    /**
     * Attempt to fill the area defined by the box, from the top down.
     *
     * This differs from Fill in how it handles blockage and the order of
     * iteration.
     *
     * Return false if is the process failed.
     */
    public static boolean flatten(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockEntity tile,
            @Nullable ItemStack stackToPlace) {
        Level world = tile.getLevel();
        int lastX = Integer.MAX_VALUE, lastY = Integer.MAX_VALUE, lastZ = Integer.MAX_VALUE;

        boolean found = false;
        for (int x = xMin; x <= xMax && !found; ++x) {
            for (int z = zMin; z <= zMax && !found; ++z) {
                for (int y = yMax; y >= yMin; --y) {
                    if (!BlockUtil.canChangeBlock(world, x, y, z) || !BlockUtil.isSoftBlock(world, x, y, z)
                            || isSkipped(tile, x, y, z)) {
                        break;
                    } else {
                        found = true;
                        lastX = x;
                        lastY = y;
                        lastZ = z;
                    }
                }
            }
        }

        if (found && stackToPlace != null) {
            place(tile, lastX, lastY, lastZ, stackToPlace);
        } else if (found) {
            missingMaterial(tile);
        }
        return found;
    }

    /**
     * 4.2.2's {@code breakBlock(world, x, y, z)} followed by {@code onItemUse(stackToPlace, buildCraftPlayer, world,
     * x, y - 1, z, 1, ...)}, as the owner. The spot is skipped when the owner may not clear it, or when using the item
     * left it soft for any reason but an entity in the way (4.2.2 waited for the entity to move, and still does).
     */
    private static void place(BlockEntity tile, int x, int y, int z, ItemStack stackToPlace) {
        Player owner = owner(tile);
        if (owner == null)
            return;
        if (!breakBlock(tile, x, y, z)) {
            skip(tile, x, y, z);
            return;
        }
        BlockPos pos = new BlockPos(x, y, z);
        // 4.2.2 side 1 on the block below; the top face of the target itself, so a replaceable target takes the block.
        BuildCraftPlayer.useItemOn(owner, stackToPlace, pos, Direction.UP);

        Level world = tile.getLevel();
        if (BlockUtil.isSoftBlock(world, x, y, z) && !obstructedByEntity(world, pos, stackToPlace))
            skip(tile, x, y, z);
    }

    private static boolean obstructedByEntity(Level world, BlockPos pos, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item))
            return false;
        BlockState state = item.getBlock().defaultBlockState();
        return !world.isUnobstructed(state, pos, CollisionContext.empty());
    }

    /**
     * Clears a block before placing, or for clear and flatten: dropped with the configured lifespan, or deleted with
     * fillerDestroy. False when the owner may not break it (a claim); the block is left alone.
     */
    private static boolean breakBlock(BlockEntity tile, int x, int y, int z) {
        Level world = tile.getLevel();
        BlockPos pos = new BlockPos(x, y, z);
        Player owner = owner(tile);
        if (owner == null || !BuildCraftPlayer.canBreak(owner, world, pos))
            return false;

        BlockState state = world.getBlockState(pos);
        if (!state.isAir()) {
            SoundType sound = state.getSoundType(world, pos, owner);
            world.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F,
                    sound.getPitch() * 0.8F);
        }
        if (fillerDestroy()) {
            world.removeBlock(pos, false);
            return true;
        } else if (BlockUtil.isToughBlock(world, x, y, z)) {
            return BlockUtil.breakBlock(world, x, y, z, lifespanTough(), owner);
        } else {
            return BlockUtil.breakBlock(world, x, y, z, lifespanNormal(), owner);
        }
    }

    /** The fake player of the filler's owner; the default BuildCraft player for a tile that has no owner. */
    private static @Nullable Player owner(BlockEntity tile) {
        if (tile instanceof TileBuildCraft buildcraftTile)
            return BuildCraftPlayer.get(buildcraftTile);
        if (tile.getLevel() instanceof ServerLevel serverLevel)
            return BuildCraftPlayer.get(serverLevel, null);
        return null;
    }

    private static boolean isSkipped(BlockEntity tile, int x, int y, int z) {
        return tile instanceof TileFiller filler && filler.isSkipped(new BlockPos(x, y, z));
    }

    private static void skip(BlockEntity tile, int x, int y, int z) {
        if (tile instanceof TileFiller filler)
            filler.skip(new BlockPos(x, y, z));
    }

    private static void missingMaterial(BlockEntity tile) {
        if (tile instanceof TileFiller filler)
            filler.setMissingMaterial();
    }

    private static boolean fillerDestroy() {
        return BuildCraftConfiguration.COMMON_SPEC.isLoaded() ? BuildCraftConfiguration.fillerDestroy.get()
                : DefaultProps.FILLER_DESTROY;
    }

    private static int lifespanTough() {
        return BuildCraftConfiguration.COMMON_SPEC.isLoaded() ? BuildCraftConfiguration.fillerLifespanTough.get()
                : DefaultProps.FILLER_LIFESPAN_TOUGH;
    }

    private static int lifespanNormal() {
        return BuildCraftConfiguration.COMMON_SPEC.isLoaded() ? BuildCraftConfiguration.fillerLifespanNormal.get()
                : DefaultProps.FILLER_LIFESPAN_NORMAL;
    }
}
