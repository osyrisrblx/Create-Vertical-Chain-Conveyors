package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.simibubi.create.foundation.blockEntity.CachedRenderBBBlockEntity;

@Mixin(value = CachedRenderBBBlockEntity.class, remap = false)
public interface VCCCachedRenderBBAccessor {
    @Invoker("invalidateRenderBoundingBox")
    void vcc_invalidateRenderBoundingBox();
}
