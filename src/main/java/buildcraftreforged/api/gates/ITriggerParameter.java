/*
 * Modified by Buildcraft Reforged, 2026: ported from buildcraft/api/gates/ITriggerParameter.java (no file header at
 * 4.2.2); NBTTagCompound becomes ValueInput/ValueOutput, and no stack is ItemStack.EMPTY instead of null.
 */

package buildcraftreforged.api.gates;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public interface ITriggerParameter {

    /** The parameter stack, {@link ItemStack#EMPTY} when none is set. */
    public abstract ItemStack getItemStack();

    public abstract void set(ItemStack stack);

    public abstract void writeToNBT(ValueOutput compound);

    public abstract void readFromNBT(ValueInput compound);

    @Deprecated
    public abstract ItemStack getItem();

}
