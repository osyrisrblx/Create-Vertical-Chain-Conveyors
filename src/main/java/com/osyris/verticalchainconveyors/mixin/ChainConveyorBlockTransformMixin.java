package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock;
import com.osyris.verticalchainconveyors.VCCChainConveyorMath;

import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Rotates the FACING property added to every ChainConveyorBlock subclass. */
@Mixin(value = BlockBehaviour.class, remap = false)
public abstract class ChainConveyorBlockTransformMixin {

    @Inject(method = "rotate", at = @At("HEAD"), cancellable = true)
    private void vccRotateFacing(BlockState state, Rotation rotation,
            CallbackInfoReturnable<BlockState> cir) {
        if ((Object) this instanceof ChainConveyorBlock
                && state.hasProperty(BlockStateProperties.FACING))
            cir.setReturnValue(state.setValue(BlockStateProperties.FACING,
                    VCCChainConveyorMath.rotateFacing(
                            state.getValue(BlockStateProperties.FACING), rotation)));
    }

    @Inject(method = "mirror", at = @At("HEAD"), cancellable = true)
    private void vccMirrorFacing(BlockState state, Mirror mirror,
            CallbackInfoReturnable<BlockState> cir) {
        if ((Object) this instanceof ChainConveyorBlock
                && state.hasProperty(BlockStateProperties.FACING))
            cir.setReturnValue(state.setValue(BlockStateProperties.FACING,
                    VCCChainConveyorMath.mirrorFacing(
                            state.getValue(BlockStateProperties.FACING), mirror)));
    }
}
