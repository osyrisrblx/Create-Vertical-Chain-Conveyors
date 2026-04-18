package com.osyris.verticalchainconveyors;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * axis-relative outline/interaction shapes for the chain conveyor. the Y-axis
 * shape mirrors Create's CHAIN_CONVEYOR_INTERACTION cross-disc; the X and Z
 * variants rotate the same disc so it sits in the correct wheel plane.
 *
 * shared between ChainConveyorBlockMixin (block outline / interaction shape)
 * and ChainConveyorShapeBBMixin.drawOutline (ChainConveyorInteractionHandler's
 * custom hover outline, which otherwise hardcodes the horizontal shape).
 */
public final class VCCChainConveyorShapes {

    private VCCChainConveyorShapes() {}

    private static final VoxelShape[] SHAPES = buildShapes();

    public static VoxelShape forAxis(Direction.Axis axis) {
        return SHAPES[axis.ordinal()];
    }

    private static VoxelShape[] buildShapes() {
        VoxelShape y = Shapes.or(
                box(-10, 2, 0, 26, 14, 16),
                box(0, 2, -10, 16, 14, 26),
                box(-5, 2, -5, 21, 14, 21),
                Shapes.block()
        );
        VoxelShape x = Shapes.or(
                box(2, -10, 0, 14, 26, 16),
                box(2, 0, -10, 14, 16, 26),
                box(2, -5, -5, 14, 21, 21),
                Shapes.block()
        );
        VoxelShape z = Shapes.or(
                box(-10, 0, 2, 26, 16, 14),
                box(0, -10, 2, 16, 26, 14),
                box(-5, -5, 2, 21, 21, 14),
                Shapes.block()
        );
        VoxelShape[] shapes = new VoxelShape[3];
        shapes[Direction.Axis.X.ordinal()] = x;
        shapes[Direction.Axis.Y.ordinal()] = y;
        shapes[Direction.Axis.Z.ordinal()] = z;
        return shapes;
    }

    private static VoxelShape box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Shapes.box(x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16);
    }
}
