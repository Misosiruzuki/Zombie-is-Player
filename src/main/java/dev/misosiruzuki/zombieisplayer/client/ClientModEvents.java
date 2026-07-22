package dev.misosiruzuki.zombieisplayer.client;

import dev.misosiruzuki.zombieisplayer.ZombieIsPlayer;
import dev.misosiruzuki.zombieisplayer.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ZombieIsPlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SMART_ZOMBIE.get(), SmartZombieRenderer::new);
    }

    private ClientModEvents() {
    }
}
