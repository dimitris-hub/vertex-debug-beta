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

public class ChunkFinder extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<List<Block>> trackedBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("tracked-blocks")
        .defaultValue(Blocks.PISTON, Blocks.STICKY_PISTON, Blocks.HOPPER)
        .build()
    );

    private final Setting<Integer> scanIntervalTicks = sgGeneral.add(new IntSetting.Builder()
        .name("scan-interval")
        .defaultValue(20)
        .build()
    );

    private final Setting<Integer> maxTrackedChunks = sgGeneral.add(new IntSetting.Builder()
        .name("max-highlights")
        .defaultValue(64)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .defaultValue(new SettingColor(255, 40, 40, 60))
        .build()
    );

    private final Setting<Double> renderHeightY = sgRender.add(new DoubleSetting.Builder()
        .name("render-y-level")
        .defaultValue(0.0)
        .build()
    );

    private final LinkedHashMap<Long, ChunkPos> activeChunks = new LinkedHashMap<>();
    private int tickCounter = 0;

    public ChunkFinder() {
        super(VertexDebugAddon.VERTEX_DEBUG, "chunk-finder", "Finds chunks.");
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

        for (WorldChunk chunk : getLoadedChunks()) {
            if (chunk == null) continue;
            if (findTrackedBlockInChunk(chunk, trackedSet) != null) {
                ChunkPos foundChunk = chunk.getPos();
                if (!activeChunks.containsKey(foundChunk.toLong())) {
                    activeChunks.put(foundChunk.toLong(), foundChunk);
                }
            }
        }
        
        while (activeChunks.size() > maxTrackedChunks.get()) {
            Iterator<Long> it = activeChunks.keySet().iterator();
            if (it.hasNext()) { it.next(); it.remove(); }
        }
    }

    private BlockPos findTrackedBlockInChunk(WorldChunk chunk, Set<Block> trackedSet) {
        int minY = chunk.getBottomY();
        int maxY = chunk.getTopYInclusive();
        ChunkPos cp = chunk.getPos();
        
        for (int y = minY; y < maxY; y += 16) {
            var section = chunk.getSection(chunk.getSectionIndex(y));
            if (section == null || section.isEmpty()) continue;
            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    for (int dy = 0; dy < 16 && (y + dy) < maxY; dy++) {
                        BlockPos pos = new BlockPos(cp.getStartX() + dx, y + dy, cp.getStartZ() + dz);
                        if (trackedSet.contains(chunk.getBlockState(pos).getBlock())) return pos;
                    }
                }
            }
        }
        return null;
    }

    private Iterable<WorldChunk> getLoadedChunks() {
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
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (ChunkPos pos : activeChunks.values()) {
            double y = renderHeightY.get();
            event.renderer.box(pos.getStartX(), y, pos.getStartZ(), pos.getEndX() + 1, y + 0.05, pos.getEndZ() + 1, sideColor.get(), sideColor.get(), ShapeMode.Sides, 0);
        }
    }

    @Override
    public String getInfoString() {
        return activeChunks.size() + " found";
    }
}
