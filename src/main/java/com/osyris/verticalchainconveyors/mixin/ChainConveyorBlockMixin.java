package com.osyris.verticalchainconveyors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.osyris.verticalchainconveyors.VCCChainConveyorShapes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

@Mixin(value = ChainConveyorBlock.class, remap = false)
public abstract class ChainConveyorBlockMixin extends KineticBlock {

    @Unique
    private static final DirectionProperty VCC_FACING = BlockStateProperties.FACING;

    private ChainConveyorBlockMixin(Properties props) {
        super(props);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void vccSetDefaultState(Properties props, CallbackInfo ci) {
        // default facing = DOWN preserves the original horizontal floor-mounted behaviour
        this.registerDefaultState(this.stateDefinition.any().setValue(VCC_FACING, Direction.DOWN));
    }

    /**
     * return the axis of the current mounting direction. FACING.DOWN / UP → Y, etc.
     * @author Osyris
     * @reason supports all 6 facing directions
     */
    @Overwrite
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(VCC_FACING).getAxis();
    }

    /**
     * place the conveyor with its "bottom" facing the clicked surface so the dark side
     * of the model aligns with the mounting wall/floor/ceiling. reject placement if any
     * neighboring conveyor shares the same wheel axis within the local 3x3 wheel plane —
     * their wheels (radius 1.25) would overlap regardless of which side of the axis each
     * is mounted on, matching vanilla Create's "no overlap in the wheel plane" rule.
     */
    @Inject(method = "m_5573_", at = @At("HEAD"), cancellable = true)
    private void vccGetStateForPlacement(BlockPlaceContext ctx, CallbackInfoReturnable<BlockState> cir) {
        Direction facing = ctx.getClickedFace().getOpposite();
        Direction.Axis axis = facing.getAxis();
        BlockPos pos = ctx.getClickedPos();

        boolean blocked;
        if (axis == Direction.Axis.Y) {
            blocked = vccHasNeighbour(ctx, pos, axis,
                    new int[]{-1, 0, 1}, new int[]{0}, new int[]{-1, 0, 1});
        } else if (axis == Direction.Axis.X) {
            blocked = vccHasNeighbour(ctx, pos, axis,
                    new int[]{0}, new int[]{-1, 0, 1}, new int[]{-1, 0, 1});
        } else {
            blocked = vccHasNeighbour(ctx, pos, axis,
                    new int[]{-1, 0, 1}, new int[]{-1, 0, 1}, new int[]{0});
        }

        if (blocked) {
            cir.setReturnValue(null);
            return;
        }

        cir.setReturnValue(this.defaultBlockState().setValue(VCC_FACING, facing));
    }

    @Unique
    private boolean vccHasNeighbour(BlockPlaceContext ctx, BlockPos pos, Direction.Axis axis,
            int[] xs, int[] ys, int[] zs) {
        for (int dx : xs)
            for (int dy : ys)
                for (int dz : zs) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockState n = ctx.getLevel().getBlockState(pos.offset(dx, dy, dz));
                    if (n.getBlock() == this && n.getValue(VCC_FACING).getAxis() == axis)
                        return true;
                }
        return false;
    }

    @Inject(method = "m_5940_", at = @At("HEAD"), cancellable = true)
    private void vccGetShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(VCCChainConveyorShapes.forAxis(state.getValue(VCC_FACING).getAxis()));
    }

    @Inject(method = "m_6079_", at = @At("HEAD"), cancellable = true)
    private void vccGetInteractionShape(BlockState state, BlockGetter level, BlockPos pos,
            CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(VCCChainConveyorShapes.forAxis(state.getValue(VCC_FACING).getAxis()));
    }
}
