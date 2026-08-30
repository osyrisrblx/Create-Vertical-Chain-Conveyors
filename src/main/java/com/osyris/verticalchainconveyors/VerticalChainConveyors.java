package com.osyris.verticalchainconveyors;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(VerticalChainConveyors.MOD_ID)
public class VerticalChainConveyors {

    public static final String MOD_ID = "verticalchainconveyors";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean DEBUG_ROUTING =
            Boolean.getBoolean(MOD_ID + ".debugRouting");

    public VerticalChainConveyors(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, VCCServerConfig.SPEC);
    }

    public static void debugRouting(String message, Object... args) {
        if (DEBUG_ROUTING)
            LOGGER.info("[VCC routing] " + message, args);
    }
}
