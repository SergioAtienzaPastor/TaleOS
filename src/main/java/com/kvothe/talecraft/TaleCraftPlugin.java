package com.kvothe.talecraft;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;

/**
 * TaleCraft: programmable Lua computers for Hytale. Entrypoint of the plugin;
 * registers blocks, commands and event listeners from {@link #setup()}.
 */
public class TaleCraftPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public TaleCraftPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("TaleCraft iniciado, version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());
        this.getCommandRegistry().registerCommand(new TaleCraftCommand(this.getName(), this.getManifest().getVersion().toString()));
    }
}
