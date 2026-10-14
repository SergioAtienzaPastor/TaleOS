package me.kvothe.talecraft.lua;

import java.util.Deque;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import me.kvothe.talecraft.os.VirtualFileSystem;

/**
 * One sandboxed Lua virtual machine for a single computer.
 *
 * <p>The whole script runs as one Lua coroutine (LuaJ runs it on its own Java
 * thread): each {@link #tick()} resumes it once with the next queued event and
 * a fresh instruction budget. The budget is enforced by a debug count hook
 * (requires {@link JsePlatform#debugGlobals()}). On exhaustion the hook throws
 * a {@link RuntimeException} and ratchets the hook interval to 1, so the kill
 * unwinds through pcall, xpcall handlers and child coroutines instead of being
 * swallowed — runaway scripts always kill the machine, never the server.
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
    private final Deque<Varargs> events = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private final StringBuilder output = new StringBuilder();

    private LuaThread thread;
    private LuaValue pendingChunk;
    private volatile State state = State.NEW;
    private volatile String error;
    private volatile int computerId;
    private volatile boolean rebootRequested;
    private volatile boolean shutdownRequested;

    public LuaMachine(long budgetPerTick) {
        this(budgetPerTick, null);
    }

    public LuaMachine(long budgetPerTick, VirtualFileSystem fs) {
        this.budgetPerTick = budgetPerTick;
        this.globals = JsePlatform.debugGlobals();
        this.sethook = globals.get("debug").get("sethook");
        stripGlobals(globals);
        installPrint(globals);
        installTaleApi(globals);
        installErrorGuard(globals);
        if (fs != null) {
            globals.set("fs", FsLib.create(fs));
        }
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
    public void queueEvent(String name, String... args) {
        LuaValue[] values = new LuaValue[args.length + 1];
        values[0] = LuaValue.valueOf(name);
        for (int i = 0; i < args.length; i++) {
            values[i + 1] = LuaValue.valueOf(args[i]);
        }
        events.add(LuaValue.varargsOf(values));
    }

    /** Resumes the machine once, with a fresh instruction budget for this tick. */
    public void tick() {
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
        if (rebootRequested || shutdownRequested) {
            // tale.reboot()/shutdown() park the coroutine; the supervisor turns
            // that into a clean halt. The plugin recreates the machine on reboot.
            state = State.DONE;
        } else if (result.arg1().optboolean(false)) {
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
    public String drainOutput() {
        synchronized (output) {
            String out = output.toString();
            output.setLength(0);
            return out;
        }
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
                synchronized (output) {
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
        tale.set("getComputerID", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(computerId);
            }
        });
        tale.set("reboot", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                rebootRequested = true;
                return g.yield(LuaValue.NIL);
            }
        });
        tale.set("shutdown", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                shutdownRequested = true;
                return g.yield(LuaValue.NIL);
            }
        });
        g.set("tale", tale);
    }

    public int getComputerId() {
        return computerId;
    }

    public void setComputerId(int computerId) {
        this.computerId = computerId;
    }

    public boolean isRebootRequested() {
        return rebootRequested;
    }

    public boolean isShutdownRequested() {
        return shutdownRequested;
    }

    /**
     * Closes the escape hatches around the budget kill: pcall is wrapped so a
     * budget death (recognizable by its marker in the wrapped "vm error"
     * message) is rethrown instead of returned — plain LuaJ pcall would
     * otherwise swallow it. xpcall needs no wrapper: its handler is only a
     * traceback hook in LuaJ and the kill keeps unwinding regardless (verified
     * empirically). Coroutines created by the script inherit the very same
     * budget hook, so they share this machine's per-tick budget instead of
     * getting a free ride.
     */
    private void installErrorGuard(Globals g) {
        final LuaValue basePcall = g.get("pcall");
        g.set("pcall", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return rethrowBudgetKill(basePcall.invoke(args));
            }
        });

        LuaValue coroutine = g.get("coroutine");
        if (coroutine.istable()) {
            LuaTable co = (LuaTable) coroutine;
            final LuaValue baseCreate = co.get("create");
            co.set("create", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    LuaThread child = baseCreate.call(args.arg1()).checkthread();
                    child.state.hookfunc = budgetHook;
                    child.state.hookcount = HOOK_INTERVAL;
                    return child;
                }
            });
            g.load("local co = ...\n"
                    + "co.wrap = function(f)\n"
                    + "  local c = co.create(f)\n"
                    + "  return function(...)\n"
                    + "    local res = table.pack(co.resume(c, ...))\n"
                    + "    if not res[1] then error(res[2], 2) end\n"
                    + "    return table.unpack(res, 2, res.n)\n"
                    + "  end\n"
                    + "end\n", "talecraft:wrap").call(co);
        }
    }

    private static Varargs rethrowBudgetKill(Varargs result) {
        if (!result.arg1().optboolean(true)) {
            String msg = result.arg(2).tojstring();
            if (msg != null && msg.contains(BudgetExceededException.class.getSimpleName())) {
                throw new LuaError(msg);
            }
        }
        return result;
    }

    /**
     * Count hook fired by the interpreter every HOOK_INTERVAL bytecodes. On
     * budget exhaustion it throws the pcall-proof {@link BudgetExceededException}
     * and ratchets the hook interval down to 1 on the running coroutine, so
     * any error-catcher that swallows the kill gets a fresh one on the very
     * next bytecode — no Lua construct (pcall, xpcall handlers, child
     * coroutines) can keep the machine alive past its budget.
     */
    private final class BudgetHook extends VarArgFunction {
        private long remaining;

        void reset(long fires) {
            this.remaining = fires;
        }

        @Override
        public Varargs invoke(Varargs args) {
            if (--remaining < 0) {
                LuaThread running = globals.running;
                if (running != null && running.state != null) {
                    running.state.hookcount = 1;
                }
                throw new BudgetExceededException();
            }
            return NIL;
        }
    }
}
