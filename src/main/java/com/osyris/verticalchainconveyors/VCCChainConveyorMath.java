package com.osyris.verticalchainconveyors;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

public final class VCCChainConveyorMath {
    public static final float OFF_BRANCH_DISTANCE = 35f;
    public static final double WHEEL_CENTER_BIAS = 0.125;
    public static final double CONNECTION_RADIUS = 1.25;
    public static final double LOOP_RADIUS = 0.875;

    private VCCChainConveyorMath() {}

    public record ConnectionStats(float tangentAngle, float chainLength, Vec3 start, Vec3 end) {}

    public record ChainRuns(Vec3 firstStart, Vec3 firstEnd, Vec3 secondStart, Vec3 secondEnd) {}

    private record RunAngles(float sourceAngle, float targetAngle) {}

    public record ConnectionGeometry(double planarDistance, double axialDisplacement) {
        public boolean axiallyInvalid() {
            return planarDistance <= 0;
        }

        public boolean tooSteep() {
            return axialDisplacement / planarDistance > 1;
        }
    }

    public record ConnectionValidation(boolean axiallyInvalid, boolean tooSteep) {}

    /**
     * Apply the configurable steepness policy without weakening same-axis links.
     * Different-facing conveyors can still share an axis (for example floor to
     * ceiling), so compare the axes rather than the Direction values.
     */
    public static boolean tooSteepUnderPolicy(ConnectionValidation validation,
            Direction sourceFacing, Direction targetFacing,
            boolean allowSteepMixedAxisConnections) {
        if (!validation.tooSteep())
            return false;
        return !allowSteepMixedAxisConnections
                || sourceFacing.getAxis() == targetFacing.getAxis();
    }

    public static ConnectionStats calculateConnectionStats(BlockPos sourcePos, BlockPos connection,
            Direction facing, boolean reversed) {
        return calculateConnectionStats(sourcePos, connection, facing, facing, reversed);
    }

    public static ConnectionStats calculateConnectionStats(BlockPos sourcePos, BlockPos connection,
            Direction sourceFacing, Direction targetFacing, boolean reversed) {
        Direction.Axis sourceAxis = sourceFacing.getAxis();
        Direction.Axis targetAxis = targetFacing.getAxis();
        Vec3 sourceBaseVec = connectionBaseVec(sourceAxis, CONNECTION_RADIUS);

        RunAngles angles = runAngles(connection, sourceFacing, targetFacing, reversed);
        float angle = angles.sourceAngle();
        float targetAngle = angles.targetAngle();

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

    /** Local-space ray-picking volume for the wheel loop. */
    public static AABB loopInteractionBounds(Direction facing) {
        return switch (facing) {
            case UP -> new AABB(-0.5, 0.5, -0.5, 1.5, 1.0, 1.5);
            case EAST -> new AABB(0.5, -0.5, -0.5, 1.0, 1.5, 1.5);
            case WEST -> new AABB(0.0, -0.5, -0.5, 0.5, 1.5, 1.5);
            case SOUTH -> new AABB(-0.5, -0.5, 0.5, 1.5, 1.5, 1.0);
            case NORTH -> new AABB(-0.5, -0.5, 0.0, 1.5, 1.5, 0.5);
            default -> new AABB(-0.5, 0.0, -0.5, 1.5, 0.5, 1.5);
        };
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

    public static boolean sameFacing(Direction sourceFacing, Direction targetFacing) {
        return sourceFacing == targetFacing;
    }

    public static Direction rotateFacing(Direction facing, Rotation rotation) {
        return rotation.rotate(facing);
    }

    public static Direction mirrorFacing(Direction facing, Mirror mirror) {
        return mirror.mirror(facing);
    }

    public static float rotationSpeedModifier(BlockPos connection, Direction sourceFacing, Direction targetFacing) {
        if (sameFacing(sourceFacing, targetFacing))
            return 1;

        return projectedTangentVelocityModifier(connection, sourceFacing, targetFacing);
    }

    public static boolean effectiveConnectionReversed(BlockPos connection, Direction sourceFacing,
            Direction targetFacing, boolean localReversed, boolean powered) {
        if (!powered || sameFacing(sourceFacing, targetFacing) || !usePairedRunForConnection(connection))
            return localReversed;

        return rotationSpeedModifier(connection, sourceFacing, targetFacing) < 0
                ? !localReversed
                : localReversed;
    }

    public static float targetEntryAngle(BlockPos connectionToSource, Direction targetFacing, boolean reversed) {
        float targetDepartureAngle = connectionDirectionAngle(connectionToSource, targetFacing.getAxis())
                - OFF_BRANCH_DISTANCE * (reversed ? -1 : 1);
        return connectionEntryAngle(wrapAngle(targetDepartureAngle), reversed);
    }

    public static float targetEntryAngle(BlockPos connectionToSource, Direction sourceFacing,
            Direction targetFacing, boolean reversed) {
        if (sameFacing(sourceFacing, targetFacing))
            return targetEntryAngle(connectionToSource, targetFacing, reversed);

        BlockPos connection = connectionToSource.multiply(-1);
        return runAngles(connection, sourceFacing, targetFacing, reversed).targetAngle();
    }

    public static float sourceTangentAngle(BlockPos connection, Direction sourceFacing, boolean reversed) {
        return wrapAngle(connectionDirectionAngle(connection, sourceFacing.getAxis())
                - branchOffset(reversed));
    }

    public static float targetTangentAngle(BlockPos connection, Direction sourceFacing,
            Direction targetFacing, boolean reversed) {
        float legacyAngle = targetEntryAngle(connection.multiply(-1), targetFacing, reversed);
        if (sameFacing(sourceFacing, targetFacing))
            return legacyAngle;

        float pairedAngle = legacyTargetPairedAngle(legacyAngle, reversed);
        return useLegacyTargetTangent(connection, sourceFacing, targetFacing, reversed,
                legacyAngle, pairedAngle) ? legacyAngle : pairedAngle;
    }

    public static float targetPairedTangentAngle(BlockPos connection, Direction sourceFacing,
            Direction targetFacing, boolean reversed) {
        float legacyAngle = targetEntryAngle(connection.multiply(-1), targetFacing, reversed);
        float pairedAngle = legacyTargetPairedAngle(legacyAngle, reversed);
        if (sameFacing(sourceFacing, targetFacing))
            return pairedAngle;

        return useLegacyTargetTangent(connection, sourceFacing, targetFacing, reversed,
                legacyAngle, pairedAngle) ? pairedAngle : legacyAngle;
    }

    public static float remoteEntryAngle(float sourceTangentAngle, boolean reversed) {
        return wrapAngle(sourceTangentAngle + 180
                + 2 * OFF_BRANCH_DISTANCE * (reversed ? -1 : 1));
    }

    public static float connectionEntryAngle(float tangentAngle, boolean reversed) {
        return wrapAngle(tangentAngle + 2 * OFF_BRANCH_DISTANCE * (reversed ? -1 : 1));
    }

    public static void forPointsAlongChains(BlockPos sourcePos, Direction sourceFacing,
            ConnectionStats stats, int positions, Consumer<Vec3> callback) {
        forPointsAlongChains(sourcePos, sourceFacing, sourceFacing, null, false,
                stats, positions, callback);
    }

    public static void forPointsAlongChains(BlockPos sourcePos, Direction sourceFacing, Direction targetFacing,
            BlockPos connection, boolean reversed, ConnectionStats stats, int positions, Consumer<Vec3> callback) {
        if (stats.end().distanceToSqr(stats.start()) < 1.0E-7 || positions <= 0)
            return;

        ChainRuns runs = connection == null
                ? chainRuns(sourcePos, sourceFacing, stats)
                : chainRuns(sourcePos, sourceFacing, targetFacing, connection, reversed, stats);
        Vec3 firstDirection = runs.firstEnd().subtract(runs.firstStart());
        Vec3 secondDirection = runs.secondEnd().subtract(runs.secondStart());

        for (boolean firstChain : new boolean[] {true, false}) {
            int steps = positions / 2;
            if (firstChain)
                steps += positions % 2;
            if (steps <= 0)
                continue;

            for (int i = 0; i < steps; i++)
                callback.accept((firstChain ? runs.firstStart() : runs.secondStart())
                        .add((firstChain ? firstDirection : secondDirection)
                                .scale((0.5 + i) / steps)));
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

    public static ChainRuns chainRuns(BlockPos sourcePos, Direction sourceFacing, Direction targetFacing,
            BlockPos connection, boolean reversed, ConnectionStats stats) {
        if (sameFacing(sourceFacing, targetFacing))
            return chainRuns(sourcePos, sourceFacing, stats);

        Vec3 sourceCenter = wheelCenter(sourcePos, sourceFacing);
        Vec3 targetCenter = wheelCenter(sourcePos.offset(connection), targetFacing);
        Direction.Axis sourceAxis = sourceFacing.getAxis();
        Direction.Axis targetAxis = targetFacing.getAxis();

        RunAngles first = runAngles(connection, sourceFacing, targetFacing, reversed);
        RunAngles second = otherRunAngles(connection, sourceFacing, targetFacing, reversed);

        Vec3 firstStart = sourceCenter.add(rotateDegrees(connectionBaseVec(sourceAxis, CONNECTION_RADIUS),
                first.sourceAngle(), sourceAxis));
        Vec3 firstEnd = targetCenter.add(rotateDegrees(connectionBaseVec(targetAxis, CONNECTION_RADIUS),
                first.targetAngle(), targetAxis));
        Vec3 secondStart = sourceCenter.add(rotateDegrees(connectionBaseVec(sourceAxis, CONNECTION_RADIUS),
                second.sourceAngle(), sourceAxis));
        Vec3 secondEnd = targetCenter.add(rotateDegrees(connectionBaseVec(targetAxis, CONNECTION_RADIUS),
                second.targetAngle(), targetAxis));
        return new ChainRuns(firstStart, firstEnd, secondStart, secondEnd);
    }

    public static ConnectionGeometry connectionGeometry(BlockPos connection, Direction.Axis axis) {
        return connectionGeometry(Vec3.atLowerCornerOf(connection), axis);
    }

    /**
     * Validate a link from both wheel planes.  For mixed-axis conveyors this must
     * be symmetric: selecting A then B cannot produce a different result from
     * selecting B then A.  Wheel-centre bias is included because it does not
     * cancel when the mounting directions differ.
     */
    public static ConnectionValidation connectionValidation(BlockPos connection,
            Direction sourceFacing, Direction targetFacing) {
        Vec3 sourceCenter = wheelCenter(BlockPos.ZERO, sourceFacing);
        Vec3 targetCenter = wheelCenter(connection, targetFacing);
        Vec3 centerDiff = targetCenter.subtract(sourceCenter);

        ConnectionGeometry source = connectionGeometry(centerDiff, sourceFacing.getAxis());
        ConnectionGeometry target = connectionGeometry(centerDiff, targetFacing.getAxis());
        boolean axiallyInvalid = source.axiallyInvalid() || target.axiallyInvalid();
        boolean tooSteep = !axiallyInvalid && (source.tooSteep() || target.tooSteep());
        return new ConnectionValidation(axiallyInvalid, tooSteep);
    }

    public static ConnectionGeometry connectionGeometry(Vec3 diff, Direction.Axis axis) {
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

    private static float branchOffset(boolean reversed) {
        return OFF_BRANCH_DISTANCE * (reversed ? -1 : 1);
    }

    private static float legacyTargetPairedAngle(float legacyTargetAngle, boolean reversed) {
        return wrapAngle(legacyTargetAngle - 2 * branchOffset(reversed));
    }

    private static boolean usePairedRunForConnection(BlockPos connection) {
        if (connection.getX() != 0)
            return connection.getX() < 0;
        if (connection.getY() != 0)
            return connection.getY() < 0;
        return connection.getZ() < 0;
    }

    private static RunAngles runAngles(BlockPos connection, Direction sourceFacing,
            Direction targetFacing, boolean reversed) {
        if (sameFacing(sourceFacing, targetFacing) || !usePairedRunForConnection(connection))
            return new RunAngles(sourceTangentAngle(connection, sourceFacing, reversed),
                    targetTangentAngle(connection, sourceFacing, targetFacing, reversed));

        BlockPos canonicalConnection = connection.multiply(-1);
        return new RunAngles(
                targetPairedTangentAngle(canonicalConnection, targetFacing, sourceFacing, reversed),
                connectionEntryAngle(sourceTangentAngle(canonicalConnection, targetFacing, reversed), reversed));
    }

    private static RunAngles otherRunAngles(BlockPos connection, Direction sourceFacing,
            Direction targetFacing, boolean reversed) {
        if (sameFacing(sourceFacing, targetFacing))
            return new RunAngles(connectionEntryAngle(sourceTangentAngle(connection, sourceFacing, reversed), reversed),
                    targetPairedTangentAngle(connection, sourceFacing, targetFacing, reversed));

        if (!usePairedRunForConnection(connection))
            return new RunAngles(connectionEntryAngle(sourceTangentAngle(connection, sourceFacing, reversed), reversed),
                    targetPairedTangentAngle(connection, sourceFacing, targetFacing, reversed));

        BlockPos canonicalConnection = connection.multiply(-1);
        return new RunAngles(
                targetTangentAngle(canonicalConnection, targetFacing, sourceFacing, reversed),
                sourceTangentAngle(canonicalConnection, targetFacing, reversed));
    }

    private static boolean useLegacyTargetTangent(BlockPos connection, Direction sourceFacing,
            Direction targetFacing, boolean reversed, float legacyAngle, float pairedAngle) {
        Direction.Axis sourceAxis = sourceFacing.getAxis();
        Direction.Axis targetAxis = targetFacing.getAxis();
        float sourceFirst = sourceTangentAngle(connection, sourceFacing, reversed);
        float sourceSecond = connectionEntryAngle(sourceFirst, reversed);
        Vec3 sourcePairOffset = rotateDegrees(connectionBaseVec(sourceAxis, CONNECTION_RADIUS),
                sourceSecond, sourceAxis)
                .subtract(rotateDegrees(connectionBaseVec(sourceAxis, CONNECTION_RADIUS),
                        sourceFirst, sourceAxis));

        Vec3 targetPairOffset = rotateDegrees(connectionBaseVec(targetAxis, CONNECTION_RADIUS),
                pairedAngle, targetAxis)
                .subtract(rotateDegrees(connectionBaseVec(targetAxis, CONNECTION_RADIUS),
                        legacyAngle, targetAxis));

        return sourcePairOffset.dot(targetPairOffset) >= -1.0E-7;
    }

    private static float projectedTangentVelocityModifier(BlockPos connection, Direction sourceFacing,
            Direction targetFacing) {
        Direction.Axis sourceAxis = sourceFacing.getAxis();
        Direction.Axis targetAxis = targetFacing.getAxis();
        RunAngles angles = runAngles(connection, sourceFacing, targetFacing, false);
        Vec3 sourceRadius = rotateDegrees(connectionBaseVec(sourceAxis, CONNECTION_RADIUS),
                angles.sourceAngle(), sourceAxis);
        Vec3 targetRadius = rotateDegrees(connectionBaseVec(targetAxis, CONNECTION_RADIUS),
                angles.targetAngle(), targetAxis);
        Vec3 sourceCenter = wheelCenter(BlockPos.ZERO, sourceFacing);
        Vec3 targetCenter = wheelCenter(connection, targetFacing);
        Vec3 runDirection = targetCenter.add(targetRadius)
                .subtract(sourceCenter.add(sourceRadius));
        if (runDirection.lengthSqr() < 1.0E-7)
            return 1;
        runDirection = runDirection.normalize();

        double sourceProjection = positiveAxis(sourceAxis).cross(sourceRadius).dot(runDirection);
        double targetProjection = positiveAxis(targetAxis).cross(targetRadius).dot(runDirection);
        if (Math.abs(sourceProjection) < 1.0E-7 || Math.abs(targetProjection) < 1.0E-7)
            return 1;
        return sourceProjection * targetProjection >= 0 ? 1 : -1;
    }

    private static Vec3 positiveAxis(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Vec3(1, 0, 0);
            case Y -> new Vec3(0, 1, 0);
            case Z -> new Vec3(0, 0, 1);
        };
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
