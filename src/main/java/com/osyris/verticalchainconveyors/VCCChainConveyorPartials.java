package com.osyris.verticalchainconveyors;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllPartialModels;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public final class VCCChainConveyorPartials {

    private static final String CREATE_ENCASED_MOD_ID = "createcasing";
    private static final String ENCASED_PARTIAL_MODELS =
            "fr.iglee42.createcasing.registries.EncasedPartialModels";

    private static Method encasedWheel;
    private static Method encasedGuard;
    private static boolean encasedLookupFailed;

    private VCCChainConveyorPartials() {}

    public static PartialModel wheel(BlockState state) {
        PartialModel partial = encasedPartial(state, true);
        return partial == null ? AllPartialModels.CHAIN_CONVEYOR_WHEEL : partial;
    }

    public static PartialModel guard(BlockState state) {
        PartialModel partial = encasedPartial(state, false);
        return partial == null ? AllPartialModels.CHAIN_CONVEYOR_GUARD : partial;
    }

    @Nullable
    private static PartialModel encasedPartial(BlockState state, boolean wheel) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!CREATE_ENCASED_MOD_ID.equals(id.getNamespace())) {
            return null;
        }

        Method method = encasedMethod(wheel);
        if (method == null) {
            return null;
        }

        try {
            Object result = method.invoke(null, state);
            return result instanceof PartialModel partial ? partial : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Nullable
    private static Method encasedMethod(boolean wheel) {
        if (encasedLookupFailed) {
            return null;
        }

        Method cached = wheel ? encasedWheel : encasedGuard;
        if (cached != null) {
            return cached;
        }

        try {
            Class<?> partials = Class.forName(ENCASED_PARTIAL_MODELS);
            encasedWheel = partials.getMethod("getChainConveyorWheel", BlockState.class);
            encasedGuard = partials.getMethod("getChainConveyorGuard", BlockState.class);
            return wheel ? encasedWheel : encasedGuard;
        } catch (ReflectiveOperationException | RuntimeException e) {
            encasedLookupFailed = true;
            return null;
        }
    }
}
