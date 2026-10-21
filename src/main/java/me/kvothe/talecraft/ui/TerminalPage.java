package me.kvothe.talecraft.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import me.kvothe.talecraft.computer.Computer;
import me.kvothe.talecraft.computer.ComputerManager;

/**
 * Terminal page of a computer: shows the TaleOS transcript in a scrollable
 * read-only field and a single-line input that feeds the shell's stdin events.
 * Enter submits. Live output is pushed from the tick system via
 * {@link #refreshFromComputer()}.
 *
 * The client's text field jumps back to its top whenever the value is set (the
 * UI protocol has no scroll command), so the transcript is rendered with the
 * newest block first (see {@link TranscriptView}): the latest command and its
 * output are always visible at the top, right under the input row, and
 * scrolling down reveals older history.
 */
public class TerminalPage extends InteractiveCustomUIPage<TerminalPage.TerminalEventData> {

    /** Layout path relative to Common/UI/Custom/. */
    public static final String LAYOUT = "TaleCraft/Terminal.ui";

    private static String reversed(String scrollback) {
        return TranscriptView.newestFirst(scrollback);
    }

    private final PlayerRef playerRef;
    private final ComputerManager manager;
    private final Computer computer;
    private final boolean readOnly;
    private String lastShown = "";
    private String lastPrompt = "";

    public TerminalPage(@Nonnull PlayerRef playerRef, ComputerManager manager, Computer computer) {
        this(playerRef, manager, computer, false);
    }

    public TerminalPage(@Nonnull PlayerRef playerRef, ComputerManager manager, Computer computer,
            boolean readOnly) {
        super(playerRef, CustomPageLifetime.CanDismiss, TerminalEventData.CODEC);
        this.playerRef = playerRef;
        this.manager = manager;
        this.computer = computer;
        this.readOnly = readOnly;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        lastShown = computer.getScrollback();
        lastPrompt = computer.getPrompt();
        cmd.set("#Title.Text", (readOnly ? "Monitor — Computer #" : "Computer #") + computer.getId());
        cmd.set("#Output.Value", reversed(lastShown));
        cmd.set("#Prompt.Text", lastPrompt);
        if (readOnly) {
            cmd.set("#InputRow.Visible", false);
        } else {
            // Enter in the input field submits the line; "@Line" captures its value.
            evt.addEventBinding(CustomUIEventBindingType.Validating, "#Input",
                    new EventData().append("Action", "input").append("@Line", "#Input.Value"), false);
        }
        evt.addEventBinding(CustomUIEventBindingType.Dismissing, "#Terminal",
                new EventData().append("Action", "close"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull TerminalEventData data) {
        if ("input".equals(data.action)) {
            if (data.line != null && !data.line.isBlank()) {
                computer.queueStdin(data.line.trim());
            }
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#Input.Value", "");
            sendUpdate(cmd, false);
        } else if ("close".equals(data.action)) {
            manager.unregisterPage(playerRef.getUuid());
        }
    }

    /** Pushes fresh computer output and prompt to the client when they changed.
     * Called once per tick by the plugin's tick system. */
    public void refreshFromComputer() {
        String current = computer.getScrollback();
        String prompt = computer.getPrompt();
        if (current.equals(lastShown) && prompt.equals(lastPrompt)) {
            return;
        }
        lastShown = current;
        lastPrompt = prompt;
        try {
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#Output.Value", reversed(current));
            cmd.set("#Prompt.Text", prompt);
            sendUpdate(cmd, false);
        } catch (Exception e) {
            // page was closed or the client went away: stop tracking it
            manager.unregisterPage(playerRef.getUuid());
        }
    }

    public static class TerminalEventData {
        public static final BuilderCodec<TerminalEventData> CODEC = BuilderCodec
                .builder(TerminalEventData.class, TerminalEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action)
                .add()
                .append(new KeyedCodec<>("@Line", Codec.STRING), (e, v) -> e.line = v, e -> e.line)
                .add()
                .build();

        private String action;
        private String line;

        public TerminalEventData() {
        }
    }
}
