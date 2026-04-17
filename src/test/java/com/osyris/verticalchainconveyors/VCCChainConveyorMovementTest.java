package com.osyris.verticalchainconveyors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

class VCCChainConveyorMovementTest {

    @Test
    void reverseTravelPositionUsesActualMixedFacingChainLength() {
        BlockPos source = new BlockPos(2, 3, 4);
        VCCChainConveyorMath.ConnectionStats stats = null;
        float vanillaRawLength = 0;

        for (int x = -6; x <= 6 && stats == null; x++) {
            for (int y = -6; y <= 6 && stats == null; y++) {
                for (int z = -6; z <= 6; z++) {
                    BlockPos connection = new BlockPos(x, y, z);
                    if (connection.distSqr(BlockPos.ZERO) < 9)
                        continue;
                    VCCChainConveyorMath.ConnectionStats candidate =
                            VCCChainConveyorMath.calculateConnectionStats(source, connection,
                                    Direction.EAST, Direction.UP, false);
                    float rawLength = (float) Vec3.atLowerCornerOf(connection).length() - 22 / 16f;
                    if (Math.abs(rawLength - candidate.chainLength()) <= 0.05f)
                        continue;

                    stats = candidate;
                    vanillaRawLength = rawLength;
                    break;
                }
            }
        }

        assertNotNull(stats);
        float currentPosition = stats.chainLength() * 0.25f;

        assertTrue(Math.abs(vanillaRawLength - stats.chainLength()) > 0.05f);
        assertEquals(stats.chainLength() * 0.75f,
                VCCChainConveyorMath.reverseTravelPosition(stats.chainLength(), currentPosition),
                1.0E-5);
    }

    @Test
    void reverseTravelPositionClampsPastEndToStart() {
        assertEquals(0, VCCChainConveyorMath.reverseTravelPosition(5, 7), 1.0E-5);
    }

    @Test
    void reversedTargetEntryAngleMatchesTargetIncomingConnection() {
        BlockPos source = new BlockPos(0, 0, 0);
        BlockPos connection = new BlockPos(0, 4, 5);
        BlockPos connectionToSource = connection.multiply(-1);
        VCCChainConveyorMath.ConnectionStats targetStats =
                VCCChainConveyorMath.calculateConnectionStats(source.offset(connection),
                        connectionToSource, Direction.EAST, Direction.EAST, true);
        float targetEntryAngle = VCCChainConveyorMath.targetEntryAngle(connectionToSource,
                Direction.EAST, true);

        assertEquals(VCCChainConveyorMath.connectionEntryAngle(targetStats.tangentAngle(), true),
                targetEntryAngle, 1.0E-5);
    }

    @Test
    void sameAlignmentConnectionsUseObjectSpaceTargetEntryAngles() {
        BlockPos source = new BlockPos(0, 0, 0);

        for (Direction facing : Direction.values()) {
            if (!VCCChainConveyorMath.sameAlignment(facing, facing))
                continue;

            VCCChainConveyorMath.ConnectionStats sourceStats =
                    VCCChainConveyorMath.calculateConnectionStats(source, connectionInPlane(facing.getAxis()),
                            facing, facing, false);
            BlockPos targetToSource = connectionInPlane(facing.getAxis()).multiply(-1);

            assertEquals(VCCChainConveyorMath.remoteEntryAngle(sourceStats.tangentAngle(), false),
                    VCCChainConveyorMath.targetEntryAngle(targetToSource, facing, false),
                    1.0E-5);
        }
    }

    private static BlockPos connectionInPlane(Direction.Axis axis) {
        return switch (axis) {
            case X -> new BlockPos(0, 4, 5);
            case Y -> new BlockPos(4, 0, 5);
            case Z -> new BlockPos(4, 5, 0);
        };
    }
}
