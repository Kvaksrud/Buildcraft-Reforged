/**
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: ValueInput/ValueOutput and codecs in place of NBT, ItemStack.EMPTY in place of
 * null; the numeric itemID legacy branch is gone.
 */

package buildcraftreforged.api.gates;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class TriggerParameter implements ITriggerParameter {

    /** The parameter as its stack, for data components and gate saves. The stack's 1.6.4 NBT is its components. */
    public static final Codec<TriggerParameter> CODEC =
            ItemStack.OPTIONAL_CODEC.xmap(TriggerParameter::of, TriggerParameter::getItemStack);
    public static final StreamCodec<RegistryFriendlyByteBuf, TriggerParameter> STREAM_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.map(TriggerParameter::of, TriggerParameter::getItemStack);

    protected ItemStack stack = ItemStack.EMPTY;

    public static TriggerParameter of(ItemStack stack) {
        TriggerParameter parameter = new TriggerParameter();
        parameter.set(stack);
        return parameter;
    }

    @Override
    public ItemStack getItemStack() {
        return stack;
    }

    @Override
    public void set(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            this.stack = stack.copyWithCount(1);
        }
    }

    @Override
    public void writeToNBT(ValueOutput compound) {
        if (!stack.isEmpty()) {
            compound.store("stack", ItemStack.CODEC, stack);
        }
    }

    @Override
    public void readFromNBT(ValueInput compound) {
        stack = compound.read("stack", ItemStack.CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    @Deprecated
    public ItemStack getItem() {
        return stack;
    }

}
