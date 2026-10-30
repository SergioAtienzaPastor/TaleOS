package me.kvothe.taleos.lua;

import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import me.kvothe.taleos.computer.Computer;
import me.kvothe.taleos.computer.MonitorCanvas;
import me.kvothe.taleos.computer.MonitorTerminal;
import me.kvothe.taleos.computer.TerminalSurface;
import me.kvothe.taleos.computer.TerminalTarget;
import me.kvothe.taleos.net.HttpBridge;
import me.kvothe.taleos.net.HttpResult;
import me.kvothe.taleos.os.VirtualFileSystem;
import me.kvothe.taleos.peripheral.Peripheral;
import me.kvothe.taleos.peripheral.PeripheralHandle;
import me.kvothe.taleos.peripheral.PeripheralRegistry;
import me.kvothe.taleos.peripheral.monitor.MonitorPeripheral;

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
    public static final int MAX_EVENT_QUEUE = 256;
    private final Deque<QueuedEvent> events = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private final AtomicInteger eventCount = new AtomicInteger();
    private final Map<Long, Timer> timers = new LinkedHashMap<>();
    private long currentTick;
    private long nextTimerId;
    private final StringBuilder output = new StringBuilder();
    private final TerminalSurface nativeSurface = new TerminalSurface(51, 19);
    private final Map<MonitorCanvas, LuaTable> monitorTables = new java.util.IdentityHashMap<>();
    private final Map<String, LuaTable> peripheralTables = new LinkedHashMap<>();
    private final PeripheralRegistry peripherals = new PeripheralRegistry(null);
    private volatile boolean nativeSpatial;
    private LuaTable nativeTerminal;
    private LuaTable currentTerminal;

    private static final class QueuedEvent {
        private final Varargs values;
        private final Timer timer;

        private QueuedEvent(Varargs values, Timer timer) {
            this.values = values;
            this.timer = timer;
        }
    }

    private static final class Timer {
        private final long id;
        private final long dueTick;
        private boolean fired;
        private volatile boolean cancelled;

        private Timer(long id, long dueTick) {
            this.id = id;
            this.dueTick = dueTick;
        }
    }

    /** Message of the LuaError that ends a program on requestTerminate; the
     * shell's pcall catches it, so the program dies but the machine survives
     * (CC:T's "Terminated"). */
    public static final String TERMINATED_MESSAGE = "Program terminated";

    private volatile boolean terminateRequested;
    /** Set by the shell (tale.setProgramRunning) while a program runs inside
     * its pcall: terminate requests only fire while this is true, so hitting
     * Detener at an idle prompt never hurts the machine itself. */
    private volatile boolean programRunning;

    private LuaThread thread;
    private LuaValue pendingChunk;
    private volatile State state = State.NEW;
    private volatile String error;
    private volatile int computerId;
    private volatile boolean rebootRequested;
    private volatile boolean shutdownRequested;
    private volatile WorldBridge worldBridge = WorldBridge.NONE;
    private volatile HttpBridge httpBridge;
    private volatile java.util.concurrent.CompletableFuture<HttpResult> pendingHttp;
    private volatile long httpRequestSerial;

    /** Marker used in the yield of a world call (see WorldBridge). */
    private static final LuaValue WORLDCALL = LuaValue.valueOf("tale:worldcall");
    private static final LuaValue PERIPHERAL_ERROR = LuaValue.valueOf("tale:peripheral_error");
    /** Marker used by http.get while the asynchronous request runs. */
    private static final LuaValue HTTPCALL = LuaValue.valueOf("tale:httpcall");
    /** Marker used by tale.sleep: yield {"tale:sleep", ticks}. */
    private static final LuaValue SLEEPCALL = LuaValue.valueOf("tale:sleep");
    /** Yield marker of tale.pullEvent: the machine parks until an event arrives. */
    private static final LuaValue EVENTWAIT = LuaValue.valueOf("tale:wait_event");
    /** World calls beyond this per tick get nil instead of executing. */
    private static final int MAX_WORLDCALLS_PER_TICK = 32;
    private static final String[] TURTLE_METHODS = {
            "forward", "back", "up", "down", "turnLeft", "turnRight",
            "detect", "detectUp", "detectDown", "inspect", "inspectUp", "inspectDown",
            "select", "getSelectedSlot", "getItemCount", "getItemSpace", "getItemDetail",
            "transferTo", "compare", "compareUp", "compareDown", "compareTo",
            "equip", "unequip", "equipLeft", "equipRight", "getEquippedLeft", "getEquippedRight",
            "getFuelLevel", "getFuelLimit", "refuel", "dig", "digUp", "digDown",
            "attack", "attackUp", "attackDown", "craft",
            "place", "placeUp", "placeDown", "suck", "suckUp", "suckDown",
            "drop", "dropUp", "dropDown"
    };
    /** Ticks the machine stays parked because of tale.sleep. */
    private int sleepTicksRemaining;
    /** True while the machine sits in tale.pullEvent with nothing to deliver. */
    private boolean waitingForEvent;
    /** Current shell/program prompt, shown by the page next to the input row. */
    private volatile String prompt = "tale> ";
    private final VirtualFileSystem fileSystem;

    public LuaMachine(long budgetPerTick) {
        this(budgetPerTick, null);
    }

    public LuaMachine(long budgetPerTick, VirtualFileSystem fs) {
        this.budgetPerTick = budgetPerTick;
        this.fileSystem = fs;
        this.globals = JsePlatform.debugGlobals();
        this.sethook = globals.get("debug").get("sethook");
        stripGlobals(globals);
        installTermApi(globals);
        installPrint(globals);
        installTaleApi(globals);
        installPeripheralApi(globals);
        installErrorGuard(globals);
        if (fs != null) {
            globals.set("fs", FsLib.create(fs));
        }
    }

    public VirtualFileSystem getFileSystem() {
        return fileSystem;
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
                "taleos:bootstrap");
        this.thread = new LuaThread(globals, bootstrap);
        this.pendingChunk = chunk;
        this.state = State.ALIVE;
    }

    /**
     * Queues a typed event in FIFO order. Returns false when the 256-event
     * bound is full; accepted values are strings, numbers, booleans, tables and
     * nil. This is also the entry point for future network events.
     */
    public boolean queueEvent(String name, Object... args) {
        LuaValue[] values = new LuaValue[args.length + 1];
        values[0] = LuaValue.valueOf(name);
        for (int i = 0; i < args.length; i++) {
            values[i + 1] = eventValue(args[i]);
        }
        return offerEvent(LuaValue.varargsOf(values), null);
    }

    private static LuaValue eventValue(Object value) {
        if (value == null || value == LuaValue.NIL) {
            return LuaValue.NIL;
        }
        if (value instanceof LuaValue lua) {
            if (lua.isnil() || lua.isstring() || lua.isnumber() || lua.isboolean() || lua.istable()) {
                return lua;
            }
            throw new IllegalArgumentException("unsupported Lua event value: " + lua.typename());
        }
        if (value instanceof String string) {
            return LuaValue.valueOf(string);
        }
        if (value instanceof Boolean bool) {
            return LuaValue.valueOf(bool);
        }
        if (value instanceof Number number) {
            return LuaValue.valueOf(number.doubleValue());
        }
        throw new IllegalArgumentException("unsupported event value type: "
                + value.getClass().getName());
    }

    private boolean offerEvent(Varargs values, Timer timer) {
        while (true) {
            int size = eventCount.get();
            if (size >= MAX_EVENT_QUEUE) {
                return false;
            }
            if (eventCount.compareAndSet(size, size + 1)) {
                events.add(new QueuedEvent(values, timer));
                return true;
            }
        }
    }

    private Varargs pollEvent() {
        while (true) {
            QueuedEvent event = events.poll();
            if (event == null) {
                return null;
            }
            eventCount.decrementAndGet();
            if (event.timer == null || !event.timer.cancelled) {
                if (event.timer != null) {
                    timers.remove(event.timer.id, event.timer);
                }
                return event.values;
            }
        }
    }

    public int getQueuedEventCount() {
        return eventCount.get();
    }

    private long startTimer(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0) {
            throw new LuaError("timer seconds must be a finite, non-negative number");
        }
        long ticks = Math.max(1, (long) Math.ceil(seconds * 20.0));
        long id = ++nextTimerId;
        timers.put(id, new Timer(id, currentTick + ticks));
        return id;
    }

    private void cancelTimer(long id) {
        Timer timer = timers.remove(id);
        if (timer != null) {
            timer.cancelled = true;
        }
    }

    private void fireDueTimers() {
        currentTick++;
        var iterator = timers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Timer> entry = iterator.next();
            Timer timer = entry.getValue();
            if (!timer.cancelled && !timer.fired && timer.dueTick <= currentTick) {
                LuaValue id = LuaValue.valueOf(entry.getKey().doubleValue());
                timer.fired = true;
                if (!offerEvent(LuaValue.varargsOf(new LuaValue[] {
                        LuaValue.valueOf("timer"), id }), timer)) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Resumes the machine once, with a fresh instruction budget for this tick.
     * World calls requested by the script (tale.getBlock/detect/chat/time) are
     * executed synchronously on <b>this</b> thread — which must be the server
     * tick thread — before resuming the coroutine with the result, so Lua code
     * never touches the world from its own thread.
     */
    public void tick() {
        if (state != State.ALIVE) {
            return;
        }
        for (PeripheralRegistry.Change change : peripherals.refresh()) {
            if (!change.attached()) {
                peripheralTables.remove(change.name());
            }
            queueEvent(change.attached() ? "peripheral" : "peripheral_detach", change.name());
        }
        fireDueTimers();
        // A pending terminate wakes the machine from any park (sleep, event
        // wait) so the budget hook gets to fire inside the running program
        // and throw the termination error there.
        if (terminateRequested && programRunning) {
            if (sleepTicksRemaining > 0) {
                sleepTicksRemaining = 1; // counted down below: wakes this tick
            }
            if (waitingForEvent) {
                waitingForEvent = false; // resumed with nil; the hook kills it
            }
        }
        // A sleeping machine is parked mid-yield: count down and only resume
        // (with nil, not an event) when the timer runs out.
        boolean wakeFromSleep = false;
        if (sleepTicksRemaining > 0) {
            wakeFromSleep = --sleepTicksRemaining == 0;
            if (!wakeFromSleep) {
                return;
            }
        }
        budgetHook.reset(budgetPerTick / HOOK_INTERVAL);
        Varargs args;
        if (pendingChunk != null) {
            args = LuaValue.varargsOf(new LuaValue[] {
                    sethook, budgetHook, LuaValue.valueOf(HOOK_INTERVAL), pendingChunk });
            pendingChunk = null;
        } else if (wakeFromSleep) {
            args = LuaValue.NIL;
        } else if (waitingForEvent) {
            // Parked in pullEvent: only resume when there is an event to deliver.
            Varargs ev = pollEvent();
            if (ev == null) {
                return;
            }
            waitingForEvent = false;
            args = ev;
        } else {
            Varargs ev = pollEvent();
            args = ev != null ? ev : LuaValue.NIL;
        }
        int worldcalls = 0;
        while (state == State.ALIVE) {
            Varargs result = thread.resume(args);
            if (rebootRequested || shutdownRequested) {
                state = State.DONE;
                return;
            }
            if (!result.arg1().optboolean(false)) {
                state = State.DEAD;
                error = result.arg(2).tojstring();
                return;
            }
            if ("dead".equals(thread.getStatus())) {
                state = State.DONE;
                return;
            }
            // Suspended: sleep timer, pullEvent yield, or a world call.
            if (SLEEPCALL.equals(result.arg(2))) {
                sleepTicksRemaining = Math.max(1, result.arg(3).optint(1));
                return;
            }
            if (EVENTWAIT.equals(result.arg(2))) {
                waitingForEvent = true;
                if (!programRunning) {
                    terminateRequested = false; // idle prompt: drop stale requests
                }
                return;
            }
            if (isWorldCall(result)) {
                args = ++worldcalls <= MAX_WORLDCALLS_PER_TICK
                        ? executeWorldCall(result)
                        : LuaValue.NIL;
                continue;
            }
            if (HTTPCALL.equals(result.arg(2))) {
                startHttpCall(result.arg(3).checkjstring());
                return;
            }
            return;
        }
    }

    private static boolean isWorldCall(Varargs yieldValues) {
        return WORLDCALL.equals(yieldValues.arg(2));
    }

    /** Executes one world call on the calling (tick) thread. */
    private Varargs executeWorldCall(Varargs yieldValues) {
        String op = yieldValues.arg(3).tojstring();
        WorldBridge bridge = worldBridge;
        switch (op) {
            case "getBlock": {
                int dx = yieldValues.arg(4).optint(0);
                int dy = yieldValues.arg(5).optint(-1);
                int dz = yieldValues.arg(6).optint(0);
                String id = bridge.getBlockId(bridge.getOriginX() + dx,
                        bridge.getOriginY() + dy, bridge.getOriginZ() + dz);
                return id == null ? LuaValue.NIL : LuaValue.valueOf(id);
            }
            case "detect": {
                int dx = yieldValues.arg(4).optint(0);
                int dy = yieldValues.arg(5).optint(-1);
                int dz = yieldValues.arg(6).optint(0);
                return LuaValue.valueOf(bridge.getBlockId(bridge.getOriginX() + dx,
                        bridge.getOriginY() + dy, bridge.getOriginZ() + dz) != null);
            }
            case "chat":
                bridge.chat(yieldValues.arg(4).tojstring());
                return LuaValue.TRUE;
            case "time":
                return LuaValue.valueOf(bridge.getTime());
            case "turtle": {
                TurtleBridge turtle = bridge.turtle();
                if (turtle == null) {
                    return LuaValue.NIL;
                }
                try {
                    return turtle.call(yieldValues.arg(4).tojstring(), yieldValues.subargs(5));
                } catch (RuntimeException exception) {
                    System.err.println("[TaleOS] turtle call failed: " + yieldValues.arg(4));
                    exception.printStackTrace();
                    throw exception;
                }
            }
            case "peripheral": {
                String action = yieldValues.arg(4).checkjstring();
                String name = yieldValues.arg(5).checkjstring();
                PeripheralHandle handle = peripherals.handle(name);
                if (handle == null) {
                    return LuaValue.NIL;
                }
                return switch (action) {
                    case "type" -> {
                        String type = handle.getType();
                        yield type == null ? LuaValue.NIL : LuaValue.valueOf(type);
                    }
                    case "methods" -> methodNames(handle.getMethods());
                    case "wrap" -> handle.isPresent()
                            ? registeredPeripheralTable(handle) : LuaValue.NIL;
                    default -> LuaValue.NIL;
                };
            }
            case "peripheralNames": {
                LuaTable names = new LuaTable();
                int index = 1;
                for (String name : bridge.peripherals().snapshot().keySet()) {
                    names.set(index++, LuaValue.valueOf(name));
                }
                return names;
            }
            case "peripheralCall": {
                try {
                    PeripheralHandle handle = peripherals.handle(yieldValues.arg(4).checkjstring());
                    return handle == null ? LuaValue.NIL : handle.call(
                            yieldValues.arg(5).checkjstring(), yieldValues.subargs(6));
                } catch (RuntimeException e) {
                    String message = e.getMessage();
                    return LuaValue.varargsOf(PERIPHERAL_ERROR,
                            LuaValue.valueOf(message == null ? e.getClass().getSimpleName() : message));
                }
            }
            case "monitor": {
                int dx = yieldValues.arg(4).optint(0);
                int dy = yieldValues.arg(5).optint(0);
                int dz = yieldValues.arg(6).optint(0);
                if (dx == 0 && dy == 0 && dz == 0) {
                    Map<String, Peripheral> snapshot = bridge.peripherals().snapshot();
                    for (String side : new String[] { "right", "left", "top", "bottom", "front", "back" }) {
                        if (snapshot.get(side) instanceof MonitorPeripheral monitor) {
                            PeripheralHandle handle = peripherals.handle(side);
                            return monitorTables.computeIfAbsent(monitor.getCanvas(),
                                    ignored -> registeredMonitorTable(handle));
                        }
                    }
                } else {
                    String alias = "@" + dx + "," + dy + "," + dz;
                    if (bridge.peripherals().find(alias) instanceof MonitorPeripheral monitor) {
                        PeripheralHandle handle = peripherals.handle(alias);
                        return monitorTables.computeIfAbsent(monitor.getCanvas(),
                                ignored -> registeredMonitorTable(handle));
                    }
                }
                MonitorCanvas canvas = bridge.monitorAt(dx, dy, dz);
                return canvas == null ? LuaValue.NIL : monitorTables.computeIfAbsent(canvas, this::monitorTable);
            }
            default:
                return LuaValue.NIL;
        }
    }

    private Varargs methodNames(java.util.List<String> methods) {
        if (methods == null) {
            return LuaValue.NIL;
        }
        LuaTable result = new LuaTable();
        int index = 1;
        for (String method : methods) {
            result.set(index++, LuaValue.valueOf(method));
        }
        return result;
    }

    private LuaTable registeredPeripheralTable(PeripheralHandle handle) {
        if (handle.resolve() instanceof MonitorPeripheral monitor) {
            return monitorTables.computeIfAbsent(monitor.getCanvas(),
                    ignored -> registeredMonitorTable(handle));
        }
        return peripheralTables.computeIfAbsent(handle.getName(), ignored -> {
            LuaTable table = new LuaTable();
            for (String method : handle.getMethods()) {
                table.set(method, peripheralFunction(handle, method));
            }
            return table;
        });
    }

    private LuaTable registeredMonitorTable(PeripheralHandle handle) {
        LuaTable monitor = new LuaTable();
        for (String method : handle.getMethods()) {
            monitor.set(method, peripheralFunction(handle, method));
        }
        monitor.set("__getCell", peripheralFunction(handle, "__getCell"));
        monitor.set("__setCell", peripheralFunction(handle, "__setCell"));
        LuaTable terminal = new LuaTable();
        for (String method : new String[] { "write", "blit", "clear", "clearLine", "scroll",
                "setCursorPos", "getCursorPos", "setCursorBlink", "getCursorBlink", "setTextColor",
                "getTextColor", "setBackgroundColor", "getBackgroundColor", "isColor",
                "__getCell", "__setCell" }) {
            terminal.set(method, monitor.get(method));
        }
        terminal.set("getSize", monitor.get("getTerminalSize"));
        monitor.set("__terminalRedirect", terminal);
        return monitor;
    }

    private VarArgFunction peripheralFunction(PeripheralHandle handle, String method) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Varargs result = globals.yield(LuaValue.varargsOf(new LuaValue[] {
                        WORLDCALL, LuaValue.valueOf("peripheralCall"),
                        LuaValue.valueOf(handle.getName()), LuaValue.valueOf(method) }, args));
                return finishPeripheralCall(result);
            }
        };
    }

    private Varargs finishPeripheralCall(Varargs result) {
        throwTerminateIfRequested();
        if (PERIPHERAL_ERROR.equals(result.arg1())) {
            throw new LuaError(result.arg(2));
        }
        return result;
    }

    /** Pixel-compatible monitor table. term.redirect uses its character-terminal facade. */
    private LuaTable monitorTable(MonitorCanvas canvas) {
        MonitorTerminal adapter = new MonitorTerminal(canvas);
        LuaTable terminal = terminalTable(adapter, false);
        LuaTable mon = new LuaTable();
        mon.set("__terminalRedirect", terminal);
        mon.set("getSize", fn(a -> LuaValue.varargsOf(LuaValue.valueOf(canvas.getWidth()),
                LuaValue.valueOf(canvas.getHeight()))));
        mon.set("getTerminalSize", terminal.get("getSize"));
        mon.set("setPixel", fn(a -> {
            canvas.setPixel(a.arg(1).checkint() - 1, a.arg(2).checkint() - 1, checkColor(a.arg(3)));
            return LuaValue.NIL;
        }));
        mon.set("getPixel", fn(a -> {
            int c = canvas.getPixel(a.arg(1).checkint() - 1, a.arg(2).checkint() - 1);
            return LuaValue.valueOf(MonitorCanvas.PALETTE[c]);
        }));
        mon.set("fill", fn(a -> {
            canvas.fill(a.arg(1).checkint() - 1, a.arg(2).checkint() - 1,
                    a.arg(3).checkint(), a.arg(4).checkint(), checkColor(a.arg(5)));
            return LuaValue.NIL;
        }));
        mon.set("clear", fn(a -> {
            if (a.narg() == 0) {
                adapter.clear();
            } else {
                canvas.clear(checkColor(a.arg1()));
            }
            return LuaValue.NIL;
        }));
        mon.set("write", fn(a -> {
            if (a.narg() < 3) {
                adapter.write(a.arg1().tojstring());
                return LuaValue.NIL;
            }
            String text = a.arg(3).checkjstring();
            int color = a.arg(4).isnil() ? MonitorCanvas.colorIndex("white") : checkColor(a.arg(4));
            canvas.writeText(a.arg(1).checkint() - 1, a.arg(2).checkint() - 1, text, color);
            return LuaValue.NIL;
        }));
        for (String method : new String[] { "blit", "clearLine", "scroll", "setCursorPos",
                "getCursorPos", "setCursorBlink", "getCursorBlink", "setTextColor", "getTextColor",
                "setBackgroundColor", "getBackgroundColor", "isColor", "__getCell", "__setCell" }) {
            mon.set(method, terminal.get(method));
        }
        return mon;
    }

    private static int checkColor(LuaValue v) {
        String name = v.checkjstring();
        int index = MonitorCanvas.colorIndex(name);
        if (index < 0) {
            throw new LuaError(me.kvothe.taleos.I18n.t("Unknown color: ") + name
                    + me.kvothe.taleos.I18n.t(" (palette: ")
                    + String.join(", ", MonitorCanvas.PALETTE) + ")");
        }
        return index;
    }

    public void setHttpBridge(HttpBridge bridge) {
        cancelPendingHttp();
        this.httpBridge = bridge;
        if (bridge == null || !bridge.isEnabled()) {
            globals.set("http", LuaValue.NIL);
        } else {
            installHttpApi(globals);
        }
    }

    private void installHttpApi(Globals g) {
        LuaTable http = new LuaTable();
        http.set("get", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Varargs result = g.yield(LuaValue.varargsOf(new LuaValue[] {
                        HTTPCALL, LuaValue.valueOf(args.arg1().checkjstring()) }));
                throwTerminateIfRequested();
                return result;
            }
        });
        g.set("http", http);
    }

    private void startHttpCall(String url) {
        waitingForEvent = true;
        long requestId = ++httpRequestSerial;
        try {
            java.util.concurrent.CompletableFuture<HttpResult> request = httpBridge.get(this, url);
            pendingHttp = request;
            request.whenComplete((response, failure) -> {
                if (requestId != httpRequestSerial) {
                    return;
                }
                pendingHttp = null;
                offerEvent(failure == null ? httpSuccess(response) : httpFailure(failure), null);
            });
        } catch (Throwable failure) {
            if (requestId == httpRequestSerial) {
                pendingHttp = null;
                offerEvent(httpFailure(failure), null);
            }
        }
    }

    private static Varargs httpSuccess(HttpResult result) {
        LuaTable response = new LuaTable();
        response.set("readAll", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(result.body());
            }
        });
        response.set("getResponseCode", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.varargsOf(LuaValue.valueOf(result.statusCode()),
                        LuaValue.valueOf(result.reason()));
            }
        });
        response.set("close", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.NIL;
            }
        });
        return response;
    }

    private static Varargs httpFailure(Throwable failure) {
        Throwable cause = failure instanceof java.util.concurrent.CompletionException
                && failure.getCause() != null ? failure.getCause() : failure;
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return LuaValue.varargsOf(LuaValue.NIL,
                LuaValue.valueOf(me.kvothe.taleos.I18n.t(message)));
    }

    public void cancelPendingHttp() {
        httpRequestSerial++;
        pendingHttp = null;
        HttpBridge bridge = httpBridge;
        if (bridge != null) {
            bridge.cancel(this);
        }
    }

    public void invalidatePeripherals() {
        peripherals.invalidate();
    }

    public void setWorldBridge(WorldBridge bridge) {
        this.worldBridge = bridge == null ? WorldBridge.NONE : bridge;
        peripherals.setProvider(this.worldBridge.peripherals());
        if (this.worldBridge.turtle() == null) {
            globals.set("turtle", LuaValue.NIL);
        } else {
            installTurtleApi(globals);
        }
    }

    private void installPeripheralApi(Globals g) {
        LuaTable peripheral = new LuaTable();
        peripheral.set("getNames", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Varargs result = g.yield(LuaValue.varargsOf(new LuaValue[] {
                        WORLDCALL, LuaValue.valueOf("peripheralNames") }));
                throwTerminateIfRequested();
                return result;
            }
        });
        for (String function : new String[] { "getType", "getMethods", "_wrap" }) {
            peripheral.set(function, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    String action = switch (function) {
                        case "getType" -> "type";
                        case "getMethods" -> "methods";
                        default -> "wrap";
                    };
                    Varargs result = g.yield(LuaValue.varargsOf(new LuaValue[] {
                            WORLDCALL, LuaValue.valueOf("peripheral"), LuaValue.valueOf(action),
                            LuaValue.valueOf(args.arg1().checkjstring()) }));
                    throwTerminateIfRequested();
                    return result;
                }
            });
        }
        peripheral.set("call", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Varargs result = g.yield(LuaValue.varargsOf(new LuaValue[] {
                        WORLDCALL, LuaValue.valueOf("peripheralCall"),
                        LuaValue.valueOf(args.arg(1).checkjstring()),
                        LuaValue.valueOf(args.arg(2).checkjstring()) }, args.subargs(3)));
                return finishPeripheralCall(result);
            }
        });
        g.set("peripheral", peripheral);
    }

    private void installTurtleApi(Globals g) {
        LuaTable turtle = new LuaTable();
        for (String method : TURTLE_METHODS) {
            turtle.set(method, new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    Varargs result = g.yield(LuaValue.varargsOf(new LuaValue[] {
                            WORLDCALL, LuaValue.valueOf("turtle"), LuaValue.valueOf(method) }, args));
                    throwTerminateIfRequested();
                    return result;
                }
            });
        }
        g.set("turtle", turtle);
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

    /** Native transcript output and control lines since the last drain. */
    public String drainOutput() {
        synchronized (output) {
            String out = output.toString();
            output.setLength(0);
            return out;
        }
    }

    /** Appends shell echo to the transcript and projects its visible text onto the native grid. */
    public void appendEcho(String s) {
        synchronized (output) {
            output.append(s);
        }
        nativeSurface.write(s.replace(Computer.ECHO_MARK + "", ""));
    }

    public String getPrompt() {
        return prompt;
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
        // Requests the editor UI for a file: emits an editor-request control
        // line that Computer turns into an EditorPage for the player who is
        // typing in the terminal.
        safeOs.set("openEditor", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String path = args.arg1().tojstring();
                synchronized (output) {
                    output.append(Computer.EDITOR_MARK).append(path).append('\n');
                }
                return LuaValue.TRUE;
            }
        });
        safeOs.set("startTimer", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(startTimer(args.arg1().checkdouble()));
            }
        });
        safeOs.set("cancelTimer", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                cancelTimer(args.arg1().checklong());
                return LuaValue.NIL;
            }
        });
        safeOs.set("queueEvent", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String name = args.arg1().checkjstring();
                LuaValue[] values = new LuaValue[args.narg() - 1];
                for (int i = 0; i < values.length; i++) {
                    values[i] = args.arg(i + 2);
                }
                return LuaValue.valueOf(queueEvent(name, (Object[]) values));
            }
        });
        g.set("os", safeOs);
    }

    private interface LuaCall {
        Varargs call(Varargs args);
    }

    private static VarArgFunction fn(LuaCall call) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return call.call(args);
            }
        };
    }

    private void installTermApi(Globals g) {
        nativeTerminal = terminalTable(nativeSurface, true);
        currentTerminal = nativeTerminal;
        LuaTable term = new LuaTable();
        for (String method : new String[] { "write", "blit", "clear", "clearLine", "scroll",
                "getSize", "setCursorPos", "getCursorPos", "setCursorBlink", "getCursorBlink",
                "setTextColor", "getTextColor", "setBackgroundColor", "getBackgroundColor", "isColor" }) {
            term.set(method, fn(args -> currentTerminal.get(method).invoke(args)));
        }
        term.set("native", fn(args -> nativeTerminal));
        term.set("current", fn(args -> currentTerminal));
        term.set("redirect", fn(args -> {
            LuaTable requested = args.arg1().checktable();
            LuaValue adapter = requested.get("__terminalRedirect");
            LuaTable next = adapter.istable() ? adapter.checktable() : requested;
            if (!next.get("write").isfunction() || !next.get("getSize").isfunction()) {
                throw new LuaError("term.redirect expects a terminal object");
            }
            LuaTable previous = currentTerminal;
            currentTerminal = next;
            return previous;
        }));
        g.set("term", term);
    }

    private LuaTable terminalTable(TerminalTarget target, boolean mirrorNative) {
        LuaTable table = new LuaTable();
        table.set("write", fn(args -> {
            String text = args.arg1().tojstring();
            if (mirrorNative) {
                appendNativeOutput(text);
            }
            target.write(text);
            return LuaValue.NIL;
        }));
        table.set("blit", fn(args -> {
            markNativeSpatial(mirrorNative);
            target.blit(args.arg(1).checkjstring(), args.arg(2).checkjstring(), args.arg(3).checkjstring());
            return LuaValue.NIL;
        }));
        table.set("clear", fn(args -> {
            if (mirrorNative) {
                markNativeSpatial(true);
                appendNativeOutput("\f");
            }
            target.clear();
            return LuaValue.NIL;
        }));
        table.set("clearLine", fn(args -> {
            markNativeSpatial(mirrorNative);
            target.clearLine();
            return LuaValue.NIL;
        }));
        table.set("scroll", fn(args -> {
            markNativeSpatial(mirrorNative);
            target.scroll(args.arg1().checkint());
            return LuaValue.NIL;
        }));
        table.set("getSize", fn(args -> LuaValue.varargsOf(
                LuaValue.valueOf(target.getWidth()), LuaValue.valueOf(target.getHeight()))));
        table.set("setCursorPos", fn(args -> {
            markNativeSpatial(mirrorNative);
            target.setCursorPos(args.arg(1).checkint() - 1, args.arg(2).checkint() - 1);
            return LuaValue.NIL;
        }));
        table.set("getCursorPos", fn(args -> LuaValue.varargsOf(
                LuaValue.valueOf(target.getCursorX() + 1), LuaValue.valueOf(target.getCursorY() + 1))));
        table.set("setCursorBlink", fn(args -> {
            markNativeSpatial(mirrorNative);
            target.setCursorBlink(args.arg1().optboolean(false));
            return LuaValue.NIL;
        }));
        table.set("getCursorBlink", fn(args -> LuaValue.valueOf(target.getCursorBlink())));
        table.set("setTextColor", fn(args -> {
            target.setTextColor(terminalColor(args.arg1()));
            return LuaValue.NIL;
        }));
        table.set("getTextColor", fn(args -> LuaValue.valueOf(1 << target.getTextColor())));
        table.set("setBackgroundColor", fn(args -> {
            target.setBackgroundColor(terminalColor(args.arg1()));
            return LuaValue.NIL;
        }));
        table.set("getBackgroundColor", fn(args -> LuaValue.valueOf(1 << target.getBackgroundColor())));
        table.set("isColor", fn(args -> LuaValue.valueOf(target.isColor())));
        table.set("__getCell", fn(args -> {
            int x = args.arg(1).checkint() - 1;
            int y = args.arg(2).checkint() - 1;
            return LuaValue.varargsOf(new LuaValue[] {
                    LuaValue.valueOf(String.valueOf(target.getChar(x, y))),
                    LuaValue.valueOf(1 << target.getCellTextColor(x, y)),
                    LuaValue.valueOf(1 << target.getCellBackgroundColor(x, y)) });
        }));
        table.set("__setCell", fn(args -> {
            markNativeSpatial(mirrorNative);
            String character = args.arg(3).checkjstring();
            if (character.length() != 1) {
                throw new LuaError("terminal cell must contain exactly one character");
            }
            target.setCell(args.arg(1).checkint() - 1, args.arg(2).checkint() - 1,
                    character.charAt(0), terminalColor(args.arg(4)), terminalColor(args.arg(5)));
            return LuaValue.NIL;
        }));
        return table;
    }

    private static int terminalColor(LuaValue value) {
        if (value.isnumber()) {
            int color = value.checkint();
            if (color > 0 && color <= 32768 && (color & (color - 1)) == 0) {
                return Integer.numberOfTrailingZeros(color);
            }
            throw new LuaError("terminal color must be one power of two between 1 and 32768");
        }
        return checkColor(value);
    }

    private Varargs invokeCurrent(String method, Varargs args) {
        return currentTerminal.get(method).invoke(args);
    }

    private void appendNativeOutput(String text) {
        synchronized (output) {
            output.append(text);
        }
    }

    public TerminalSurface getNativeSurface() {
        return nativeSurface;
    }

    public boolean isNativeSpatial() {
        return nativeSpatial;
    }

    private void markNativeSpatial(boolean nativeTarget) {
        if (nativeTarget) {
            nativeSpatial = true;
        }
    }

    public void appendSystemMessage(String message) {
        nativeSurface.write(message + "\n");
    }

    private void installPrint(Globals g) {
        g.set("print", fn(args -> {
            StringBuilder line = new StringBuilder();
            for (int i = 1; i <= args.narg(); i++) {
                if (i > 1) {
                    line.append('\t');
                }
                line.append(args.arg(i).tojstring());
            }
            line.append('\n');
            return invokeCurrent("write", LuaValue.valueOf(line.toString()));
        }));
        g.set("rawwrite", fn(args -> {
            StringBuilder text = new StringBuilder();
            for (int i = 1; i <= args.narg(); i++) {
                text.append(args.arg(i).tojstring());
            }
            appendNativeOutput(text.toString());
            nativeSurface.write(text.toString());
            return LuaValue.NIL;
        }));
    }

    private void installTaleApi(Globals g) {
        LuaTable tale = new LuaTable();
        tale.set("tr", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(me.kvothe.taleos.I18n.t(args.arg1().checkjstring()));
            }
        });
        tale.set("language", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(me.kvothe.taleos.I18n.language());
            }
        });
        tale.set("pullEvent", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                // The optional filter is visible to parallel when this yields
                // inside a child coroutine; the machine itself only needs EVENTWAIT.
                Varargs result = g.yield(LuaValue.varargsOf(EVENTWAIT, args.arg1()));
                throwTerminateIfRequested();
                return result;
            }
        });
        tale.set("getComputerID", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(computerId);
            }
        });
        tale.set("setPrompt", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                prompt = args.arg1().tojstring();
                return LuaValue.NIL;
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
        // World API v1: each call yields a marker that tick() executes on the
        // tick thread, resuming with the result (CC:T main-thread pattern).
        tale.set("getBlock", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Varargs result = g.yield(LuaValue.varargsOf(new LuaValue[] {
                        WORLDCALL, LuaValue.valueOf("getBlock"),
                        LuaValue.valueOf(args.arg(1).optint(0)),
                        LuaValue.valueOf(args.arg(2).optint(-1)),
                        LuaValue.valueOf(args.arg(3).optint(0)) }));
                throwTerminateIfRequested();
                return result;
            }
        });
        tale.set("detect", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Varargs result = g.yield(LuaValue.varargsOf(new LuaValue[] {
                        WORLDCALL, LuaValue.valueOf("detect"),
                        LuaValue.valueOf(args.arg(1).optint(0)),
                        LuaValue.valueOf(args.arg(2).optint(-1)),
                        LuaValue.valueOf(args.arg(3).optint(0)) }));
                throwTerminateIfRequested();
                return result;
            }
        });
        tale.set("chat", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Varargs result = g.yield(LuaValue.varargsOf(new LuaValue[] {
                        WORLDCALL, LuaValue.valueOf("chat"),
                        LuaValue.valueOf(args.arg(1).tojstring()) }));
                throwTerminateIfRequested();
                return result;
            }
        });
        tale.set("time", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Varargs result = g.yield(LuaValue.varargsOf(new LuaValue[] {
                        WORLDCALL, LuaValue.valueOf("time") }));
                throwTerminateIfRequested();
                return result;
            }
        });
        tale.set("sleep", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                // Parks the machine for N ticks (min 1), then resumes with nil.
                Varargs result = g.yield(LuaValue.varargsOf(new LuaValue[] {
                        SLEEPCALL, LuaValue.valueOf(args.arg(1).optint(1)) }));
                throwTerminateIfRequested();
                return result;
            }
        });
        // Internal (TaleOS): marks whether a program is running inside the
        // shell's pcall; terminate requests only fire while true.
        tale.set("setProgramRunning", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                programRunning = args.arg1().optboolean(false);
                return LuaValue.NIL;
            }
        });
        // tale.monitor([dx,dy,dz]) → drawable canvas table of the monitor block
        // (no args = search the 6 face neighbours), nil when there is none.
        tale.set("monitor", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                Varargs result = g.yield(LuaValue.varargsOf(new LuaValue[] {
                        WORLDCALL, LuaValue.valueOf("monitor"),
                        LuaValue.valueOf(args.arg(1).optint(0)),
                        LuaValue.valueOf(args.arg(2).optint(0)),
                        LuaValue.valueOf(args.arg(3).optint(0)) }));
                throwTerminateIfRequested();
                return result;
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

    /** Requests termination of the running program: a catchable Lua error
     * (TERMINATED_MESSAGE) is raised inside it — at the next yield return, or
     * within a handful of bytecodes via the budget hook in tight loops. The
     * shell's pcall catches it, so the program dies but the machine survives
     * (CC:T's "Terminated"). Only fires while a program runs. */
    public void requestTerminate() {
        terminateRequested = true;
        cancelPendingHttp();
    }

    public boolean isProgramRunning() {
        return programRunning;
    }

    /** Raises the termination as a LuaError wrapping a plain Lua string — the
     * LuaValue constructor skips the position prefix and traceback that the
     * string constructor (and Lua's own error()) would add, so pcall receives
     * exactly TERMINATED_MESSAGE. Called from the budget hook and right after
     * every yield returns (a sleeping program runs too few bytecodes per
     * resume for the hook to ever fire, so the yield points must check too). */
    private void throwTerminateIfRequested() {
        if (terminateRequested && programRunning) {
            terminateRequested = false;
            throw new LuaError(LuaValue.valueOf(me.kvothe.taleos.I18n.t(TERMINATED_MESSAGE)));
        }
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
                    + "end\n", "taleos:wrap").call(co);
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
     * Count hook fired by the interpreter every HOOK_INTERVAL bytecodes. A
     * requested termination throws a catchable LuaError here (the shell's
     * pcall around the program catches it); on budget exhaustion it throws
     * the pcall-proof {@link BudgetExceededException} and ratchets the hook
     * interval down to 1 on the running coroutine, so any error-catcher that
     * swallows the kill gets a fresh one on the very next bytecode — no Lua
     * construct (pcall, xpcall handlers, child coroutines) can keep the
     * machine alive past its budget.
     */
    private final class BudgetHook extends VarArgFunction {
        private long remaining;

        void reset(long fires) {
            this.remaining = fires;
        }

        @Override
        public Varargs invoke(Varargs args) {
            throwTerminateIfRequested();
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
