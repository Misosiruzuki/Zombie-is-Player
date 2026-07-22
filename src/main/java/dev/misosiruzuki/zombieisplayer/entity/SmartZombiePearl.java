package dev.misosiruzuki.zombieisplayer.entity;

import dev.misosiruzuki.zombieisplayer.SmartZombie;
import dev.misosiruzuki.zombieisplayer.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Ender pearl whose owner may be a SmartZombie instead of only a ServerPlayer. */
public final class SmartZombiePearl extends ThrowableItemProjectile {
    public SmartZombiePearl(EntityType<? extends SmartZombiePearl> type, Level level) {
        super(type, level);
    }

    public SmartZombiePearl(Level level, LivingEntity owner) {
        super(ModEntities.SMART_ZOMBIE_PEARL.get(), owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY(), getZ(),
                32, 0.5D, 1.0D, 0.5D, 0.15D);
        if (getOwner() instanceof SmartZombie smartZombie && smartZombie.isAlive()
                && smartZombie.level() == serverLevel) {
            Vec3 impact = hitResult.getLocation();
            boolean teleported = smartZombie.randomTeleport(impact.x, impact.y + 0.1D, impact.z, true);
            if (!teleported) {
                for (int yOffset = 1; yOffset <= 3 && !teleported; ++yOffset) {
                    teleported = smartZombie.randomTeleport(
                            impact.x, impact.y + yOffset, impact.z, true);
                }
            }
            if (teleported) {
                serverLevel.playSound(null, smartZombie.getX(), smartZombie.getY(), smartZombie.getZ(),
                        SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
                smartZombie.hurt(smartZombie.damageSources().fall(), 5.0F);
            }
        }
        discard();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
