/*
 * Ported from buildcraft/transport/ItemGate.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: the two items with seven damage values each (pipeGate, pipeGateAutarchic)
 * become fourteen items, one per kind and series; icons become item models; fitting the gate (4.2.2
 * BlockGenericPipe.addOrStripGate) is the item's use on a pipe.
 */
package buildcraftreforged.transport;

import buildcraftreforged.core.ItemBuildCraft;
import buildcraftreforged.transport.Gate.GateKind;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

public class ItemGate extends ItemBuildCraft {

    public static final int Gate								=  0;
    public static final int Gate_Iron_And						=  1;
    public static final int Gate_Iron_Or						=  2;
    public static final int Gate_Gold_And						=  3;
    public static final int Gate_Gold_Or						=  4;
    public static final int Gate_Diamond_And					=  5;
    public static final int Gate_Diamond_Or						=  6;

    public static final int Autarchic_Gate						=  7;
    public static final int Autarchic_Gate_Iron_And				=  8;
    public static final int Autarchic_Gate_Iron_Or				=  9;
    public static final int Autarchic_Gate_Gold_And				= 10;
    public static final int Autarchic_Gate_Gold_Or				= 11;
    public static final int Autarchic_Gate_Diamond_And			= 12;
    public static final int Autarchic_Gate_Diamond_Or			= 13;

    public static final int MAX									= 14;

    public final GateKind kind;
    /** 4.2.2 {@code series}: 0 normal, 1 autarchic. */
    public final boolean autarchic;

    public ItemGate(GateKind kind, boolean autarchic, Properties properties) {
        super(properties);
        this.kind = kind;
        this.autarchic = autarchic;
    }

    /** 4.2.2 item damage of a kind: Single 0 to OR_4 6. */
    public static int damage(GateKind kind) {
        return kind.ordinal() - 1;
    }

    /** The 4.2.2 icon name after "gate": "", "_iron_and" ... "_diamond_or". */
    public static String suffix(GateKind kind) {
        switch (kind) {
            case AND_2:
                return "_iron_and";
            case OR_2:
                return "_iron_or";
            case AND_3:
                return "_gold_and";
            case OR_3:
                return "_gold_or";
            case AND_4:
                return "_diamond_and";
            case OR_4:
                return "_diamond_or";
            default:
                return "";
        }
    }

    /** Registry name: the 4.2.2 icon name, {@code gate_iron_and} or {@code autarchic_gate_iron_and}. */
    public static String registryName(GateKind kind, boolean autarchic) {
        return (autarchic ? "autarchic_gate" : "gate") + suffix(kind);
    }

    /** 4.2.2 {@code getUnlocalizedName}: {@code item.pipeGate.<damage>} or {@code item.pipeGateAutarchic.<damage>}. */
    public static String translationKey(GateKind kind, boolean autarchic) {
        return (autarchic ? "item.pipeGateAutarchic." : "item.pipeGate.") + damage(kind);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TilePipe pipe
                && pipe.gateHolder.addOrStripGate(context.getPlayer(), context.getItemInHand(),
                        context.getClickLocation()))
            return InteractionResult.SUCCESS;
        return super.useOn(context);
    }
}
