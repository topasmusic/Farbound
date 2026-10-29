package de.topas.farbound;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** One bounded chunk request at a time. Never generates the corridor between home and destination. */
public final class SpawnSearch implements AutoCloseable {
    private final ServerLevel level;
    private final Expedition expedition;
    private final List<Expedition.Position> protectedStarts;
    private final List<FarboundConfig.Circle> protectedAreas;
    private CompletableFuture<?> pending;
    private ChunkPos held;
    private int ticks, attempts;
    private final List<ChunkPos> customChunks;
    private boolean closed;
    public List<BlockPos> result;
    public boolean failed;
    public SpawnSearch(ServerLevel level, Expedition expedition, List<Expedition.Position> protectedStarts, List<FarboundConfig.Circle> protectedAreas) {
        this.level = level; this.expedition = expedition; this.protectedStarts = protectedStarts;
        this.protectedAreas = List.copyOf(protectedAreas);
        customChunks = new ArrayList<>();
        if (expedition.customDestination) {
            for (int z = Math.floorDiv(expedition.customZ - 100, 16); z <= Math.floorDiv(expedition.customZ + 100, 16); z++)
                for (int x = Math.floorDiv(expedition.customX - 100, 16); x <= Math.floorDiv(expedition.customX + 100, 16); x++)
                    customChunks.add(new ChunkPos(x, z));
            customChunks.sort(Comparator.comparingDouble(c -> Math.hypot(c.getMiddleBlockX() - (double)expedition.customX, c.getMiddleBlockZ() - (double)expedition.customZ)));
        }
    }
    public int attempts() { return attempts; }
    public void tick() {
        if (closed || failed || result != null) return;
        if (++ticks > 1800) { failed = true; close(); return; }
        if (pending != null) {
            if (!pending.isDone()) return;
            if (!pending.isCompletedExceptionally() && level.getChunkSource().getChunkNow(held.x(), held.z()) != null) {
                var positions = expedition.customDestination ? customPositions(held) : groupPositions(level, held.getMiddleBlockX(), held.getMiddleBlockZ(), expedition.members.size());
                if (positions.size() == expedition.members.size()) { result = positions; return; }
            }
            releaseChunk(); pending = null;
            return;
        }
        int limit = expedition.customDestination ? customChunks.size() : 48;
        if (attempts >= limit) { failed = true; close(); return; }
        if (expedition.customDestination) {
            var chunk = customChunks.get(attempts++);
            if (customColumns(chunk).isEmpty()) return;
            request(chunk);
            return;
        }
        attempts++;
        double angle = level.getRandom().nextDouble() * Math.PI * 2;
        double radius = expedition.distance * (1 + level.getRandom().nextDouble() * .1);
        int x = (int)Math.floor(expedition.originX + Math.cos(angle) * radius);
        int z = (int)Math.floor(expedition.originZ + Math.sin(angle) * radius);
        var chunk = new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        x = chunk.getMiddleBlockX(); z = chunk.getMiddleBlockZ();
        var border = level.getWorldBorder();
        if (!ExpeditionPolicy.validTarget(expedition.originX, expedition.originZ, x, z, expedition.distance,
            border.getMinX(), border.getMaxX(), border.getMinZ(), border.getMaxZ(), 16)) return;
        final int candidateX = x, candidateZ = z;
        if (protectedStarts.stream().anyMatch(p -> Math.hypot(p.x() - candidateX, p.z() - candidateZ) < 1024)) return;
        if (protectedAreas.stream().anyMatch(c -> Math.hypot(c.x() - candidateX, c.z() - candidateZ) < c.radius() + 16)) return;
        request(chunk);
    }
    private void request(ChunkPos chunk) {
        held = chunk;
        level.getChunkSource().addTicketWithRadius(Farbound.SEARCH_TICKET, held, 1);
        pending = level.getChunkSource().getChunkFuture(held.x(), held.z(), ChunkStatus.FULL, true);
    }
    /** Scan loaded chunk interiors only; safety checks never generate an adjacent chunk synchronously. */
    private List<BlockPos> customPositions(ChunkPos chunk) {
        var columns = customColumns(chunk);
        columns.sort(Comparator.comparingDouble(p -> Math.hypot(p.getX() - (double)expedition.customX, p.getZ() - (double)expedition.customZ)));
        var positions = new ArrayList<BlockPos>();
        for (var column : columns) {
            int x = column.getX(), z = column.getZ();
            if (positions.stream().anyMatch(p -> Math.hypot(p.getX() - x, p.getZ() - z) < 2)) continue;
            var pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (safe(level, pos)) positions.add(pos);
            if (positions.size() == expedition.members.size()) break;
        }
        return positions;
    }
    private ArrayList<BlockPos> customColumns(ChunkPos chunk) {
        var columns = new ArrayList<BlockPos>();
        for (int dz = 1; dz < 15; dz++) for (int dx = 1; dx < 15; dx++) {
            int x = chunk.getMinBlockX() + dx, z = chunk.getMinBlockZ() + dz;
            var pos = new BlockPos(x, 0, z);
            if (ExpeditionPolicy.withinCustomRadius(expedition.customX, expedition.customZ, x, z)
                && level.getWorldBorder().isWithinBounds(pos)
                && protectedAreas.stream().noneMatch(c -> Math.hypot(c.x() - (x + .5), c.z() - (z + .5)) < c.radius() + 16)) columns.add(pos);
        }
        return columns;
    }
    public static List<BlockPos> groupPositions(ServerLevel level, int x, int z, int count) {
        var result = new ArrayList<BlockPos>();
        for (int dz = -3; dz <= 3 && result.size() < count; dz += 2) {
            for (int dx = -3; dx <= 3 && result.size() < count; dx += 2) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                BlockPos pos = new BlockPos(x + dx, y, z + dz);
                if (safe(level, pos)) result.add(pos);
            }
        }
        return result;
    }
    public static boolean safe(ServerLevel level, BlockPos pos) {
        if (!level.getWorldBorder().isWithinBounds(pos) || pos.getY() <= level.getMinY() || pos.getY() + 2 >= level.getMaxY()) return false;
        var ground = level.getBlockState(pos.below());
        if (!ground.isCollisionShapeFullBlock(level, pos.below()) || ground.is(BlockTags.LEAVES)) return false;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                var state = level.getBlockState(pos.offset(dx, dy, dz));
                if (!state.getFluidState().isEmpty() || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)
                    || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                    || state.is(Blocks.WITHER_ROSE)) return false;
            }
            if (level.getBlockState(pos.offset(dx, -2, dz)).isAir() && level.getBlockState(pos.offset(dx, -1, dz)).isAir()) return false;
        }
        return level.noCollision(new AABB(pos.getX() + .2, pos.getY(), pos.getZ() + .2, pos.getX() + .8, pos.getY() + 1.8, pos.getZ() + .8));
    }
    public static BlockPos nearby(ServerLevel level, BlockPos anchor) {
        if (safe(level, anchor)) return anchor;
        for (int r = 1; r <= 12; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.getX() + dx, anchor.getZ() + dz);
            var pos = new BlockPos(anchor.getX() + dx, y, anchor.getZ() + dz);
            if (safe(level, pos)) return pos;
        }
        return null;
    }
    public void close() {
        closed = true;
        releaseChunk();
    }
    private void releaseChunk() {
        if (held != null) level.getChunkSource().removeTicketWithRadius(Farbound.SEARCH_TICKET, held, 1);
        held = null;
    }
}

