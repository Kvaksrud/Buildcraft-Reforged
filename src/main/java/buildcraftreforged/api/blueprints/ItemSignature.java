/** 
 * Copyright (c) SpaceToad, 2011
 * http://www.mod-buildcraft.com
 * 
 * BuildCraft is distributed under the terms of the Minecraft Mod Public 
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 *
 * Modified by Buildcraft Reforged, 2026: package only; kept for the 1.6.4 blueprint importer.
 */

package buildcraftreforged.api.blueprints;

/**
 * The signature of a item in a 1.6.4 blueprint's {@code idMap}. Ported unchanged from 4.2.2
 * {@code buildcraft.api.blueprints.ItemSignature}.
 *
 * <p>The new blueprint format saves blocks and items by name and needs no signatures (plan section 13.3). This class
 * stays for the 1.6.4 importer (phase P9, plan section 13.5), which reads the old {@code idMap}.
 */
public class ItemSignature {

    public String itemClassName;
    public String itemName;

    public ItemSignature(String str) {
        String[] values = str.split("/");

        itemClassName = values[1];
        itemName = values[2];

        replaceNullWithStar();

    }

    public ItemSignature() {
        replaceNullWithStar();
    }

    @Override
    public String toString() {
        replaceNullWithStar();

        return "#I/" + itemClassName + "/" + itemName;
    }

    public void replaceNullWithStar() {
        if (itemClassName == null) {
            itemClassName = "*";
        }

        if (itemName == null) {
            itemName = "*";
        }
    }
}
