package dev.transerver.neoforge;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(TranserverMod.MOD_ID)
public final class TranserverMod {
    public static final String MOD_ID = "transerver";

    public TranserverMod(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, TranserverNeoForgeConfig.SPEC);
    }
}
