package com.osyris.verticalchainconveyors;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class VCCChainConveyorMath {
    public static final float OFF_BRANCH_DISTANCE = 35f;
    public static final double WHEEL_CENTER_BIAS = 0.125;
    public static final double CONNECTION_RADIUS = 1.25;
    public static final double LOOP_RADIUS = 0.875;

    private VCCChainConveyorMath() {}

    public record ConnectionStats(float tangentAngle, float chainLength, Vec3 start, Vec3 end) {}

    public record ChainRuns(Vec3 firstStart, Vec3 firstEnd, Vec3 secondStart, Vec3 secondEnd) {}

    public record ConnectionGeometry(double planarDistance, double axialDisplacement) {
        public boolean axiallyInvalid() {
            return planarDistance <= 0;
        }

        public boolean tooSteep() {
            return axialDisplacement / planarDistance > 1;
        }
    }

    public static ConnectionStats calculateConnectionStats(BlockPos sourcePos, BlockPos connection,
            Direction facing, boolean reversed) {
        return calculateConnectionStats(sourcePos, connection, facing, facing, reversed);
    }

    public static ConnectionStats calculateConnectionStats(BlockPos sourcePos, BlockPos connection,
            Direction sourceFacing, Direction targetFacing, boolean reversed) {
        Direction.Axis sourceAxis = sourceFacing.getAxis();
        Direction.Axis targetAxis = targetFacing.getAxis();
        float direction = connectionDirectionAngle(connection, sourceAxis);
        Vec3 sourceBaseVec = connectionBaseVec(sourceAxis, CONNECTION_RADIUS);

        float angle = wrapAngle(direction - OFF_BRANCH_DISTANCE * (reversed ? -1 : 1));
        float targetAngle = targetEntryAngle(connection.multiply(-1), targetFacing, reversed);

        Vec3 thisCenter = wheelCenter(sourcePos, sourceFacing);
        Vec3 otherCenter = wheelCenter(sourcePos.offset(connection), targetFacing);

        Vec3 start = thisCenter.add(rotateDegrees(sourceBaseVec, angle, sourceAxis));
        Vec3 end = otherCenter.add(rotateDegrees(connectionBaseVec(targetAxis, CONNECTION_RADIUS),
                targetAngle, targetAxis));

        return new ConnectionStats(angle, (float) start.distanceTo(end), start, end);
    }

    public static Vec3 wheelCenter(BlockPos pos, Direction facing) {
        return new Vec3(
                pos.getX() + 0.5 + facing.getStepX() * WHEEL_CENTER_BIAS,
                pos.getY() + 0.5 + facing.getStepY() * WHEEL_CENTER_BIAS,
                pos.getZ() + 0.5 + facing.getStepZ() * WHEEL_CENTER_BIAS
        );
    }

    public static Vec3 mountFaceCenter(BlockPos pos, Direction facing) {
        return new Vec3(
                pos.getX() + 0.5 + facing.getStepX() * 0.5,
                pos.getY() + 0.5 + facing.getStepY() * 0.5,
                pos.getZ() + 0.5 + facing.getStepZ() * 0.5
        );
    }

    public static Vec3 outlinePoint(BlockPos pos, Direction facing, double axialOffset, float angle) {
        Direction.Axis axis = facing.getAxis();
        Vec3 inward = new Vec3(-facing.getStepX() * axialOffset,
                -facing.getStepY() * axialOffset,
                -facing.getStepZ() * axialOffset);
        return mountFaceCenter(pos, facing)
                .add(inward)
                .add(rotateDegrees(connectionBaseVec(axis, CONNECTION_RADIUS), angle, axis));
    }

    public static Vec3 previewNormal(ConnectionStats stats, Direction facing, double scale) {
        Vec3 diff = stats.end().subtract(stats.start());
        Vec3 axisNormal = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Vec3 normal = diff.cross(axisNormal);
        if (normal.lengthSqr() < 1.0E-7)
            normal = fallbackPreviewNormal(facing.getAxis());
        return normal.normalize().scale(scale);
    }

    public static Vec3 loopPosition(BlockPos pos, Direction facing, float chainPosition) {
        return wheelCenter(pos, facing)
                .add(rotateDegrees(loopRadiusVec(facing.getAxis(), LOOP_RADIUS),
                        chainPosition, facing.getAxis()));
    }

    public static Vec3 travelPosition(ConnectionStats stats, float chainPosition) {
        if (stats.chainLength() <= 1.0E-5)
            return stats.start();

        float clamped = Mth.clamp(chainPosition, 0, stats.chainLength());
        Vec3 direction = stats.end().subtract(stats.start()).normalize();
        return stats.start().add(direction.scale(clamped));
    }

    public static Float horizontalYaw(Vec3 direction) {
        Vec3 horizontal = direction.multiply(1, 0, 1);
        if (horizontal.lengthSqr() < 1.0E-5)
            return null;
        return Mth.wrapDegrees((float) Mth.atan2(horizontal.x, horizontal.z) * Mth.RAD_TO_DEG - 90);
    }

    public static float defaultYaw(Direction facing) {
        return switch (facing) {
            case EAST, WEST -> 0;
            case NORTH, SOUTH -> -90;
            default -> 0;
        };
    }

    public static boolean sameAlignment(Direction sourceFacing, Direction targetFacing) {
        return sourceFacing == targetFacing;
    }

    public static float targetEntryAngle(BlockPos connectionToSource, Direction targetFacing, boolean reversed) {
        float targetDepartureAngle = connectionDirectionAngle(connectionToSource, targetFacing.getAxis())
                - OFF_BRANCH_DISTANCE * (reversed ? -1 : 1);
        return connectionEntryAngle(wrapAngle(targetDepartureAngle), reversed);
    }

    public static float remoteEntryAngle(float sourceTangentAngle, boolean reversed) {
        return wrapAngle(sourceTangentAngle + 180
                + 2 * OFF_BRANCH_DISTANCE * (reversed ? -1 : 1));
    }

    public static float connectionEntryAngle(float tangentAngle, boolean reversed) {
        return wrapAngle(tangentAngle + 2 * OFF_BRANCH_DISTANCE * (reversed ? -1 : 1));
    }

    public static float reverseTravelPosition(float chainLength, float chainPosition) {
        return Math.max(0, chainLength - chainPosition);
    }

    public static void forPointsAlongChains(BlockPos sourcePos, Direction sourceFacing,
            ConnectionStats stats, int positions, Consumer<Vec3> callback) {
        if (stats.end().distanceToSqr(stats.start()) < 1.0E-7 || positions <= 0)
            return;

        ChainRuns runs = chainRuns(sourcePos, sourceFacing, stats);
        Vec3 direction = stats.end().subtract(stats.start());

        for (boolean firstChain : new boolean[] {true, false}) {
            int steps = positions / 2;
            if (firstChain)
                steps += positions % 2;
            if (steps <= 0)
                continue;

            for (int i = 0; i < steps; i++)
                callback.accept((firstChain ? runs.firstStart() : runs.secondStart())
                        .add(direction.scale((0.5 + i) / steps)));
        }
    }

    public static ChainRuns chainRuns(BlockPos sourcePos, Direction sourceFacing, ConnectionStats stats) {
        Vec3 direction = stats.end().subtract(stats.start());
        if (direction.lengthSqr() < 1.0E-7)
            return new ChainRuns(stats.start(), stats.end(), stats.start(), stats.end());

        Vec3 origin = wheelCenter(sourcePos, sourceFacing);
        Vec3 normal = previewNormal(stats, sourceFacing, 1);
        Vec3 offset = stats.start().subtract(origin);
        Vec3 secondStart = origin.add(offset.add(normal.scale(-2 * normal.dot(offset))));
        Vec3 secondEnd = secondStart.add(direction);
        return new ChainRuns(stats.start(), stats.end(), secondStart, secondEnd);
    }

    public static ConnectionGeometry connectionGeometry(BlockPos connection, Direction.Axis axis) {
        Vec3 diff = Vec3.atLowerCornerOf(connection);
        double planarDistance;
        double axialDisplacement;

        if (axis == Direction.Axis.X) {
            planarDistance = diff.multiply(0, 1, 1).length() - 1.5;
            axialDisplacement = Math.abs(diff.x);
        } else if (axis == Direction.Axis.Z) {
            planarDistance = diff.multiply(1, 1, 0).length() - 1.5;
            axialDisplacement = Math.abs(diff.z);
        } else {
            planarDistance = diff.multiply(1, 0, 1).length() - 1.5;
            axialDisplacement = Math.abs(diff.y);
        }

        return new ConnectionGeometry(planarDistance, axialDisplacement);
    }

    public static float wrapAngle(float angle) {
        angle %= 360;
        if (angle < 0)
            angle += 360;
        return angle;
    }

    public static float connectionDirectionAngle(BlockPos connection, Direction.Axis axis) {
        return switch (axis) {
            case X -> Mth.RAD_TO_DEG * (float) Mth.atan2(connection.getZ(), connection.getY());
            case Z -> Mth.RAD_TO_DEG * (float) Mth.atan2(connection.getY(), connection.getX());
            default -> Mth.RAD_TO_DEG * (float) Mth.atan2(connection.getX(), connection.getZ());
        };
    }

    public static Vec3 connectionBaseVec(Direction.Axis axis, double radius) {
        return switch (axis) {
            case X -> new Vec3(0, radius, 0);
            case Z -> new Vec3(radius, 0, 0);
            default -> new Vec3(0, 0, radius);
        };
    }

    public static Vec3 loopRadiusVec(Direction.Axis axis, double radius) {
        return switch (axis) {
            case X -> new Vec3(0, radius, 0);
            case Z -> new Vec3(radius, 0, 0);
            default -> new Vec3(0, 0, radius);
        };
    }

    public static Vec3 fallbackPreviewNormal(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vec3(0, 0, 1);
            case Z -> new Vec3(0, 1, 0);
            default -> new Vec3(1, 0, 0);
        };
    }

    // rider-on-loop target position. matches vanilla for facing=DOWN
    // (Vec3.atBottomCenterOf(pos).add(rotate((0, 0.25, 1), angle, Y))) and
    // generalizes to any mounting face: orbit in the wheel plane at radius 1,
    // offset 0.25 away from the mounting face.
    public static Vec3 riderLoopPosition(BlockPos pos, Direction facing, float chainPosition) {
        double dangle = 0.25;
        Vec3 dangleOffset = new Vec3(
                -facing.getStepX() * dangle,
                -facing.getStepY() * dangle,
                -facing.getStepZ() * dangle);
        return mountFaceCenter(pos, facing)
                .add(dangleOffset)
                .add(rotateDegrees(loopRadiusVec(facing.getAxis(), 1.0),
                        chainPosition, facing.getAxis()));
    }

    public static Vec3 rotateDegrees(Vec3 vec, float angle, Direction.Axis axis) {
        double radians = angle * Mth.DEG_TO_RAD;
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);

        return switch (axis) {
            case X -> new Vec3(vec.x, vec.y * cos - vec.z * sin, vec.y * sin + vec.z * cos);
            case Y -> new Vec3(vec.x * cos + vec.z * sin, vec.y, vec.z * cos - vec.x * sin);
            case Z -> new Vec3(vec.x * cos - vec.y * sin, vec.x * sin + vec.y * cos, vec.z);
        };
    }
}
