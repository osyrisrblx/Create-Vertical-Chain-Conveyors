package com.osyris.verticalchainconveyors;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;

@Mod(VerticalChainConveyors.MOD_ID)
public class VerticalChainConveyors {

    public static final String MOD_ID = "verticalchainconveyors";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final boolean DEBUG_ROUTING =
            Boolean.getBoolean(MOD_ID + ".debugRouting");

    public VerticalChainConveyors() {
    }

    public static void debugRouting(String message, Object... args) {
        if (DEBUG_ROUTING)
            LOGGER.info("[VCC routing] " + message, args);
    }
}
