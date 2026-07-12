package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorShape;
import com.simibubi.create.content.trains.track.TrackBlockOutline;
import com.osyris.verticalchainconveyors.VCCChainConveyorBBFacing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Rotates Create's custom loop outline into the mounted wheel plane. */
@Mixin(value = ChainConveyorShape.ChainConveyorBB.class, remap = false)
public abstract class ChainConveyorShapeBBClientMixin {

    @Inject(method = "drawOutline", at = @At("HEAD"), cancellable = true)
    private void vccDrawAxisAwareOutline(BlockPos anchor, PoseStack poseStack,
            VertexConsumer consumer, CallbackInfo ci) {
        Direction facing = ((VCCChainConveyorBBFacing)(Object)this).vccFacing();
        if (facing == null || facing.getAxis() == Direction.Axis.Y)
            return;

        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.mulPose((facing.getAxis() == Direction.Axis.X ? Axis.ZP : Axis.XP).rotationDegrees(90));
        poseStack.translate(-0.5, -0.5, -0.5);
        TrackBlockOutline.renderShape(AllShapes.CHAIN_CONVEYOR_INTERACTION, poseStack, consumer, null);
        ci.cancel();
    }
}
