package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import com.osyris.verticalchainconveyors.AxisContext;
import com.osyris.verticalchainconveyors.VCCChainConveyorShapes;

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
        Direction.Axis axis = vcc_facing.getAxis();
        if (axis == Direction.Axis.X)
            angle = (float) ((Mth.RAD_TO_DEG * Mth.atan2(diff.z, diff.y) + 360 + 180) % 360);
        else if (axis == Direction.Axis.Z)
            angle = (float) ((Mth.RAD_TO_DEG * Mth.atan2(diff.y, diff.x) + 360 + 180) % 360);
        else
            angle = (float) ((Mth.RAD_TO_DEG * Mth.atan2(diff.x, diff.z) + 360 + 180) % 360);
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
        Direction.Axis axis = vcc_facing.getAxis();
        if (axis == Direction.Axis.X)
            radiusVec = new Vec3(0, VCC_RADIUS, 0);
        else if (axis == Direction.Axis.Z)
            radiusVec = new Vec3(VCC_RADIUS, 0, 0);
        else
            radiusVec = new Vec3(0, 0, VCC_RADIUS);

        Vec3 rotated = VecHelper.rotate(radiusVec, position, axis);
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

    /**
     * Create's drawOutline hardcodes AllShapes.CHAIN_CONVEYOR_INTERACTION — the
     * horizontal cross-disc — which ChainConveyorInteractionHandler renders when
     * the player hovers a conveyor while holding a chain, package, or frogport.
     * For non-DOWN facings we swap in the axis-relative shape so the outline
     * sits in the correct wheel plane.
     */
    @Inject(method = "drawOutline", at = @At("HEAD"), cancellable = true)
    private void vccDrawOutline(BlockPos pos, PoseStack poseStack, VertexConsumer vertexConsumer,
            CallbackInfo ci) {
        if (vcc_facing == null || vcc_facing == Direction.DOWN) return;

        TrackBlockOutline.renderShape(VCCChainConveyorShapes.forAxis(vcc_facing.getAxis()),
                poseStack, vertexConsumer, null);
        ci.cancel();
    }
}
