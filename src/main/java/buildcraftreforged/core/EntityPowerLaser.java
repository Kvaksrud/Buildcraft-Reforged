/*
 * Ported from buildcraft/core/EntityPowerLaser.java of BuildCraft 4.2.2, which carries no header.
 * Modified by Buildcraft Reforged, 2026: render data instead of an Entity (see EntityLaser); the data watcher slot 15
 * becomes writeData/readData. Ported with the laser (#71) for the builders' lasers (quarry, builder, path markers).
 */
package buildcraftreforged.core;

import buildcraftreforged.api.core.Position;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public class EntityPowerLaser extends EntityLaser {

    private byte texture;

    public EntityPowerLaser(@Nullable Level world) {
        super(world);
    }

    public EntityPowerLaser(@Nullable Level world, Position head, Position tail) {
        super(world, head, tail);
    }

    @Override
    public Identifier getTexture() {
        return LASER_TEXTURES[texture];
    }

    public void setTexture(int texture) {
        this.texture = (byte) texture;
        needsUpdate = true;
    }

    @Override
    public void readData(RegistryFriendlyByteBuf data) {
        super.readData(data);
        texture = (byte) Math.floorMod(data.readByte(), LASER_TEXTURES.length);
    }

    @Override
    public void writeData(RegistryFriendlyByteBuf data) {
        super.writeData(data);
        data.writeByte(texture);
    }
}
