package me.kvothe.taleos.peripheral.speaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

class SpeakerPeripheralTest {
    private static final class FakeBackend implements SpeakerPeripheral.SoundBackend {
        int plays;
        String event;
        float volume;
        float pitch;
        boolean fail;

        @Override
        public String resolve(String name) {
            return "known".equals(name) || "SFX/known".equals(name) ? name : null;
        }

        @Override
        public void play(String soundEvent, float volume, float pitch) {
            if (fail) {
                throw new IllegalStateException("no audio in tests");
            }
            plays++;
            event = soundEvent;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    private static Varargs args(LuaValue... values) {
        return LuaValue.varargsOf(values);
    }

    private static Varargs play(SpeakerPeripheral speaker, LuaValue... values) {
        return speaker.call("playSound", args(values));
    }

    @Test
    void exposesOnlyPlaySoundAndUsesDefaults() {
        FakeBackend backend = new FakeBackend();
        SpeakerPeripheral speaker = new SpeakerPeripheral(new SpeakerRateLimiter(), backend);

        assertEquals("speaker", speaker.getType());
        assertEquals(List.of("playSound"), speaker.getMethods());
        assertFalse(speaker.supportsMethod("stop"));
        assertTrue(play(speaker, LuaValue.valueOf("known")).arg1().toboolean());
        assertEquals(1, backend.plays);
        assertEquals("known", backend.event);
        assertEquals(1.0, backend.volume, 0.0001);
        assertEquals(1.0, backend.pitch, 0.0001);
    }

    @Test
    void validatesIdsNumericTypesAndRangesWithoutPlaying() {
        FakeBackend backend = new FakeBackend();
        SpeakerPeripheral speaker = new SpeakerPeripheral(new SpeakerRateLimiter(), backend);
        List<Varargs> invalid = List.of(
                args(LuaValue.NIL), args(LuaValue.valueOf(7)), args(LuaValue.valueOf("bad id")),
                args(LuaValue.valueOf("")), args(LuaValue.valueOf("missing")),
                args(LuaValue.valueOf("known"), LuaValue.valueOf(-0.1)),
                args(LuaValue.valueOf("known"), LuaValue.valueOf(3.1)),
                args(LuaValue.valueOf("known"), LuaValue.valueOf(Double.NaN)),
                args(LuaValue.valueOf("known"), LuaValue.valueOf("loud")),
                args(LuaValue.valueOf("known"), LuaValue.ONE, LuaValue.valueOf(0.49)),
                args(LuaValue.valueOf("known"), LuaValue.ONE, LuaValue.valueOf(2.01)),
                args(LuaValue.valueOf("known"), LuaValue.ONE, LuaValue.valueOf(Double.POSITIVE_INFINITY)));
        for (Varargs arguments : invalid) {
            Varargs result = speaker.call("playSound", arguments);
            assertFalse(result.arg1().toboolean());
            assertFalse(result.arg(2).isnil());
        }
        assertEquals(0, backend.plays);
    }

    @Test
    void acceptsBoundaryValuesAndReportsBackendFailure() {
        FakeBackend backend = new FakeBackend();
        SpeakerRateLimiter limiter = new SpeakerRateLimiter();
        SpeakerPeripheral speaker = new SpeakerPeripheral(limiter, backend);
        assertTrue(play(speaker, LuaValue.valueOf("SFX/known"), LuaValue.ZERO,
                LuaValue.valueOf(0.5)).arg1().toboolean());
        limiter.beginTick();
        assertTrue(play(speaker, LuaValue.valueOf("known"), LuaValue.valueOf(3),
                LuaValue.valueOf(2)).arg1().toboolean());
        assertEquals(3.0, backend.volume, 0.0001);
        assertEquals(2.0, backend.pitch, 0.0001);

        limiter.beginTick();
        backend.fail = true;
        Varargs result = play(speaker, LuaValue.valueOf("known"));
        assertFalse(result.arg1().toboolean());
        assertEquals("could not play the SoundEvent", result.arg(2).tojstring());
    }

    @Test
    void rateLimitIsSharedPerComputerAndResetEachTick() {
        FakeBackend backend = new FakeBackend();
        SpeakerRateLimiter computerLimiter = new SpeakerRateLimiter();
        SpeakerPeripheral left = new SpeakerPeripheral(computerLimiter, backend);
        SpeakerPeripheral right = new SpeakerPeripheral(computerLimiter, backend);

        assertTrue(play(left, LuaValue.valueOf("known")).arg1().toboolean());
        Varargs limited = play(right, LuaValue.valueOf("known"));
        assertFalse(limited.arg1().toboolean());
        assertEquals("limit of one sound per computer and tick", limited.arg(2).tojstring());
        assertEquals(1, backend.plays);

        computerLimiter.beginTick();
        assertTrue(play(right, LuaValue.valueOf("known")).arg1().toboolean());
        SpeakerPeripheral anotherComputer = new SpeakerPeripheral(new SpeakerRateLimiter(), backend);
        assertTrue(play(anotherComputer, LuaValue.valueOf("known")).arg1().toboolean());
        assertEquals(3, backend.plays);
    }
}
