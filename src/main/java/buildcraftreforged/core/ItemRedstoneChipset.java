/*
 * Ported from buildcraft/core/ItemRedstoneChipset.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: the item with five damage values becomes five items, one per chipset, named
 * after the 4.2.2 icon and custom item stack names; icons become item models; getSubItems becomes the creative tab
 * entries (silicon.SiliconRegistry); registerItemStacks (GameRegistry custom item stacks) is dropped, since each
 * chipset now has a registry name of its own.
 */
package buildcraftreforged.core;

public class ItemRedstoneChipset extends ItemBuildCraft {

    /** The 4.2.2 icon names, by damage value. */
    public static final String[] chipsetNames = {"redstone_red", "redstone_iron", "redstone_gold", "redstone_diamond", "redstone_pulsating"};

    /** 4.2.2 item damage: 0 Redstone, 1 Iron, 2 Golden, 3 Diamond, 4 Pulsating. */
    public final int chipset;

    public ItemRedstoneChipset(int chipset, Properties properties) {
        super(properties);
        this.chipset = chipset;
    }

    /** Registry name: the 4.2.2 icon and custom item stack name, {@code redstone_red_chipset} ... */
    public static String registryName(int chipset) {
        return chipsetNames[chipset] + "_chipset";
    }

    /** 4.2.2 {@code getUnlocalizedName}: {@code item.redstoneChipset.<damage>}. */
    public static String translationKey(int chipset) {
        return "item.redstoneChipset." + chipset;
    }
}
