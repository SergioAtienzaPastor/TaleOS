package me.kvothe.talecraft.computer;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.spawning.SpawnTestResult;

/**
 * In-world rendering of a {@link MonitorCanvas}: an invisible, motionless NPC
 * (role {@code TaleCraft_MonitorDisplay}, vanilla empty model) hovers over the
 * monitor block and its nameplate shows the canvas content as '█' text rows
 * (cropped to the content; the client centers nameplate text on the entity, so
 * exact per-pixel placement on the block face is not achievable — the text
 * reads as a hologram above the monitor). Hytale has no client-side block
 * renderers and nameplates are plain monochrome strings, so this is the only
 * world-anchored readout the server can push; the F page stays as the
 * full-color detailed view.
 *
 * Lifecycle: spawned lazily on first non-blank content, despawned when the
 * canvas clears or the monitor block disappears (re-checked every few ticks).
 * Chunk reads go through an in-memory {@link SectionReader}: an unloaded chunk
 * means "skip", never "despawn". NPC spawning requires a TICKING chunk (a
 * player within simulation range): while nobody is around the spawn waits and
 * retries with backoff — the readout only matters when someone can see it.
 */
public final class MonitorDisplay {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String ROLE = "TaleCraft_MonitorDisplay";
    private static final int BLOCK_RECHECK_TICKS = 40;

    /** Height of the nameplate anchor above the block's base (1.0 = block top). */
    private static final double HEIGHT_ABOVE_BLOCK = 1.02;

    private final World world;
    private final int x, y, z;
    private final MonitorCanvas canvas;

    private Ref<EntityStore> entityRef;
    private String shownText;
    private String rawTextOverride;
    private boolean testMode;
    private int ticksUntilCheck = 0;
    private int spawnRetryTicks = 0;
    private String lastSpawnFailure;

    public MonitorDisplay(World world, int x, int y, int z, MonitorCanvas canvas) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.canvas = canvas;
    }

    /** Test hook (console /talecraft ... __displaytest): skip the block check. */
    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    /** Test hook: show this exact text instead of rendering the canvas. */
    public void setRawText(String rawText) {
        this.rawTextOverride = rawText;
        this.shownText = null;
    }

    /** One tick; returns false when the monitor block is gone for good. */
    public boolean tick() {
        if (!testMode && --ticksUntilCheck <= 0) {
            ticksUntilCheck = BLOCK_RECHECK_TICKS;
            Boolean stillMonitor = readBlockIsMonitor();
            if (stillMonitor == null) {
                return true; // chunk not in memory: neither spawn nor despawn
            }
            if (!stillMonitor) {
                LOGGER.atInfo().log("monitor display at " + x + "," + y + "," + z
                        + ": block gone, despawning");
                despawn();
                return false;
            }
        }
        String text = rawTextOverride != null ? rawTextOverride : canvas.toNameplateText();
        if (text.isBlank()) {
            if (entityRef != null) {
                despawn();
            }
            return true;
        }
        if (entityRef == null || !entityRef.isValid()) {
            if (spawnRetryTicks > 0) {
                spawnRetryTicks--;
                return true;
            }
            spawnRetryTicks = BLOCK_RECHECK_TICKS;
            spawn();
        }
        if (entityRef != null && !text.equals(shownText)) {
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                store.ensureAndGetComponent(entityRef, Nameplate.getComponentType()).setText(text);
                shownText = text;
            } catch (Exception e) {
                LOGGER.atWarning().log("nameplate update failed at " + x + "," + y + "," + z
                        + ": " + e.getMessage());
            }
        }
        return true;
    }

    public void despawn() {
        if (entityRef != null && entityRef.isValid()) {
            try {
                world.getEntityStore().getStore().removeEntity(entityRef, RemoveReason.REMOVE);
            } catch (Exception e) {
                LOGGER.atWarning().log("display despawn failed: " + e.getMessage());
            }
        }
        entityRef = null;
        shownText = null;
    }

    /** Null when the chunk is not in memory; otherwise whether the block is a monitor. */
    private Boolean readBlockIsMonitor() {
        try {
            SectionReader reader = new SectionReader(world.getChunkStore());
            if (!reader.hasStorageInMemory(x, y, z)) {
                return null;
            }
            BlockType type = BlockType.getAssetMap().getAsset(reader.getBlock(x, y, z));
            return type != null && "TaleCraft_Monitor".equals(type.getId());
        } catch (IllegalStateException wrongThread) {
            return null;
        }
    }

    private void spawn() {
        try {
            NPCPlugin.get().validateSpawnableRole(ROLE);
        } catch (Exception e) {
            logSpawnFailure("role '" + ROLE + "' not spawnable: " + e.getMessage());
            return;
        }
        try {
            Store<EntityStore> store = world.getEntityStore().getStore();
            Vector3d pos = new Vector3d(x + 0.5, y + HEIGHT_ABOVE_BLOCK, z + 0.5);
            // Gate on the space validation: without a ticking chunk (no player
            // nearby) or with something in the way, spawning is pointless.
            var result = NPCPlugin.get().spawnNPCWithSpaceValidation(store, ROLE, null, pos,
                    new Rotation3f());
            if (result != SpawnTestResult.TEST_OK) {
                logSpawnFailure("validation: " + result);
                return;
            }
            var pair = NPCPlugin.get().spawnNPC(store, ROLE, null, pos, new Rotation3f());
            if (pair == null || pair.first() == null) {
                logSpawnFailure("spawnNPC returned null");
                return;
            }
            entityRef = pair.first();
            shownText = null;
            LOGGER.atInfo().log("monitor display spawned over " + x + "," + y + "," + z);
            lastSpawnFailure = null;
        } catch (Exception e) {
            logSpawnFailure("exception: " + e);
        }
    }

    /** Logs a spawn failure once per distinct cause (the retry loop would
     * otherwise spam the same line every BLOCK_RECHECK_TICKS ticks). */
    private void logSpawnFailure(String cause) {
        if (!cause.equals(lastSpawnFailure)) {
            lastSpawnFailure = cause;
            LOGGER.atInfo().log("monitor display at " + x + "," + y + "," + z
                    + " cannot spawn yet: " + cause);
        }
    }
}
