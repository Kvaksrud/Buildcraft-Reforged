/*
 * Ported from buildcraft/energy/render/EntityDropParticleFX.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: EntityFX becomes SingleQuadParticle; particles.png indices 113, 112 and 114
 * become vanilla's drip_hang, drip_fall and drip_land sprites (particles/drop.json); the colour comes from the
 * ColorParticleOption that BlockBuildcraftFluid sends; the liquid check reads the fluid height instead of
 * BlockFluid.getFluidHeightPercent.
 */
package buildcraftreforged.energy.render;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class EntityDropParticleFX extends SingleQuadParticle {
    private static final int HANG = 0, FALL = 1, LAND = 2;

    private final SpriteSet sprites;

    /**
     * The height of the current bob
     */
    private int bobTimer;

    public EntityDropParticleFX(ClientLevel world, double x, double y, double z, float particleRed, float particleGreen,
            float particleBlue, SpriteSet sprites) {
        super(world, x, y, z, sprites.get(HANG, LAND));
        this.sprites = sprites;
        this.xd = this.yd = this.zd = 0.0D;

        this.rCol = particleRed;
        this.gCol = particleGreen;
        this.bCol = particleBlue;

        this.setSize(0.01F, 0.01F);
        this.gravity = 0.06F;
        this.bobTimer = 40;
        this.lifetime = (int) (64.0D / (Math.random() * 0.8D + 0.2D));
        this.xd = this.yd = this.zd = 0.0D;
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    /**
     * Called to update the entity's position/logic.
     */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        this.yd -= this.gravity;

        if (this.bobTimer-- > 0) {
            this.xd *= 0.02D;
            this.yd *= 0.02D;
            this.zd *= 0.02D;
            this.setSprite(sprites.get(HANG, LAND));
        } else {
            this.setSprite(sprites.get(FALL, LAND));
        }

        this.move(this.xd, this.yd, this.zd);
        this.xd *= 0.9800000190734863D;
        this.yd *= 0.9800000190734863D;
        this.zd *= 0.9800000190734863D;

        if (this.lifetime-- <= 0) {
            this.remove();
        }

        if (this.onGround) {
            this.setSprite(sprites.get(LAND, LAND));

            this.xd *= 0.699999988079071D;
            this.zd *= 0.699999988079071D;
        }

        BlockPos pos = BlockPos.containing(this.x, this.y, this.z);
        BlockState state = this.level.getBlockState(pos);
        FluidState fluid = state.getFluidState();

        if (!fluid.isEmpty() || state.isSolid()) {
            double d0 = fluid.isEmpty() ? pos.getY() + 1 : pos.getY() + fluid.getHeight(this.level, pos);

            if (this.y < d0) {
                this.remove();
            }
        }
    }

    public static class Provider implements ParticleProvider<ColorParticleOption> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(ColorParticleOption options, ClientLevel level, double x, double y, double z, double xAux,
                double yAux, double zAux, RandomSource random) {
            return new EntityDropParticleFX(level, x, y, z, options.getRed(), options.getGreen(), options.getBlue(), sprites);
        }
    }
}
