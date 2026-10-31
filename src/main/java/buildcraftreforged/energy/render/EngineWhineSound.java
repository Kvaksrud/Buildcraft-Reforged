/*
 * Buildcraft Reforged, 2026: new (plan section 9, #40). The whine of an engine whose overheat timer runs; its pitch
 * rises from 0.5 to 2 as the timer runs out.
 */
package buildcraftreforged.energy.render;

import buildcraftreforged.energy.EnergyRegistry;
import buildcraftreforged.energy.TileEngine;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/* Client only: called from the client branch of TileEngine.updateEntity. */
public final class EngineWhineSound extends AbstractTickableSoundInstance {

    private static final Map<TileEngine, EngineWhineSound> PLAYING = new WeakHashMap<>();

    private final TileEngine engine;

    private EngineWhineSound(TileEngine engine) {
        super(EnergyRegistry.engineWhine.get(), SoundSource.BLOCKS, RandomSource.create());
        this.engine = engine;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.6F;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = engine.getBlockPos().getX() + 0.5;
        this.y = engine.getBlockPos().getY() + 0.5;
        this.z = engine.getBlockPos().getZ() + 0.5;
        this.pitch = getPitch(engine);
    }

    /** Starts the whine for this engine unless it already plays. */
    public static void play(TileEngine engine) {
        EngineWhineSound sound = PLAYING.get(engine);
        Minecraft minecraft = Minecraft.getInstance();
        if (sound != null && !sound.isStopped() && minecraft.getSoundManager().isActive(sound))
            return;
        sound = new EngineWhineSound(engine);
        PLAYING.put(engine, sound);
        minecraft.getSoundManager().play(sound);
    }

    /** 0.5 when the timer starts, 2 when it runs out. */
    public static float getPitch(TileEngine engine) {
        if (engine.overheatTimerTotal <= 0)
            return 2F;
        float done = 1F - Math.clamp((float) engine.overheatTimer / engine.overheatTimerTotal, 0F, 1F);
        return 0.5F + 1.5F * done;
    }

    @Override
    public void tick() {
        if (engine.isRemoved() || engine.overheatTimer < 0) {
            PLAYING.remove(engine);
            stop();
            return;
        }
        pitch = getPitch(engine);
    }
}
