package com.osyris.verticalchainconveyors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

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
    void ascendingEastToNorthCornerUsesAlignedTargetTangentSide() {
        BlockPos source = new BlockPos(0, 0, 0);
        BlockPos connection = new BlockPos(1, 1, 1);
        Direction sourceFacing = Direction.EAST;
        Direction targetFacing = Direction.NORTH;

        float targetAngle = VCCChainConveyorMath.targetTangentAngle(connection,
                sourceFacing, targetFacing, false);

        assertEquals(190, targetAngle, EPS);
    }

    @Test
    void mixedFacingTargetTangentKeepsPairedChainRunsAligned() {
        BlockPos[] connections = {
                new BlockPos(1, 1, 1),
                new BlockPos(1, 1, -1),
                new BlockPos(-1, 1, 1),
                new BlockPos(-1, 1, -1),
                new BlockPos(2, 3, 1),
                new BlockPos(-2, 3, 1)
        };

        for (Direction sourceFacing : Direction.values()) {
            for (Direction targetFacing : Direction.values()) {
                if (VCCChainConveyorMath.sameFacing(sourceFacing, targetFacing))
                    continue;

                for (BlockPos connection : connections) {
                    for (boolean reversed : new boolean[] {false, true}) {
                        Vec3 sourcePairOffset = tangentPairOffset(connection,
                                sourceFacing, reversed, true);
                        Vec3 targetPairOffset = tangentPairOffset(connection,
                                sourceFacing, targetFacing, reversed);

                        assertTrue(sourcePairOffset.dot(targetPairOffset) >= -1.0E-5,
                                sourceFacing + " -> " + targetFacing + " via " + connection
                                        + " reversed=" + reversed);
                    }
                }
            }
        }
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
    void sameFacingRequiresExactFacing() {
        for (Direction facing : Direction.values())
            assertTrue(VCCChainConveyorMath.sameFacing(facing, facing));

        assertEquals(false, VCCChainConveyorMath.sameFacing(Direction.WEST, Direction.EAST));
        assertEquals(false, VCCChainConveyorMath.sameFacing(Direction.DOWN, Direction.UP));
        assertEquals(false, VCCChainConveyorMath.sameFacing(Direction.NORTH, Direction.SOUTH));
    }

    @Test
    void mixedFacingRotationModifiersKeepSelectedChainRunMovingOneWay() {
        for (Direction sourceFacing : Direction.values()) {
            for (Direction targetFacing : Direction.values()) {
                if (sourceFacing == targetFacing)
                    continue;

                for (BlockPos connection : representativeMixedFacingConnections()) {
                    VCCChainConveyorMath.ConnectionStats stats =
                            VCCChainConveyorMath.calculateConnectionStats(BlockPos.ZERO, connection,
                                    sourceFacing, targetFacing, false);
                    Vec3 direction = stats.end().subtract(stats.start());
                    if (direction.lengthSqr() < 1.0E-7)
                        continue;
                    direction = direction.normalize();

                    Vec3 sourceRadius = stats.start()
                            .subtract(VCCChainConveyorMath.wheelCenter(BlockPos.ZERO, sourceFacing));
                    Vec3 targetRadius = stats.end()
                            .subtract(VCCChainConveyorMath.wheelCenter(connection, targetFacing));
                    double sourceProjection = positiveAxis(sourceFacing.getAxis()).cross(sourceRadius)
                            .dot(direction);
                    double targetProjection = positiveAxis(targetFacing.getAxis()).cross(targetRadius)
                            .scale(VCCChainConveyorMath.rotationSpeedModifier(connection,
                                    sourceFacing, targetFacing))
                            .dot(direction);

                    if (Math.abs(sourceProjection) < 1.0E-7 || Math.abs(targetProjection) < 1.0E-7)
                        continue;

                    assertTrue(sourceProjection * targetProjection > 0,
                            sourceFacing + " -> " + targetFacing + " via " + connection
                                    + " should not reverse the selected chain run");
                }
            }
        }
    }

    @Test
    void mixedFacingRotationModifiersAreReciprocal() {
        for (Direction sourceFacing : Direction.values()) {
            for (Direction targetFacing : Direction.values()) {
                if (sourceFacing == targetFacing)
                    continue;

                for (BlockPos connection : representativeMixedFacingConnections()) {
                    float forward = VCCChainConveyorMath.rotationSpeedModifier(connection,
                            sourceFacing, targetFacing);
                    float backward = VCCChainConveyorMath.rotationSpeedModifier(connection.multiply(-1),
                            targetFacing, sourceFacing);

                    assertEquals(forward, backward, EPS,
                            sourceFacing + " <-> " + targetFacing + " via " + connection
                                    + " should transfer rotation symmetrically");
                }
            }
        }
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
    void mixedAxisConnectionValidationIsSymmetric() {
        BlockPos connection = new BlockPos(5, 2, 2);
        VCCChainConveyorMath.ConnectionValidation forward =
                VCCChainConveyorMath.connectionValidation(connection, Direction.EAST, Direction.UP);
        VCCChainConveyorMath.ConnectionValidation reverse =
                VCCChainConveyorMath.connectionValidation(connection.multiply(-1), Direction.UP, Direction.EAST);

        assertEquals(forward, reverse);
        assertTrue(forward.tooSteep() || forward.axiallyInvalid());
    }

    @Test
    void connectionValidationIsSymmetricAcrossFacingsAndOffsets() {
        for (Direction sourceFacing : Direction.values()) {
            for (Direction targetFacing : Direction.values()) {
                for (BlockPos connection : representativeMixedFacingConnections()) {
                    VCCChainConveyorMath.ConnectionValidation forward =
                            VCCChainConveyorMath.connectionValidation(connection,
                                    sourceFacing, targetFacing);
                    VCCChainConveyorMath.ConnectionValidation reverse =
                            VCCChainConveyorMath.connectionValidation(connection.multiply(-1),
                                    targetFacing, sourceFacing);
                    assertEquals(forward, reverse,
                            sourceFacing + " <-> " + targetFacing + " via " + connection);
                }
            }
        }
    }

    @Test
    void connectionValidationIsSymmetricAcrossAnExhaustiveLocalCube() {
        for (Direction sourceFacing : Direction.values()) {
            for (Direction targetFacing : Direction.values()) {
                for (int x = -4; x <= 4; x++) {
                    for (int y = -4; y <= 4; y++) {
                        for (int z = -4; z <= 4; z++) {
                            if (x == 0 && y == 0 && z == 0)
                                continue;
                            BlockPos connection = new BlockPos(x, y, z);
                            assertEquals(
                                    VCCChainConveyorMath.connectionValidation(connection,
                                            sourceFacing, targetFacing),
                                    VCCChainConveyorMath.connectionValidation(connection.multiply(-1),
                                            targetFacing, sourceFacing),
                                    sourceFacing + " <-> " + targetFacing + " via " + connection);
                        }
                    }
                }
            }
        }
    }

    @Test
    void sameFacingValidationMatchesSingleAxisGeometry() {
        for (Direction facing : Direction.values()) {
            for (BlockPos connection : representativeMixedFacingConnections()) {
                VCCChainConveyorMath.ConnectionGeometry geometry =
                        VCCChainConveyorMath.connectionGeometry(connection, facing.getAxis());
                VCCChainConveyorMath.ConnectionValidation validation =
                        VCCChainConveyorMath.connectionValidation(connection, facing, facing);

                assertEquals(geometry.axiallyInvalid(), validation.axiallyInvalid());
                assertEquals(!geometry.axiallyInvalid() && geometry.tooSteep(), validation.tooSteep());
            }
        }
    }

    @Test
    void slopeThresholdIsInclusiveAndRejectsOnlyValuesAboveOne() {
        VCCChainConveyorMath.ConnectionGeometry atLimit =
                VCCChainConveyorMath.connectionGeometry(new Vec3(3.5, 2, 0), Direction.Axis.Y);
        VCCChainConveyorMath.ConnectionGeometry aboveLimit =
                VCCChainConveyorMath.connectionGeometry(new Vec3(3.5, 2.000001, 0), Direction.Axis.Y);

        assertEquals(2, atLimit.planarDistance(), EPS);
        assertFalse(atLimit.tooSteep());
        assertTrue(aboveLimit.tooSteep());
    }

    @Test
    void loopInteractionBoundsFollowEveryMountingFace() {
        for (Direction facing : Direction.values()) {
            AABB bounds = VCCChainConveyorMath.loopInteractionBounds(facing);
            Vec3 center = bounds.getCenter();
            double expectedAxisCenter = 0.5 + axisStep(facing) * 0.25;
            assertEquals(expectedAxisCenter, axisComponent(center, facing.getAxis()), EPS);
            assertEquals(0.5, axisSize(bounds, facing.getAxis()), EPS);
        }
    }

    @Test
    void loopInteractionBoundsAreRayPickableFromTheirMountingFace() {
        for (Direction facing : Direction.values()) {
            AABB bounds = VCCChainConveyorMath.loopInteractionBounds(facing);
            Vec3 axis = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 center = new Vec3(0.5, 0.5, 0.5);
            Vec3 hit = bounds.clip(center.add(axis.scale(2)), center.subtract(axis.scale(2)))
                    .orElseThrow(() -> new AssertionError("No hit for " + facing));

            assertEquals(0.5 + axisStep(facing) * 0.5,
                    axisComponent(hit, facing.getAxis()), EPS, facing.toString());
        }
    }

    @Test
    void loopInteractionBoundsRejectRaysOutsideTheWheelSpan() {
        for (Direction facing : Direction.values()) {
            AABB bounds = VCCChainConveyorMath.loopInteractionBounds(facing);
            Vec3 axis = Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 outside = switch (facing.getAxis()) {
                case X -> new Vec3(0.5, 2.0, 0.5);
                case Y -> new Vec3(2.0, 0.5, 0.5);
                case Z -> new Vec3(2.0, 0.5, 0.5);
            };

            assertTrue(bounds.clip(outside.add(axis.scale(2)), outside.subtract(axis.scale(2))).isEmpty(),
                    facing.toString());
        }
    }

    @Test
    void facingTransformsRoundTripForEveryDirection() {
        for (Direction facing : Direction.values()) {
            Direction rotated = facing;
            for (int i = 0; i < 4; i++)
                rotated = VCCChainConveyorMath.rotateFacing(rotated, Rotation.CLOCKWISE_90);
            assertEquals(facing, rotated);

            for (Mirror mirror : Mirror.values()) {
                Direction mirrored = VCCChainConveyorMath.mirrorFacing(facing, mirror);
                assertEquals(facing, VCCChainConveyorMath.mirrorFacing(mirrored, mirror));
            }
        }
    }

    @Test
    void poweredReciprocalStatsUseOppositeRunsForEveryFacingPair() {
        BlockPos[] connections = representativeMixedFacingConnections().toArray(BlockPos[]::new);
        for (Direction sourceFacing : Direction.values()) {
            for (Direction targetFacing : Direction.values()) {
                if (sourceFacing == targetFacing)
                    continue;
                assertPoweredReciprocalMixedFacingStatsRenderOppositeChainRuns(
                        BlockPos.ZERO, connections, sourceFacing, targetFacing);
            }
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
    void mixedFacingChainRunsUseTargetWheelPairedTangent() {
        BlockPos source = new BlockPos(0, 0, 0);
        BlockPos connection = new BlockPos(1, 1, 1);
        Direction sourceFacing = Direction.EAST;
        Direction targetFacing = Direction.NORTH;
        VCCChainConveyorMath.ConnectionStats stats =
                VCCChainConveyorMath.calculateConnectionStats(source, connection,
                        sourceFacing, targetFacing, false);

        VCCChainConveyorMath.ChainRuns runs =
                VCCChainConveyorMath.chainRuns(source, sourceFacing, targetFacing,
                        connection, false, stats);

        Vec3 targetCenter = VCCChainConveyorMath.wheelCenter(source.offset(connection), targetFacing);
        assertAxisComponentEquals(targetFacing.getAxis(), targetCenter, runs.secondEnd());
        assertEquals(VCCChainConveyorMath.CONNECTION_RADIUS,
                runs.secondEnd().distanceTo(targetCenter), EPS);
        assertTrue(runs.secondEnd().subtract(runs.firstEnd())
                .dot(runs.secondStart().subtract(runs.firstStart())) >= -1.0E-5);
    }

    @Test
    void reciprocalMixedFacingStatsRenderOppositeChainRunsForEveryWallCorner() {
        BlockPos source = new BlockPos(0, 0, 0);
        Direction[] wallFacings = {
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
        };
        BlockPos[] connections = {
                new BlockPos(1, 1, 1),
                new BlockPos(1, 1, -1),
                new BlockPos(-1, 1, 1),
                new BlockPos(-1, 1, -1),
                new BlockPos(1, -1, 1),
                new BlockPos(1, -1, -1),
                new BlockPos(-1, -1, 1),
                new BlockPos(-1, -1, -1)
        };

        for (int i = 0; i < wallFacings.length; i++) {
            assertReciprocalMixedFacingStatsRenderOppositeChainRuns(source, connections,
                    wallFacings[i], wallFacings[(i + 1) % wallFacings.length]);
            assertReciprocalMixedFacingStatsRenderOppositeChainRuns(source, connections,
                    wallFacings[i], wallFacings[(i + wallFacings.length - 1) % wallFacings.length]);
        }
    }

    @Test
    void poweredReciprocalMixedFacingStatsUseOppositeChainRunsWhenRotationIsInverted() {
        BlockPos source = new BlockPos(0, 0, 0);
        Direction[] wallFacings = {
                Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
        };
        BlockPos[] connections = {
                new BlockPos(1, 1, 1),
                new BlockPos(1, 1, -1),
                new BlockPos(-1, 1, 1),
                new BlockPos(-1, 1, -1),
                new BlockPos(1, -1, 1),
                new BlockPos(1, -1, -1),
                new BlockPos(-1, -1, 1),
                new BlockPos(-1, -1, -1)
        };

        for (int i = 0; i < wallFacings.length; i++) {
            assertPoweredReciprocalMixedFacingStatsRenderOppositeChainRuns(source, connections,
                    wallFacings[i], wallFacings[(i + 1) % wallFacings.length]);
            assertPoweredReciprocalMixedFacingStatsRenderOppositeChainRuns(source, connections,
                    wallFacings[i], wallFacings[(i + wallFacings.length - 1) % wallFacings.length]);
        }
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

    private static List<BlockPos> representativeMixedFacingConnections() {
        return List.of(
                new BlockPos(1, 1, -1),
                new BlockPos(1, 1, 1),
                new BlockPos(-1, 1, -1),
                new BlockPos(2, 3, 4),
                new BlockPos(-2, 3, 4),
                new BlockPos(2, -3, 4),
                new BlockPos(2, 3, -4),
                new BlockPos(-2, -3, 4),
                new BlockPos(-2, 3, -4),
                new BlockPos(2, -3, -4),
                new BlockPos(-2, -3, -4)
        );
    }

    private static Vec3 positiveAxis(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vec3(1, 0, 0);
            case Y -> new Vec3(0, 1, 0);
            case Z -> new Vec3(0, 0, 1);
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

    private static void assertVecEquals(Vec3 expected, Vec3 actual, String message) {
        assertEquals(expected.x, actual.x, EPS, message);
        assertEquals(expected.y, actual.y, EPS, message);
        assertEquals(expected.z, actual.z, EPS, message);
    }

    private static void assertReciprocalMixedFacingStatsRenderOppositeChainRuns(BlockPos source,
            BlockPos[] connections, Direction sourceFacing, Direction targetFacing) {
        for (BlockPos connection : connections) {
            for (boolean reversed : new boolean[] {false, true}) {
                VCCChainConveyorMath.ConnectionStats sourceStats =
                        VCCChainConveyorMath.calculateConnectionStats(source, connection,
                                sourceFacing, targetFacing, reversed);
                VCCChainConveyorMath.ChainRuns runs =
                        VCCChainConveyorMath.chainRuns(source, sourceFacing, targetFacing,
                                connection, reversed, sourceStats);

                VCCChainConveyorMath.ConnectionStats targetStats =
                        VCCChainConveyorMath.calculateConnectionStats(source.offset(connection),
                                connection.multiply(-1), targetFacing, sourceFacing, reversed);

                String message = sourceFacing + " -> " + targetFacing + " via " + connection
                        + " reversed=" + reversed;
                assertVecEquals(runs.firstStart(), sourceStats.start(), message + " source start");
                assertVecEquals(runs.firstEnd(), sourceStats.end(), message + " source end");
                assertVecEquals(runs.secondEnd(), targetStats.start(), message + " reciprocal start");
                assertVecEquals(runs.secondStart(), targetStats.end(), message + " reciprocal end");
            }
        }
    }

    private static void assertPoweredReciprocalMixedFacingStatsRenderOppositeChainRuns(BlockPos source,
            BlockPos[] connections, Direction sourceFacing, Direction targetFacing) {
        for (BlockPos connection : connections) {
            for (boolean sourceReversed : new boolean[] {false, true}) {
                float modifier = VCCChainConveyorMath.rotationSpeedModifier(connection,
                        sourceFacing, targetFacing);
                boolean targetReversed = modifier < 0 ? !sourceReversed : sourceReversed;
                boolean sourceStatsReversed = VCCChainConveyorMath.effectiveConnectionReversed(connection,
                        sourceFacing, targetFacing, sourceReversed, true);
                boolean targetStatsReversed = VCCChainConveyorMath.effectiveConnectionReversed(
                        connection.multiply(-1), targetFacing, sourceFacing, targetReversed, true);

                VCCChainConveyorMath.ConnectionStats sourceStats =
                        VCCChainConveyorMath.calculateConnectionStats(source, connection,
                                sourceFacing, targetFacing, sourceStatsReversed);
                VCCChainConveyorMath.ChainRuns runs =
                        VCCChainConveyorMath.chainRuns(source, sourceFacing, targetFacing,
                                connection, sourceStatsReversed, sourceStats);

                VCCChainConveyorMath.ConnectionStats targetStats =
                        VCCChainConveyorMath.calculateConnectionStats(source.offset(connection),
                                connection.multiply(-1), targetFacing, sourceFacing, targetStatsReversed);

                String message = sourceFacing + " -> " + targetFacing + " via " + connection
                        + " sourceReversed=" + sourceReversed + " targetReversed=" + targetReversed
                        + " sourceStatsReversed=" + sourceStatsReversed
                        + " targetStatsReversed=" + targetStatsReversed + " modifier=" + modifier;
                assertVecEquals(runs.firstStart(), sourceStats.start(), message + " source start");
                assertVecEquals(runs.firstEnd(), sourceStats.end(), message + " source end");
                assertVecEquals(runs.secondEnd(), targetStats.start(), message + " reciprocal start");
                assertVecEquals(runs.secondStart(), targetStats.end(), message + " reciprocal end");
            }
        }
    }

    private static Vec3 tangentPairOffset(BlockPos connection, Direction facing,
            boolean reversed, boolean source) {
        Direction.Axis axis = facing.getAxis();
        float first = source
                ? VCCChainConveyorMath.sourceTangentAngle(connection, facing, reversed)
                : VCCChainConveyorMath.targetEntryAngle(connection.multiply(-1), facing, reversed);
        float second = source
                ? VCCChainConveyorMath.connectionEntryAngle(first, reversed)
                : VCCChainConveyorMath.wrapAngle(first
                        - 2 * VCCChainConveyorMath.OFF_BRANCH_DISTANCE * (reversed ? -1 : 1));

        return VCCChainConveyorMath.rotateDegrees(
                        VCCChainConveyorMath.connectionBaseVec(axis,
                                VCCChainConveyorMath.CONNECTION_RADIUS),
                        second, axis)
                .subtract(VCCChainConveyorMath.rotateDegrees(
                        VCCChainConveyorMath.connectionBaseVec(axis,
                                VCCChainConveyorMath.CONNECTION_RADIUS),
                        first, axis));
    }

    private static Vec3 tangentPairOffset(BlockPos connection, Direction sourceFacing,
            Direction targetFacing, boolean reversed) {
        Direction.Axis axis = targetFacing.getAxis();
        float first = VCCChainConveyorMath.targetTangentAngle(connection,
                sourceFacing, targetFacing, reversed);
        float second = VCCChainConveyorMath.targetPairedTangentAngle(connection,
                sourceFacing, targetFacing, reversed);

        return VCCChainConveyorMath.rotateDegrees(
                        VCCChainConveyorMath.connectionBaseVec(axis,
                                VCCChainConveyorMath.CONNECTION_RADIUS),
                        second, axis)
                .subtract(VCCChainConveyorMath.rotateDegrees(
                        VCCChainConveyorMath.connectionBaseVec(axis,
                                VCCChainConveyorMath.CONNECTION_RADIUS),
                        first, axis));
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

    private static double axisSize(AABB bounds, Direction.Axis axis) {
        return switch (axis) {
            case X -> bounds.getXsize();
            case Y -> bounds.getYsize();
            case Z -> bounds.getZsize();
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
