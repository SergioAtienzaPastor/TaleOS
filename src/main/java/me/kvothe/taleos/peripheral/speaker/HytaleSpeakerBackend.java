package me.kvothe.taleos.peripheral.speaker;

import java.util.function.Supplier;

import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;

public final class HytaleSpeakerBackend implements SpeakerPeripheral.SoundBackend {
    private final World world;
    private final Supplier<BlockPosition> position;

    public HytaleSpeakerBackend(World world, Supplier<BlockPosition> position) {
        this.world = world;
        this.position = position;
    }

    @Override
    public String resolve(String name) {
        if (soundIndex(name) >= 0) {
            return name;
        }
        String qualified = "SFX/" + name;
        return soundIndex(qualified) >= 0 ? qualified : null;
    }

    @Override
    public void play(String soundEvent, float volume, float pitch) {
        int index = soundIndex(soundEvent);
        if (index < 0) {
            throw new IllegalArgumentException("SoundEvent no registrado: " + soundEvent);
        }
        BlockPosition current = position.get();
        SoundUtil.playSoundEvent3d(index, SoundCategory.SFX,
                current.x + 0.5, current.y + 0.5, current.z + 0.5,
                volume, pitch, world.getEntityStore().getStore());
    }

    private static int soundIndex(String name) {
        try {
            return SoundEvent.getAssetStore().getAssetMap().getIndex(name);
        } catch (RuntimeException exception) {
            return -1;
        }
    }
}
