package com.osyris.verticalchainconveyors;

import net.minecraft.core.Direction;

/**
 * thread-local storage used to pass the current conveyor's mounting direction from
 * ChainConveyorBlockEntityMixin.updateChainShapes into ChainConveyorShapeBBMixin's
 * constructor, without changing ChainConveyorBB's constructor signature.
 *
 * the "facing" is the direction the block's BOTTOM (mounting surface) points —
 * i.e., the opposite of the face the player clicked when placing it.
 * DOWN = floor mount (original horizontal behaviour)
 * UP   = ceiling mount
 * N/S/E/W = wall mounts
 */
public final class AxisContext {

    private AxisContext() {}

    public static final ThreadLocal<Direction> CURRENT_FACING =
            ThreadLocal.withInitial(() -> Direction.DOWN);
}
