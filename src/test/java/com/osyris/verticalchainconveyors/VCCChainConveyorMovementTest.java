package com.osyris.verticalchainconveyors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

class VCCChainConveyorMovementTest {

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
    void sameFacingConnectionsUseObjectSpaceTargetEntryAngles() {
        BlockPos source = new BlockPos(0, 0, 0);

        for (Direction facing : Direction.values()) {
            if (!VCCChainConveyorMath.sameFacing(facing, facing))
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
