package dev.misosiruzuki.zombieisplayer.ai;

import dev.misosiruzuki.zombieisplayer.SmartZombie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraftforge.common.ForgeMod;

/** Uses the same charged-attack timing and entity reach attributes as a player. */
public final class PlayerAttackGoal extends ZombieAttackGoal {
    private final SmartZombie smartZombie;

    public PlayerAttackGoal(SmartZombie smartZombie, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        super(smartZombie, speedModifier, followingTargetEvenIfNotSeen);
        this.smartZombie = smartZombie;
    }

    @Override
    protected void checkAndPerformAttack(LivingEntity target, double ignoredDistanceSquared) {
        double reach = smartZombie.getAttributeValue(ForgeMod.ENTITY_REACH.get());
        double distanceSquared = target.getBoundingBox()
                .inflate(target.getPickRadius())
                .distanceToSqr(smartZombie.getEyePosition());

        if (distanceSquared < reach * reach && smartZombie.getAttackStrengthScale(0.5F) >= 1.0F) {
            smartZombie.swing(InteractionHand.MAIN_HAND);
            smartZombie.doHurtTarget(target);
        }
    }

    @Override
    protected double getAttackReachSqr(LivingEntity target) {
        double reach = smartZombie.getAttributeValue(ForgeMod.ENTITY_REACH.get());
        return reach * reach;
    }
}
