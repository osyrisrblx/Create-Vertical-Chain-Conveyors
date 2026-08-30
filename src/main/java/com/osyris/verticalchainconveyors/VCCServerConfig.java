package com.osyris.verticalchainconveyors;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class VCCServerConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ALLOW_STEEP_MIXED_AXIS_CONNECTIONS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("connections");
        ALLOW_STEEP_MIXED_AXIS_CONNECTIONS = builder
                .comment(
                        "Allow connections between conveyors on different axes even when Create's",
                        "normal 45-degree slope limit is exceeded. Clearance, range, and all other",
                        "connection checks still apply. Existing connections are not affected.")
                .define("allowSteepMixedAxisConnections", false);
        builder.pop();
        SPEC = builder.build();
    }

    private VCCServerConfig() {}

    public static boolean allowSteepMixedAxisConnections() {
        return ALLOW_STEEP_MIXED_AXIS_CONNECTIONS.get();
    }
}
