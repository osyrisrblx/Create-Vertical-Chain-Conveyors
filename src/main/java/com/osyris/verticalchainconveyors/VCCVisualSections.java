package com.osyris.verticalchainconveyors;

import dev.engine_room.flywheel.api.visual.SectionTrackedVisual;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

public final class VCCVisualSections {
    private VCCVisualSections() {}

    public static void updateTrackedSections(SectionTrackedVisual.SectionCollector collector,
            BlockEntity blockEntity, AABB bounds) {
        if (collector == null)
            return;

        collector.sections(sectionsFor(bounds, blockEntity.getBlockPos()));
    }

    public static LongSet sectionsFor(AABB bounds, net.minecraft.core.BlockPos fallbackPos) {
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
