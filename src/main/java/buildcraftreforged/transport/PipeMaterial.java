package buildcraftreforged.transport;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * The ten pipe materials of the tier ladder (docs/TRANSPORT.md 2.1, PLAN 8.1). A gold pipe is tier 5 whatever it
 * carries. {@link #getSerializedName()} is the material id of the config keys ({@code transport.tiers.<material>});
 * {@link #unlocalizedName} is the part of the 4.2.2-style pipe name ({@code PipeItemsGold}).
 */
public enum PipeMaterial implements StringRepresentable {

    WOOD(1, "Wood"),
    STONE(2, "Stone"),
    IRON(3, "Iron"),
    COPPER(4, "Copper"),
    GOLD(5, "Gold"),
    QUARTZ(6, "Quartz"),
    DIAMOND(7, "Diamond"),
    EMERALD(8, "Emerald"),
    NETHERITE(9, "Netherite"),
    ENDER(10, "Ender");

    public static final Codec<PipeMaterial> CODEC = StringRepresentable.fromEnum(PipeMaterial::values);

    public final int tier;
    public final String unlocalizedName;

    PipeMaterial(int tier, String unlocalizedName) {
        this.tier = tier;
        this.unlocalizedName = unlocalizedName;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ENGLISH);
    }
}
