package dev.misosiruzuki.zombieisplayer.client;

import dev.misosiruzuki.zombieisplayer.SmartZombie;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class SmartZombieRenderer extends HumanoidMobRenderer<SmartZombie, PlayerModel<SmartZombie>> {
    private static final ResourceLocation ZOMBIE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/zombie/zombie.png");

    public SmartZombieRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(SmartZombie entity) {
        return ZOMBIE_TEXTURE;
    }
}
