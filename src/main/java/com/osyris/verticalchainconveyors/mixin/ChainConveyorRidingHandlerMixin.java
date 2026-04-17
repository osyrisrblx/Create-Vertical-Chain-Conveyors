package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRidingHandler;
import com.osyris.verticalchainconveyors.VCCChainConveyorMath;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

@Mixin(value = ChainConveyorRidingHandler.class, remap = false)
public abstract class ChainConveyorRidingHandlerMixin {

    // ChainConveyorRidingHandler.clientTick computes the on-loop rider target as
    // atBottomCenterOf(pos).add(rotate((0, 0.25, 1), chainPosition, Axis.Y)).
    // that only works for facing=DOWN; for vertical mounts we need to orbit in
    // the wheel plane. We redirect both calls: atBottomCenterOf returns the full
    // axis-aware target, and VecHelper.rotate returns zero, so the .add() keeps
    // our value.

    @Redirect(method = "clientTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;atBottomCenterOf(Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/phys/Vec3;",
            remap = true))
    private static Vec3 vccRiderLoopCenter(net.minecraft.core.Vec3i vec) {
        BlockPos pos = (BlockPos) vec;
        Direction facing = vccFacingAt(pos);
        if (facing == null || facing == Direction.DOWN)
            return Vec3.atBottomCenterOf(vec);

        return VCCChainConveyorMath.riderLoopPosition(pos, facing,
                ChainConveyorRidingHandler.chainPosition);
    }

    @Redirect(method = "clientTick", at = @At(value = "INVOKE",
            target = "Lnet/createmod/catnip/math/VecHelper;rotate(Lnet/minecraft/world/phys/Vec3;DLnet/minecraft/core/Direction$Axis;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 vccRiderLoopOffset(Vec3 vec, double angle, Direction.Axis axis) {
        BlockPos pos = ChainConveyorRidingHandler.ridingChainConveyor;
        if (pos == null)
            return VecHelper.rotate(vec, angle, axis);
        Direction facing = vccFacingAt(pos);
        if (facing == null || facing == Direction.DOWN)
            return VecHelper.rotate(vec, angle, axis);

        // axis-aware target already baked into the redirected atBottomCenterOf
        return Vec3.ZERO;
    }

    private static Direction vccFacingAt(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return null;
        BlockState state = mc.level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.FACING))
            return null;
        return state.getValue(BlockStateProperties.FACING);
    }
}
