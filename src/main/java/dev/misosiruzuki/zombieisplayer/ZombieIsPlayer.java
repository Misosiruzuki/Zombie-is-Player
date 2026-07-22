package dev.misosiruzuki.zombieisplayer;

import dev.misosiruzuki.zombieisplayer.config.ModConfig;
import dev.misosiruzuki.zombieisplayer.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ZombieIsPlayer.MOD_ID)
public final class ZombieIsPlayer {
    public static final String MOD_ID = "zombieisplayer";

    public ZombieIsPlayer() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.register(modBus);
        modBus.addListener(this::registerAttributes);
        ModLoadingContext.get().registerConfig(Type.COMMON, ModConfig.SPEC, "zombieisplayer-common.toml");
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SMART_ZOMBIE.get(), SmartZombie.createAttributes().build());
    }
}
