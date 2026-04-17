package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import com.osyris.verticalchainconveyors.AxisContext;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * makes ChainConveyorBB work for all 6 mounting directions. the loop centre and
 * rotation axis change per facing; the radius stays fixed at 0.875 (same as the
 * original private final field).
 */
@Mixin(value = ChainConveyorShape.ChainConveyorBB.class, remap = false)
public abstract class ChainConveyorShapeBBMixin {

    // ChainConveyorBB.radius == 0.875 (final field, same for all axes)
    @Unique private static final double VCC_RADIUS = 0.875;

    /** captured from the thread-local at construction time */
    @Unique private Direction vcc_facing;

    /**
     * loop centre in LOCAL block coords. for Y-axis facing=DOWN the original bounds.getCenter()
     * equals (0.5, 0.25, 0.5) — the block centre in the wheel plane, shifted 0.25 from the
     * mounting face along the axis. we mirror that pattern for every facing.
     */
    @Unique private Vec3 vcc_center;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void vccCaptureFacing(Vec3 center, CallbackInfo ci) {
        vcc_facing = AxisContext.CURRENT_FACING.get();

        if (vcc_facing == null || vcc_facing == Direction.DOWN) {
            // facing=DOWN matches Create's original hard-coded path — leave it alone
            vcc_center = null;
        } else {
            vcc_center = new Vec3(
                    0.5 + vcc_facing.getStepX() * 0.25,
                    0.5 + vcc_facing.getStepY() * 0.25,
                    0.5 + vcc_facing.getStepZ() * 0.25
            );
        }
    }

    /**
     * return the discretised chain position (0–315 in 45° steps) for a package
     * at the given intersection point. diff components chosen per right-hand-rule
     * cyclic pattern so angle 0° matches the baseVec direction used in getVec.
     */
    @Inject(method = "getChainPosition", at = @At("HEAD"), cancellable = true)
    private void vccGetChainPosition(Vec3 intersection, CallbackInfoReturnable<Float> cir) {
        if (vcc_center == null) return;

        Vec3 diff = vcc_center.subtract(intersection);
        float angle;
        switch (vcc_facing.getAxis()) {
            case X:
                angle = (float) ((Mth.RAD_TO_DEG * Mth.atan2(diff.z, diff.y) + 360 + 180) % 360);
                break;
            case Z:
                angle = (float) ((Mth.RAD_TO_DEG * Mth.atan2(diff.y, diff.x) + 360 + 180) % 360);
                break;
            default: // Y (facing=UP falls here; facing=DOWN returned early above)
                angle = (float) ((Mth.RAD_TO_DEG * Mth.atan2(diff.x, diff.z) + 360 + 180) % 360);
                break;
        }
        cir.setReturnValue(Math.round(angle / 45) * 45f);
    }

    /**
     * world-space position of a package at the given chain position. mirrors the
     * original formula:
     *   bounds.getCenter() + rotate(baseVec, position, axis) + atLowerCornerOf(anchor) + axialOffset
     * where axialOffset shifts the loop toward the mounting face (e.g. (0, -0.125, 0) for DOWN).
     */
    @Inject(method = "getVec", at = @At("HEAD"), cancellable = true)
    private void vccGetVec(BlockPos anchor, float position, CallbackInfoReturnable<Vec3> cir) {
        if (vcc_center == null) return;

        Vec3 radiusVec;
        switch (vcc_facing.getAxis()) {
            case X:
                radiusVec = new Vec3(0, VCC_RADIUS, 0);
                break;
            case Z:
                radiusVec = new Vec3(VCC_RADIUS, 0, 0);
                break;
            default: // Y
                radiusVec = new Vec3(0, 0, VCC_RADIUS);
                break;
        }

        Vec3 rotated = VecHelper.rotate(radiusVec, position, vcc_facing.getAxis());
        Vec3 axialOffset = new Vec3(
                vcc_facing.getStepX() * 0.125,
                vcc_facing.getStepY() * 0.125,
                vcc_facing.getStepZ() * 0.125
        );

        Vec3 point = vcc_center.add(rotated)
                .add(Vec3.atLowerCornerOf(anchor))
                .add(axialOffset);
        cir.setReturnValue(point);
    }
}
