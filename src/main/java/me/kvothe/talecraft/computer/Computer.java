package me.kvothe.talecraft.computer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import me.kvothe.talecraft.lua.LuaMachine;
import me.kvothe.talecraft.lua.WorldBridge;
import me.kvothe.talecraft.os.VirtualFileSystem;

/**
 * One in-world computer: a LuaMachine running TaleOS over a jailed,
 * quota-bounded filesystem rooted at {@code mods/TaleCraft/computers/<id>/}.
 * Computers boot on first use and halt on server shutdown; their files
 * persist on disk, their running state does not (same as CC:Tweaked).
 */
public final class Computer {

    /** Instruction budget per server tick (Lua bytecodes). */
    static final long BUDGET_PER_TICK = 200_000;
    /** Per-computer disk quota (CC:T defaults to 1 MB as well). */
    static final long DISK_QUOTA = 1_000_000;

    /** TaleOS files shipped in the plugin jar under /lua/rom/. */
    private static final String[] ROM_FILES = {
            "bios.lua", "shell.lua",
            "programs/id.lua", "programs/ls.lua", "programs/cat.lua",
            "programs/mkdir.lua", "programs/rm.lua", "programs/echo.lua",
            "programs/reboot.lua", "programs/shutdown.lua",
            "programs/lua.lua", "programs/edit.lua",
            "programs/clear.lua", "programs/help.lua",
    };

    private final int id;
    private final LuaMachine machine;
    private final Path root;
    private final StringBuilder scrollback = new StringBuilder();
    private int scrollbackReadPos;
    /** Per-computer transcript log (mods/TaleCraft/logs/computer-<id>.log). */
    private BufferedWriter log;
    private LuaMachine.State lastState = LuaMachine.State.ALIVE;

    private static final int SCROLLBACK_MAX_CHARS = 8000;

    public Computer(int id, Path root, WorldBridge bridge) throws IOException {
        this.id = id;
        this.root = root;
        VirtualFileSystem fs = new VirtualFileSystem(root, DISK_QUOTA);
        fs.mountRom(loadRomOverlay());
        this.machine = new LuaMachine(BUDGET_PER_TICK, fs);
        this.machine.setComputerId(id);
        this.machine.setWorldBridge(bridge);
    }

    /** Loads bios and runs the first tick (boot to the TaleOS prompt). */
    public void boot() {
        try {
            Path logDir = root.getParent().getParent().resolve("logs");
            Files.createDirectories(logDir);
            log = Files.newBufferedWriter(logDir.resolve("computer-" + id + ".log"),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logLine("[BOOT] computer #" + id);
        } catch (IOException e) {
            System.err.println("[TaleCraft] cannot open log for computer " + id + ": " + e.getMessage());
        }
        machine.load(romOverlayCache().get("rom/bios.lua"), "taleos:bios");
        machine.tick();
        pullMachineOutput();
    }

    public void tick() {
        if (machine.isAlive()) {
            machine.tick();
        }
        pullMachineOutput();
        LuaMachine.State now = machine.getState();
        if (now != lastState) {
            lastState = now;
            if (now == LuaMachine.State.DEAD) {
                String msg = "\n[máquina detenida: " + machine.getError() + "]\n";
                machine.appendEcho(msg);
                pullMachineOutput();
                logLine("[DEAD] " + machine.getError());
            } else {
                logLine("[" + now + "]");
            }
            closeLog();
        }
    }

    /** Moves fresh machine output into the scrollback. A form-feed (\f) control
     * line (term.clear()) wipes the scrollback instead of being appended;
     * anything before the last \f in the batch was on screen when the clear
     * ran, so it is dropped as well. */
    private void pullMachineOutput() {
        String out = machine.drainOutput();
        if (out.isEmpty()) {
            return;
        }
        int clearAt = out.lastIndexOf("\f");
        if (clearAt >= 0) {
            synchronized (scrollback) {
                scrollback.setLength(0);
                scrollbackReadPos = 0;
            }
            logLine("[clear]");
            out = out.substring(clearAt + 1);
            if (out.startsWith("\n")) {
                out = out.substring(1);
            }
            if (out.isEmpty()) {
                return;
            }
        }
        logLine(out);
        synchronized (scrollback) {
            scrollback.append(out);
            if (scrollback.length() > SCROLLBACK_MAX_CHARS) {
                int trimmed = scrollback.length() - SCROLLBACK_MAX_CHARS;
                scrollback.delete(0, trimmed);
                scrollbackReadPos = Math.max(0, scrollbackReadPos - trimmed);
            }
        }
    }

    /** Output produced since the previous call to this method. */
    public String drainOutput() {
        synchronized (scrollback) {
            String out = scrollback.substring(scrollbackReadPos);
            scrollbackReadPos = scrollback.length();
            return out;
        }
    }

    /** Recent terminal output (capped), for pages that open mid-session. */
    public String getScrollback() {
        synchronized (scrollback) {
            return scrollback.toString();
        }
    }

    public void queueStdin(String line) {
        // Eco estilo terminal: la línea enviada aparece tras el prompt actual.
        machine.appendEcho(machine.getPrompt() + line + "\n");
        logLine("[stdin] " + line);
        machine.queueEvent("stdin", line);
    }

    public String getPrompt() {
        return machine.getPrompt();
    }

    private void logLine(String s) {
        BufferedWriter w = log;
        if (w == null) {
            return;
        }
        try {
            w.write(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME));
            w.write(' ');
            w.write(s);
            if (!s.endsWith("\n")) {
                w.newLine();
            }
            w.flush();
        } catch (IOException ignored) {
        }
    }

    private void closeLog() {
        if (log != null) {
            try {
                log.close();
            } catch (IOException ignored) {
            }
            log = null;
        }
    }

    public LuaMachine getMachine() {
        return machine;
    }

    public int getId() {
        return id;
    }

    public boolean isAlive() {
        return machine.isAlive();
    }

    /** True after a reboot/shutdown request: the caller drops this computer
     * (and, for reboot, boots a fresh one over the same id/disk). */
    public boolean isHalted() {
        return machine.getState() == LuaMachine.State.DONE
                || machine.getState() == LuaMachine.State.DEAD;
    }

    public boolean wantsReboot() {
        return machine.isRebootRequested();
    }

    /** Rom overlay is identical for every computer: load once, share. */
    private static Map<String, String> romOverlayCache() {
        Map<String, String> cached = ROM_OVERLAY;
        if (cached == null) {
            cached = loadRomOverlayUncached();
            ROM_OVERLAY = cached;
        }
        return cached;
    }

    private static volatile Map<String, String> ROM_OVERLAY;

    private static Map<String, String> loadRomOverlayUncached() {
        Map<String, String> overlay = new HashMap<>();
        for (String name : ROM_FILES) {
            String resource = "/lua/rom/" + name;
            try (InputStream in = Computer.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("missing rom resource " + resource);
                }
                overlay.put("rom/" + name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("cannot read rom resource " + resource, e);
            }
        }
        return overlay;
    }

    private Map<String, String> loadRomOverlay() {
        return romOverlayCache();
    }
}
