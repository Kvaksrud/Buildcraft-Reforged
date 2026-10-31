/**
 * Copyright (c) SpaceToad, 2011-2012
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: the library number and name move from the damage and the "BptName" tag to
 * the BptItemData component, which also carries the author; maxStackSize 1 and the creative tab move to registration;
 * getIconFromDamage becomes the item model's has_component condition (clean or used); addInformation becomes
 * appendHoverText; onItemRightClick becomes use and refreshes the component in place; the empty onUpdate is dropped.
 */

package buildcraftreforged.builders;

import buildcraftreforged.core.ItemBuildCraft;
import buildcraftreforged.core.blueprints.BptBase;
import buildcraftreforged.core.blueprints.BptRootIndex;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public abstract class ItemBptBase extends ItemBuildCraft {

    public ItemBptBase(Properties properties) {
        super(properties);
    }

    /** The library number this stack holds, 4.2.2's getItemDamage(); 0 for a clean item. */
    public static int getBptId(ItemStack stack) {
        BptItemData data = getBptData(stack);
        return data == null ? 0 : data.id();
    }

    public static @Nullable BptItemData getBptData(ItemStack stack) {
        return stack.get(BuildersRegistry.bptData.get());
    }

    @Override
    public void appendHoverText(ItemStack itemstack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> list, TooltipFlag advanced) {
        BptItemData data = getBptData(itemstack);
        if (data != null && !data.name().isEmpty()) {
            list.accept(Component.literal(data.name()));
        }
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        int id = getBptId(itemStack);
        if (world instanceof ServerLevel serverLevel && id > 0) {
            BptRootIndex index = BuildCraftBuilders.getBptRootIndex(serverLevel.getServer());
            BptBase bpt = index == null ? null : index.getBluePrint(id);
            if (bpt != null) {
                BptItemData data = BuildCraftBuilders.getBptItemStack(this, id, bpt.getName(), bpt.author)
                        .get(BuildersRegistry.bptData.get());
                itemStack.set(BuildersRegistry.bptData.get(), data);
            }
        }
        return InteractionResult.PASS;
    }
}
