package dev.misosiruzuki.zombieisplayer.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ModConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue SMART_ZOMBIE_REPLACEMENT_CHANCE;
    public static final ForgeConfigSpec.IntValue SMART_ZOMBIE_CHUNK_RADIUS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("smart_zombie");
        SMART_ZOMBIE_REPLACEMENT_CHANCE = builder
                .comment("Chance that a naturally spawning vanilla zombie is replaced by a smart zombie (0.0 to 1.0).")
                .defineInRange("replacementChance", 0.05D, 0.0D, 1.0D);
        SMART_ZOMBIE_CHUNK_RADIUS = builder
                .comment("Radius of fully ticking chunks kept loaded around each smart zombie. 1 means a 3x3 area.")
                .defineInRange("chunkRadius", 1, 0, 4);
        builder.pop();
        SPEC = builder.build();
    }

    private ModConfig() {
    }
}
