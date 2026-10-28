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
import me.kvothe.talecraft.ui.completion.CompletionSession;
import me.kvothe.talecraft.ui.completion.ShellCompleter;

/**
 * Shell output keeps the scrollable transcript until a program uses spatial
 * terminal operations; from then on the native grid is projected top-to-bottom.
 */
public class TerminalPage extends InteractiveCustomUIPage<TerminalPage.TerminalEventData> {

    /** Layout path relative to Common/UI/Custom/. */
    public static final String LAYOUT = "TaleCraft/Terminal.ui";

    private String terminalText() {
        return computer.isTerminalSpatial() ? computer.getTerminalText()
                : TranscriptView.newestFirst(computer.getScrollback());
    }

    private final PlayerRef playerRef;
    private final ComputerManager manager;
    private final Computer computer;
    private final boolean readOnly;
    private final ShellCompleter completer;
    private final CompletionSession completion;
    private java.util.List<String> visibleCandidates = java.util.List.of();
    private String lastShown = "";
    private long lastRevision = -1;
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
        this.completer = new ShellCompleter(computer.getFileSystem());
        this.completion = new CompletionSession(completer);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append(layout());
        lastShown = terminalText();
        lastRevision = computer.getTerminalRevision();
        lastPrompt = computer.getPrompt();
        cmd.set("#Title.Text", (readOnly ? "Monitor — Computer #" : "Computer #") + computer.getId());
        cmd.set("#Output.Text", lastShown);
        cmd.set("#Prompt.Text", lastPrompt);
        completion.reset("");
        visibleCandidates = java.util.List.of();
        if (readOnly) {
            cmd.set("#InputRow.Visible", false);
            cmd.set("#CompletionHint.Visible", false);
            cmd.set("#Stop.Visible", false);
        } else {
            // Enter in the input field submits the line; "@Line" captures its value.
            evt.addEventBinding(CustomUIEventBindingType.Validating, "#Input",
                    new EventData().append("Action", "input").append("@Line", "#Input.Value"), false);
            evt.addEventBinding(CustomUIEventBindingType.ValueChanged, "#Input",
                    new EventData().append("Action", "draft").append("@Line", "#Input.Value"), false);
            evt.addEventBinding(CustomUIEventBindingType.KeyDown, "#Input",
                    new EventData().append("Action", "key").append("@Line", "#Input.Value"), false);
            // Detener kills the running program (the shell catches the error
            // and returns to the prompt — CC:T's Ctrl+T).
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#Stop",
                    new EventData().append("Action", "stop"), false);
        }
        evt.addEventBinding(CustomUIEventBindingType.Dismissing, "#Terminal",
                new EventData().append("Action", "close"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull TerminalEventData data) {
        if ("draft".equals(data.action)) {
            completion.valueChanged(data.line);
            me.kvothe.talecraft.ui.completion.Completion suggestions =
                    completer.complete(completion.getValue(), completion.getCaret());
            visibleCandidates = suggestions == null ? java.util.List.of() : suggestions.candidates();
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#CompletionHint.Text", completionHint());
            sendUpdate(cmd, false);
        } else if ("complete".equals(data.action) || "key".equals(data.action)) {
            if ("key".equals(data.action) && data.keycode != 9) {
                return;
            }
            completion.valueChanged(data.line);
            completion.complete();
            visibleCandidates = completion.getCandidates();
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#Input.Value", completion.getValue());
            cmd.set("#Input.AutoFocus", true);
            cmd.set("#CompletionHint.Text", completionHint());
            sendUpdate(cmd, false);
        } else if ("input".equals(data.action)) {
            if (data.line != null && !data.line.isBlank()) {
                computer.queueStdin(data.line.trim(), playerRef.getUuid());
            }
            completion.reset("");
            visibleCandidates = java.util.List.of();
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#Input.Value", "");
            cmd.set("#CompletionHint.Text", completionHint());
            sendUpdate(cmd, false);
        } else if ("stop".equals(data.action)) {
            computer.requestTerminate();
        } else if ("close".equals(data.action)) {
            manager.unregisterPage(playerRef.getUuid());
        }
    }

    private String completionHint() {
        if (visibleCandidates.isEmpty()) {
            return "Escribe para ver sugerencias";
        }
        String candidates = String.join("  ", visibleCandidates);
        return candidates.length() > 90 ? candidates.substring(0, 89) + "…" : candidates;
    }

    protected String layout() {
        return LAYOUT;
    }

    public Computer getComputer() {
        return computer;
    }

    /** Pushes fresh computer output and prompt to the client when they changed.
     * Called once per tick by the plugin's tick system. Never pushes to a page
     * the client is not showing — a stale selector disconnects the client. */
    public void refreshFromComputer() {
        long revision = computer.getTerminalRevision();
        String prompt = computer.getPrompt();
        if (revision == lastRevision && prompt.equals(lastPrompt)) {
            return;
        }
        String current = revision == lastRevision ? lastShown : terminalText();
        lastRevision = revision;
        if (current.equals(lastShown) && prompt.equals(lastPrompt)) {
            return;
        }
        if (!ComputerManager.isCurrentPage(playerRef, this)) {
            manager.unregisterPage(playerRef.getUuid());
            return;
        }
        lastShown = current;
        lastPrompt = prompt;
        try {
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.set("#Output.Text", current);
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
                .append(new KeyedCodec<>("Keycode", Codec.INTEGER), (e, v) -> e.keycode = v, e -> e.keycode)
                .add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER), (e, v) -> e.slotIndex = v, e -> e.slotIndex)
                .add()
                .append(new KeyedCodec<>("FromSlotIndex", Codec.INTEGER), (e, v) -> e.fromSlotIndex = v, e -> e.fromSlotIndex)
                .add()
                .append(new KeyedCodec<>("ToSlotIndex", Codec.INTEGER), (e, v) -> e.toSlotIndex = v, e -> e.toSlotIndex)
                .add()
                .append(new KeyedCodec<>("ItemStackId", Codec.STRING), (e, v) -> e.itemStackId = v, e -> e.itemStackId)
                .add()
                .build();

        protected String action;
        protected String line;
        protected int keycode = -1;
        protected int slotIndex = -1;
        protected int fromSlotIndex = -1;
        protected int toSlotIndex = -1;
        protected String itemStackId;

        public TerminalEventData() {
        }
    }
}
