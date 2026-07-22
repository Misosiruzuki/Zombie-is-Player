package dev.misosiruzuki.zombieisplayer.registry;

import dev.misosiruzuki.zombieisplayer.SmartZombie;
import dev.misosiruzuki.zombieisplayer.ZombieIsPlayer;
import dev.misosiruzuki.zombieisplayer.entity.SmartZombiePearl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ZombieIsPlayer.MOD_ID);

    public static final RegistryObject<EntityType<SmartZombie>> SMART_ZOMBIE = ENTITY_TYPES.register(
            "smart_zombie",
            () -> EntityType.Builder.of(SmartZombie::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(ZombieIsPlayer.MOD_ID + ":smart_zombie")
    );

    public static final RegistryObject<EntityType<SmartZombiePearl>> SMART_ZOMBIE_PEARL = ENTITY_TYPES.register(
            "smart_zombie_pearl",
            () -> EntityType.Builder.<SmartZombiePearl>of(SmartZombiePearl::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(ZombieIsPlayer.MOD_ID + ":smart_zombie_pearl")
    );

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }

    private ModEntities() {
    }
}
