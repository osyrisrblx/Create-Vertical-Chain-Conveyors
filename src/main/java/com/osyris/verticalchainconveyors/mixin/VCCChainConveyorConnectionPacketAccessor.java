package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionPacket;

import net.minecraft.core.BlockPos;

@Mixin(value = ChainConveyorConnectionPacket.class, remap = false)
public interface VCCChainConveyorConnectionPacketAccessor {
    @Accessor("targetPos")
    BlockPos vcc_getTargetPos();

    @Accessor("connect")
    boolean vcc_isConnect();
}
