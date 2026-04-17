package com.osyris.verticalchainconveyors.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorVisual;

import dev.engine_room.flywheel.lib.instance.TransformedInstance;

@Mixin(value = ChainConveyorVisual.class, remap = false)
public interface VCCChainVisualAccessor {
    @Accessor("guards")
    List<TransformedInstance> vcc_getGuards();

    @Invoker("setupGuards")
    void vcc_setupGuards();
}
