package dev.misosiruzuki.zombieisplayer;

import dev.misosiruzuki.zombieisplayer.config.ModConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraftforge.common.world.ForgeChunkManager;

import javax.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

public final class SmartZombie extends Zombie {
    private static final String PLAYER_DATA_TAG = "ZombieIsPlayerData";

    private final SmartZombiePlayerData playerData = new SmartZombiePlayerData();
    private final Set<Long> forcedChunks = new HashSet<>();
    private double lastFoodX;
    private double lastFoodZ;
    private boolean hasFoodPosition;

    public SmartZombie(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        setPersistenceRequired();
        setCanPickUpLoot(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            tickPlayerState();
            if (tickCount % 20 == 1) {
                refreshForcedChunks();
            }
        }
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide) {
            refreshForcedChunks();
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
                                        MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag spawnTag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, spawnData, spawnTag);
        setPersistenceRequired();
        setCanPickUpLoot(true);
        return result;
    }

    private void tickPlayerState() {
        playerData.tickFood(this);
        if (hasFoodPosition) {
            double dx = getX() - lastFoodX;
            double dz = getZ() - lastFoodZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > 0.0D) {
                playerData.addExhaustion((float) (distance * (isSprinting() ? 0.1D : 0.01D)));
            }
        }
        lastFoodX = getX();
        lastFoodZ = getZ();
        hasFoodPosition = true;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !level().isClientSide) {
            playerData.addExhaustion(0.1F);
        }
        return hit;
    }

    @Override
    public boolean teleportTo(ServerLevel destination, double x, double y, double z,
                              Set<RelativeMovement> relativeMovements, float yaw, float pitch) {
        if (destination == level()) {
            forceDestinationBeforeTeleport(destination, new ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4));
        }
        boolean teleported = super.teleportTo(destination, x, y, z, relativeMovements, yaw, pitch);
        if (teleported && destination == level()) {
            refreshForcedChunks();
        }
        return teleported;
    }

    private void forceDestinationBeforeTeleport(ServerLevel serverLevel, ChunkPos center) {
        int radius = ModConfig.SMART_ZOMBIE_CHUNK_RADIUS.get();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                long packed = ChunkPos.asLong(chunkX, chunkZ);
                if (forcedChunks.add(packed)) {
                    ForgeChunkManager.forceChunk(serverLevel, ZombieIsPlayer.MOD_ID, this, chunkX, chunkZ, true, true);
                }
            }
        }
    }

    @Override
    public void checkDespawn() {
        noActionTime = 0;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return playerData.inventory().canAddItem(stack);
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        ItemStack original = itemEntity.getItem();
        ItemStack remainder = playerData.inventory().addItem(original.copy());
        int taken = original.getCount() - remainder.getCount();
        if (taken > 0) {
            onItemPickup(itemEntity);
            take(itemEntity, taken);
            if (remainder.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(remainder);
            }
        }
    }

    private void refreshForcedChunks() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int radius = ModConfig.SMART_ZOMBIE_CHUNK_RADIUS.get();
        ChunkPos center = chunkPosition();
        Set<Long> desired = new HashSet<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                desired.add(ChunkPos.asLong(center.x + dx, center.z + dz));
            }
        }

        for (long packed : Set.copyOf(forcedChunks)) {
            if (!desired.contains(packed)) {
                ChunkPos chunk = new ChunkPos(packed);
                ForgeChunkManager.forceChunk(serverLevel, ZombieIsPlayer.MOD_ID, this, chunk.x, chunk.z, false, true);
                forcedChunks.remove(packed);
            }
        }
        for (long packed : desired) {
            if (forcedChunks.add(packed)) {
                ChunkPos chunk = new ChunkPos(packed);
                ForgeChunkManager.forceChunk(serverLevel, ZombieIsPlayer.MOD_ID, this, chunk.x, chunk.z, true, true);
            }
        }
    }

    private void releaseForcedChunks() {
        if (level() instanceof ServerLevel serverLevel) {
            for (long packed : forcedChunks) {
                ChunkPos chunk = new ChunkPos(packed);
                ForgeChunkManager.forceChunk(serverLevel, ZombieIsPlayer.MOD_ID, this, chunk.x, chunk.z, false, true);
            }
        }
        forcedChunks.clear();
    }

    @Override
    public void onRemovedFromWorld() {
        Entity.RemovalReason reason = getRemovalReason();
        // Keep persistent entity tickets during chunk serialization/server shutdown so the owner loads again.
        if (reason != null && reason != Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            releaseForcedChunks();
        }
        super.onRemovedFromWorld();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put(PLAYER_DATA_TAG, playerData.save());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setPersistenceRequired();
        setCanPickUpLoot(true);
        if (tag.contains(PLAYER_DATA_TAG, CompoundTag.TAG_COMPOUND)) {
            playerData.load(tag.getCompound(PLAYER_DATA_TAG));
        }
    }

    public SmartZombiePlayerData playerData() {
        return playerData;
    }
}
