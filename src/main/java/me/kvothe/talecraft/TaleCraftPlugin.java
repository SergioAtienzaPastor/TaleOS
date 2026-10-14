package me.kvothe.talecraft;

import java.nio.file.Path;

import javax.annotation.Nonnull;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import me.kvothe.talecraft.computer.Computer;
import me.kvothe.talecraft.computer.ComputerManager;
import me.kvothe.talecraft.computer.ComputerTickSystem;
import me.kvothe.talecraft.computer.MonitorPageSupplier;
import me.kvothe.talecraft.computer.TerminalPageSupplier;
import me.kvothe.talecraft.lua.LuaMachine;

/**
 * TaleCraft: programmable Lua computers for Hytale. Entrypoint of the plugin;
 * registers blocks, commands and event listeners from {@link #setup()}.
 */
public class TaleCraftPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ComputerManager computerManager;

    public TaleCraftPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("TaleCraft iniciado, version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());
        computerManager = new ComputerManager(Path.of("mods", "TaleCraft"));
        computerManager.load();
        getEntityStoreRegistry().registerSystem(new ComputerTickSystem(computerManager));
        // Block "Use" interactions reference this page supplier by name.
        OpenCustomUIInteraction.registerCustomPageSupplier(this, TerminalPageSupplier.class,
                "TaleCraftComputer", new TerminalPageSupplier(computerManager));
        OpenCustomUIInteraction.registerCustomPageSupplier(this, MonitorPageSupplier.class,
                "TaleCraftMonitor", new MonitorPageSupplier(computerManager));
        this.getCommandRegistry().registerCommand(new TaleCraftCommand(this.getName(),
                this.getManifest().getVersion().toString()));
        if (Boolean.parseBoolean(System.getProperty("talecraft.selftest", "false"))) {
            runSelfTest();
        }
    }

    /**
     * Headless smoke test enabled with -Dtalecraft.selftest=true: boots a
     * computer without world context, drives the shell over stdin events and
     * verifies an infinite loop dies by budget without harming the server.
     */
    private void runSelfTest() {
        try {
            Computer c = computerManager.bootAt(null, new BlockPosition(0, 64, 0));
            String boot = c.drainOutput();
            boolean bootOk = boot.contains("TaleOS v0.1") && boot.contains("tale>");
            LOGGER.atInfo().log("[selftest] boot to TaleOS prompt: " + bootOk);
            c.queueStdin("id");
            c.tick();
            LOGGER.atInfo().log("[selftest] id command output: " + c.drainOutput().trim());
            LuaMachine rogue = new LuaMachine(100_000);
            rogue.load("while true do end", "rogue");
            rogue.tick();
            LOGGER.atInfo().log("[selftest] infinite-loop machine state (expected DEAD): " + rogue.getState());
            LOGGER.atInfo().log("[selftest] server still alive after the budget kill");
        } catch (Throwable t) {
            LOGGER.atSevere().log("[selftest] FAILED: " + t);
        }
    }

    @Override
    public void shutdown() {
        if (computerManager != null) {
            computerManager.shutdown();
        }
    }
}
