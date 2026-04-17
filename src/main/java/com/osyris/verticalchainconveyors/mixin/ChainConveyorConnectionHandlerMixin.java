package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionHandler;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorConnectionPacket;
import com.simibubi.create.content.equipment.blueprint.BlueprintOverlayRenderer;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.osyris.verticalchainconveyors.VCCChainConveyorMath;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

@Mixin(value = ChainConveyorConnectionHandler.class, remap = false)
public abstract class ChainConveyorConnectionHandlerMixin {

    @Shadow private static BlockPos firstPos;
    @Shadow private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> firstDim;

    @Inject(method = "clientTick", at = @At("HEAD"), cancellable = true)
    private static void vccClientTick(CallbackInfo ci) {
        if (firstPos == null) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        Level level = player.level();
        BlockState sourceState = level.getBlockState(firstPos);
        if (!sourceState.hasProperty(BlockStateProperties.FACING)) return;

        Direction sourceFacing = sourceState.getValue(BlockStateProperties.FACING);

        if (firstDim != level.dimension()) {
            firstPos = null;
            CreateLang.translate("chain_conveyor.selection_cleared")
                    .sendStatus(player);
            ci.cancel();
            return;
        }

        if (!(level.getBlockEntity(firstPos) instanceof ChainConveyorBlockEntity sourceConveyor)) {
            firstPos = null;
            CreateLang.translate("chain_conveyor.selection_cleared")
                    .sendStatus(player);
            ci.cancel();
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!vccIsChain(stack)) {
            stack = player.getOffhandItem();
            if (!vccIsChain(stack)) {
                ci.cancel();
                return;
            }
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult == null || hitResult.getType() != Type.BLOCK) {
            if (sourceFacing == Direction.DOWN) return;
            vccDrawConveyorOutline(firstPos, 0xFFFFFF, "chain_connect", sourceFacing);
            ci.cancel();
            return;
        }

        BlockHitResult bhr = (BlockHitResult) hitResult;
        BlockPos pos = bhr.getBlockPos();
        BlockState hitState = level.getBlockState(pos);

        if (pos.equals(firstPos)) {
            if (sourceFacing == Direction.DOWN) return;
            vccDrawConveyorOutline(firstPos, 0xFFFFFF, "chain_connect", sourceFacing);
            CreateLang.translate("chain_conveyor.select_second")
                    .sendStatus(player);
            ci.cancel();
            return;
        }

        if (!(hitState.getBlock() instanceof ChainConveyorBlock)) {
            if (sourceFacing == Direction.DOWN) return;
            vccDrawConveyorOutline(firstPos, 0xFFFFFF, "chain_connect", sourceFacing);
            ci.cancel();
            return;
        }

        Direction targetFacing = hitState.hasProperty(BlockStateProperties.FACING)
                ? hitState.getValue(BlockStateProperties.FACING)
                : Direction.DOWN;
        if (sourceFacing == Direction.DOWN && targetFacing == Direction.DOWN) return;

        boolean success = ChainConveyorConnectionHandler.validateAndConnect(level, pos, player, stack, true);
        if (success)
            CreateLang.translate("chain_conveyor.valid_connection")
                    .style(ChatFormatting.GREEN)
                    .sendStatus(player);

        int color = success ? 0x95CD41 : 0xEA5C2B;
        vccDrawConveyorOutline(firstPos, color, "chain_connect", sourceFacing);
        vccDrawConveyorOutline(pos, color, "chain_connect_to", targetFacing);
        vccDrawConnectionPreview(firstPos, pos, sourceFacing, targetFacing,
                sourceConveyor.getSpeed() < 0, color);

        ci.cancel();
    }

    /**
     * inject before the horizontal-distance checks. for any connection involving
     * a non-floor-facing conveyor, we apply axis-relative distance and slope
     * checks then take over the rest of the validation ourselves.
     */
    @Inject(method = "validateAndConnect", at = @At("HEAD"), cancellable = true)
    private static void vccValidateAndConnect(LevelAccessor level, BlockPos pos, Player player,
            ItemStack chain, boolean simulate, CallbackInfoReturnable<Boolean> cir) {

        if (firstPos == null) return;

        var sourceState = level.getBlockState(firstPos);
        if (!AllBlocks.CHAIN_CONVEYOR.has(sourceState)) return;

        if (!sourceState.hasProperty(BlockStateProperties.FACING)) return;
        Direction facing = sourceState.getValue(BlockStateProperties.FACING);

        var targetState = level.getBlockState(pos);
        Direction targetFacing = targetState.hasProperty(BlockStateProperties.FACING)
                ? targetState.getValue(BlockStateProperties.FACING)
                : Direction.DOWN;
        if (facing == Direction.DOWN && targetFacing == Direction.DOWN)
            return; // let original handle floor-to-floor mounts

        if (!VCCChainConveyorMath.sameAlignment(facing, targetFacing)) {
            cir.setReturnValue(vccFail("chain_conveyor.cannot_connect_misaligned"));
            return;
        }

        if (!simulate && player.isShiftKeyDown()) {
            CreateLang.translate("chain_conveyor.selection_cleared")
                    .sendStatus(player);
            cir.setReturnValue(false);
            return;
        }

        // --- replicate common up-front checks ---

        if (pos.equals(firstPos)) { cir.setReturnValue(false); return; }

        if (!pos.closerThan(firstPos, AllConfigs.server().kinetics.maxChainConveyorLength.get())) {
            cir.setReturnValue(vccFail("chain_conveyor.too_far"));
            return;
        }
        if (pos.closerThan(firstPos, 2.5)) {
            cir.setReturnValue(vccFail("chain_conveyor.too_close"));
            return;
        }

        VCCChainConveyorMath.ConnectionGeometry geometry =
                VCCChainConveyorMath.connectionGeometry(pos.subtract(firstPos), facing.getAxis());

        if (geometry.axiallyInvalid()) {
            cir.setReturnValue(vccFail("chain_conveyor.cannot_connect_axially"));
            return;
        }
        if (geometry.tooSteep()) {
            cir.setReturnValue(vccFail("chain_conveyor.too_steep"));
            return;
        }

        // --- replicate downstream checks ---

        ChainConveyorBlock ccBlock = AllBlocks.CHAIN_CONVEYOR.get();
        ChainConveyorBlockEntity sourceBE = ccBlock.getBlockEntity(level, firstPos);
        ChainConveyorBlockEntity targetBE = ccBlock.getBlockEntity(level, pos);

        if (targetBE == null || sourceBE == null) {
            cir.setReturnValue(vccFail("chain_conveyor.blocks_invalid"));
            return;
        }
        int maxConnections = AllConfigs.server().kinetics.maxChainConveyorConnections.get();
        if (sourceBE.connections.size() >= maxConnections) {
            cir.setReturnValue(vccFail("chain_conveyor.cannot_add_more_connections"));
            return;
        }
        if (targetBE.connections.size() >= maxConnections) {
            cir.setReturnValue(vccFail("chain_conveyor.cannot_add_more_connections"));
            return;
        }
        if (targetBE.connections.contains(firstPos.subtract(pos))) {
            cir.setReturnValue(vccFail("chain_conveyor.already_connected"));
            return;
        }

        if (!player.isCreative()) {
            int chainCost = ChainConveyorBlockEntity.getChainCost(pos.subtract(firstPos));
            boolean hasEnough = ChainConveyorBlockEntity.getChainsFromInventory(player, chain, chainCost, true);
            if (simulate)
                BlueprintOverlayRenderer.displayChainRequirements(chain.getItem(), chainCost, hasEnough);
            if (!hasEnough) {
                cir.setReturnValue(vccFail("chain_conveyor.not_enough_chains"));
                return;
            }
        }

        if (simulate) { cir.setReturnValue(true); return; }

        com.simibubi.create.AllPackets.getChannel()
                .sendToServer(new ChainConveyorConnectionPacket(firstPos, pos, chain, true));

        CreateLang.text("").sendStatus(player);
        firstPos = null;
        firstDim = null;
        cir.setReturnValue(true);
    }

    private static boolean vccFail(String key) {
        CreateLang.translate(key)
                .style(ChatFormatting.RED)
                .sendStatus(Minecraft.getInstance().player);
        return false;
    }

    /**
     * replace the octagonal selection outline for non-DOWN facings. the original
     * always draws in XZ around the block's bottom-face centre; we draw in the
     * wheel plane around the mounting face centre.
     */
    @Inject(method = "highlightConveyor", at = @At("HEAD"), cancellable = true)
    private static void vccHighlightConveyor(BlockPos pos, int color, String id, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockState state = mc.level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.FACING)) return;
        Direction facing = state.getValue(BlockStateProperties.FACING);
        if (facing == Direction.DOWN) return;

        vccDrawConveyorOutline(pos, color, id, facing);

        ci.cancel();
    }

    @Unique
    private static boolean vccIsChain(ItemStack itemStack) {
        return itemStack.is(Items.CHAIN);
    }

    @Unique
    private static void vccDrawConveyorOutline(BlockPos pos, int color, String id, Direction facing) {
        for (int zeroOrOne = 0; zeroOrOne < 2; zeroOrOne++) {
            double axialOffset = 0.125 + zeroOrOne * 0.75;
            Vec3 prev = VCCChainConveyorMath.outlinePoint(pos, facing, axialOffset, -22.5f);
            for (int i = 0; i < 8; i++) {
                Vec3 curr = VCCChainConveyorMath.outlinePoint(pos, facing,
                        axialOffset, 22.5f + i * 45.0f);
                Outliner.getInstance()
                        .showLine(id + zeroOrOne + i, prev, curr)
                        .lineWidth(0.0625f)
                        .colored(color);
                prev = curr;
            }
        }
    }

    @Unique
    private static void vccDrawConnectionPreview(BlockPos sourcePos, BlockPos targetPos, Direction sourceFacing,
            Direction targetFacing, boolean reversed, int color) {
        BlockPos connection = targetPos.subtract(sourcePos);
        VCCChainConveyorMath.ConnectionStats stats =
                VCCChainConveyorMath.calculateConnectionStats(sourcePos, connection, sourceFacing,
                        targetFacing, reversed);
        if (stats.start().distanceToSqr(stats.end()) < 1.0E-7)
            return;

        VCCChainConveyorMath.ChainRuns runs =
                VCCChainConveyorMath.chainRuns(sourcePos, sourceFacing, stats);

        Outliner.getInstance().showLine("chain_connect_line", runs.firstStart(), runs.firstEnd())
                .lineWidth(0.0625f)
                .colored(color);
        Outliner.getInstance().showLine("chain_connect_line_1", runs.secondStart(), runs.secondEnd())
                .lineWidth(0.0625f)
                .colored(color);
    }

}
