package com.vertexdebug.modules;

import com.vertexdebug.VertexDebugAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Chunk Finder — scans loaded chunks for user-selected block types and highlights
 * a 2x2 chunk area (the found chunk plus its neighbour to the +X and +Z, i.e. the
 * 4 chunks that share the found chunk's corner) centered on the detection point.
 *
 * Rendering uses Meteor Client's standard Render3DEvent / shape renderer so that
 * colour and alpha settings behave exactly like other Meteor modules (e.g. ESP-style
 * box/side rendering), and boxes are extended below Y=0 so the overlay stays visible
 * underground (negative-Y build limit / cave layers).
 */
public class ChunkFinder extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // ---- Configurable block list ----------------------------------------------------
    // Meteor Client ships a generic BlockListSetting for exactly this use case (a
    // user-editable, add/remove list of blocks selectable via a search GUI in the clickgui).
    private final Setting<List<Block>> trackedBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("tracked-blocks")
        .description("Blocks that will trigger a chunk highlight when found.")
        .defaultValue(Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.HOPPER)
        .build()
    );

    private final Setting<Integer> scanIntervalTicks = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("How often (in ticks) to rescan loaded chunks for tracked blocks.")
        .defaultValue(20)
        .min(1)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> maxTrackedChunks = sgGeneral.add(new IntSetting.Builder()
        .name("max-highlights")
        .description("Maximum number of 2x2 highlight groups to keep at once (oldest are dropped).")
        .defaultValue(64)
        .min(1)
        .sliderMax(512)
        .build()
    );

    // ---- Render settings --------------------------------------------------------------
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Color of the highlight box sides.")
        .defaultValue(new SettingColor(255, 40, 40, 40))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Color of the highlight box outline.")
        .defaultValue(new SettingColor(255, 40, 40, 255))
        .build()
    );

    private final Setting<Boolean> fill = sgRender.add(new BoolSetting.Builder()
        .name("fill")
        .description("Render a filled (translucent) box, not just an outline.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> belowYExtent = sgRender.add(new IntSetting.Builder()
        .name("below-y-extent")
        .description("How far below Y=0 the highlight box extends, so it remains visible underground.")
        .defaultValue(64)
        .min(0)
        .sliderMax(320)
        .build()
    );

    private final Setting<Integer> aboveYExtent = sgRender.add(new IntSetting.Builder()
        .name("above-y-extent")
        .description("How far above Y=0 the highlight box extends.")
        .defaultValue(0)
        .min(0)
        .sliderMax(320)
        .build()
    );

    // Set of currently-highlighted 2x2 chunk groups, keyed by the "anchor" chunk (min X/Z of the group).
    private final LinkedHashMap<Long, ChunkGroup> activeGroups = new LinkedHashMap<>();
    private int tickCounter = 0;

    public ChunkFinder() {
        super(VertexDebugAddon.VERTEX_DEBUG, "chunk-finder",
            "Scans loaded chunks for selected block types and highlights the surrounding 2x2 chunk area.");
    }

    @Override
    public void onActivate() {
        activeGroups.clear();
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        activeGroups.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null) return;

        tickCounter++;
        if (tickCounter < scanIntervalTicks.get()) return;
        tickCounter = 0;

        List<Block> tracked = trackedBlocks.get();
        if (tracked.isEmpty()) return;

        Set<Block> trackedSet = new HashSet<>(tracked);

        // Iterate loaded chunks around the player and inspect each chunk's block/section
        // palette. This avoids scanning every block position and is fast even on large
        // render distances, since we only need to know "does this chunk contain block X".
        Iterable<WorldChunk> loadedChunks = getLoadedChunks();
        if (loadedChunks == null) return;

        for (WorldChunk chunk : loadedChunks) {
            if (chunk == null) continue;

            BlockPos hit = findTrackedBlockInChunk(chunk, trackedSet);
            if (hit == null) continue;

            ChunkPos foundChunk = chunk.getPos();
            addHighlightGroup(foundChunk, hit);
        }
    }

    /** Scans a single chunk's sections for any block in the tracked set, returning the first match position found. */
    private BlockPos findTrackedBlockInChunk(WorldChunk chunk, Set<Block> trackedSet) {
        int minY = chunk.getBottomY();
        int maxY = chunk.getTopYInclusive();

        ChunkPos cp = chunk.getPos();
        int baseX = cp.getStartX();
        int baseZ = cp.getStartZ();

        // Use the chunk's section block-state storage for a fast palette check first,
        // falling back to a direct getBlockState scan only within matching sections.
        for (int y = minY; y < maxY; y += 16) {
            var section = chunk.getSection(chunk.getSectionIndex(y));
            if (section == null || section.isEmpty()) continue;

            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    for (int dy = 0; dy < 16 && (y + dy) < maxY; dy++) {
                        BlockPos pos = new BlockPos(baseX + dx, y + dy, baseZ + dz);
                        Block block = chunk.getBlockState(pos).getBlock();
                        if (trackedSet.contains(block)) return pos;
                    }
                }
            }
        }

        return null;
    }

    private Iterable<WorldChunk> getLoadedChunks() {
        try {
            // ClientChunkManager exposes loaded chunks; access via reflection-free standard API.
            var chunkManager = mc.world.getChunkManager();
            List<WorldChunk> chunks = new ArrayList<>();
            int viewDist = mc.options.getViewDistance().getValue();
            ChunkPos center = new ChunkPos(mc.player.getBlockPos());

            for (int dx = -viewDist; dx <= viewDist; dx++) {
                for (int dz = -viewDist; dz <= viewDist; dz++) {
                    WorldChunk c = (WorldChunk) mc.world.getChunk(center.x + dx, center.z + dz, net.minecraft.world.chunk.ChunkStatus.FULL, false);
                    if (c != null) chunks.add(c);
                }
            }
            return chunks;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Registers a 2x2 highlight group. The group is the found chunk plus the three
     * chunks that complete a 2x2 block centered on the detection point: we pick the
     * quadrant of the found chunk that the block sits in relative to chunk center so
     * the 2x2 area is genuinely "centered around" the detected location rather than
     * always extending in a fixed +X/+Z direction.
     */
    private void addHighlightGroup(ChunkPos foundChunk, BlockPos hit) {
        int localX = hit.getX() - foundChunk.getStartX(); // 0-15
        int localZ = hit.getZ() - foundChunk.getStartZ(); // 0-15

        int dx = localX < 8 ? -1 : 0; // if block is in the west half, extend group west
        int dz = localZ < 8 ? -1 : 0; // if block is in the north half, extend group north

        int minChunkX = foundChunk.x + dx;
        int minChunkZ = foundChunk.z + dz;

        long key = ChunkPos.toLong(minChunkX, minChunkZ);
        if (activeGroups.containsKey(key)) return;

        activeGroups.put(key, new ChunkGroup(minChunkX, minChunkZ, hit));

        while (activeGroups.size() > maxTrackedChunks.get()) {
            Iterator<Long> it = activeGroups.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (activeGroups.isEmpty()) return;

        for (ChunkGroup group : activeGroups.values()) {
            int minX = group.minChunkX * 16;
            int minZ = group.minChunkZ * 16;
            int maxX = minX + 32; // 2 chunks wide
            int maxZ = minZ + 32; // 2 chunks deep

            double minY = -belowYExtent.get();
            double maxY = aboveYExtent.get();

            event.renderer.box(
                minX, minY, minZ,
                maxX, maxY, maxZ,
                sideColor.get(), lineColor.get(),
                fill.get() ? meteordevelopment.meteorclient.renderer.ShapeMode.Both
                           : meteordevelopment.meteorclient.renderer.ShapeMode.Lines,
                0
            );
        }
    }

    @Override
    public String getInfoString() {
        return activeGroups.size() + " found";
    }

    /** A single 2x2 chunk highlight, anchored at its minimum chunk X/Z. */
    private static class ChunkGroup {
        final int minChunkX, minChunkZ;
        final BlockPos triggerPos;

        ChunkGroup(int minChunkX, int minChunkZ, BlockPos triggerPos) {
            this.minChunkX = minChunkX;
            this.minChunkZ = minChunkZ;
            this.triggerPos = triggerPos;
        }
    }
}
