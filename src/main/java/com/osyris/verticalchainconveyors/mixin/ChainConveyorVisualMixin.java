package com.osyris.verticalchainconveyors.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.osyris.verticalchainconveyors.VCCVisualSections;

import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

@Mixin(value = ChainConveyorVisual.class, remap = false)
public abstract class ChainConveyorVisualMixin {

    @Unique private int vcc_lastGuardConnectionCount = -1;
    @Unique private Direction vcc_lastGuardFacing = Direction.DOWN;

    @Inject(method = "setupGuards", at = @At("HEAD"))
    private void vccPrepareStatsBeforeGuardSetup(CallbackInfo ci) {
        ChainConveyorBlockEntity be =
                (ChainConveyorBlockEntity) ((VCCVisualAccessor)(Object)this).vcc_getBlockEntity();
        be.prepareStats();
    }

    @Inject(method = "beginFrame", at = @At("HEAD"))
    private void vccRepairLoadedGuardVisuals(DynamicVisual.Context context, CallbackInfo ci) {
        VCCVisualAccessor accessor = (VCCVisualAccessor)(Object)this;
        BlockState state = accessor.vcc_getBlockState();
        if (!state.hasProperty(BlockStateProperties.FACING)) return;

        ChainConveyorBlockEntity be = (ChainConveyorBlockEntity) accessor.vcc_getBlockEntity();
        be.prepareStats();

        Direction facing = state.getValue(BlockStateProperties.FACING);
        VCCVisualSections.updateTrackedSections(accessor.vcc_getLightSections(),
                be, be.getRenderBoundingBox());
        int connectionCount = be.connections.size();
        int expectedGuardCount = connectionCount + 1;
        int actualGuardCount = ((VCCChainVisualAccessor)(Object)this).vcc_getGuards().size();

        if (actualGuardCount >= expectedGuardCount
                && connectionCount == vcc_lastGuardConnectionCount
                && facing == vcc_lastGuardFacing) {
            return;
        }

        ((VCCChainVisualAccessor)(Object)this).vcc_setupGuards();
    }

    /**
     * rotate the wheel to match its facing, and rebuild each chain-guide with
     * the correct orientation in the wheel plane. guards[0] is the wheel model;
     * guards[1..n] are per-connection guide frames, added in connections-iteration
     * order — we iterate the same way to match them up.
     *
     * wheel rotation (from default facing=DOWN orientation):
     *   DOWN  → no rotation
     *   UP    → 180° around X
     *   NORTH →  90° around X
     *   SOUTH → 270° around X
     *   EAST  →  90° around Z
     *   WEST  → 270° around Z
     *
     * guide rotation (default guide direction = +Z), rotating so guide points
     * toward the connected conveyor in the wheel plane and rolling it so the
     * model's bottom faces the conveyor's mounting surface:
     *   axis=Y (UP):   rotateY(inPlaneYaw)
     *   axis=X:        rotateX(inPlaneYaw - 90°)
     *   axis=Z:        rotateZ(inPlaneYaw - 90°) → rotateX(-90°)
     */
    @Inject(method = "setupGuards", at = @At("TAIL"))
    private void vccFixGuardOrientations(CallbackInfo ci) {
        List<TransformedInstance> guards = ((VCCChainVisualAccessor)(Object)this).vcc_getGuards();
        if (guards.isEmpty()) return;

        VCCVisualAccessor accessor = (VCCVisualAccessor)(Object)this;
        BlockState state = accessor.vcc_getBlockState();
        if (!state.hasProperty(BlockStateProperties.FACING)) return;
        Direction facing = state.getValue(BlockStateProperties.FACING);
        vcc_lastGuardConnectionCount =
                ((ChainConveyorBlockEntity) accessor.vcc_getBlockEntity()).connections.size();
        vcc_lastGuardFacing = facing;
        VCCVisualSections.updateTrackedSections(accessor.vcc_getLightSections(),
                accessor.vcc_getBlockEntity(),
                ((ChainConveyorBlockEntity) accessor.vcc_getBlockEntity()).getRenderBoundingBox());
        if (facing == Direction.DOWN) return;

        RotatingInstance shaft = ((VCCSingleAxisVisualAccessor)(Object)this).vcc_getRotatingModel();
        shaft.rotation.identity();
        shaft.rotateToFace(facing.getOpposite());
        shaft.setChanged();

        // wheel orientation
        TransformedInstance wheel = guards.get(0);
        switch (facing) {
            case UP:
                wheel.rotateCenteredDegrees(180, Direction.Axis.X);
                break;
            case NORTH:
                wheel.rotateCenteredDegrees(90, Direction.Axis.X);
                break;
            case SOUTH:
                wheel.rotateCenteredDegrees(270, Direction.Axis.X);
                break;
            case EAST:
                wheel.rotateCenteredDegrees(90, Direction.Axis.Z);
                break;
            case WEST:
                wheel.rotateCenteredDegrees(270, Direction.Axis.Z);
                break;
            default:
                break;
        }
        wheel.setChanged();

        if (guards.size() <= 1) return;

        // chain guides (index 1+): rebuild from identity
        ChainConveyorBlockEntity be = (ChainConveyorBlockEntity) accessor.vcc_getBlockEntity();
        BlockPos visualPos = accessor.vcc_getVisualPos();
        float visualX = visualPos.getX();
        float visualY = visualPos.getY();
        float visualZ = visualPos.getZ();

        Direction.Axis axis = facing.getAxis();

        int guardIdx = 1;
        for (BlockPos connection : be.connections) {
            if (guardIdx >= guards.size()) break;
            ConnectionStats cs = be.connectionStats.get(connection);
            if (cs == null) continue;

            TransformedInstance guard = guards.get(guardIdx);
            guardIdx++;

            float inPlaneYaw;
            switch (axis) {
                case X:
                    inPlaneYaw = Mth.RAD_TO_DEG * (float) Mth.atan2(connection.getZ(), connection.getY());
                    break;
                case Z:
                    inPlaneYaw = Mth.RAD_TO_DEG * (float) Mth.atan2(connection.getY(), connection.getX());
                    break;
                default: // Y (facing=UP)
                    inPlaneYaw = Mth.RAD_TO_DEG * (float) Mth.atan2(connection.getX(), connection.getZ());
                    break;
            }

            guard.setIdentityTransform()
                 .translate(visualX, visualY, visualZ)
                 .center();

            switch (axis) {
                case X:
                    guard.rotateXDegrees(inPlaneYaw - 90f)
                         .rotateZDegrees(facing == Direction.WEST ? -90f : 90f);
                    break;
                case Z:
                    guard.rotateZDegrees(inPlaneYaw - 90f)
                         .rotateXDegrees(-90f);
                    if (facing == Direction.NORTH) {
                        guard.rotateZDegrees(180f);
                    }
                    break;
                default: // Y — for facing=UP we also flip the guide's bottom upward
                    guard.rotateYDegrees(inPlaneYaw)
                         .rotateZDegrees(180f);
                    break;
            }

            guard.uncenter();
            guard.setChanged();
        }
    }
}
