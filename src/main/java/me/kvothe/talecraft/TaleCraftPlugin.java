package me.kvothe.talecraft;

import java.nio.file.Path;

import javax.annotation.Nonnull;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import me.kvothe.talecraft.computer.ComputerManager;
import me.kvothe.talecraft.computer.ComputerTickSystem;
import me.kvothe.talecraft.computer.TerminalPageSupplier;

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
        this.getCommandRegistry().registerCommand(new TaleCraftCommand(this.getName(),
                this.getManifest().getVersion().toString()));
    }

    @Override
    public void shutdown() {
        if (computerManager != null) {
            computerManager.shutdown();
        }
    }
}
