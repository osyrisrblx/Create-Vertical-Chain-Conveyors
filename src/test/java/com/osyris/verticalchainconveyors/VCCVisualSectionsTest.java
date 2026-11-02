package com.osyris.verticalchainconveyors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.joml.FrustumIntersection;
import org.junit.jupiter.api.Test;

import dev.engine_room.flywheel.api.task.TaskExecutor;
import dev.engine_room.flywheel.api.visual.DistanceUpdateLimiter;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual;
import dev.engine_room.flywheel.api.visual.SectionTrackedVisual.SectionCollector;
import dev.engine_room.flywheel.impl.visualization.storage.LightUpdatedVisualStorage;
import dev.engine_room.flywheel.impl.visualization.storage.SectionTracker;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
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

    @Test
    void unchangedGeometryKeepsRegistrationAcrossFrames() {
        SectionTracker tracker = new SectionTracker();
        AtomicInteger registrations = countRegistrations(tracker);
        AABB bounds = new AABB(BlockPos.ZERO).inflate(64);

        VCCVisualSections.updateTrackedSections(tracker, BlockPos.ZERO, bounds);
        LongSet registered = tracker.sections();
        for (int frame = 0; frame < 100; frame++) {
            VCCVisualSections.updateTrackedSections(tracker, new BlockPos(0, 0, 0),
                    new AABB(BlockPos.ZERO).inflate(64));
        }

        assertEquals(729, registered.size());
        assertEquals(1, registrations.get());
        assertSame(registered, tracker.sections());
    }

    @Test
    void changedBoundsReplaceRegisteredSections() {
        SectionTracker tracker = new SectionTracker();
        AtomicInteger registrations = countRegistrations(tracker);
        AABB initial = new AABB(1, 2, 3, 4, 5, 6);
        AABB expanded = new AABB(-1, 0, 15, 17, 33, 32);

        VCCVisualSections.updateTrackedSections(tracker, BlockPos.ZERO, initial);
        VCCVisualSections.updateTrackedSections(tracker, BlockPos.ZERO, expanded);
        VCCVisualSections.updateTrackedSections(tracker, BlockPos.ZERO, expanded);

        assertEquals(2, registrations.get());
        assertEquals(VCCVisualSections.sectionsFor(expanded, BlockPos.ZERO), tracker.sections());
    }

    @Test
    void changedPositionInvalidatesEvenWhenBoundsAreUnchanged() {
        SectionTracker tracker = new SectionTracker();
        AtomicInteger registrations = countRegistrations(tracker);
        AABB bounds = new AABB(BlockPos.ZERO);
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos(0, 0, 0);

        VCCVisualSections.updateTrackedSections(tracker, position, bounds);
        position.set(1, 0, 0);
        VCCVisualSections.updateTrackedSections(tracker, position, bounds);
        VCCVisualSections.updateTrackedSections(tracker, new BlockPos(1, 0, 0), bounds);

        assertEquals(2, registrations.get());
        assertEquals(VCCVisualSections.sectionsFor(bounds, position), tracker.sections());
    }

    @Test
    void replacementCollectorReceivesItsOwnRegistration() {
        SectionTracker first = new SectionTracker();
        SectionTracker replacement = new SectionTracker();
        AtomicInteger firstRegistrations = countRegistrations(first);
        AtomicInteger replacementRegistrations = countRegistrations(replacement);
        AABB bounds = new AABB(BlockPos.ZERO);

        VCCVisualSections.updateTrackedSections(first, BlockPos.ZERO, bounds);
        VCCVisualSections.updateTrackedSections(replacement, BlockPos.ZERO, bounds);
        VCCVisualSections.updateTrackedSections(first, BlockPos.ZERO, bounds);

        assertEquals(1, firstRegistrations.get());
        assertEquals(1, replacementRegistrations.get());
        assertEquals(first.sections(), replacement.sections());
    }

    @Test
    void externalRegistrationsInvalidateEvenWhenSectionContentsMatch() {
        SectionTracker tracker = new SectionTracker();
        AtomicInteger registrations = countRegistrations(tracker);
        AABB bounds = new AABB(BlockPos.ZERO);
        LongSet expected = VCCVisualSections.sectionsFor(bounds, BlockPos.ZERO);
        VCCVisualSections.updateTrackedSections(tracker, BlockPos.ZERO, bounds);

        int expectedRegistrations = 1;
        for (LongSet external : List.of(LongSet.of(SectionPos.asLong(10, 10, 10)), expected)) {
            tracker.sections(external);
            LongSet externalSnapshot = tracker.sections();
            VCCVisualSections.updateTrackedSections(tracker, BlockPos.ZERO, bounds);
            VCCVisualSections.updateTrackedSections(tracker, BlockPos.ZERO, bounds);

            expectedRegistrations += 2;
            assertEquals(expectedRegistrations, registrations.get());
            assertNotSame(externalSnapshot, tracker.sections());
            assertEquals(expected, tracker.sections());
        }
    }

    @Test
    void unknownCollectorKeepsReceivingEveryRegistration() {
        AtomicInteger registrations = new AtomicInteger();
        AABB bounds = new AABB(-1, 0, 15, 17, 33, 32);
        LongSet expected = VCCVisualSections.sectionsFor(bounds, BlockPos.ZERO);
        SectionCollector collector = sections -> {
            registrations.incrementAndGet();
            assertEquals(expected, sections);
        };

        VCCVisualSections.updateTrackedSections(collector, BlockPos.ZERO, bounds);
        VCCVisualSections.updateTrackedSections(collector, BlockPos.ZERO, bounds);

        assertEquals(2, registrations.get());
    }

    @Test
    void trackerSubclassKeepsReceivingEveryRegistration() {
        SectionTracker tracker = new SectionTracker() {};
        AtomicInteger registrations = countRegistrations(tracker);
        AABB bounds = new AABB(BlockPos.ZERO);

        VCCVisualSections.updateTrackedSections(tracker, BlockPos.ZERO, bounds);
        VCCVisualSections.updateTrackedSections(tracker, BlockPos.ZERO, bounds);

        assertEquals(2, registrations.get());
        assertEquals(VCCVisualSections.sectionsFor(bounds, BlockPos.ZERO), tracker.sections());
    }

    @Test
    void nullCollectorDoesNotAccessTheBlockEntity() {
        assertDoesNotThrow(() -> VCCVisualSections.updateTrackedSections(null, (BlockEntity) null, null));
    }

    @Test
    void concurrentUpdatesRegisterUnchangedGeometryOnce() throws Exception {
        SectionTracker tracker = new SectionTracker();
        AtomicInteger registrations = countRegistrations(tracker);
        AABB bounds = new AABB(BlockPos.ZERO).inflate(64);
        List<Callable<Void>> updates = new ArrayList<>();
        for (int task = 0; task < 16; task++) {
            updates.add(() -> {
                for (int frame = 0; frame < 100; frame++)
                    VCCVisualSections.updateTrackedSections(tracker, BlockPos.ZERO, bounds);
                return null;
            });
        }

        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var result : executor.invokeAll(updates))
                result.get();
        }

        assertEquals(1, registrations.get());
        assertEquals(VCCVisualSections.sectionsFor(bounds, BlockPos.ZERO), tracker.sections());
    }

    @Test
    void cachedRegistrationStillReceivesFlywheelLightUpdates() {
        SectionTracker tracker = new SectionTracker();
        AtomicInteger registrations = countRegistrations(tracker);
        AABB bounds = new AABB(BlockPos.ZERO).inflate(64);
        LightUpdatedVisualStorage storage = new LightUpdatedVisualStorage();
        TestLightVisual visual = new TestLightVisual();
        VCCVisualSections.updateTrackedSections(tracker, BlockPos.ZERO, bounds);
        storage.add(visual, tracker);

        for (int frame = 1; frame <= 2; frame++) {
            VCCVisualSections.updateTrackedSections(tracker, BlockPos.ZERO, bounds);
            storage.onLightUpdate(SectionPos.asLong(0, 0, 0));
            storage.onLightUpdate(SectionPos.asLong(1, 0, 0));
            storage.plan().execute(DIRECT_EXECUTOR, LIGHT_CONTEXT);

            assertEquals(frame, visual.lightUpdates);
        }
        assertEquals(1, registrations.get());

        AABB movedBounds = bounds.move(256, 0, 0);
        VCCVisualSections.updateTrackedSections(tracker, new BlockPos(256, 0, 0), movedBounds);
        storage.onLightUpdate(SectionPos.asLong(16, 0, 0));
        storage.plan().execute(DIRECT_EXECUTOR, LIGHT_CONTEXT);
        assertEquals(3, visual.lightUpdates);
        assertEquals(2, registrations.get());

        storage.onLightUpdate(SectionPos.asLong(0, 0, 0));
        storage.plan().execute(DIRECT_EXECUTOR, LIGHT_CONTEXT);
        assertEquals(3, visual.lightUpdates);
    }

    private static AtomicInteger countRegistrations(SectionTracker tracker) {
        AtomicInteger registrations = new AtomicInteger();
        tracker.addListener(registrations::incrementAndGet);
        return registrations;
    }

    private static final TaskExecutor DIRECT_EXECUTOR = new TaskExecutor() {
        @Override public int threadCount() { return 1; }
        @Override public void execute(Runnable command) { command.run(); }
    };

    private static final DynamicVisual.Context LIGHT_CONTEXT = new DynamicVisual.Context() {
        @Override public Camera camera() { return null; }
        @Override public FrustumIntersection frustum() { return null; }
        @Override public float partialTick() { return 0.5f; }
        @Override public DistanceUpdateLimiter limiter() { return null; }
    };

    private static class TestLightVisual implements LightUpdatedVisual {
        int lightUpdates;

        @Override public void updateLight(float partialTick) { lightUpdates++; }
        @Override public void setSectionCollector(SectionCollector collector) {}
        @Override public void update(float partialTick) {}
        @Override public void delete() {}
    }
}
