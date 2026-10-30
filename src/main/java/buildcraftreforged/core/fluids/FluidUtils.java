/*
 * Ported from buildcraft/core/fluids/FluidUtils.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: FluidContainerRegistry becomes the item fluid capability through ItemAccess,
 * which also handles the creative-mode case; IFluidHandler becomes ResourceHandler<FluidResource> and the right-click
 * takes the hand; block ids become the fluid's LiquidBlock state; added getFluid/getFluidName for the tank tag names.
 */
package buildcraftreforged.core.fluids;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.core.TileBuffer;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class FluidUtils {

    public static boolean handleRightClick(ResourceHandler<FluidResource> tank, @Nullable Player player, InteractionHand hand, boolean fill, boolean drain) {
        if (player == null) {
            return false;
        }
        ItemAccess current = ItemAccess.forPlayerInteraction(player, hand).oneByOne();
        ResourceHandler<FluidResource> container = current.getCapability(Capabilities.Fluid.ITEM);
        if (container != null) {

            if (fill && !ResourceHandlerUtil.isEmpty(container)) {
                if (ResourceHandlerUtil.moveFirst(container, tank, resource -> true, Integer.MAX_VALUE, null) != null) {
                    return true;
                }

            } else if (drain) {

                if (ResourceHandlerUtil.moveFirst(tank, container, resource -> true, Integer.MAX_VALUE, null) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 4.2.2 getFluidBlockId. 1.6.4 had a still and a moving block id per fluid; now each fluid has one LiquidBlock,
     * and placing the source state schedules the flow, so both give the source state.
     */
    public static BlockState getFluidBlock(Fluid fluid, boolean moving) {
        Fluid source = fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
        return source.defaultFluidState().createLegacyBlock();
    }

    public static void pushFluidToConsumers(Tank tank, int flowCap, TileBuffer[] tileBuffer) {
        int amountToPush = flowCap;
        for (Direction side : Direction.values()) {
            if (tank.drain(amountToPush, false).getAmount() > 0) {
                BlockEntity tile = tileBuffer[side.ordinal()].getTile();
                ResourceHandler<FluidResource> handler = tile == null || tile.getLevel() == null ? null
                        : tile.getLevel().getCapability(Capabilities.Fluid.BLOCK, tile.getBlockPos(), tile.getBlockState(), tile, side.getOpposite());
                if (handler != null) {
                    int used = ResourceHandlerUtil.move(tank, handler, resource -> true, amountToPush, null);
                    if (used > 0) {
                        amountToPush -= used;
                        if (amountToPush <= 0)
                            return;
                    }
                }
            }
        }
    }

    /**
     * The fluid for a name saved by a tank: a registry id, or a 1.6.4 Forge name without a namespace ("water",
     * "oil"), which is looked up in minecraft and then in buildcraftreforged. Null for none, as FluidRegistry.getFluid.
     */
    public static @Nullable Fluid getFluid(String name) {
        if (name.isEmpty())
            return null;
        Identifier id = Identifier.tryParse(name);
        if (id == null)
            return null;
        Fluid fluid = BuiltInRegistries.FLUID.getValue(id);
        if (fluid == Fluids.EMPTY && name.indexOf(':') < 0)
            fluid = BuiltInRegistries.FLUID.getValue(Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, name));
        return fluid == Fluids.EMPTY ? null : fluid;
    }

    public static String getFluidName(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid).toString();
    }
}
