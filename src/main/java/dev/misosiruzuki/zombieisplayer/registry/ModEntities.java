package dev.misosiruzuki.zombieisplayer.registry;

import dev.misosiruzuki.zombieisplayer.SmartZombie;
import dev.misosiruzuki.zombieisplayer.ZombieIsPlayer;
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

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }

    private ModEntities() {
    }
}
