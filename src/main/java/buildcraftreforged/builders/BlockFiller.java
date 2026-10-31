/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: BlockContainer becomes BlockBuildCraft; Material.iron, the hardness and the
 * creative tab move to registration; getBlockTexture becomes the ACTIVE and PATTERN state properties (kept by
 * TileFiller) and the JSON model; the unused currentPattern field is dropped; openGui becomes openMenu with the tile
 * as the MenuProvider; breakBlock's Utils.preDestroyBlock runs from TileBuildCraft.preRemoveSideEffects; the red light
 * shows a filler waiting for blocks (plan section 10).
 */

package buildcraftreforged.builders;

import buildcraftreforged.api.filler.IFillerPattern;
import buildcraftreforged.builders.filler.pattern.FillerPattern;
import buildcraftreforged.core.BlockBuildCraft;
import buildcraftreforged.core.status.StatusPanel;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class BlockFiller extends BlockBuildCraft {

    /** 4.2.2 textureTopOn / textureTopOff on the top and bottom: TileFiller.isActive(). */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** 4.2.2 drew currentPattern.getIcon() on the four sides. */
    public static final EnumProperty<PatternFace> PATTERN = EnumProperty.create("pattern", PatternFace.class);

    /**
     * The side texture: one per 4.2.2 pattern, and 4.2.2's textureSides for a pattern added through the API, whose
     * icon the block model cannot know.
     */
    public enum PatternFace implements StringRepresentable {
        SIDES, BOX, CLEAR, FILL, FLATTEN, HORIZON, PYRAMID, STAIRS;

        private final String name = name().toLowerCase(Locale.ROOT);

        public static PatternFace of(IFillerPattern pattern) {
            if (pattern instanceof FillerPattern fillerPattern) {
                for (PatternFace face : values()) {
                    if (face.name.equals(fillerPattern.getTag()))
                        return face;
                }
            }
            return SIDES;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public BlockFiller(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, true).setValue(PATTERN, PatternFace.FILL)
                .setValue(StatusPanel.BLOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, PATTERN, StatusPanel.BLOCKED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player entityplayer,
            BlockHitResult hitResult) {

        // Drop through if the player is sneaking
        if (entityplayer.isShiftKeyDown())
            return InteractionResult.PASS;

        if (!world.isClientSide() && world.getBlockEntity(pos) instanceof TileFiller filler) {
            entityplayer.openMenu(filler, pos);
        }
        return InteractionResult.SUCCESS;

    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileFiller(pos, state);
    }
}
