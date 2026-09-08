package dev.transerver.neoforge;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TranserverNeoForgeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.ConfigValue<String> NODE_ALIAS;
    public static final ModConfigSpec.ConfigValue<String> ROUTER_URL;
    public static final ModConfigSpec.ConfigValue<String> NETWORK_SECRET;
    public static final ModConfigSpec.IntValue POLL_MILLIS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Transerver node settings. The stable UUID is stored separately and must be backed up.");
        ENABLED = builder.comment("Start the Transerver node runtime on this Minecraft server.")
                .define("enabled", false);
        NODE_ALIAS = builder.comment("Human-readable name. Changing it does not change the node UUID.")
                .define("nodeAlias", "Minecraft Server");
        ROUTER_URL = builder.comment("Shared Router base URL, for example http://127.0.0.1:8765/")
                .define("routerUrl", "http://127.0.0.1:8765/");
        NETWORK_SECRET = builder.comment("Shared HMAC secret. Use at least 32 UTF-8 bytes.")
                .define("networkSecret", "REPLACE_WITH_AT_LEAST_32_CHARACTERS");
        POLL_MILLIS = builder.comment("Background delivery interval in milliseconds.")
                .defineInRange("pollMillis", 500, 50, 60_000);
        SPEC = builder.build();
    }

    private TranserverNeoForgeConfig() {
    }
}
