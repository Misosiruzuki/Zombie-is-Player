package dev.misosiruzuki.zombieisplayer.ai;

import dev.misosiruzuki.zombieisplayer.SmartZombie;
import dev.misosiruzuki.zombieisplayer.entity.SmartZombiePearl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import java.util.EnumSet;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * PvP combat goal based on the supplied 1.20.1 guide: charged hits, spacing, strafing,
 * sprint resets, shield play, hotbar-like equipment swaps, recovery and ranged pressure.
 */
public final class PlayerAttackGoal extends Goal {
    private static final double MELEE_APPROACH_MARGIN = 0.65D;
    private static final double MIN_SPACING = 2.15D;
    private static final double RANGED_MIN_DISTANCE = 7.0D;
    private static final double RANGED_MAX_DISTANCE = 20.0D;
    private static final int EQUIPMENT_RECHECK_INTERVAL = 10;

    private final SmartZombie smartZombie;
    private final double speedModifier;
    private int strafeDirection = 1;
    private int strafeTicks;
    private int postHitRetreatTicks;
    private int criticalTicks;
    private int rangedChargeTicks;
    private int rangedCooldownTicks;
    private int pearlCooldownTicks;
    private int fluidActionCooldownTicks;
    private int fluidPickupTicks;
    private BlockPos tacticalFluidPos;
    private boolean tacticalFluidIsLava;
    private int lastTargetSwingTime = -1;
    private boolean pendingCritical;
    private boolean consumingFood;
    private FoodProperties consumedFood;

    public PlayerAttackGoal(SmartZombie smartZombie, double speedModifier, boolean followingTargetEvenIfNotSeen) {
        this.smartZombie = smartZombie;
        this.speedModifier = speedModifier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = smartZombie.getTarget();
        return smartZombie.isCombatMode() && target != null && target.isAlive() && smartZombie.canAttack(target);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        smartZombie.setAggressive(true);
        equipBestArmor();
        equipShield();
        chooseMeleeWeapon(smartZombie.getTarget());
        strafeTicks = 20 + smartZombie.getRandom().nextInt(30);
    }

    @Override
    public void stop() {
        smartZombie.getNavigation().stop();
        smartZombie.stopUsingItem();
        smartZombie.setSprinting(false);
        smartZombie.setAggressive(false);
        pendingCritical = false;
        consumingFood = false;
        consumedFood = null;
        rangedChargeTicks = 0;
        fluidPickupTicks = 0;
        tickFluidRecovery();
    }

    @Override
    public void tick() {
        LivingEntity target = smartZombie.getTarget();
        if (target == null) {
            return;
        }

        smartZombie.getLookControl().setLookAt(target, 18.0F, 18.0F);
        double distance = distanceToHitbox(target);
        boolean lineOfSight = smartZombie.hasLineOfSight(target);
        boolean targetJustSwung = target.swinging && target.swingTime <= 2 && target.swingTime != lastTargetSwingTime;
        lastTargetSwingTime = target.swingTime;

        if (rangedCooldownTicks > 0) {
            --rangedCooldownTicks;
        }
        if (pearlCooldownTicks > 0) {
            --pearlCooldownTicks;
        }
        if (fluidActionCooldownTicks > 0) {
            --fluidActionCooldownTicks;
        }
        tickFluidRecovery();
        if (--strafeTicks <= 0) {
            strafeDirection = -strafeDirection;
            strafeTicks = 25 + smartZombie.getRandom().nextInt(35);
        }

        if (finishTacticalItemUse()) {
            retreatFrom(target, 1.15D);
            return;
        }

        if (smartZombie.tickCount % EQUIPMENT_RECHECK_INTERVAL == 0) {
            equipBestArmor();
            if (!smartZombie.isUsingItem()) {
                equipShield();
            }
        }

        if (shouldRetreat()) {
            equipTotemIfCritical();
            if (tryThrowEnderPearl(target, true)) {
                retreatFrom(target, 1.25D);
                return;
            }
            tryPlaceWater(target);
            if (tryUseRecoveryItem()) {
                retreatFrom(target, 1.25D);
                return;
            }
            raiseShield();
            retreatFrom(target, 1.2D);
            return;
        }

        if (distance > 8.0D && tryUsePreparationPotion()) {
            retreatFrom(target, 1.0D);
            return;
        }

        if (distance > 14.0D && target.getHealth() <= target.getMaxHealth() * 0.25F
                && tryThrowEnderPearl(target, false)) {
            return;
        }

        if (distance <= 4.5D && tryPlaceLava(target)) {
            circleStrafe(-0.5F, 0.6F * strafeDirection);
            return;
        }

        if ((smartZombie.isOnFire() || smartZombie.fallDistance > 3.0F) && tryPlaceWater(target)) {
            return;
        }

        if (lineOfSight && distance >= RANGED_MIN_DISTANCE && distance <= RANGED_MAX_DISTANCE
                && rangedCooldownTicks == 0 && useRangedWeapon(target)) {
            circleStrafe(-0.15F, 0.75F);
            return;
        }

        rangedChargeTicks = 0;
        chooseMeleeWeapon(target);
        double reach = smartZombie.getAttributeValue(ForgeMod.ENTITY_REACH.get());
        boolean attackReady = smartZombie.getAttackStrengthScale(0.5F) >= 1.0F;
        boolean targetThreatening = isTargetThreatening(target, distance, reach);

        if (distance > reach + MELEE_APPROACH_MARGIN || !lineOfSight) {
            lowerShield();
            smartZombie.setSprinting(true);
            if (smartZombie.tickCount % 5 == 0 || smartZombie.getNavigation().isDone()) {
                smartZombie.getNavigation().moveTo(target, speedModifier * 1.25D);
            }
            return;
        }

        smartZombie.getNavigation().stop();
        if (postHitRetreatTicks > 0) {
            --postHitRetreatTicks;
            raiseShield();
            circleStrafe(-0.8F, 0.35F * strafeDirection);
            return;
        }

        if (targetJustSwung && distance > reach) {
            lowerShield();
            smartZombie.setSprinting(true);
            if (smartZombie.tickCount % 3 == 0 || smartZombie.getNavigation().isDone()) {
                smartZombie.getNavigation().moveTo(target, speedModifier * 1.3D);
            }
            return;
        }

        if (!attackReady) {
            if (targetThreatening) {
                raiseShield();
            }
            float forward = distance < MIN_SPACING ? -0.65F : 0.12F;
            circleStrafe(forward, 0.7F * strafeDirection);
            return;
        }

        if (distance >= reach) {
            lowerShield();
            smartZombie.setSprinting(true);
            circleStrafe(0.8F, 0.28F * strafeDirection);
            return;
        }

        if (tryCriticalAttack(target, distance, reach)) {
            return;
        }

        performAttack(target, false);
    }

    private boolean tryCriticalAttack(LivingEntity target, double distance, double reach) {
        if (pendingCritical) {
            if (++criticalTicks > 12 || distance >= reach) {
                pendingCritical = false;
                criticalTicks = 0;
                return false;
            }
            smartZombie.setSprinting(false);
            circleStrafe(0.2F, 0.2F * strafeDirection);
            if (smartZombie.fallDistance > 0.0F && !smartZombie.onGround()
                    && !smartZombie.isInWater() && !smartZombie.onClimbable()) {
                performAttack(target, true);
            }
            return true;
        }

        if (smartZombie.onGround() && distance < reach - 0.25D
                && smartZombie.getRandom().nextFloat() < 0.16F) {
            lowerShield();
            smartZombie.setSprinting(false);
            smartZombie.getJumpControl().jump();
            pendingCritical = true;
            criticalTicks = 0;
            return true;
        }
        return false;
    }

    private void performAttack(LivingEntity target, boolean critical) {
        lowerShield();
        smartZombie.setSprinting(!critical);
        if (critical) {
            smartZombie.prepareCriticalAttack();
        }
        smartZombie.swing(InteractionHand.MAIN_HAND);
        boolean hit = smartZombie.doHurtTarget(target);
        if (hit) {
            if (target instanceof Player player && player.isBlocking()
                    && smartZombie.getMainHandItem().getItem() instanceof AxeItem) {
                player.disableShield(true);
            }
            postHitRetreatTicks = 3;
            smartZombie.setSprinting(false);
            raiseShield();
        }
        pendingCritical = false;
        criticalTicks = 0;
    }

    private boolean isTargetThreatening(LivingEntity target, double distance, double reach) {
        if (distance > reach + 0.5D) {
            return false;
        }
        if (target.swinging && target.swingTime <= 3) {
            return true;
        }
        return target instanceof Player player && player.getAttackStrengthScale(0.0F) > 0.82F;
    }

    private boolean shouldRetreat() {
        return smartZombie.getHealth() <= smartZombie.getMaxHealth() * 0.35F
                || smartZombie.playerData().foodLevel() <= 10;
    }

    private double distanceToHitbox(LivingEntity target) {
        return Math.sqrt(target.getBoundingBox().inflate(target.getPickRadius())
                .distanceToSqr(smartZombie.getEyePosition()));
    }

    private void retreatFrom(LivingEntity target, double speed) {
        Vec3 away = smartZombie.position().subtract(target.position());
        if (away.horizontalDistanceSqr() < 1.0E-4D) {
            away = new Vec3(1.0D, 0.0D, 0.0D);
        }
        away = away.normalize();
        if (smartZombie.tickCount % 5 == 0 || smartZombie.getNavigation().isDone()) {
            smartZombie.getNavigation().moveTo(
                    smartZombie.getX() + away.x * 6.0D,
                    smartZombie.getY(),
                    smartZombie.getZ() + away.z * 6.0D,
                    speed);
        }
    }

    private void circleStrafe(float forward, float sideways) {
        smartZombie.getMoveControl().strafe(forward, sideways);
    }

    private void raiseShield() {
        if (smartZombie.getOffhandItem().getItem() instanceof ShieldItem) {
            if (!smartZombie.isUsingItem()) {
                smartZombie.startUsingItem(InteractionHand.OFF_HAND);
            }
        }
    }

    private void lowerShield() {
        if (smartZombie.isUsingItem()) {
            smartZombie.stopUsingItem();
        }
    }

    private boolean finishTacticalItemUse() {
        if (smartZombie.isUsingItem() && !(smartZombie.getUseItem().getItem() instanceof ShieldItem)) {
            return true;
        }
        if (consumingFood && !smartZombie.isUsingItem()) {
            if (consumedFood != null) {
                smartZombie.playerData().eat(consumedFood);
            }
            consumingFood = false;
            consumedFood = null;
            equipShield();
        }
        return false;
    }

    private boolean tryUseRecoveryItem() {
        if (smartZombie.isUsingItem()) {
            return true;
        }
        int slot = findBestInventoryItem(this::isRecoveryItem, this::recoveryScore);
        EquipmentSlot useSlot = smartZombie.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        if (slot < 0 || !equipFromInventory(slot, useSlot)) {
            return false;
        }
        InteractionHand useHand = useSlot == EquipmentSlot.MAINHAND
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = smartZombie.getItemInHand(useHand);
        consumedFood = stack.getFoodProperties(smartZombie);
        consumingFood = consumedFood != null;
        smartZombie.startUsingItem(useHand);
        return smartZombie.isUsingItem();
    }

    private boolean tryUsePreparationPotion() {
        if (smartZombie.isUsingItem() || smartZombie.tickCount % 20 != 0) {
            return false;
        }
        int slot = findBestInventoryItem(this::isUsefulPreparationPotion, stack -> 1.0D);
        if (slot < 0 || !equipFromInventory(slot, EquipmentSlot.OFFHAND)) {
            return false;
        }
        smartZombie.startUsingItem(InteractionHand.OFF_HAND);
        return smartZombie.isUsingItem();
    }

    private boolean isRecoveryItem(ItemStack stack) {
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.GOLDEN_APPLE)) {
            return true;
        }
        if (stack.is(Items.APPLE)) {
            return smartZombie.playerData().foodLevel() <= 10;
        }
        return stack.is(Items.POTION) && PotionUtils.getMobEffects(stack).stream().anyMatch(effect ->
                effect.getEffect() == MobEffects.HEAL || effect.getEffect() == MobEffects.REGENERATION);
    }

    private double recoveryScore(ItemStack stack) {
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return 4.0D;
        if (stack.is(Items.GOLDEN_APPLE)) return 3.0D;
        if (stack.getItem() instanceof PotionItem) return 2.0D;
        if (stack.is(Items.APPLE)) return 1.0D;
        return 0.0D;
    }

    private boolean isUsefulPreparationPotion(ItemStack stack) {
        if (!stack.is(Items.POTION)) {
            return false;
        }
        return PotionUtils.getMobEffects(stack).stream().anyMatch(effect ->
                (effect.getEffect() == MobEffects.DAMAGE_BOOST && !smartZombie.hasEffect(MobEffects.DAMAGE_BOOST))
                        || (effect.getEffect() == MobEffects.MOVEMENT_SPEED
                        && !smartZombie.hasEffect(MobEffects.MOVEMENT_SPEED)));
    }

    private boolean useRangedWeapon(LivingEntity target) {
        ItemStack held = smartZombie.getMainHandItem();
        if (!(held.getItem() instanceof BowItem) && !(held.getItem() instanceof CrossbowItem)) {
            int weaponSlot = findBestInventoryItem(
                    stack -> stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem,
                    stack -> stack.getItem() instanceof CrossbowItem ? 2.0D : 1.0D);
            if (weaponSlot < 0 || !equipFromInventory(weaponSlot, EquipmentSlot.MAINHAND)) {
                return false;
            }
            held = smartZombie.getMainHandItem();
        }

        if (held.getItem() instanceof CrossbowItem) {
            return useCrossbow(target, held);
        }

        int arrowSlot = findBestInventoryItem(stack -> stack.getItem() instanceof ArrowItem, stack -> 1.0D);
        if (arrowSlot < 0) {
            return false;
        }
        if (++rangedChargeTicks < 20) {
            return true;
        }

        ItemStack arrowStack = smartZombie.playerData().inventory().getItem(arrowSlot);
        ArrowItem arrowItem = (ArrowItem) arrowStack.getItem();
        AbstractArrow arrow = arrowItem.createArrow(smartZombie.level(), arrowStack, smartZombie);
        Vec3 origin = smartZombie.getEyePosition();
        Vec3 aim = target.getEyePosition().add(target.getDeltaMovement().scale(0.45D));
        Vec3 direction = aim.subtract(origin);
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        arrow.shoot(direction.x, direction.y + horizontal * 0.03D, direction.z, 3.0F, 1.0F);
        arrow.setCritArrow(true);
        smartZombie.level().addFreshEntity(arrow);
        arrowStack.shrink(1);
        smartZombie.getMainHandItem().hurtAndBreak(1, smartZombie,
                zombie -> zombie.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        rangedChargeTicks = 0;
        rangedCooldownTicks = 24;
        smartZombie.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private boolean useCrossbow(LivingEntity target, ItemStack crossbow) {
        if (CrossbowItem.isCharged(crossbow)) {
            fireCrossbow(target, crossbow);
            return true;
        }

        int projectileSlot = findBestInventoryItem(stack -> stack.is(Items.FIREWORK_ROCKET), stack -> 2.0D);
        if (projectileSlot < 0) {
            projectileSlot = findBestInventoryItem(stack -> stack.getItem() instanceof ArrowItem, stack -> 1.0D);
        }
        if (projectileSlot < 0) {
            return false;
        }
        if (++rangedChargeTicks < CrossbowItem.getChargeDuration(crossbow)) {
            return true;
        }

        ItemStack ammunition = smartZombie.playerData().inventory().getItem(projectileSlot);
        int projectileCount = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, crossbow) > 0 ? 3 : 1;
        ListTag chargedProjectiles = new ListTag();
        for (int index = 0; index < projectileCount; ++index) {
            CompoundTag projectileTag = new CompoundTag();
            ammunition.copyWithCount(1).save(projectileTag);
            chargedProjectiles.add(projectileTag);
        }
        crossbow.getOrCreateTag().put("ChargedProjectiles", chargedProjectiles);
        CrossbowItem.setCharged(crossbow, true);
        ammunition.shrink(1);
        rangedChargeTicks = 0;
        fireCrossbow(target, crossbow);
        return true;
    }

    private void fireCrossbow(LivingEntity target, ItemStack crossbow) {
        aimDirectlyAt(target);
        float velocity = CrossbowItem.containsChargedProjectile(crossbow, Items.FIREWORK_ROCKET)
                ? 1.6F : 3.15F;
        CrossbowItem.performShooting(smartZombie.level(), smartZombie, InteractionHand.MAIN_HAND,
                crossbow, velocity, 1.0F);
        CrossbowItem.setCharged(crossbow, false);
        rangedChargeTicks = 0;
        rangedCooldownTicks = 24;
        smartZombie.swing(InteractionHand.MAIN_HAND);
    }

    private void aimDirectlyAt(LivingEntity target) {
        Vec3 direction = target.getEyePosition().add(target.getDeltaMovement().scale(0.35D))
                .subtract(smartZombie.getEyePosition());
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float yaw = (float) (Mth.atan2(direction.z, direction.x) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(direction.y, horizontal) * (180.0D / Math.PI)));
        smartZombie.setYRot(yaw);
        smartZombie.setYHeadRot(yaw);
        smartZombie.setXRot(pitch);
    }

    private boolean tryThrowEnderPearl(LivingEntity target, boolean retreating) {
        if (pearlCooldownTicks > 0) {
            return false;
        }
        int pearlSlot = findBestInventoryItem(stack -> stack.is(Items.ENDER_PEARL), stack -> 1.0D);
        if (pearlSlot < 0) {
            return false;
        }
        SimpleContainer inventory = smartZombie.playerData().inventory();
        int pearlCount = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); ++slot) {
            if (inventory.getItem(slot).is(Items.ENDER_PEARL)) {
                pearlCount += inventory.getItem(slot).getCount();
            }
        }
        double targetDistance = smartZombie.distanceTo(target);
        if (retreating) {
            if (smartZombie.getHealth() > smartZombie.getMaxHealth() * 0.35F
                    || smartZombie.getHealth() <= 5.0F || targetDistance > 7.0D) {
                return false;
            }
        } else if (pearlCount <= 1) {
            return false;
        }

        Vec3 direction;
        if (retreating) {
            direction = smartZombie.position().subtract(target.position());
            direction = new Vec3(direction.x, Math.max(0.35D, direction.horizontalDistance() * 0.08D), direction.z);
        } else {
            direction = target.getEyePosition().add(target.getDeltaMovement().scale(0.6D))
                    .subtract(smartZombie.getEyePosition());
            direction = direction.add(0.0D, direction.horizontalDistance() * 0.04D, 0.0D);
        }
        if (direction.lengthSqr() < 1.0E-4D) {
            return false;
        }

        SmartZombiePearl pearl = new SmartZombiePearl(smartZombie.level(), smartZombie);
        pearl.shoot(direction.x, direction.y, direction.z, 1.5F, 1.0F);
        smartZombie.level().addFreshEntity(pearl);
        inventory.getItem(pearlSlot).shrink(1);
        pearlCooldownTicks = 100;
        smartZombie.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private boolean tryPlaceWater(LivingEntity target) {
        if (!canUseTacticalFluid() || smartZombie.level().dimensionType().ultraWarm()) {
            return false;
        }
        int bucketSlot = findBestInventoryItem(stack -> stack.is(Items.WATER_BUCKET), stack -> 1.0D);
        if (bucketSlot < 0) {
            return false;
        }

        BlockPos placePos;
        if (smartZombie.fallDistance > 3.0F) {
            BlockPos cursor = smartZombie.blockPosition();
            placePos = null;
            for (int depth = 0; depth <= 5; ++depth) {
                BlockPos ground = cursor.below(depth + 1);
                if (smartZombie.level().getBlockState(ground).isFaceSturdy(
                        smartZombie.level(), ground, Direction.UP)) {
                    placePos = ground.above();
                    break;
                }
            }
            if (placePos == null) {
                return false;
            }
        } else {
            placePos = smartZombie.blockPosition();
            if (!canPlaceFluidAt(placePos)) {
                Vec3 away = smartZombie.position().subtract(target.position());
                Direction direction = Direction.getNearest(away.x, 0.0D, away.z);
                placePos = placePos.relative(direction);
            }
        }
        return placeTacticalFluid(placePos, bucketSlot, false);
    }

    private boolean tryPlaceLava(LivingEntity target) {
        if (!canUseTacticalFluid() || target.fireImmune()
                || smartZombie.distanceToSqr(target) < 6.25D) {
            return false;
        }
        int bucketSlot = findBestInventoryItem(stack -> stack.is(Items.LAVA_BUCKET), stack -> 1.0D);
        if (bucketSlot < 0) {
            return false;
        }
        Vec3 predicted = target.position().add(target.getDeltaMovement().scale(2.0D));
        BlockPos placePos = BlockPos.containing(predicted.x, target.getY(), predicted.z);
        return placeTacticalFluid(placePos, bucketSlot, true);
    }

    private boolean canUseTacticalFluid() {
        return fluidActionCooldownTicks <= 0 && tacticalFluidPos == null
                && smartZombie.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    private boolean canPlaceFluidAt(BlockPos pos) {
        BlockState state = smartZombie.level().getBlockState(pos);
        BlockPos below = pos.below();
        return state.canBeReplaced() && smartZombie.level().getBlockState(below)
                .isFaceSturdy(smartZombie.level(), below, Direction.UP);
    }

    private boolean placeTacticalFluid(BlockPos pos, int inventorySlot, boolean lava) {
        if (!canPlaceFluidAt(pos)) {
            return false;
        }
        BlockState fluidState = lava ? Blocks.LAVA.defaultBlockState() : Blocks.WATER.defaultBlockState();
        if (!smartZombie.level().setBlock(pos, fluidState, 3)) {
            return false;
        }
        smartZombie.playerData().inventory().setItem(inventorySlot, new ItemStack(Items.BUCKET));
        tacticalFluidPos = pos.immutable();
        tacticalFluidIsLava = lava;
        fluidPickupTicks = lava ? 60 : 40;
        fluidActionCooldownTicks = lava ? 120 : 80;
        smartZombie.level().playSound(null, pos,
                lava ? SoundEvents.BUCKET_EMPTY_LAVA : SoundEvents.BUCKET_EMPTY,
                SoundSource.HOSTILE, 1.0F, 1.0F);
        smartZombie.level().gameEvent(smartZombie, GameEvent.FLUID_PLACE, pos);
        return true;
    }

    private void tickFluidRecovery() {
        if (tacticalFluidPos == null || --fluidPickupTicks > 0) {
            return;
        }
        BlockState state = smartZombie.level().getBlockState(tacticalFluidPos);
        if (!state.is(tacticalFluidIsLava ? Blocks.LAVA : Blocks.WATER)) {
            tacticalFluidPos = null;
            return;
        }
        int bucketSlot = findBestInventoryItem(stack -> stack.is(Items.BUCKET), stack -> 1.0D);
        if (bucketSlot < 0) {
            fluidPickupTicks = 20;
            return;
        }
        smartZombie.level().setBlock(tacticalFluidPos, Blocks.AIR.defaultBlockState(), 3);
        smartZombie.playerData().inventory().setItem(bucketSlot,
                new ItemStack(tacticalFluidIsLava ? Items.LAVA_BUCKET : Items.WATER_BUCKET));
        smartZombie.level().playSound(null, tacticalFluidPos,
                tacticalFluidIsLava ? SoundEvents.BUCKET_FILL_LAVA : SoundEvents.BUCKET_FILL,
                SoundSource.HOSTILE, 1.0F, 1.0F);
        smartZombie.level().gameEvent(smartZombie, GameEvent.FLUID_PICKUP, tacticalFluidPos);
        tacticalFluidPos = null;
    }

    private void chooseMeleeWeapon(LivingEntity target) {
        boolean needsAxe = target instanceof Player player && player.isBlocking();
        Predicate<ItemStack> weaponTest = needsAxe
                ? stack -> stack.getItem() instanceof AxeItem
                : stack -> stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
        ItemStack held = smartZombie.getMainHandItem();
        double heldScore = weaponTest.test(held) ? weaponScore(held) : -1.0D;
        int slot = findBestInventoryItem(weaponTest, this::weaponScore);
        if (slot >= 0) {
            ItemStack candidate = smartZombie.playerData().inventory().getItem(slot);
            if (weaponScore(candidate) > heldScore + 0.01D) {
                equipFromInventory(slot, EquipmentSlot.MAINHAND);
            }
        }
    }

    private double weaponScore(ItemStack stack) {
        double score = 0.0D;
        for (AttributeModifier modifier : stack.getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE)) {
            score += modifier.getAmount();
        }
        return score;
    }

    private void equipShield() {
        if (smartZombie.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                && smartZombie.getHealth() <= smartZombie.getMaxHealth() * 0.25F) {
            return;
        }
        if (smartZombie.getOffhandItem().getItem() instanceof ShieldItem) {
            return;
        }
        int slot = findBestInventoryItem(stack -> stack.getItem() instanceof ShieldItem,
                stack -> stack.getMaxDamage() - stack.getDamageValue());
        if (slot >= 0) {
            equipFromInventory(slot, EquipmentSlot.OFFHAND);
        }
    }

    private void equipTotemIfCritical() {
        if (smartZombie.getHealth() > smartZombie.getMaxHealth() * 0.25F
                || smartZombie.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        int slot = findBestInventoryItem(stack -> stack.is(Items.TOTEM_OF_UNDYING), stack -> 1.0D);
        if (slot >= 0) {
            equipFromInventory(slot, EquipmentSlot.OFFHAND);
        }
    }

    private void equipBestArmor() {
        for (EquipmentSlot equipmentSlot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack equipped = smartZombie.getItemBySlot(equipmentSlot);
            double equippedScore = armorScore(equipped, equipmentSlot);
            int slot = findBestInventoryItem(stack -> stack.getItem() instanceof ArmorItem armor
                            && armor.getEquipmentSlot() == equipmentSlot,
                    stack -> armorScore(stack, equipmentSlot));
            if (slot >= 0 && armorScore(smartZombie.playerData().inventory().getItem(slot), equipmentSlot)
                    > equippedScore + 0.01D) {
                equipFromInventory(slot, equipmentSlot);
            }
        }
    }

    private double armorScore(ItemStack stack, EquipmentSlot slot) {
        if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) {
            return -1.0D;
        }
        return armor.getDefense() + armor.getToughness() * 0.5D
                + (double) (stack.getMaxDamage() - stack.getDamageValue()) / Math.max(1, stack.getMaxDamage());
    }

    private int findBestInventoryItem(Predicate<ItemStack> predicate, ToDoubleFunction<ItemStack> score) {
        SimpleContainer inventory = smartZombie.playerData().inventory();
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int slot = 0; slot < inventory.getContainerSize(); ++slot) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && predicate.test(stack)) {
                double candidateScore = score.applyAsDouble(stack);
                if (candidateScore > bestScore) {
                    bestScore = candidateScore;
                    bestSlot = slot;
                }
            }
        }
        return bestSlot;
    }

    private boolean equipFromInventory(int inventorySlot, EquipmentSlot equipmentSlot) {
        SimpleContainer inventory = smartZombie.playerData().inventory();
        ItemStack incoming = inventory.getItem(inventorySlot);
        if (incoming.isEmpty()) {
            return false;
        }
        ItemStack previous = smartZombie.getItemBySlot(equipmentSlot);
        inventory.setItem(inventorySlot, previous);
        smartZombie.setItemSlot(equipmentSlot, incoming);
        if (equipmentSlot == EquipmentSlot.MAINHAND && inventorySlot < 9) {
            smartZombie.playerData().setSelectedHotbarSlot(inventorySlot);
        }
        return true;
    }
}
