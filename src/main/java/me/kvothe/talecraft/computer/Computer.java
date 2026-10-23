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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
            "programs/clear.lua", "programs/help.lua", "programs/reloj.lua",
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

    /** Control char prepended to echo lines in the scrollback: it marks the
     * start of a transcript block so the terminal page can render the blocks
     * newest-first. Never displayed; stripped from the log and drainOutput. */
    public static final char ECHO_MARK = '\u0001';

    /** Control char starting an editor-request control line in the machine
     * output (os.openEditor). The rest of the line is the file path. Never
     * displayed nor logged. */
    public static final char EDITOR_MARK = '\u0002';

    /** Opens the editor UI for a computer file; set by ComputerManager. */
    public interface EditorOpener {
        void openEditor(Computer computer, String path, UUID requestedBy);
    }

    private volatile EditorOpener editorOpener;
    private UUID lastStdinPlayer;
    private final List<String> editorRequests = new ArrayList<>();

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
        drainEditorRequests();
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
        // Editor-request control lines (EDITOR_MARK + path + \n) never reach
        // the transcript; they are queued for the next tick.
        if (out.indexOf(EDITOR_MARK) >= 0) {
            StringBuilder visible = new StringBuilder(out.length());
            int i = 0;
            while (i < out.length()) {
                int mark = out.indexOf(EDITOR_MARK, i);
                if (mark < 0) {
                    visible.append(out.substring(i));
                    break;
                }
                visible.append(out, i, mark);
                int nl = out.indexOf('\n', mark);
                if (nl < 0) {
                    break; // truncated control line: drop it
                }
                editorRequests.add(out.substring(mark + 1, nl).trim());
                i = nl + 1;
            }
            out = visible.toString();
            if (out.isEmpty()) {
                return;
            }
        }
        logLine(out.replace(ECHO_MARK + "", ""));
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
            return out.replace(ECHO_MARK + "", "");
        }
    }

    /** Recent terminal output (capped), for pages that open mid-session. */
    public String getScrollback() {
        synchronized (scrollback) {
            return scrollback.toString();
        }
    }

    public void queueStdin(String line) {
        queueStdin(line, null);
    }

    /** Queues a shell input line; the player uuid identifies who typed it so
     * os.openEditor knows which terminal to open the editor in (null = server
     * console). */
    public void queueStdin(String line, UUID player) {
        lastStdinPlayer = player;
        // Eco estilo terminal: la línea enviada aparece tras el prompt actual,
        // precedida de ECHO_MARK para delimitar el bloque en el transcript.
        machine.appendEcho(ECHO_MARK + machine.getPrompt() + line + "\n");
        logLine("[stdin] " + line);
        machine.queueEvent("stdin", line);
    }

    /** Asks the machine to stop the running program (CC:T's Ctrl+T): the
     * program dies with a "Programa detenido" error that the shell's pcall
     * catches, returning to the prompt. With nothing running it just says so. */
    public void requestTerminate() {
        if (!machine.isAlive()) {
            systemMessage("el ordenador está apagado");
            return;
        }
        if (!machine.isProgramRunning()) {
            systemMessage("no hay ningún programa en ejecución");
            return;
        }
        machine.requestTerminate();
    }

    public String getPrompt() {
        return machine.getPrompt();
    }

    public VirtualFileSystem getFileSystem() {
        return machine.getFileSystem();
    }

    public void setEditorOpener(EditorOpener opener) {
        this.editorOpener = opener;
    }

    /** Prints a server-side message into the transcript and the log. */
    public void systemMessage(String s) {
        logLine("[system] " + s);
        synchronized (scrollback) {
            scrollback.append(s).append('\n');
        }
    }

    /** Opens the editor UI for each queued os.openEditor request. */
    private void drainEditorRequests() {
        if (editorRequests.isEmpty()) {
            return;
        }
        List<String> pending = new ArrayList<>(editorRequests);
        editorRequests.clear();
        EditorOpener opener = editorOpener;
        if (opener == null) {
            for (String path : pending) {
                systemMessage("edit: editor no disponible en este contexto (" + path + ")");
            }
            return;
        }
        for (String path : pending) {
            opener.openEditor(this, path, lastStdinPlayer);
        }
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
