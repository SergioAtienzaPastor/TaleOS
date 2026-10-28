package me.kvothe.talecraft.peripheral.speaker;

import java.util.List;
import java.util.regex.Pattern;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

import me.kvothe.talecraft.peripheral.Peripheral;

public final class SpeakerPeripheral implements Peripheral {
    public interface SoundBackend {
        String resolve(String name);

        void play(String soundEvent, float volume, float pitch);
    }

    private static final List<String> METHODS = List.of("playSound");
    private static final Pattern SOUND_ID = Pattern.compile("[A-Za-z0-9_./-]+");
    private static final int MAX_SOUND_ID_LENGTH = 256;

    private final SpeakerRateLimiter rateLimiter;
    private final SoundBackend backend;

    public SpeakerPeripheral(SpeakerRateLimiter rateLimiter, SoundBackend backend) {
        this.rateLimiter = rateLimiter;
        this.backend = backend;
    }

    @Override
    public String getType() {
        return "speaker";
    }

    @Override
    public List<String> getMethods() {
        return METHODS;
    }

    @Override
    public Varargs call(String method, Varargs args) {
        if (!"playSound".equals(method)) {
            return LuaValue.NIL;
        }
        if (!args.arg1().isstring() || args.arg1().isnumber()) {
            return failure("el nombre del sonido debe ser una cadena");
        }
        String name = args.arg1().tojstring();
        if (name.isBlank() || name.length() > MAX_SOUND_ID_LENGTH || !SOUND_ID.matcher(name).matches()) {
            return failure("ID de SoundEvent no válido");
        }
        LuaValue volumeArg = args.arg(2);
        if (!volumeArg.isnil() && !volumeArg.isnumber()) {
            return failure("el volumen debe ser un número entre 0 y 3");
        }
        double volume = volumeArg.isnil() ? 1.0 : volumeArg.todouble();
        if (!Double.isFinite(volume) || volume < 0.0 || volume > 3.0) {
            return failure("el volumen debe estar entre 0 y 3");
        }
        LuaValue pitchArg = args.arg(3);
        if (!pitchArg.isnil() && !pitchArg.isnumber()) {
            return failure("el pitch debe ser un número entre 0.5 y 2");
        }
        double pitch = pitchArg.isnil() ? 1.0 : pitchArg.todouble();
        if (!Double.isFinite(pitch) || pitch < 0.5 || pitch > 2.0) {
            return failure("el pitch debe estar entre 0.5 y 2");
        }
        String soundEvent = backend.resolve(name);
        if (soundEvent == null) {
            return failure("SoundEvent no registrado: " + name);
        }
        if (!rateLimiter.tryAcquire()) {
            return failure("límite de un sonido por equipo y tick");
        }
        try {
            backend.play(soundEvent, (float) volume, (float) pitch);
            return LuaValue.TRUE;
        } catch (RuntimeException exception) {
            return failure("no se pudo reproducir el SoundEvent");
        }
    }

    private static Varargs failure(String reason) {
        return LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf(reason));
    }
}
