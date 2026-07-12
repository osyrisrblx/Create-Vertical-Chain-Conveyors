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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@Mixin(value = ChainConveyorBlock.class, remap = false)
public abstract class ChainConveyorBlockMixin extends KineticBlock {

    @Unique
    private static final DirectionProperty VCC_FACING = BlockStateProperties.FACING;

    // pre-built shapes for each axis orientation.
    // shape only depends on the axis of the wheel (not the facing's sign)
    // so we index by axis.ordinal() (X=0, Y=1, Z=2).
    @Unique
    private static final VoxelShape[] VCC_SHAPES = buildShapes();

    private ChainConveyorBlockMixin(Properties props) {
        super(props);
    }

    @Unique
    private static VoxelShape[] buildShapes() {
        // Y-axis: original CHAIN_CONVEYOR_INTERACTION cross shape (horizontal disk)
        VoxelShape y = Shapes.or(
                vccBox(-10, 2, 0, 26, 14, 16),
                vccBox(0, 2, -10, 16, 14, 26),
                vccBox(-5, 2, -5, 21, 14, 21),
                Shapes.block()
        );
        // X-axis: disk in YZ plane
        VoxelShape x = Shapes.or(
                vccBox(2, -10, 0, 14, 26, 16),
                vccBox(2, 0, -10, 14, 16, 26),
                vccBox(2, -5, -5, 14, 21, 21),
                Shapes.block()
        );
        // Z-axis: disk in XY plane
        VoxelShape z = Shapes.or(
                vccBox(-10, 0, 2, 26, 16, 14),
                vccBox(0, -10, 2, 16, 26, 14),
                vccBox(-5, -5, 2, 21, 21, 14),
                Shapes.block()
        );
        VoxelShape[] shapes = new VoxelShape[3];
        shapes[Direction.Axis.X.ordinal()] = x;
        shapes[Direction.Axis.Y.ordinal()] = y;
        shapes[Direction.Axis.Z.ordinal()] = z;
        return shapes;
    }

    @Unique
    private static VoxelShape vccBox(double x0, double y0, double z0, double x1, double y1, double z1) {
        return Shapes.box(x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16);
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
    @Inject(method = "getStateForPlacement", at = @At("HEAD"), cancellable = true)
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

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void vccGetShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(VCC_SHAPES[state.getValue(VCC_FACING).getAxis().ordinal()]);
    }

    @Inject(method = "getInteractionShape", at = @At("HEAD"), cancellable = true)
    private void vccGetInteractionShape(BlockState state, BlockGetter level, BlockPos pos,
            CallbackInfoReturnable<VoxelShape> cir) {
        cir.setReturnValue(VCC_SHAPES[state.getValue(VCC_FACING).getAxis().ordinal()]);
    }
}
