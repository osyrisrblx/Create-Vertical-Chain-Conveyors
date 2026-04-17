package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.osyris.verticalchainconveyors.AxisContext;
import com.osyris.verticalchainconveyors.VCCChainConveyorMath;
import com.osyris.verticalchainconveyors.VerticalChainConveyors;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Mixin(value = ChainConveyorBlockEntity.class, remap = false)
public abstract class ChainConveyorBlockEntityMixin {

    @Shadow public abstract void prepareStats();
    @Shadow public Map<BlockPos, ConnectionStats> connectionStats;
    @Shadow public Set<BlockPos> connections;
    @Shadow List<ChainConveyorPackage> loopingPackages;
    @Shadow Map<BlockPos, List<ChainConveyorPackage>> travellingPackages;
    @Shadow public boolean reversed;
    @Shadow private void updateChainShapes() {}

    @Unique private final java.util.IdentityHashMap<ChainConveyorPackage, Float> vcc_packageYaws =
            new java.util.IdentityHashMap<>();
    @Unique private boolean vcc_pendingVisualRefresh;

    @Inject(method = "read", at = @At("TAIL"))
    private void vccRefreshVisualsAfterRead(CompoundTag tag, boolean clientPacket, CallbackInfo ci) {
        BlockEntity self = (BlockEntity)(Object)this;
        Level level = self.getLevel();
        vcc_pendingVisualRefresh = level == null || level.isClientSide();
        if (level == null || !level.isClientSide()) return;

        vccRefreshClientVisuals(self, level);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void vccRefreshVisualsAfterLoad(CallbackInfo ci) {
        if (!vcc_pendingVisualRefresh) return;

        BlockEntity self = (BlockEntity)(Object)this;
        Level level = self.getLevel();
        if (level == null || !level.isClientSide()) return;

        vccRefreshClientVisuals(self, level);
    }

    @Unique
    private void vccRefreshClientVisuals(BlockEntity self, Level level) {
        prepareStats();
        updateChainShapes();
        ((VCCCachedRenderBBAccessor)(Object)this).vcc_invalidateRenderBoundingBox();

        if (VisualizationManager.supportsVisualization(level)) {
            VisualizationHelper.tryAddBlockEntity(self);
            VisualizationHelper.queueUpdate(self);
        }

        vcc_pendingVisualRefresh = false;
        ((KineticBlockEntity)(Object)this).requestModelDataUpdate();

        BlockState state = self.getBlockState();
        level.sendBlockUpdated(self.getBlockPos(), state, state, 16);
    }

    @Inject(method = "createRenderBoundingBox", at = @At("HEAD"), cancellable = true)
    private void vccUseConnectedRenderBounds(CallbackInfoReturnable<AABB> cir) {
        BlockEntity self = (BlockEntity)(Object)this;
        cir.setReturnValue(new AABB(self.getBlockPos()).inflate(connections.isEmpty() ? 3 : 64));
    }

    /**
     * push the block's facing into the thread-local before ChainConveyorBB is
     * constructed inside updateChainShapes, so ChainConveyorShapeBBMixin can read it.
     */
    @Inject(method = "updateChainShapes", at = @At("HEAD"))
    private void vccPushFacing(CallbackInfo ci) {
        BlockEntity self = (BlockEntity)(Object)this;
        AxisContext.CURRENT_FACING.set(vccFacing(self));
    }

    @Inject(method = "updateChainShapes", at = @At("RETURN"))
    private void vccClearFacing(CallbackInfo ci) {
        AxisContext.CURRENT_FACING.set(Direction.DOWN);
    }

    @Inject(method = "updateBoxWorldPositions", at = @At("TAIL"))
    private void vccStabilizeVerticalPackageYaw(CallbackInfo ci) {
        BlockEntity self = (BlockEntity)(Object)this;
        Direction facing = vccFacing(self);
        if (facing.getAxis() == Direction.Axis.Y) return;

        prepareStats();

        for (Map.Entry<BlockPos, List<ChainConveyorPackage>> entry : travellingPackages.entrySet()) {
            ConnectionStats stats = connectionStats.get(entry.getKey());
            if (stats == null) continue;

            Vec3 direction = stats.end().subtract(stats.start()).normalize();
            Float yaw = vccHorizontalYaw(direction);

            for (ChainConveyorPackage box : entry.getValue()) {
                box.yaw = vccRememberedYaw(box, yaw, facing);
            }
        }

        for (ChainConveyorPackage box : loopingPackages) {
            box.yaw = vccDefaultYaw(facing);
        }

        vcc_packageYaws.keySet().removeIf(box -> !loopingPackages.contains(box)
                && travellingPackages.values().stream().noneMatch(packages -> packages.contains(box)));
    }

    /**
     * replace the connection-stats calculation for non-DOWN facings. the chain
     * departs tangentially from the wheel; we compute the departure points in
     * the correct plane using the same right-hand-rule cyclic pattern.
     *
     * axis=Y: rotate around Y, atan2(x, z), baseVec = (0, 0, 1.25)  [facing=DOWN uses original; facing=UP uses ours with wheel at +Y side]
     * axis=X: rotate around X, atan2(z, y), baseVec = (0, 1.25, 0)
     * axis=Z: rotate around Z, atan2(y, x), baseVec = (1.25, 0, 0)
     */
    @Inject(method = "calculateConnectionStats", at = @At("HEAD"), cancellable = true)
    private void vccCalculateConnectionStats(BlockPos connection, CallbackInfo ci) {
        BlockEntity self = (BlockEntity)(Object)this;
        Direction facing = vccFacing(self);

        Direction targetFacing = facing;
        // during BlockEntity.load the level isn't attached yet; Create's read() calls
        // updateBoxWorldPositions → prepareStats → calculateConnectionStats before setLevel.
        // assume same-facing target in that case; prepareStats will recompute when the
        // BE later ticks with a valid level.
        Level level = self.getLevel();
        if (level != null) {
            BlockState targetState = level.getBlockState(self.getBlockPos().offset(connection));
            if (targetState.hasProperty(BlockStateProperties.FACING))
                targetFacing = targetState.getValue(BlockStateProperties.FACING);
        }

        // facing=DOWN to facing=DOWN matches the original hard-coded behaviour; skip our override for it
        if (facing == Direction.DOWN && targetFacing == Direction.DOWN) return;

        boolean reversed = ((KineticBlockEntity)(Object)this).getSpeed() < 0;
        VCCChainConveyorMath.ConnectionStats stats =
                VCCChainConveyorMath.calculateConnectionStats(self.getBlockPos(), connection,
                        facing, targetFacing, reversed);
        connectionStats.put(connection, new ConnectionStats(stats.tangentAngle(), stats.chainLength(),
                stats.start(), stats.end()));
        ci.cancel();
    }

    @Redirect(method = "tick", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;length()D", remap = true))
    private double vccUseActualChainLengthWhenFlippingTravellingPackage(Vec3 offsetVec) {
        BlockEntity self = (BlockEntity)(Object)this;
        BlockPos offset = new BlockPos(Mth.floor(offsetVec.x), Mth.floor(offsetVec.y), Mth.floor(offsetVec.z));
        ConnectionStats stats = connectionStats.get(offset);
        if (stats == null)
            return offsetVec.length();

        Direction sourceFacing = vccFacing(self);
        Direction targetFacing = sourceFacing;
        BlockState targetState = self.getLevel().getBlockState(self.getBlockPos().offset(offset));
        if (targetState.hasProperty(BlockStateProperties.FACING))
            targetFacing = targetState.getValue(BlockStateProperties.FACING);
        if (sourceFacing == Direction.DOWN && targetFacing == Direction.DOWN)
            return offsetVec.length();

        return stats.chainLength() + 22 / 16f;
    }

    @Inject(method = "addConnectionTo", at = @At("HEAD"), cancellable = true)
    private void vccRejectMisalignedConnection(BlockPos target, CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = (BlockEntity)(Object)this;
        if (self.getLevel() == null)
            return;

        BlockState sourceState = self.getBlockState();
        BlockState targetState = self.getLevel().getBlockState(target);
        if (!sourceState.hasProperty(BlockStateProperties.FACING)
                || !targetState.hasProperty(BlockStateProperties.FACING))
            return;

        if (!VCCChainConveyorMath.sameAlignment(sourceState.getValue(BlockStateProperties.FACING),
                targetState.getValue(BlockStateProperties.FACING)))
            cir.setReturnValue(false);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorBlockEntity;addLoopingPackage(Lcom/simibubi/create/content/kinetics/chainConveyor/ChainConveyorPackage;)Z"))
    private boolean vccAddLoopingPackageAtTargetEntry(ChainConveyorBlockEntity target,
            ChainConveyorPackage box) {
        BlockEntity source = (BlockEntity)(Object)this;
        BlockEntity targetEntity = target;
        Direction sourceFacing = vccFacing(source);
        Direction targetFacing = vccFacing(targetEntity);

        if (sourceFacing == Direction.DOWN && targetFacing == Direction.DOWN)
            return target.addLoopingPackage(box);

        BlockPos connectionToSource = source.getBlockPos().subtract(targetEntity.getBlockPos());
        BlockPos connectionToTarget = targetEntity.getBlockPos().subtract(source.getBlockPos());
        boolean sourceReversed = reversed;

        prepareStats();
        ConnectionStats sourceStats = connectionStats.get(connectionToTarget);
        if (sourceStats != null && VCCChainConveyorMath.sameAlignment(sourceFacing, targetFacing)) {
            box.chainPosition = VCCChainConveyorMath.remoteEntryAngle(sourceStats.tangentAngle(),
                    sourceReversed);
            VerticalChainConveyors.debugRouting(
                    "{} facing={} handed package {} to {} facing={} via {}, tangent={}, entry={}",
                    source.getBlockPos(), sourceFacing, box.netId, targetEntity.getBlockPos(),
                    targetFacing, connectionToTarget, sourceStats.tangentAngle(), box.chainPosition);
            return target.addLoopingPackage(box);
        }

        box.chainPosition = VCCChainConveyorMath.targetEntryAngle(connectionToSource,
                targetFacing, sourceReversed);
        VerticalChainConveyors.debugRouting(
                "{} facing={} handed package {} to {} facing={} via mixed-facing entry {}, entry={}",
                source.getBlockPos(), sourceFacing, box.netId, targetEntity.getBlockPos(),
                targetFacing, connectionToTarget, box.chainPosition);
        return target.addLoopingPackage(box);
    }

    /**
     * Vanilla local-loop package positions always orbit in the horizontal XZ
     * plane. For wall/ceiling-mounted conveyors, use the mounted wheel plane so
     * packages can reach the same tangent angles as the vertical chain exits.
     */
    @Inject(method = "getPackagePosition", at = @At("HEAD"), cancellable = true)
    private void vccGetPackagePosition(float chainPosition, BlockPos connection,
            CallbackInfoReturnable<Vec3> cir) {
        if (connection != null) return;

        BlockEntity self = (BlockEntity)(Object)this;
        Direction facing = vccFacing(self);
        if (facing == Direction.DOWN) return;

        cir.setReturnValue(VCCChainConveyorMath.loopPosition(self.getBlockPos(), facing, chainPosition));
    }

    @Inject(method = "forPointsAlongChains", at = @At("HEAD"), cancellable = true)
    private void vccForPointsAlongChains(BlockPos connection, int positions, Consumer<Vec3> callback,
            CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = (BlockEntity)(Object)this;
        Direction sourceFacing = vccFacing(self);
        Direction targetFacing = sourceFacing;
        BlockState targetState = self.getLevel().getBlockState(self.getBlockPos().offset(connection));
        if (targetState.hasProperty(BlockStateProperties.FACING))
            targetFacing = targetState.getValue(BlockStateProperties.FACING);
        if (sourceFacing == Direction.DOWN && targetFacing == Direction.DOWN)
            return;

        prepareStats();
        ConnectionStats stats = connectionStats.get(connection);
        if (stats == null) {
            cir.setReturnValue(false);
            return;
        }

        VCCChainConveyorMath.forPointsAlongChains(self.getBlockPos(), sourceFacing,
                new VCCChainConveyorMath.ConnectionStats(stats.tangentAngle(), stats.chainLength(),
                        stats.start(), stats.end()),
                positions, callback);
        cir.setReturnValue(true);
    }

    @Unique
    private static Float vccHorizontalYaw(Vec3 direction) {
        return VCCChainConveyorMath.horizontalYaw(direction);
    }

    @Unique
    private float vccRememberedYaw(ChainConveyorPackage box, Float yaw, Direction facing) {
        if (yaw == null)
            return vcc_packageYaws.computeIfAbsent(box, $ -> vccDefaultYaw(facing));

        vcc_packageYaws.put(box, yaw);
        return yaw;
    }

    @Unique
    private static float vccDefaultYaw(Direction facing) {
        return VCCChainConveyorMath.defaultYaw(facing);
    }

    @Unique
    private static Direction vccFacing(BlockEntity self) {
        var state = self.getBlockState();
        if (!state.hasProperty(BlockStateProperties.FACING) && self.getLevel() != null)
            state = self.getLevel().getBlockState(self.getBlockPos());
        return state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : Direction.DOWN;
    }

}
