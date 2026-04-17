package com.osyris.verticalchainconveyors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

class VCCChainConveyorMathTest {
    private static final double EPS = 1.0E-5;

    @Test
    void wheelCenterIsBiasedTowardMountingFaceForEveryFacing() {
        BlockPos pos = new BlockPos(10, 20, 30);

        for (Direction facing : Direction.values()) {
            Vec3 center = VCCChainConveyorMath.wheelCenter(pos, facing);

            assertEquals(pos.getX() + 0.5 + facing.getStepX() * 0.125, center.x, EPS);
            assertEquals(pos.getY() + 0.5 + facing.getStepY() * 0.125, center.y, EPS);
            assertEquals(pos.getZ() + 0.5 + facing.getStepZ() * 0.125, center.z, EPS);
        }
    }

    @Test
    void connectionStatsStayInWheelPlaneAndUseExpectedRadius() {
        BlockPos source = new BlockPos(3, 7, -2);
        Map<Direction, BlockPos> connections = Map.of(
                Direction.UP, new BlockPos(4, 0, 3),
                Direction.NORTH, new BlockPos(4, 3, 0),
                Direction.EAST, new BlockPos(0, 3, 4),
                Direction.SOUTH, new BlockPos(4, 3, 0),
                Direction.WEST, new BlockPos(0, 3, 4)
        );

        for (Map.Entry<Direction, BlockPos> entry : connections.entrySet()) {
            Direction facing = entry.getKey();
            BlockPos connection = entry.getValue();
            VCCChainConveyorMath.ConnectionStats stats =
                    VCCChainConveyorMath.calculateConnectionStats(source, connection, facing, false);

            Vec3 startCenter = VCCChainConveyorMath.wheelCenter(source, facing);
            Vec3 endCenter = VCCChainConveyorMath.wheelCenter(source.offset(connection), facing);

            assertAxisComponentEquals(facing.getAxis(), startCenter, stats.start());
            assertAxisComponentEquals(facing.getAxis(), endCenter, stats.end());
            assertEquals(VCCChainConveyorMath.CONNECTION_RADIUS, stats.start().distanceTo(startCenter), EPS);
            assertEquals(VCCChainConveyorMath.CONNECTION_RADIUS, stats.end().distanceTo(endCenter), EPS);
            assertEquals(stats.start().distanceTo(stats.end()), stats.chainLength(), EPS);
        }
    }

    @Test
    void sameFacingOverloadMatchesLegacyStatsForEveryAxis() {
        BlockPos source = new BlockPos(-3, 12, 5);

        for (Direction facing : Direction.values()) {
            BlockPos connection = connectionInPlane(facing.getAxis());
            VCCChainConveyorMath.ConnectionStats singleFacing =
                    VCCChainConveyorMath.calculateConnectionStats(source, connection, facing, true);
            VCCChainConveyorMath.ConnectionStats explicitTarget =
                    VCCChainConveyorMath.calculateConnectionStats(source, connection, facing, facing, true);

            assertStatsEqual(singleFacing, explicitTarget);
        }
    }

    @Test
    void mixedFacingConnectionStatsEndOnTargetWheelPlane() {
        BlockPos source = new BlockPos(2, 4, 6);
        BlockPos connection = new BlockPos(5, 3, -4);
        Direction sourceFacing = Direction.EAST;
        Direction targetFacing = Direction.UP;

        VCCChainConveyorMath.ConnectionStats stats =
                VCCChainConveyorMath.calculateConnectionStats(source, connection,
                        sourceFacing, targetFacing, false);

        Vec3 sourceCenter = VCCChainConveyorMath.wheelCenter(source, sourceFacing);
        Vec3 targetCenter = VCCChainConveyorMath.wheelCenter(source.offset(connection), targetFacing);

        assertAxisComponentEquals(sourceFacing.getAxis(), sourceCenter, stats.start());
        assertAxisComponentEquals(targetFacing.getAxis(), targetCenter, stats.end());
        assertEquals(VCCChainConveyorMath.CONNECTION_RADIUS, stats.start().distanceTo(sourceCenter), EPS);
        assertEquals(VCCChainConveyorMath.CONNECTION_RADIUS, stats.end().distanceTo(targetCenter), EPS);
    }

    @Test
    void floorSourceToWallTargetStatsUseBothWheelPlanes() {
        BlockPos source = new BlockPos(2, 4, 6);
        BlockPos connection = new BlockPos(4, 1, 5);
        Direction sourceFacing = Direction.DOWN;
        Direction targetFacing = Direction.EAST;

        VCCChainConveyorMath.ConnectionStats stats =
                VCCChainConveyorMath.calculateConnectionStats(source, connection,
                        sourceFacing, targetFacing, false);

        Vec3 sourceCenter = VCCChainConveyorMath.wheelCenter(source, sourceFacing);
        Vec3 targetCenter = VCCChainConveyorMath.wheelCenter(source.offset(connection), targetFacing);

        assertAxisComponentEquals(Direction.Axis.Y, sourceCenter, stats.start());
        assertAxisComponentEquals(Direction.Axis.X, targetCenter, stats.end());
        assertEquals(VCCChainConveyorMath.CONNECTION_RADIUS, stats.start().distanceTo(sourceCenter), EPS);
        assertEquals(VCCChainConveyorMath.CONNECTION_RADIUS, stats.end().distanceTo(targetCenter), EPS);
    }

    @Test
    void targetEntryAngleMatchesSourceRemoteAngleWhenFacingsMatch() {
        BlockPos source = new BlockPos(7, -2, 11);

        for (Direction facing : Direction.values()) {
            for (boolean reversed : new boolean[] {false, true}) {
                BlockPos connection = connectionInPlane(facing.getAxis());
                VCCChainConveyorMath.ConnectionStats sourceStats =
                        VCCChainConveyorMath.calculateConnectionStats(source, connection, facing, reversed);

                float sourceRemoteAngle = VCCChainConveyorMath.remoteEntryAngle(
                        sourceStats.tangentAngle(), reversed);
                float targetEntryAngle = VCCChainConveyorMath.targetEntryAngle(connection.multiply(-1),
                        facing, reversed);

                assertEquals(sourceRemoteAngle, targetEntryAngle, EPS);
            }
        }
    }

    @Test
    void remoteEntryAngleMatchesVanillaSourceTransferFormula() {
        for (boolean reversed : new boolean[] {false, true}) {
            for (float tangentAngle : new float[] {0, 35, 90, 181, 270, 359}) {
                float expected = VCCChainConveyorMath.wrapAngle(tangentAngle + 180
                        + 2 * VCCChainConveyorMath.OFF_BRANCH_DISTANCE * (reversed ? -1 : 1));

                assertEquals(expected,
                        VCCChainConveyorMath.remoteEntryAngle(tangentAngle, reversed),
                        EPS);
            }
        }
    }

    @Test
    void mixedFacingTargetEntryAngleUsesTargetPlane() {
        BlockPos source = new BlockPos(0, 0, 0);
        BlockPos connection = new BlockPos(4, 3, 0);
        Direction sourceFacing = Direction.NORTH;
        Direction targetFacing = Direction.EAST;

        VCCChainConveyorMath.ConnectionStats mixed =
                VCCChainConveyorMath.calculateConnectionStats(source, connection,
                        sourceFacing, targetFacing, false);
        float targetEntryAngle = VCCChainConveyorMath.targetEntryAngle(connection.multiply(-1),
                targetFacing, false);
        Vec3 expectedEnd = VCCChainConveyorMath.wheelCenter(source.offset(connection), targetFacing)
                .add(VCCChainConveyorMath.rotateDegrees(
                        VCCChainConveyorMath.connectionBaseVec(targetFacing.getAxis(),
                                VCCChainConveyorMath.CONNECTION_RADIUS),
                        targetEntryAngle, targetFacing.getAxis()));

        assertVecEquals(expectedEnd, mixed.end());
    }

    @Test
    void reversedConnectionStatsUseOppositeTangentSide() {
        BlockPos source = new BlockPos(0, 0, 0);
        BlockPos connection = new BlockPos(0, 4, 5);

        VCCChainConveyorMath.ConnectionStats forward =
                VCCChainConveyorMath.calculateConnectionStats(source, connection, Direction.EAST, false);
        VCCChainConveyorMath.ConnectionStats reversed =
                VCCChainConveyorMath.calculateConnectionStats(source, connection, Direction.EAST, true);

        assertNotEquals(forward.tangentAngle(), reversed.tangentAngle());
        assertEquals(VCCChainConveyorMath.wrapAngle(forward.tangentAngle() + 70), reversed.tangentAngle(), EPS);
    }

    @Test
    void selectionOutlinePointsOffsetIntoMountedBlockForEveryFacing() {
        BlockPos pos = new BlockPos(3, 5, 7);

        for (Direction facing : Direction.values()) {
            Vec3 faceCenter = VCCChainConveyorMath.mountFaceCenter(pos, facing);
            for (double axialOffset : new double[] {0.125, 0.875}) {
                for (float angle : new float[] {-22.5f, 22.5f, 67.5f, 112.5f}) {
                    Vec3 point = VCCChainConveyorMath.outlinePoint(pos, facing, axialOffset, angle);
                    assertEquals(axisComponent(faceCenter, facing.getAxis())
                                    - axisStep(facing) * axialOffset,
                            axisComponent(point, facing.getAxis()), EPS);
                    assertEquals(VCCChainConveyorMath.CONNECTION_RADIUS,
                            radialDistance(point, faceCenter, facing), EPS);
                }
            }
        }
    }

    @Test
    void packageLoopPositionsStayInWheelPlaneAtPackageRadiusForEveryFacing() {
        BlockPos pos = new BlockPos(-4, 9, 2);

        for (Direction facing : Direction.values()) {
            Vec3 center = VCCChainConveyorMath.wheelCenter(pos, facing);
            for (float angle : new float[] {0, 45, 90, 180, 270}) {
                Vec3 loopPosition = VCCChainConveyorMath.loopPosition(pos, facing, angle);

                assertAxisComponentEquals(facing.getAxis(), center, loopPosition);
                assertEquals(VCCChainConveyorMath.LOOP_RADIUS, loopPosition.distanceTo(center), EPS);
            }
        }
    }

    @Test
    void packageLoopPositionKeepsVanillaRadiusInsideConnectionTangent() {
        BlockPos source = new BlockPos(-4, 9, 2);

        for (Direction facing : Direction.values()) {
            BlockPos connection = connectionInPlane(facing.getAxis());
            VCCChainConveyorMath.ConnectionStats stats =
                    VCCChainConveyorMath.calculateConnectionStats(source, connection, facing, false);
            Vec3 loopPosition = VCCChainConveyorMath.loopPosition(source, facing, stats.tangentAngle());

            assertEquals(VCCChainConveyorMath.CONNECTION_RADIUS - VCCChainConveyorMath.LOOP_RADIUS,
                    stats.start().distanceTo(loopPosition), EPS);
        }
    }

    @Test
    void floorLoopPositionMatchesVanillaCreateGeometry() {
        BlockPos source = new BlockPos(-4, 9, 2);

        for (float angle : new float[] {0, 45, 90, 180, 270}) {
            Vec3 expected = new Vec3(source.getX() + 0.5, source.getY() + 0.375, source.getZ() + 0.5)
                    .add(VCCChainConveyorMath.rotateDegrees(new Vec3(0, 0, 0.875),
                            angle, Direction.Axis.Y));

            assertVecEquals(expected, VCCChainConveyorMath.loopPosition(source, Direction.DOWN, angle));
        }
    }

    @Test
    void sameAlignmentRequiresExactFacing() {
        for (Direction facing : Direction.values())
            assertTrue(VCCChainConveyorMath.sameAlignment(facing, facing));

        assertEquals(false, VCCChainConveyorMath.sameAlignment(Direction.WEST, Direction.EAST));
        assertEquals(false, VCCChainConveyorMath.sameAlignment(Direction.DOWN, Direction.UP));
        assertEquals(false, VCCChainConveyorMath.sameAlignment(Direction.NORTH, Direction.SOUTH));
    }

    @Test
    void travelPositionInterpolatesAndClampsToConnectionEndpoints() {
        VCCChainConveyorMath.ConnectionStats stats =
                new VCCChainConveyorMath.ConnectionStats(0, 10,
                        new Vec3(1, 2, 3), new Vec3(1, 2, 13));

        assertVecEquals(stats.start(), VCCChainConveyorMath.travelPosition(stats, -4));
        assertVecEquals(new Vec3(1, 2, 8), VCCChainConveyorMath.travelPosition(stats, 5));
        assertVecEquals(stats.end(), VCCChainConveyorMath.travelPosition(stats, 14));
    }

    @Test
    void connectionGeometryDetectsAxialConnectionsForEachAxis() {
        Map<Direction.Axis, BlockPos> axialConnections = Map.of(
                Direction.Axis.X, new BlockPos(6, 0, 0),
                Direction.Axis.Y, new BlockPos(0, 6, 0),
                Direction.Axis.Z, new BlockPos(0, 0, 6)
        );

        for (Map.Entry<Direction.Axis, BlockPos> entry : axialConnections.entrySet()) {
            VCCChainConveyorMath.ConnectionGeometry geometry =
                    VCCChainConveyorMath.connectionGeometry(entry.getValue(), entry.getKey());

            assertTrue(geometry.axiallyInvalid());
        }
    }

    @Test
    void connectionGeometryDetectsTooSteepConnectionsForEachAxis() {
        Map<Direction.Axis, BlockPos> steepConnections = Map.of(
                Direction.Axis.X, new BlockPos(5, 2, 2),
                Direction.Axis.Y, new BlockPos(2, 5, 2),
                Direction.Axis.Z, new BlockPos(2, 2, 5)
        );

        for (Map.Entry<Direction.Axis, BlockPos> entry : steepConnections.entrySet()) {
            VCCChainConveyorMath.ConnectionGeometry geometry =
                    VCCChainConveyorMath.connectionGeometry(entry.getValue(), entry.getKey());

            assertEquals(false, geometry.axiallyInvalid());
            assertTrue(geometry.tooSteep());
        }
    }

    @Test
    void connectionGeometryAllowsReasonablePlanarConnectionsForEachAxis() {
        Map<Direction.Axis, BlockPos> validConnections = Map.of(
                Direction.Axis.X, new BlockPos(1, 4, 4),
                Direction.Axis.Y, new BlockPos(4, 1, 4),
                Direction.Axis.Z, new BlockPos(4, 4, 1)
        );

        for (Map.Entry<Direction.Axis, BlockPos> entry : validConnections.entrySet()) {
            VCCChainConveyorMath.ConnectionGeometry geometry =
                    VCCChainConveyorMath.connectionGeometry(entry.getValue(), entry.getKey());

            assertEquals(false, geometry.axiallyInvalid());
            assertEquals(false, geometry.tooSteep());
        }
    }

    @Test
    void previewNormalIsPerpendicularToConnectionAndMountAxis() {
        BlockPos source = new BlockPos(1, 2, 3);

        for (Direction facing : Direction.values()) {
            BlockPos connection = connectionInPlane(facing.getAxis());
            VCCChainConveyorMath.ConnectionStats stats =
                    VCCChainConveyorMath.calculateConnectionStats(source, connection, facing, false);
            Vec3 normal = VCCChainConveyorMath.previewNormal(stats, facing, 0.875);
            Vec3 diff = stats.end().subtract(stats.start());
            Vec3 axis = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());

            assertEquals(0, normal.dot(diff), 1.0E-4);
            assertEquals(0, normal.dot(axis), 1.0E-4);
            assertEquals(0.875, normal.length(), EPS);
        }
    }

    @Test
    void previewNormalFallsBackWhenConnectionIsParallelToMountAxis() {
        VCCChainConveyorMath.ConnectionStats parallelStats =
                new VCCChainConveyorMath.ConnectionStats(0, 3,
                        new Vec3(0, 0, 0), new Vec3(3, 0, 0));

        Vec3 normal = VCCChainConveyorMath.previewNormal(parallelStats, Direction.EAST, 0.875);

        assertVecEquals(new Vec3(0, 0, 0.875), normal);
    }

    @Test
    void chainPointSamplingHandlesConnectionsParallelToMountAxisWithoutNaN() {
        BlockPos source = BlockPos.ZERO;
        Direction facing = Direction.EAST;
        Vec3 start = VCCChainConveyorMath.wheelCenter(source, facing).add(0, VCCChainConveyorMath.CONNECTION_RADIUS, 0);
        VCCChainConveyorMath.ConnectionStats stats =
                new VCCChainConveyorMath.ConnectionStats(0, 6, start, start.add(6, 0, 0));
        List<Vec3> points = new ArrayList<>();

        VCCChainConveyorMath.forPointsAlongChains(source, facing, stats, 9, points::add);

        assertEquals(9, points.size());
        for (Vec3 point : points)
            assertFinite(point);
    }

    @Test
    void chainPointSamplingKeepsTheTwoChainRunsSeparated() {
        BlockPos source = new BlockPos(1, 2, 3);
        VCCChainConveyorMath.ConnectionStats stats =
                VCCChainConveyorMath.calculateConnectionStats(source, new BlockPos(0, 4, 5),
                        Direction.EAST, Direction.UP, false);
        List<Vec3> points = new ArrayList<>();

        VCCChainConveyorMath.forPointsAlongChains(source, Direction.EAST, stats, 2, points::add);

        assertEquals(2, points.size());
        assertTrue(points.get(0).distanceTo(points.get(1)) > 0.1);
        points.forEach(VCCChainConveyorMathTest::assertFinite);
    }

    @Test
    void chainRunsUseTangentPathWithoutExtraPreviewNormalOffset() {
        BlockPos source = new BlockPos(1, 2, 3);
        VCCChainConveyorMath.ConnectionStats stats =
                VCCChainConveyorMath.calculateConnectionStats(source, new BlockPos(0, 5, 0),
                        Direction.WEST, false);

        VCCChainConveyorMath.ChainRuns runs =
                VCCChainConveyorMath.chainRuns(source, Direction.WEST, stats);

        assertVecEquals(stats.start(), runs.firstStart());
        assertVecEquals(stats.end(), runs.firstEnd());
        assertEquals(stats.chainLength(), runs.secondStart().distanceTo(runs.secondEnd()), EPS);
        assertTrue(runs.firstStart().distanceTo(runs.secondStart()) > 0.1);
        assertFinite(runs.secondStart());
        assertFinite(runs.secondEnd());
    }

    @Test
    void rotateDegreesKeepsRotationAxisComponentStable() {
        Vec3 vec = new Vec3(1, 2, 3);

        for (Direction.Axis axis : Direction.Axis.values()) {
            Vec3 rotated = VCCChainConveyorMath.rotateDegrees(vec, 123, axis);
            assertEquals(axisComponent(vec, axis), axisComponent(rotated, axis), EPS);
        }
    }

    @Test
    void horizontalYawIgnoresPureVerticalMotion() {
        assertNull(VCCChainConveyorMath.horizontalYaw(new Vec3(0, 1, 0)));
        assertEquals(0, VCCChainConveyorMath.horizontalYaw(new Vec3(1, 0, 0)), EPS);
        assertEquals(-90, VCCChainConveyorMath.horizontalYaw(new Vec3(0, 0, 1)), EPS);
    }

    private static BlockPos connectionInPlane(Direction.Axis axis) {
        return switch (axis) {
            case X -> new BlockPos(0, 4, 5);
            case Y -> new BlockPos(4, 0, 5);
            case Z -> new BlockPos(4, 5, 0);
        };
    }

    private static void assertStatsEqual(VCCChainConveyorMath.ConnectionStats expected,
            VCCChainConveyorMath.ConnectionStats actual) {
        assertEquals(expected.tangentAngle(), actual.tangentAngle(), EPS);
        assertEquals(expected.chainLength(), actual.chainLength(), EPS);
        assertVecEquals(expected.start(), actual.start());
        assertVecEquals(expected.end(), actual.end());
    }

    private static void assertVecEquals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPS);
        assertEquals(expected.y, actual.y, EPS);
        assertEquals(expected.z, actual.z, EPS);
    }

    private static void assertFinite(Vec3 vec) {
        assertTrue(Double.isFinite(vec.x), "x must be finite");
        assertTrue(Double.isFinite(vec.y), "y must be finite");
        assertTrue(Double.isFinite(vec.z), "z must be finite");
    }

    private static double radialDistance(Vec3 point, Vec3 faceCenter, Direction facing) {
        Vec3 inwardCenter = faceCenter.add(
                -facing.getStepX() * axisDistance(point, faceCenter, facing.getAxis()),
                -facing.getStepY() * axisDistance(point, faceCenter, facing.getAxis()),
                -facing.getStepZ() * axisDistance(point, faceCenter, facing.getAxis()));
        return point.subtract(inwardCenter).length();
    }

    private static double axisDistance(Vec3 point, Vec3 faceCenter, Direction.Axis axis) {
        return Math.abs(axisComponent(point, axis) - axisComponent(faceCenter, axis));
    }

    private static int axisStep(Direction facing) {
        return switch (facing.getAxis()) {
            case X -> facing.getStepX();
            case Y -> facing.getStepY();
            case Z -> facing.getStepZ();
        };
    }

    private static double axisComponent(Vec3 vec, Direction.Axis axis) {
        return switch (axis) {
            case X -> vec.x;
            case Y -> vec.y;
            case Z -> vec.z;
        };
    }

    private static void assertAxisComponentEquals(Direction.Axis axis, Vec3 expected, Vec3 actual) {
        switch (axis) {
            case X -> assertEquals(expected.x, actual.x, EPS);
            case Y -> assertEquals(expected.y, actual.y, EPS);
            case Z -> assertEquals(expected.z, actual.z, EPS);
        }
    }
}
