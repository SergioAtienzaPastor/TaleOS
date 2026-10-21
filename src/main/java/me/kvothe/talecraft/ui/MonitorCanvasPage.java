package me.kvothe.talecraft.ui;

import java.util.BitSet;

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

import me.kvothe.talecraft.computer.ComputerManager;
import me.kvothe.talecraft.computer.MonitorCanvas;

/**
 * Page of a Monitor block: renders the block's {@link MonitorCanvas} as a
 * 40x24 grid of colored cells. Each grid row is a Group of cell Groups whose
 * Background comes from the palette; changed rows are rebuilt server-side and
 * re-inserted (colors travel inside inline documents, the only fully
 * supported way to set them dynamically).
 */
public class MonitorCanvasPage extends InteractiveCustomUIPage<MonitorCanvasPage.CanvasEventData> {

    /** Layout path relative to Common/UI/Custom/. */
    public static final String LAYOUT = "TaleCraft/Monitor.ui";

    /** Cell edge in UI units (40x24 cells at 15px = 600x360 canvas). */
    private static final int CELL = 15;

    private final PlayerRef playerRef;
    private final ComputerManager manager;
    private final MonitorCanvas canvas;

    public MonitorCanvasPage(@Nonnull PlayerRef playerRef, ComputerManager manager,
            MonitorCanvas canvas) {
        super(playerRef, CustomPageLifetime.CanDismiss, CanvasEventData.CODEC);
        this.playerRef = playerRef;
        this.manager = manager;
        this.canvas = canvas;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append(LAYOUT);
        cmd.set("#Title.Text", "Monitor");
        for (int y = 0; y < MonitorCanvas.HEIGHT; y++) {
            cmd.appendInline("#Canvas", rowDocument(y, canvas));
        }
        canvas.consumeDirtyRows(); // everything was just painted
        evt.addEventBinding(CustomUIEventBindingType.Dismissing, "#Monitor",
                new EventData().append("Action", "close"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull CanvasEventData data) {
        if ("close".equals(data.action)) {
            manager.unregisterMonitorPage(playerRef.getUuid());
        }
    }

    /** Pushes the rows that changed since the last tick. Called once per tick
     * by the plugin's tick system. */
    public void refreshFromCanvas() {
        BitSet dirty = canvas.consumeDirtyRows();
        if (dirty.isEmpty()) {
            return;
        }
        try {
            UICommandBuilder cmd = new UICommandBuilder();
            // Remove every dirty row first, then re-insert ascending before the
            // next surviving row (or append when the tail was dirty).
            for (int y = dirty.nextSetBit(0); y >= 0; y = dirty.nextSetBit(y + 1)) {
                cmd.remove("#row_" + y);
            }
            for (int y = dirty.nextSetBit(0); y >= 0; y = dirty.nextSetBit(y + 1)) {
                int next = y + 1;
                while (next < MonitorCanvas.HEIGHT && dirty.get(next)) {
                    next++;
                }
                String doc = rowDocument(y, canvas);
                if (next < MonitorCanvas.HEIGHT) {
                    cmd.insertBeforeInline("#row_" + next, doc);
                } else {
                    cmd.appendInline("#Canvas", doc);
                }
            }
            sendUpdate(cmd, false);
        } catch (Exception e) {
            // page was closed or the client went away: stop tracking it
            manager.unregisterMonitorPage(playerRef.getUuid());
        }
    }

    /** Inline document of one canvas row with its cells' current colors. */
    private static String rowDocument(int y, MonitorCanvas canvas) {
        StringBuilder sb = new StringBuilder(64 * MonitorCanvas.WIDTH);
        sb.append("Group #row_").append(y)
                .append(" { LayoutMode: Left; Anchor: (Height: ").append(CELL).append("); ");
        int run = 1;
        int runColor = canvas.getPixel(0, y);
        for (int x = 1; x < MonitorCanvas.WIDTH; x++) {
            int color = canvas.getPixel(x, y);
            if (color != runColor) {
                appendCell(sb, run, runColor);
                run = 0;
                runColor = color;
            }
            run++;
        }
        appendCell(sb, run, runColor);
        sb.append('}');
        return sb.toString();
    }

    /** Consecutive same-color cells collapse into one wider element (solid
     * rows cost one element instead of forty). */
    private static void appendCell(StringBuilder sb, int cellsWide, int color) {
        sb.append("Group { Anchor: (Width: ").append(cellsWide * CELL)
                .append("); Background: ").append(MonitorCanvas.PALETTE_HEX[color]).append("; } ");
    }

    public static class CanvasEventData {
        public static final BuilderCodec<CanvasEventData> CODEC = BuilderCodec
                .builder(CanvasEventData.class, CanvasEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action)
                .add()
                .build();

        private String action;

        public CanvasEventData() {
        }
    }
}
