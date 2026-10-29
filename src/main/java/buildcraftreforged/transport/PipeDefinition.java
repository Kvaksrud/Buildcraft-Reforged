package buildcraftreforged.transport;

import buildcraftreforged.api.transport.IPipeTile.PipeType;
import buildcraftreforged.core.RegistryNames;

/**
 * One row of the pipe table in {@link TransportRegistry}: a kind and a material. Names follow the 4.2.2 pipe classes,
 * so the item pipe of gold is {@code PipeItemsGold}, registry name {@code pipe_items_gold}, translation key
 * {@code item.PipeItemsGold} (already in the 4.2.2 language file for the materials 4.2.2 had).
 */
public record PipeDefinition(PipeType kind, PipeMaterial material) {

    public PipeDefinition {
        if (kind == PipeType.STRUCTURE)
            throw new IllegalArgumentException("The Structure Pipe is outside the tier ladder (#54)");
    }

    public String unlocalizedName() {
        String kindName = switch (kind) {
        case ITEM -> "Items";
        case FLUID -> "Fluids";
        case POWER -> "Power";
        case STRUCTURE -> throw new IllegalStateException();
        };
        return "Pipe" + kindName + material.unlocalizedName;
    }

    public String registryName() {
        return RegistryNames.of(unlocalizedName());
    }
}
