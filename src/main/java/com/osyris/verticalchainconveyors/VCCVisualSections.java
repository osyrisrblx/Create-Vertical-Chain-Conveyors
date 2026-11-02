package com.osyris.verticalchainconveyors;

import java.util.Map;
import java.util.WeakHashMap;

import dev.engine_room.flywheel.api.visual.SectionTrackedVisual;
import dev.engine_room.flywheel.impl.visualization.storage.SectionTracker;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

public final class VCCVisualSections {
    // entries contain only immutable geometry and section snapshots, never the
    // collector or visual, so discarded visuals can release their cache entries.
    private static final Map<SectionTracker, CachedSections> CACHE = new WeakHashMap<>();

    private record CachedSections(AABB bounds, BlockPos position, LongSet registeredSections) {}

    private VCCVisualSections() {}

    public static void updateTrackedSections(SectionTrackedVisual.SectionCollector collector,
            BlockEntity blockEntity, AABB bounds) {
        if (collector == null)
            return;

        updateTrackedSections(collector, blockEntity.getBlockPos(), bounds);
    }

    static void updateTrackedSections(SectionTrackedVisual.SectionCollector collector,
            BlockPos position, AABB bounds) {
        if (collector == null)
            return;

        // flywheel's stock tracker copies each registration into an immutable
        // set. other implementations, including subclasses, may not do so.
        if (collector.getClass() != SectionTracker.class) {
            collector.sections(sectionsFor(bounds, position));
            return;
        }

        SectionTracker tracker = (SectionTracker) collector;
        // beginFrame can run on flywheel worker threads. keep the cache check,
        // registration and snapshot together, including concurrent first use.
        synchronized (CACHE) {
            CachedSections cached = CACHE.get(tracker);
            // section membership is cached, not lighting. external registration
            // replaces the immutable snapshot and invalidates this cache too.
            if (cached != null && cached.registeredSections() == tracker.sections()
                    && cached.bounds().equals(bounds) && cached.position().equals(position))
                return;

            tracker.sections(sectionsFor(bounds, position));
            CACHE.put(tracker, new CachedSections(bounds, position.immutable(), tracker.sections()));
        }
    }

    public static LongSet sectionsFor(AABB bounds, BlockPos fallbackPos) {
        LongSet sections = new LongOpenHashSet();

        int minX = SectionPos.blockToSectionCoord(Mth.floor(bounds.minX));
        int minY = SectionPos.blockToSectionCoord(Mth.floor(bounds.minY));
        int minZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.minZ));
        int maxX = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxX));
        int maxY = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxY));
        int maxZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxZ));

        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    sections.add(SectionPos.asLong(x, y, z));

        if (sections.isEmpty())
            sections.add(SectionPos.asLong(fallbackPos));

        return sections;
    }
}
