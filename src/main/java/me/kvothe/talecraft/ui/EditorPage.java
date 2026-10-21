package me.kvothe.talecraft.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.talecraft.computer.Computer;
import me.kvothe.talecraft.computer.ComputerManager;

/**
 * File editor page opened by os.openEditor (the TaleOS `edit` program): an
 * editable multiline field with Guardar / Guardar+Ejecutar / Salir. Any exit
 * path reopens the computer's terminal page. Files longer than
 * {@link #MAX_CONTENT_CHARS} are refused whole rather than truncated silently.
 */
public class EditorPage extends InteractiveCustomUIPage<EditorPage.EditorEventData> {

    /** Layout path relative to Common/UI/Custom/. */
    public static final String LAYOUT = "TaleCraft/Editor.ui";

    /** Field and packet friendly content cap. */
    private static final int MAX_CONTENT_CHARS = 16_000;

    private final PlayerRef playerRef;
    private final ComputerManager manager;
    private final Computer computer;
    private final String path;

    public EditorPage(@Nonnull PlayerRef playerRef, ComputerManager manager, Computer computer,
            String path) {
        super(playerRef, CustomPageLifetime.CanDismiss, EditorEventData.CODEC);
        this.playerRef = playerRef;
        this.manager = manager;
        this.computer = computer;
        this.path = path;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        cmd.set("#Title.Text", "Editor — " + path + "  ·  Computer #" + computer.getId());
        String content = null;
        try {
            content = computer.getFileSystem().read(path);
        } catch (Exception e) {
            cmd.set("#Status.Text", "no se pudo leer: " + e.getMessage());
        }
        if (content != null && content.length() > MAX_CONTENT_CHARS) {
            cmd.set("#Status.Text", "archivo demasiado grande para el editor ("
                    + content.length() + " caracteres)");
            content = null;
        }
        cmd.set("#Content.Value", content == null ? "" : content);
        // Each button carries the current field content back with the event.
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#Save",
                new EventData().append("Action", "save").append("@Content", "#Content.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#Run",
                new EventData().append("Action", "run").append("@Content", "#Content.Value"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#Exit",
                new EventData().append("Action", "close"), false);
        evt.addEventBinding(CustomUIEventBindingType.Dismissing, "#Editor",
                new EventData().append("Action", "close"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull EditorEventData data) {
        if ("save".equals(data.action) || "run".equals(data.action)) {
            String content = data.content == null ? "" : data.content;
            if (content.length() > MAX_CONTENT_CHARS) {
                content = content.substring(0, MAX_CONTENT_CHARS);
            }
            try {
                computer.getFileSystem().write(path, content);
                computer.systemMessage("edit: guardado " + path
                        + " (" + content.length() + " caracteres)");
            } catch (Exception e) {
                computer.systemMessage("edit: no se pudo guardar " + path + ": " + e.getMessage());
            }
            backToTerminal(ref, store);
            if ("run".equals(data.action)) {
                computer.queueStdin(runCommandFor(path), playerRef.getUuid());
            }
        } else if ("close".equals(data.action)) {
            backToTerminal(ref, store);
        }
    }

    /** Reopens this computer's terminal in place of the editor. */
    private void backToTerminal(Ref<EntityStore> ref, Store<EntityStore> store) {
        TerminalPage page = new TerminalPage(playerRef, manager, computer);
        manager.registerPage(playerRef.getUuid(), page);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().openCustomPage(ref, store, page);
        }
    }

    /** Shell command that runs the edited file: its name without .lua. */
    static String runCommandFor(String path) {
        String name = path;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".lua")) {
            name = name.substring(0, name.length() - 4);
        }
        return name;
    }

    public static class EditorEventData {
        public static final BuilderCodec<EditorEventData> CODEC = BuilderCodec
                .builder(EditorEventData.class, EditorEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action)
                .add()
                .append(new KeyedCodec<>("@Content", Codec.STRING), (e, v) -> e.content = v,
                        e -> e.content)
                .add()
                .build();

        private String action;
        private String content;

        public EditorEventData() {
        }
    }
}
