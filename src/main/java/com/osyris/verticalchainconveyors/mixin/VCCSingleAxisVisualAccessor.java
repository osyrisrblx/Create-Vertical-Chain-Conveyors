package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

@Mixin(value = SingleAxisRotatingVisual.class, remap = false)
public interface VCCSingleAxisVisualAccessor {
    @Accessor("rotatingModel")
    RotatingInstance vcc_getRotatingModel();
}
