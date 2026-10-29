package buildcraftreforged.platform.jei;

import buildcraftreforged.BuildcraftReforged;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import net.minecraft.resources.Identifier;

/**
 * JEI plugin, replacing 4.2.2's external NEI plugins (PLAN.md section 6). JEI finds it through {@link JeiPlugin};
 * nothing else refers to this class, so it is never loaded when JEI is absent. Recipe categories for the assembly
 * table, refinery, gates and modules come with those features.
 */
@JeiPlugin
public class BuildCraftJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "jei");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }
}
