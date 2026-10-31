/*
 * Ported from buildcraft/builders/ItemBptTemplate.java (BuildCraft 4.2.2, MMPL; the original has no header).
 * Modified by Buildcraft Reforged, 2026: getIconFromDamage and registerIcons become the item model, which shows
 * template_clean without a BptItemData component and template_used with one.
 */
package buildcraftreforged.builders;

public class ItemBptTemplate extends ItemBptBase {
    public ItemBptTemplate(Properties properties) {
        super(properties);
    }
}
