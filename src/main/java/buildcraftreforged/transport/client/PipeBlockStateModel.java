package buildcraftreforged.transport.client;

import buildcraftreforged.BuildcraftReforged;
import buildcraftreforged.transport.PipeModelLayout;
import buildcraftreforged.transport.TilePipe;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;

/**
 * The static pipe body (docs/TRANSPORT.md 9.4): a cached model per connection mask. The block state file names one
 * texture; baking turns two template models ({@code block/pipe_centre_face} and {@code block/pipe_arm}, both facing
 * north) into twelve parts, one centre face and one arm per side, and stores the part list of each of the 64 masks.
 * Rendering then only looks the list up by the mask in the block entity's model data.
 *
 * <p>Blockstate JSON: {@code {"variants": {"": {"type": "buildcraftreforged:pipe", "texture": "<sprite>"}}}}.
 */
public final class PipeBlockStateModel implements DynamicBlockStateModel {

    public static final Identifier TYPE = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "pipe");
    static final Identifier CENTRE_FACE = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "block/pipe_centre_face");
    static final Identifier ARM = Identifier.fromNamespaceAndPath(BuildcraftReforged.MOD_ID, "block/pipe_arm");

    private final List<List<BlockStateModelPart>> partsByMask;
    private final Material.Baked particle;
    private final @BakedQuad.MaterialFlags int materialFlags;

    PipeBlockStateModel(BlockStateModelPart[] parts, Material.Baked particle) {
        List<List<BlockStateModelPart>> byMask = new ArrayList<>(64);
        for (int mask = 0; mask < 64; mask++) {
            List<BlockStateModelPart> list = new ArrayList<>(6);
            for (int part : PipeModelLayout.parts(mask)) {
                list.add(parts[part]);
            }
            byMask.add(List.copyOf(list));
        }
        int flags = 0;
        for (BlockStateModelPart part : parts) {
            flags |= part.materialFlags();
        }
        this.partsByMask = List.copyOf(byMask);
        this.particle = particle;
        this.materialFlags = flags;
    }

    private static int connectionMask(BlockAndTintGetter level, BlockPos pos) {
        Byte mask = level.getModelData(pos).get(TilePipe.CONNECTIONS);
        return mask == null ? 0 : mask & 63;
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
            List<BlockStateModelPart> parts) {
        parts.addAll(partsByMask.get(connectionMask(level, pos)));
    }

    @Override
    public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        return new GeometryKey(this, connectionMask(level, pos));
    }

    @Override
    public Material.Baked particleMaterial() {
        return particle;
    }

    @Override
    public @BakedQuad.MaterialFlags int materialFlags() {
        return materialFlags;
    }

    private record GeometryKey(PipeBlockStateModel model, int mask) {
    }

    public record Unbaked(Identifier texture) implements CustomUnbakedBlockStateModel {

        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance
                .group(Identifier.CODEC.fieldOf("texture").forGetter(Unbaked::texture))
                .apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> codec() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            resolver.markDependency(CENTRE_FACE);
            resolver.markDependency(ARM);
        }

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            ModelDebugName name = () -> TYPE + "[" + texture + "]";
            Material material = new Material(texture);
            TextureSlots slots = new TextureSlots.Resolver()
                    .addLast(new TextureSlots.Data.Builder().addTexture("pipe", material).addTexture("particle", material).build())
                    .resolve(name);

            ResolvedModel centreFace = baker.getModel(CENTRE_FACE);
            ResolvedModel arm = baker.getModel(ARM);
            BlockStateModelPart[] parts = new BlockStateModelPart[PipeModelLayout.PART_COUNT];
            for (Direction side : Direction.values()) {
                BlockModelRotation rotation = BlockModelRotation.get(PipeModelLayout.rotationTowards(side));
                parts[PipeModelLayout.centreFace(side)] = bakePart(baker, centreFace, slots, rotation, name);
                parts[PipeModelLayout.arm(side)] = bakePart(baker, arm, slots, rotation, name);
            }
            return new PipeBlockStateModel(parts, baker.materials().resolveSlot(slots, "particle", name));
        }

        private static BlockStateModelPart bakePart(ModelBaker baker, ResolvedModel template, TextureSlots slots,
                BlockModelRotation rotation, ModelDebugName name) {
            return new SimpleModelWrapper(template.bakeTopGeometry(slots, baker, rotation),
                    template.getTopAmbientOcclusion(), baker.materials().resolveSlot(slots, "particle", name));
        }
    }
}
