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
 * Terminal page of a computer: shows the TaleOS transcript (scrollback label)
 * and a single-line input that feeds the shell's stdin events. Enter submits.
 * Live output is pushed from the tick system via {@link #refreshFromComputer()}.
 */
public class TerminalPage extends InteractiveCustomUIPage<TerminalPage.TerminalEventData> {

    /** Layout path relative to Common/UI/Custom/. */
    public static final String LAYOUT = "TaleCraft/Terminal.ui";

    /** The UI has no scroll-to-bottom command, so the output field shows the
     * tail of the transcript, small enough to always fit the screen — like a
     * real console, the latest lines are always visible without scrolling.
     * Full history stays in the computer's log file. */
    private static final int MAX_VISIBLE_LINES = 14;

    /** Last ~screenful of the transcript. */
    static String tail(String s) {
        int lines = 0;
        int i = s.length();
        while (i > 0) {
            int nl = s.lastIndexOf('\n', i - 1);
            if (nl < 0) {
                break;
            }
            if (++lines > MAX_VISIBLE_LINES) {
                return s.substring(nl + 1);
            }
            i = nl;
        }
        return s;
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
        cmd.set("#Output.Value", tail(lastShown));
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
            cmd.set("#Output.Value", tail(current));
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
