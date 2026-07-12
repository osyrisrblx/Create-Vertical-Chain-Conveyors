package com.osyris.verticalchainconveyors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

class VCCVisualSectionsTest {

    @Test
    void sectionsForIncludesSingleSectionBounds() {
        LongSet sections = VCCVisualSections.sectionsFor(
                new AABB(1, 2, 3, 4, 5, 6), BlockPos.ZERO);

        assertEquals(1, sections.size());
        assertTrue(sections.contains(SectionPos.asLong(0, 0, 0)));
    }

    @Test
    void sectionsForIncludesEverySectionTouchedByExpandedBounds() {
        LongSet sections = VCCVisualSections.sectionsFor(
                new AABB(-1, 0, 15, 17, 33, 32), BlockPos.ZERO);

        assertTrue(sections.contains(SectionPos.asLong(-1, 0, 0)));
        assertTrue(sections.contains(SectionPos.asLong(0, 0, 0)));
        assertTrue(sections.contains(SectionPos.asLong(1, 2, 2)));
        assertEquals(27, sections.size());
    }
}
