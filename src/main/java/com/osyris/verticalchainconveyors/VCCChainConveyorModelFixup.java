package com.osyris.verticalchainconveyors;

import java.util.Map;

import com.mojang.math.Transformation;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock;

import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * catches chain-conveyor blocks registered by other mods (Create Encased's
 * encased_chain_conveyor variants, Create: Colored Chain Conveyor, etc.) whose
 * blockstate JSONs were generated with a single un-rotated variant. for each
 * such block, every non-DOWN facing is swapped for a rotated wrapper of the
 * DOWN baked model, using the same x/y rotations as
 * {@code assets/create/blockstates/chain_conveyor.json}.
 *
 * detection: if every possible block state of a ChainConveyorBlock resolves to
 * the same baked model instance in the bakery, the source JSON was a
 * simpleBlock — we replace the non-DOWN variants. blocks that already ship
 * per-facing variants (our own create:chain_conveyor override) are left alone
 * because the baked models are distinct.
 */
@EventBusSubscriber(modid = VerticalChainConveyors.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class VCCChainConveyorModelFixup {

    private VCCChainConveyorModelFixup() {}

    // keep in sync with assets/create/blockstates/chain_conveyor.json
    private static final int[][] FACING_ROTATIONS = new int[6][2];
    static {
        FACING_ROTATIONS[Direction.DOWN.get3DDataValue()]  = new int[] {  0,   0 };
        FACING_ROTATIONS[Direction.UP.get3DDataValue()]    = new int[] {180,   0 };
        FACING_ROTATIONS[Direction.NORTH.get3DDataValue()] = new int[] { 90, 180 };
        FACING_ROTATIONS[Direction.SOUTH.get3DDataValue()] = new int[] {270, 180 };
        FACING_ROTATIONS[Direction.WEST.get3DDataValue()]  = new int[] { 90,  90 };
        FACING_ROTATIONS[Direction.EAST.get3DDataValue()]  = new int[] { 90, 270 };
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof ChainConveyorBlock))
                continue;
            if (!block.defaultBlockState().hasProperty(BlockStateProperties.FACING))
                continue;
            if (!isSimpleBlockBaking(block, models))
                continue;

            BlockState downState = block.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.DOWN);
            BakedModel downModel = models.get(BlockModelShaper.stateToModelLocation(downState));
            if (downModel == null)
                continue;

            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                Direction facing = state.getValue(BlockStateProperties.FACING);
                if (facing == Direction.DOWN)
                    continue;
                int[] rot = FACING_ROTATIONS[facing.get3DDataValue()];
                Transformation t = BlockModelRotation.by(rot[0], rot[1]).getRotation();
                ModelResourceLocation mrl = BlockModelShaper.stateToModelLocation(state);
                models.put(mrl, new VCCRotatingBakedModel(downModel, t));
            }
        }
    }

    private static boolean isSimpleBlockBaking(Block block, Map<ModelResourceLocation, BakedModel> models) {
        BakedModel reference = null;
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            BakedModel m = models.get(BlockModelShaper.stateToModelLocation(state));
            if (m == null)
                return false;
            if (reference == null)
                reference = m;
            else if (m != reference)
                return false;
        }
        return reference != null;
    }
}
