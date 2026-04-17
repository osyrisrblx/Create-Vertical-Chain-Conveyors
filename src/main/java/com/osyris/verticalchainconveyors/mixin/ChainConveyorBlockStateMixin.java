package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * injects into Block.createBlockStateDefinition (m_7926_) and adds the AXIS
 * property when the block being constructed is a ChainConveyorBlock.
 *
 * ChainConveyorBlock does not override createBlockStateDefinition itself, so
 * the injection must live on the declaring class (Block) with an instanceof check.
 */
@Mixin(value = Block.class, remap = false)
public abstract class ChainConveyorBlockStateMixin {

    @Inject(method = "m_7926_", at = @At("TAIL"))
    private void vccAddFacingProperty(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        if ((Object) this instanceof ChainConveyorBlock) {
            builder.add(BlockStateProperties.FACING);
        }
    }
}
