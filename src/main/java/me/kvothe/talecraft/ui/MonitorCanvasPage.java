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
 * 40x24 grid of colored cells. Every pixel run is a Label showing full-block
 * glyphs tinted with its palette color — only constructs proven to parse in
 * inline documents (the vanilla CommandListPage clear+appendInline pattern:
 * no element ids, no Background on inline groups). Changed content repaints
 * the whole grid, which stays small thanks to run merging.
 */
public class MonitorCanvasPage extends InteractiveCustomUIPage<MonitorCanvasPage.CanvasEventData> {

    /** Layout path relative to Common/UI/Custom/. */
    public static final String LAYOUT = "TaleCraft/Monitor.ui";

    /** Cell edge in UI units (40x24 cells at 15px = 600x360 canvas). */
    private static final int CELL = 15;

    /** Glyph used as one pixel. */
    private static final String PIXEL = "█";

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
        cmd.set("#Title.Text", "Monitor — " + MonitorCanvas.WIDTH + "x" + MonitorCanvas.HEIGHT);
        appendCanvas(cmd, canvas);
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

    /** Repaints the grid when anything changed since the last tick. Called
     * once per tick by the plugin's tick system. Never pushes to a page the
     * client is not showing — a stale selector disconnects the client. */
    public void refreshFromCanvas() {
        if (!ComputerManager.isCurrentPage(playerRef, this)) {
            manager.unregisterMonitorPage(playerRef.getUuid());
            return;
        }
        BitSet dirty = canvas.consumeDirtyRows();
        if (dirty.isEmpty()) {
            return;
        }
        try {
            UICommandBuilder cmd = new UICommandBuilder();
            cmd.clear("#Canvas");
            appendCanvas(cmd, canvas);
            sendUpdate(cmd, false);
        } catch (Exception e) {
            // page was closed or the client went away: stop tracking it
            manager.unregisterMonitorPage(playerRef.getUuid());
        }
    }

    /** Appends the 24 row documents under #Canvas. */
    private static void appendCanvas(UICommandBuilder cmd, MonitorCanvas canvas) {
        for (int y = 0; y < MonitorCanvas.HEIGHT; y++) {
            cmd.appendInline("#Canvas", rowDocument(y, canvas));
        }
    }

    /** One canvas row as an inline document: consecutive same-color pixels
     * merge into one Label of repeated block glyphs. */
    private static String rowDocument(int y, MonitorCanvas canvas) {
        StringBuilder sb = new StringBuilder(96 * MonitorCanvas.WIDTH);
        sb.append("Group { LayoutMode: Left; Anchor: (Height: ").append(CELL).append("); ");
        int x = 0;
        while (x < MonitorCanvas.WIDTH) {
            int color = canvas.getPixel(x, y);
            int run = 1;
            while (x + run < MonitorCanvas.WIDTH && canvas.getPixel(x + run, y) == color) {
                run++;
            }
            appendRun(sb, run, color);
            x += run;
        }
        sb.append('}');
        return sb.toString();
    }

    private static void appendRun(StringBuilder sb, int cells, int color) {
        sb.append("Label { Anchor: (Width: ").append(cells * CELL).append(", Height: ").append(CELL)
                .append("); Text: \"").append(PIXEL.repeat(cells))
                .append("\"; Style: (TextColor: ").append(MonitorCanvas.PALETTE_HEX[color])
                .append(", FontSize: ").append(CELL + 5).append("); } ");
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
