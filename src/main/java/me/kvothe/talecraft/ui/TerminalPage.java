package me.kvothe.talecraft.ui;

import java.util.ArrayList;
import java.util.List;

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
 * Terminal page of a computer: shows the TaleOS transcript and a single-line
 * input that feeds the shell's stdin events. Enter submits.
 * Live output is pushed from the tick system via {@link #refreshFromComputer()}.
 *
 * The UI has no scroll-to-bottom command, so the output label has no scroll at
 * all: it shows a window of the transcript sized to always fit the screen. The
 * Subir/Bajar pager buttons move that window through the history; back at the
 * bottom the view follows live output again, like a real console. The full
 * history stays in the computer's log file.
 */
public class TerminalPage extends InteractiveCustomUIPage<TerminalPage.TerminalEventData> {

    /** Layout path relative to Common/UI/Custom/. */
    public static final String LAYOUT = "TaleCraft/Terminal.ui";

    /** Window height in lines: what fits the screen without scrolling. */
    private static final int MAX_VISIBLE_LINES = 14;

    /** Lines jumped per pager button press. */
    private static final int PAGE_STEP = MAX_VISIBLE_LINES;

    /** Window of the transcript ending OFFSET lines before its end
     * (0 = live tail). Never more than maxLines lines. */
    static String window(String s, int offset, int maxLines) {
        List<String> lines = splitLines(s);
        int end = Math.max(0, lines.size() - Math.min(offset, lines.size()));
        int start = Math.max(0, end - maxLines);
        return String.join("\n", lines.subList(start, end));
    }

    static int maxOffset(String s, int maxLines) {
        return Math.max(0, lineCount(s) - maxLines);
    }

    static int lineCount(String s) {
        return splitLines(s).size();
    }

    private static List<String> splitLines(String s) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        if (start < s.length()) {
            out.add(s.substring(start));
        }
        return out;
    }

    private final PlayerRef playerRef;
    private final ComputerManager manager;
    private final Computer computer;
    private final boolean readOnly;

    /** Lines from the transcript end; 0 = following live output. */
    private int scrollOffset = 0;

    private String lastShown = "";
    private String lastSentView;
    private String lastSentPrompt = "";
    private String lastSentTitle = "";
    private String lastSentHint = "";

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
        if (readOnly) {
            cmd.set("#InputRow.Visible", false);
        } else {
            // Enter in the input field submits the line; "@Line" captures its value.
            evt.addEventBinding(CustomUIEventBindingType.Validating, "#Input",
                    new EventData().append("Action", "input").append("@Line", "#Input.Value"), false);
        }
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PageUp",
                new EventData().append("Action", "pageUp"), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PageDown",
                new EventData().append("Action", "pageDown"), false);
        evt.addEventBinding(CustomUIEventBindingType.Dismissing, "#Terminal",
                new EventData().append("Action", "close"), false);
        pushView(cmd);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull TerminalEventData data) {
        if ("input".equals(data.action)) {
            if (data.line != null && !data.line.isBlank()) {
                computer.queueStdin(data.line.trim());
            }
            // Typing a command snaps the view back to the live tail.
            scrollOffset = 0;
            lastShown = computer.getScrollback();
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#Input.Value", "");
            pushView(cmd);
            sendUpdate(cmd, false);
        } else if ("pageUp".equals(data.action)) {
            scrollOffset = Math.min(scrollOffset + PAGE_STEP, maxOffset(lastShown, MAX_VISIBLE_LINES));
            UICommandBuilder cmd = new UICommandBuilder();
            if (pushView(cmd)) {
                sendUpdate(cmd, false);
            }
        } else if ("pageDown".equals(data.action)) {
            scrollOffset = Math.max(0, scrollOffset - PAGE_STEP);
            UICommandBuilder cmd = new UICommandBuilder();
            if (pushView(cmd)) {
                sendUpdate(cmd, false);
            }
        } else if ("close".equals(data.action)) {
            manager.unregisterPage(playerRef.getUuid());
        }
    }

    /** Pushes fresh computer output and prompt to the client when they changed.
     * Called once per tick by the plugin's tick system. */
    public void refreshFromComputer() {
        String current = computer.getScrollback();
        String prompt = computer.getPrompt();
        if (current.equals(lastShown) && prompt.equals(lastSentPrompt)) {
            return;
        }
        if (scrollOffset > 0) {
            // Keep the same content in view while scrolled up: lines appended
            // meanwhile push the window's end further from the tail.
            int added = lineCount(current) - lineCount(lastShown);
            if (added > 0) {
                scrollOffset += added;
            }
        }
        lastShown = current;
        scrollOffset = Math.min(scrollOffset, maxOffset(current, MAX_VISIBLE_LINES));
        try {
            UICommandBuilder cmd = new UICommandBuilder();
            if (pushView(cmd)) {
                sendUpdate(cmd, false);
            }
        } catch (Exception e) {
            // page was closed or the client went away: stop tracking it
            manager.unregisterPage(playerRef.getUuid());
        }
    }

    /** Writes the current window/prompt/title into the builder if they changed
     * since the last push. Returns true when anything was written. */
    private boolean pushView(UICommandBuilder cmd) {
        String view = window(lastShown, scrollOffset, MAX_VISIBLE_LINES);
        String prompt = computer.getPrompt();
        String title = (readOnly ? "Monitor — Computer #" : "Computer #") + computer.getId();
        String hint = scrollOffset > 0 ? "historial — pulsa Bajar hasta el final para seguir en directo" : "";
        if (view.equals(lastSentView) && prompt.equals(lastSentPrompt)
                && title.equals(lastSentTitle) && hint.equals(lastSentHint)) {
            return false;
        }
        cmd.set("#Output.Text", view);
        cmd.set("#Prompt.Text", prompt);
        cmd.set("#Title.Text", title);
        cmd.set("#ScrollHint.Text", hint);
        lastSentView = view;
        lastSentPrompt = prompt;
        lastSentTitle = title;
        lastSentHint = hint;
        return true;
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
