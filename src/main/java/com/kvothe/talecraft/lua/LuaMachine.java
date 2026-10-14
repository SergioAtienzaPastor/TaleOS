package com.kvothe.talecraft.lua;

import java.util.ArrayDeque;
import java.util.Deque;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * One sandboxed Lua virtual machine for a single computer.
 *
 * <p>The whole script runs as one Lua coroutine (LuaJ runs it on its own Java
 * thread): each {@link #tick()} resumes it once with the next queued event and
 * a fresh instruction budget. The budget is enforced by a debug count hook;
 * when it is exhausted the hook throws a plain {@link RuntimeException}, which
 * Lua {@code pcall} cannot catch (LuaJ's pcall only catches {@link LuaError}),
 * so runaway scripts always end up killing the machine, never the server.
 */
public final class LuaMachine {

    public enum State { NEW, ALIVE, DONE, DEAD }

    /** Bytecodes of Lua between two hook calls; budget granularity. */
    private static final int HOOK_INTERVAL = 64;

    /** Not a LuaError on purpose: Lua code (pcall) cannot catch this. */
    static final class BudgetExceededException extends RuntimeException {
        BudgetExceededException() {
            super("instruction budget exceeded");
        }
    }

    private final Globals globals;
    private final LuaValue sethook; // captured debug.sethook, hidden from scripts
    private final BudgetHook budgetHook = new BudgetHook();
    private final long budgetPerTick;
    private final Deque<Varargs> events = new ArrayDeque<>();
    private final StringBuilder output = new StringBuilder();

    private LuaThread thread;
    private LuaValue pendingChunk;
    private State state = State.NEW;
    private String error;

    public LuaMachine(long budgetPerTick) {
        this.budgetPerTick = budgetPerTick;
        this.globals = JsePlatform.debugGlobals();
        this.sethook = globals.get("debug").get("sethook");
        stripGlobals(globals);
        installPrint(globals);
        installTaleApi(globals);
    }

    /** Compiles the script and arms the machine. Compile errors kill it at once. */
    public void load(String script, String chunkName) {
        final LuaValue chunk;
        try {
            chunk = globals.load(script, chunkName);
        } catch (LuaError e) {
            state = State.DEAD;
            error = "compile: " + e.getMessage();
            return;
        }
        // Trusted bootstrap: sets the budget hook on the new coroutine, then
        // runs the user chunk. Args arrive on the first resume.
        LuaValue bootstrap = globals.load(
                "local sethook, hook, interval, chunk = ...\n"
                        + "sethook(hook, \"\", interval)\n"
                        + "return chunk()\n",
                "talecraft:bootstrap");
        this.thread = new LuaThread(globals, bootstrap);
        this.pendingChunk = chunk;
        this.state = State.ALIVE;
    }

    /** Queues an event; the next tick resumes the machine with it. */
    public void queueEvent(String name) {
        events.add(LuaValue.varargsOf(new LuaValue[] { LuaValue.valueOf(name) }));
    }

    /** Resumes the machine once, with a fresh instruction budget for this tick. */
    public synchronized void tick() {
        if (state != State.ALIVE) {
            return;
        }
        budgetHook.reset(budgetPerTick / HOOK_INTERVAL);
        Varargs args;
        if (pendingChunk != null) {
            args = LuaValue.varargsOf(new LuaValue[] {
                    sethook, budgetHook, LuaValue.valueOf(HOOK_INTERVAL), pendingChunk });
            pendingChunk = null;
        } else {
            Varargs ev = events.poll();
            args = ev != null ? ev : LuaValue.NIL;
        }
        Varargs result = thread.resume(args);
        if (result.arg1().optboolean(false)) {
            if ("dead".equals(thread.getStatus())) {
                state = State.DONE;
            }
        } else {
            state = State.DEAD;
            error = result.arg(2).tojstring();
        }
    }

    public State getState() {
        return state;
    }

    public boolean isAlive() {
        return state == State.ALIVE;
    }

    public String getError() {
        return error;
    }

    /** Everything printed since the last {@link #drainOutput()}. */
    public synchronized String drainOutput() {
        String out = output.toString();
        output.setLength(0);
        return out;
    }

    private void stripGlobals(Globals g) {
        g.set("io", LuaValue.NIL);
        g.set("debug", LuaValue.NIL);
        g.set("luajava", LuaValue.NIL);
        g.set("package", LuaValue.NIL);
        g.set("require", LuaValue.NIL);
        g.set("dofile", LuaValue.NIL);
        g.set("loadfile", LuaValue.NIL);
        LuaValue os = g.get("os");
        LuaTable safeOs = new LuaTable();
        for (String fn : new String[] { "clock", "time", "date" }) {
            safeOs.set(fn, os.get(fn));
        }
        g.set("os", safeOs);
    }

    private void installPrint(Globals g) {
        g.set("print", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                StringBuilder line = new StringBuilder();
                for (int i = 1; i <= args.narg(); i++) {
                    if (i > 1) {
                        line.append('\t');
                    }
                    line.append(args.arg(i).tojstring());
                }
                synchronized (LuaMachine.this) {
                    output.append(line).append('\n');
                }
                return LuaValue.NIL;
            }
        });
    }

    private void installTaleApi(Globals g) {
        LuaTable tale = new LuaTable();
        tale.set("pullEvent", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                // Suspends the coroutine; the next tick's resume value is
                // returned to the Lua caller as the event.
                return g.yield(LuaValue.NIL);
            }
        });
        g.set("tale", tale);
    }

    private static final class BudgetHook extends VarArgFunction {
        private long remaining;

        void reset(long fires) {
            this.remaining = fires;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (--remaining < 0) {
                throw new BudgetExceededException();
            }
            return NIL;
        }
    }
}
