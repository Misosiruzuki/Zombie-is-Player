package dev.misosiruzuki.zombieisplayer.event;

import dev.misosiruzuki.zombieisplayer.SmartZombie;
import dev.misosiruzuki.zombieisplayer.ZombieIsPlayer;
import dev.misosiruzuki.zombieisplayer.config.ModConfig;
import dev.misosiruzuki.zombieisplayer.registry.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZombieIsPlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {
    @SubscribeEvent
    public static void replaceNaturalZombie(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getEntity() instanceof Zombie zombie)
                || zombie.getType() != EntityType.ZOMBIE
                || event.getSpawnType() != MobSpawnType.NATURAL
                || !(event.getLevel() instanceof ServerLevel level)
                || level.getRandom().nextDouble() >= ModConfig.SMART_ZOMBIE_REPLACEMENT_CHANCE.get()) {
            return;
        }

        SmartZombie smartZombie = ModEntities.SMART_ZOMBIE.get().create(level);
        if (smartZombie == null) {
            return;
        }

        smartZombie.moveTo(zombie.getX(), zombie.getY(), zombie.getZ(), zombie.getYRot(), zombie.getXRot());
        smartZombie.setYHeadRot(zombie.getYHeadRot());
        smartZombie.finalizeSpawn(
                level,
                event.getDifficulty(),
                MobSpawnType.NATURAL,
                event.getSpawnData(),
                event.getSpawnTag()
        );
        event.setSpawnCancelled(true);
        level.getServer().execute(() -> level.addFreshEntityWithPassengers(smartZombie));
    }

    private CommonEvents() {
    }
}
