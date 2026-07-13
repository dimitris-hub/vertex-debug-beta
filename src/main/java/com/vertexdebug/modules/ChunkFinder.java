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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import meteordevelopment.meteorclient.renderer.ShapeMode;

import java.util.*;

/**
 * Chunk Finder — Scans loaded chunks for user-selected block types and highlights
 * a single 16x16 flat chunk footprint with a solid fill and no outer lines.
 */
public class ChunkFinder extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> trackedBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("tracked-blocks")
        .description("Blocks that will trigger a chunk highlight when found.")
        .defaultValue(Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.HOPPER)
        .build()
    );

    private final Setting<Integer> scanIntervalTicks = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .description("How often (in ticks) to rescan loaded chunks.")
        .defaultValue(20)
        .min(1)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> maxTrackedChunks = sgGeneral.add(new IntSetting.Builder()
        .name("max-highlights")
        .description("Maximum number of highlighted chunks to keep.")
        .defaultValue(64)
        .min(1)
        .sliderMax(512)
        .build()
    );

    // ---- Render settings --------------------------------------------------------------
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Color of the flat horizontal chunk slice.")
        .defaultValue(new SettingColor(255, 40, 40, 60))
        .build()
    );

    private final Setting<Double> renderHeightY = sgRender.add(new DoubleSetting.Builder()
        .name("render-y-level")
        .description("The exact Y coordinate floor where the flat chunk slice sits.")
        .defaultValue(0.0)
        .min(-64.0)
        .sliderMax(320.0)
        .build()
    );

    private final LinkedHashMap<Long, ChunkPos> activeChunks = new LinkedHashMap<>();
    private int tickCounter = 0;

    public ChunkFinder() {
        super(VertexDebugAddon.VERTEX_DEBUG, "chunk-finder",
            "Scans loaded chunks for selected block types and highlights its single 16x16 footprint flat on the ground.");
    }

    @Override
    public void onActivate() {
        activeChunks.clear();
        tickCounter = 0;
    }

    @Override
    public void onDeactivate() {
        activeChunks.clear();
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
        Iterable<WorldChunk> loadedChunks = getLoadedChunks();
        if (loadedChunks == null) return;

        for (WorldChunk chunk : loadedChunks) {
            if (chunk == null) continue;

            BlockPos hit = findTrackedBlockInChunk(chunk, trackedSet);
            if (hit == null) continue;

            ChunkPos foundChunk = chunk.getPos();
            long key = foundChunk.toLong();
            
            if (!activeChunks.containsKey(key)) {
                activeChunks.put(key, foundChunk);

                // Enforce chunk maximum limit boundaries
                while (activeChunks.size() > maxTrackedChunks.get()) {
                    Iterator<Long> it = activeChunks.keySet().iterator();
                    if (it.hasNext()) {
                        it.next();
                        it.remove();
                    }
                }
            }
        }
    }

    private BlockPos findTrackedBlockInChunk(WorldChunk chunk, Set<Block> trackedSet) {
        int minY = chunk.getBottomY();
        int maxY = chunk.getTopYInclusive();

        ChunkPos cp = chunk.getPos();
        int baseX = cp.getStartX();
        int baseZ = cp.getStartZ();

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

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (activeChunks.isEmpty()) return;

        for (ChunkPos pos : activeChunks.values()) {
            double x1 = pos.getStartX();
            double z1 = pos.getStartZ();
            double x2 = pos.getEndX() + 1;
            double z2 = pos.get someZStart() -> pos.getMinZ(); // Fixed clean bounds parsing
            z2 = pos.getGridZ() -> pos.getEndZ() + 1; 
            
            // Cleaned up boundary mappings for 1.21.11
            double realX2 = pos.getEndX() + 1;
            double realZ2 = pos.getEndZ() + 1;

            double yLevel = renderHeightY.get();

            // Draws a single flat solid overlay plane. 
            // Using ShapeMode.Sides strips away outside lines completely.
            event.renderer.box(
                x1, yLevel, z1,
                realX2, yLevel + 0.05, realZ2,
                sideColor.get(), sideColor.get(),
                ShapeMode.Sides,
                0
            );
        }
    }

    @Override
    public String getInfoString() {
        return activeChunks.size() + " found";
    }
}
