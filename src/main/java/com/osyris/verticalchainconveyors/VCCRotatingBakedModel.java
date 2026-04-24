package com.osyris.verticalchainconveyors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * baked-model wrapper that rotates every quad of the wrapped model by a fixed
 * {@link Transformation}. used to back the non-DOWN facings of chain-conveyor
 * variants whose source mod generated the blockstate JSON with a single
 * un-rotated variant (e.g. Create Encased's simpleBlock call).
 *
 * {@link net.minecraft.client.resources.model.BlockModelRotation#getRotation()}
 * is a pure rotation, so this wrapper adds the block-centre pivot normally
 * supplied by vanilla blockstate baking.
 */
public class VCCRotatingBakedModel extends BakedModelWrapper<BakedModel> {

    private final IQuadTransformer quadTransformer;
    private final Direction[] rotatedByOriginal = new Direction[6];
    private final Direction[] originalByRotated = new Direction[6];

    public VCCRotatingBakedModel(BakedModel original, Transformation transformation) {
        super(original);
        this.quadTransformer = QuadTransformers.applying(aroundBlockCenter(transformation));
        for (Direction d : Direction.values()) {
            Vec3i n = d.getNormal();
            Vector3f v = new Vector3f(n.getX(), n.getY(), n.getZ());
            transformation.transformNormal(v);
            Direction rotated = Direction.getNearest(v.x, v.y, v.z);
            rotatedByOriginal[d.ordinal()] = rotated;
            originalByRotated[rotated.ordinal()] = d;
        }
    }

    private static Transformation aroundBlockCenter(Transformation transformation) {
        Matrix4f matrix = new Matrix4f()
                .translation(0.5f, 0.5f, 0.5f)
                .mul(transformation.getMatrix())
                .translate(-0.5f, -0.5f, -0.5f);
        return new Transformation(matrix);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
            ModelData data, @Nullable RenderType renderType) {
        Direction originalSide = side == null ? null : originalByRotated[side.ordinal()];
        List<BakedQuad> orig = originalModel.getQuads(state, originalSide, rand, data, renderType);
        if (orig.isEmpty())
            return orig;
        List<BakedQuad> out = new ArrayList<>(orig.size());
        for (BakedQuad q : orig) {
            Direction rotatedDir = rotatedByOriginal[q.getDirection().ordinal()];
            BakedQuad copy = new BakedQuad(
                    Arrays.copyOf(q.getVertices(), q.getVertices().length),
                    q.getTintIndex(),
                    rotatedDir,
                    q.getSprite(),
                    q.isShade(),
                    q.hasAmbientOcclusion());
            quadTransformer.processInPlace(copy);
            out.add(copy);
        }
        return out;
    }
}
