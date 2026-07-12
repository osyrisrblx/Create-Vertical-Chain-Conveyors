package com.osyris.verticalchainconveyors.mixin;

import org.joml.FrustumIntersection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorVisual;
import com.osyris.verticalchainconveyors.VCCVisualSections;

import dev.engine_room.flywheel.api.visual.SectionTrackedVisual;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

@Mixin(value = AbstractBlockEntityVisual.class, remap = false)
public abstract class VCCBlockEntityVisualVisibilityMixin {

    @Shadow protected BlockEntity blockEntity;
    @Shadow protected BlockPos pos;
    @Shadow protected BlockPos visualPos;
    @Shadow protected SectionTrackedVisual.SectionCollector lightSections;

    @Inject(method = "setSectionCollector", at = @At("HEAD"), cancellable = true)
    private void vccTrackWholeChainConveyorBounds(SectionTrackedVisual.SectionCollector collector,
            CallbackInfo ci) {
        if (!((Object)this instanceof ChainConveyorVisual)) return;
        if (!(blockEntity instanceof ChainConveyorBlockEntity conveyor)) return;
        // when no connections exist, fall through to flywheel's default section
        // tracking so the visual stays reachable via the small-radius path; the
        // large-bounds override is only needed when chains extend past the block.
        if (conveyor.connections.isEmpty()) return;

        lightSections = collector;
        VCCVisualSections.updateTrackedSections(collector, blockEntity, conveyor.getRenderBoundingBox());
        ci.cancel();
    }

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void vccUseChainConveyorRenderBounds(FrustumIntersection frustum,
            CallbackInfoReturnable<Boolean> cir) {
        if (!((Object)this instanceof ChainConveyorVisual)) return;
        if (!(blockEntity instanceof ChainConveyorBlockEntity conveyor)) return;
        // empty connections → let flywheel's default sphere test handle visibility.
        // our large-AABB override caused idle, unconnected conveyors to cull incorrectly
        // when connections hadn't yet synced from the server.
        if (conveyor.connections.isEmpty()) return;

        AABB bounds = conveyor.getRenderBoundingBox()
                .move(visualPos.getX() - pos.getX(),
                        visualPos.getY() - pos.getY(),
                        visualPos.getZ() - pos.getZ());

        cir.setReturnValue(frustum.testAab(
                (float) bounds.minX, (float) bounds.minY, (float) bounds.minZ,
                (float) bounds.maxX, (float) bounds.maxY, (float) bounds.maxZ));
    }
}
