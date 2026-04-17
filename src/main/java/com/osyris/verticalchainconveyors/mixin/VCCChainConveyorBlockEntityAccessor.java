package com.osyris.verticalchainconveyors.mixin;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;

import net.minecraft.core.BlockPos;

@Mixin(value = ChainConveyorBlockEntity.class, remap = false)
public interface VCCChainConveyorBlockEntityAccessor {
    @Accessor("loopingPackages")
    List<ChainConveyorPackage> vcc_getLoopingPackages();

    @Accessor("travellingPackages")
    Map<BlockPos, List<ChainConveyorPackage>> vcc_getTravellingPackages();
}
