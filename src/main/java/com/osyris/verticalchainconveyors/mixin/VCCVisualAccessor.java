package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import dev.engine_room.flywheel.api.visual.SectionTrackedVisual;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(value = AbstractBlockEntityVisual.class, remap = false)
public interface VCCVisualAccessor {
    @Accessor("blockState")
    BlockState vcc_getBlockState();

    @Accessor("blockEntity")
    BlockEntity vcc_getBlockEntity();

    @Accessor("visualPos")
    BlockPos vcc_getVisualPos();

    @Accessor("lightSections")
    SectionTrackedVisual.SectionCollector vcc_getLightSections();
}
