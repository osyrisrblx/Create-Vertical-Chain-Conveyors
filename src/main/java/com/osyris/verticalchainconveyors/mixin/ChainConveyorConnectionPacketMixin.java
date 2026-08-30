package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionPacket;
import com.osyris.verticalchainconveyors.VCCChainConveyorMath;
import com.osyris.verticalchainconveyors.VCCServerConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

@Mixin(value = ChainConveyorConnectionPacket.class, remap = false)
public abstract class ChainConveyorConnectionPacketMixin {

    /**
     * Create performs placement validation on the client before sending this
     * packet. Repeat the geometry policy here so the synchronized server config
     * remains authoritative for mismatched or modified clients.
     */
    @Inject(method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;"
            + "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private void vccEnforceConnectionPolicy(ServerPlayer player, ChainConveyorBlockEntity source,
            CallbackInfo ci) {
        VCCChainConveyorConnectionPacketAccessor packet =
                (VCCChainConveyorConnectionPacketAccessor)(Object)this;
        if (!packet.vcc_isConnect())
            return;

        BlockPos targetPos = packet.vcc_getTargetPos();
        if (!(source.getLevel().getBlockEntity(targetPos) instanceof ChainConveyorBlockEntity target))
            return;

        BlockState sourceState = source.getBlockState();
        BlockState targetState = target.getBlockState();
        if (!sourceState.hasProperty(BlockStateProperties.FACING)
                || !targetState.hasProperty(BlockStateProperties.FACING))
            return;

        Direction sourceFacing = sourceState.getValue(BlockStateProperties.FACING);
        Direction targetFacing = targetState.getValue(BlockStateProperties.FACING);
        VCCChainConveyorMath.ConnectionValidation validation =
                VCCChainConveyorMath.connectionValidation(
                        targetPos.subtract(source.getBlockPos()), sourceFacing, targetFacing);

        if (validation.axiallyInvalid()
                || VCCChainConveyorMath.tooSteepUnderPolicy(validation,
                        sourceFacing, targetFacing,
                        VCCServerConfig.allowSteepMixedAxisConnections()))
            ci.cancel();
    }
}
