package com.osyris.verticalchainconveyors.mixin;

import java.util.List;
import java.util.Map.Entry;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity.ConnectionStats;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage.ChainConveyorPackagePhysicsData;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorRenderer;
import com.simibubi.create.content.logistics.box.PackageItem;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChainConveyorRenderer.class, remap = false)
public abstract class ChainConveyorRendererMixin extends KineticBlockEntityRenderer<ChainConveyorBlockEntity> {

    private ChainConveyorRendererMixin(Context context) {
        super(context);
    }

    @Inject(method = "renderSafe", at = @At("HEAD"), cancellable = true)
    private void vccRenderAxisAware(ChainConveyorBlockEntity be, float partialTicks, PoseStack ms,
            MultiBufferSource buffer, int light, int overlay, CallbackInfo ci) {
        BlockState state = be.getBlockState();
        if (!state.hasProperty(BlockStateProperties.FACING)) return;

        Direction facing = state.getValue(BlockStateProperties.FACING);
        if (facing == Direction.DOWN) return;

        be.prepareStats();
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        vccRenderChains(be, ms, buffer, light, overlay);

        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            ci.cancel();
            return;
        }

        SuperByteBuffer wheel = CachedBuffers.partial(AllPartialModels.CHAIN_CONVEYOR_WHEEL, state);
        vccOrientWheel(wheel, facing);
        wheel.light(light)
                .overlay(overlay)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

        BlockPos pos = be.getBlockPos();
        VCCChainConveyorBlockEntityAccessor beAccessor = (VCCChainConveyorBlockEntityAccessor)(Object)be;
        for (ChainConveyorPackage box : beAccessor.vcc_getLoopingPackages())
            vccRenderBox(be, ms, buffer, overlay, pos, box, partialTicks);
        for (Entry<BlockPos, List<ChainConveyorPackage>> entry : beAccessor.vcc_getTravellingPackages().entrySet())
            for (ChainConveyorPackage box : entry.getValue())
                vccRenderBox(be, ms, buffer, overlay, pos, box, partialTicks);

        ci.cancel();
    }

    @Unique
    private void vccRenderChains(ChainConveyorBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light,
            int overlay) {
        float speed = Math.abs(be.getSpeed());
        float time = speed == 0 ? 0 : AnimationTickHolder.getRenderTime(be.getLevel()) / (360f / speed);
        time %= 1;
        if (time < 0)
            time += 1;

        float animation = time - 0.5f;
        Direction facing = be.getBlockState().getValue(BlockStateProperties.FACING);

        for (BlockPos blockPos : be.connections) {
            ConnectionStats stats = be.connectionStats.get(blockPos);
            if (stats == null)
                continue;

            if (!VisualizationManager.supportsVisualization(be.getLevel())) {
                SuperByteBuffer guard = CachedBuffers.partial(AllPartialModels.CHAIN_CONVEYOR_GUARD, be.getBlockState());
                vccOrientGuard(guard, facing, blockPos);
                guard.light(light)
                        .overlay(overlay)
                        .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
            }

            Vec3 diff = stats.end().subtract(stats.start());
            if (diff.lengthSqr() < 1.0E-7)
                continue;

            Level level = be.getLevel();
            BlockPos tilePos = be.getBlockPos();
            Vec3 startOffset = stats.start().subtract(Vec3.atCenterOf(tilePos));
            Vec3 direction = diff.normalize();

            ms.pushPose();
            TransformStack.of(ms)
                    .center()
                    .translate(startOffset);

            ms.mulPose(new Quaternionf().rotateTo(0, 1, 0,
                    (float) direction.x, (float) direction.y, (float) direction.z));
            ms.mulPose(Axis.YP.rotationDegrees(45));

            TransformStack.of(ms)
                    .translate(0, 8 / 16f, 0)
                    .uncenter();

            int light1 = LightTexture.pack(level.getBrightness(LightLayer.BLOCK, tilePos),
                    level.getBrightness(LightLayer.SKY, tilePos));
            int light2 = LightTexture.pack(level.getBrightness(LightLayer.BLOCK, tilePos.offset(blockPos)),
                    level.getBrightness(LightLayer.SKY, tilePos.offset(blockPos)));

            boolean far = Minecraft.getInstance().level == be.getLevel() && !Minecraft.getInstance()
                    .getBlockEntityRenderDispatcher().camera.getPosition()
                    .closerThan(Vec3.atCenterOf(tilePos).add(blockPos.getX() / 2f,
                            blockPos.getY() / 2f, blockPos.getZ() / 2f), ChainConveyorRenderer.MIP_DISTANCE);

            ChainConveyorRenderer.renderChain(ms, buffer, animation, stats.chainLength(), light1, light2, far);
            ms.popPose();
        }
    }

    @Unique
    private static void vccOrientWheel(SuperByteBuffer wheel, Direction facing) {
        wheel.center();
        switch (facing) {
            case UP:
                wheel.rotateXDegrees(180);
                break;
            case NORTH:
                wheel.rotateXDegrees(90);
                break;
            case SOUTH:
                wheel.rotateXDegrees(270);
                break;
            case EAST:
                wheel.rotateZDegrees(90);
                break;
            case WEST:
                wheel.rotateZDegrees(270);
                break;
            default:
                break;
        }
        wheel.uncenter();
    }

    @Unique
    private static void vccOrientGuard(SuperByteBuffer guard, Direction facing, BlockPos connection) {
        float inPlaneYaw;
        switch (facing.getAxis()) {
            case X:
                inPlaneYaw = Mth.RAD_TO_DEG * (float) Mth.atan2(connection.getZ(), connection.getY());
                break;
            case Z:
                inPlaneYaw = Mth.RAD_TO_DEG * (float) Mth.atan2(connection.getY(), connection.getX());
                break;
            default:
                inPlaneYaw = Mth.RAD_TO_DEG * (float) Mth.atan2(connection.getX(), connection.getZ());
                break;
        }

        guard.center();
        switch (facing.getAxis()) {
            case X:
                guard.rotateXDegrees(inPlaneYaw - 90f)
                        .rotateZDegrees(facing == Direction.WEST ? -90f : 90f);
                break;
            case Z:
                guard.rotateZDegrees(inPlaneYaw - 90f)
                        .rotateXDegrees(-90f);
                if (facing == Direction.NORTH)
                    guard.rotateZDegrees(180f);
                break;
            default:
                guard.rotateYDegrees(inPlaneYaw)
                        .rotateZDegrees(180f);
                break;
        }
        guard.uncenter();
    }

    @Unique
    private void vccRenderBox(ChainConveyorBlockEntity be, PoseStack ms, MultiBufferSource buffer, int overlay,
            BlockPos pos, ChainConveyorPackage box, float partialTicks) {
        if (box.worldPosition == null)
            return;
        if (box.item == null || box.item.isEmpty())
            return;

        ChainConveyorPackagePhysicsData physicsData = box.physicsData(be.getLevel());
        if (physicsData.prevPos == null)
            return;

        Vec3 position = physicsData.prevPos.lerp(physicsData.pos, partialTicks);
        Vec3 targetPosition = physicsData.prevTargetPos.lerp(physicsData.targetPos, partialTicks);
        float yaw = AngleHelper.angleLerp(partialTicks, physicsData.prevYaw, physicsData.yaw);
        Vec3 offset = new Vec3(targetPosition.x - pos.getX(), targetPosition.y - pos.getY(), targetPosition.z - pos.getZ());

        BlockPos containingPos = BlockPos.containing(position);
        Level level = be.getLevel();
        BlockState blockState = be.getBlockState();
        int light = LightTexture.pack(level.getBrightness(LightLayer.BLOCK, containingPos),
                level.getBrightness(LightLayer.SKY, containingPos));

        if (physicsData.modelKey == null) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(box.item.getItem());
            if (key == BuiltInRegistries.ITEM.getDefaultKey())
                return;
            physicsData.modelKey = key;
        }

        SuperByteBuffer rigBuffer =
                CachedBuffers.partial(AllPartialModels.PACKAGE_RIGGING.get(physicsData.modelKey), blockState);
        SuperByteBuffer boxBuffer =
                CachedBuffers.partial(AllPartialModels.PACKAGES.get(physicsData.modelKey), blockState);

        Vec3 dangleDiff = VecHelper.rotate(targetPosition.add(0, 0.5, 0)
                .subtract(position), -yaw, Direction.Axis.Y);
        float zRot = Mth.wrapDegrees((float) Mth.atan2(-dangleDiff.x, dangleDiff.y) * Mth.RAD_TO_DEG) / 2;
        float xRot = Mth.wrapDegrees((float) Mth.atan2(dangleDiff.z, dangleDiff.y) * Mth.RAD_TO_DEG) / 2;
        zRot = Mth.clamp(zRot, -25, 25);
        xRot = Mth.clamp(xRot, -25, 25);

        for (SuperByteBuffer buf : new SuperByteBuffer[] { rigBuffer, boxBuffer }) {
            buf.translate(offset);
            buf.translate(0, 10 / 16f, 0);
            buf.rotateYDegrees(yaw);

            buf.rotateZDegrees(zRot);
            buf.rotateXDegrees(xRot);

            if (physicsData.flipped && buf == rigBuffer)
                buf.rotateYDegrees(180);

            buf.uncenter();
            buf.translate(0, -PackageItem.getHookDistance(box.item) + 7 / 16f, 0);

            buf.light(light)
                    .overlay(overlay)
                    .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
        }
    }
}
