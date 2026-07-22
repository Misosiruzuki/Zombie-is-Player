package dev.misosiruzuki.zombieisplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

public final class SmartZombiePlayerData {
    public static final int PLAYER_INVENTORY_SIZE = 41;
    public static final int ENDER_CHEST_SIZE = 27;

    private final SimpleContainer inventory = new SimpleContainer(PLAYER_INVENTORY_SIZE);
    private final SimpleContainer enderChest = new SimpleContainer(ENDER_CHEST_SIZE);
    private final Abilities abilities = new Abilities();
    private int foodLevel = 20;
    private int lastFoodLevel = 20;
    private int foodTickTimer;
    private float saturationLevel = 5.0F;
    private float exhaustionLevel;
    private int selectedHotbarSlot;
    private int experienceLevel;
    private int totalExperience;
    private float experienceProgress;
    private int score;

    public void tickFood(SmartZombie zombie) {
        lastFoodLevel = foodLevel;
        if (exhaustionLevel > 4.0F) {
            exhaustionLevel -= 4.0F;
            if (saturationLevel > 0.0F) {
                saturationLevel = Math.max(saturationLevel - 1.0F, 0.0F);
            } else if (zombie.level().getDifficulty() != Difficulty.PEACEFUL) {
                foodLevel = Math.max(foodLevel - 1, 0);
            }
        }

        boolean naturalRegeneration = zombie.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        if (naturalRegeneration && saturationLevel > 0.0F && zombie.getHealth() < zombie.getMaxHealth() && foodLevel >= 20) {
            if (++foodTickTimer >= 10) {
                float amount = Math.min(saturationLevel, 6.0F);
                zombie.heal(amount / 6.0F);
                addExhaustion(amount);
                foodTickTimer = 0;
            }
        } else if (naturalRegeneration && foodLevel >= 18 && zombie.getHealth() < zombie.getMaxHealth()) {
            if (++foodTickTimer >= 80) {
                zombie.heal(1.0F);
                addExhaustion(6.0F);
                foodTickTimer = 0;
            }
        } else if (foodLevel <= 0) {
            if (++foodTickTimer >= 80) {
                float health = zombie.getHealth();
                switch (zombie.level().getDifficulty()) {
                    case HARD -> zombie.hurt(zombie.damageSources().starve(), 1.0F);
                    case NORMAL -> {
                        if (health > 1.0F) zombie.hurt(zombie.damageSources().starve(), 1.0F);
                    }
                    case EASY -> {
                        if (health > 10.0F) zombie.hurt(zombie.damageSources().starve(), 1.0F);
                    }
                    default -> {
                    }
                }
                foodTickTimer = 0;
            }
        } else {
            foodTickTimer = 0;
        }
    }

    public void addExhaustion(float amount) {
        exhaustionLevel = Math.min(exhaustionLevel + amount, 40.0F);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Inventory", saveContainer(inventory));
        tag.put("EnderItems", saveContainer(enderChest));
        abilities.addSaveData(tag);
        tag.putInt("foodLevel", foodLevel);
        tag.putInt("foodLastLevel", lastFoodLevel);
        tag.putInt("foodTickTimer", foodTickTimer);
        tag.putFloat("foodSaturationLevel", saturationLevel);
        tag.putFloat("foodExhaustionLevel", exhaustionLevel);
        tag.putInt("SelectedItemSlot", selectedHotbarSlot);
        tag.putInt("XpLevel", experienceLevel);
        tag.putInt("XpTotal", totalExperience);
        tag.putFloat("XpP", experienceProgress);
        tag.putInt("Score", score);
        return tag;
    }

    public void load(CompoundTag tag) {
        loadContainer(tag.getList("Inventory", CompoundTag.TAG_COMPOUND), inventory);
        loadContainer(tag.getList("EnderItems", CompoundTag.TAG_COMPOUND), enderChest);
        abilities.loadSaveData(tag);
        foodLevel = tag.contains("foodLevel") ? tag.getInt("foodLevel") : 20;
        lastFoodLevel = tag.contains("foodLastLevel") ? tag.getInt("foodLastLevel") : foodLevel;
        foodTickTimer = tag.getInt("foodTickTimer");
        saturationLevel = tag.contains("foodSaturationLevel") ? tag.getFloat("foodSaturationLevel") : 5.0F;
        exhaustionLevel = tag.getFloat("foodExhaustionLevel");
        selectedHotbarSlot = Math.max(0, Math.min(8, tag.getInt("SelectedItemSlot")));
        experienceLevel = Math.max(0, tag.getInt("XpLevel"));
        totalExperience = Math.max(0, tag.getInt("XpTotal"));
        experienceProgress = Math.max(0.0F, Math.min(1.0F, tag.getFloat("XpP")));
        score = tag.getInt("Score");
    }

    private static ListTag saveContainer(SimpleContainer container) {
        ListTag items = new ListTag();
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = stack.save(new CompoundTag());
                itemTag.putByte("Slot", (byte) slot);
                items.add(itemTag);
            }
        }
        return items;
    }

    private static void loadContainer(ListTag items, SimpleContainer container) {
        container.clearContent();
        for (int index = 0; index < items.size(); index++) {
            CompoundTag itemTag = items.getCompound(index);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot < container.getContainerSize()) {
                container.setItem(slot, ItemStack.of(itemTag));
            }
        }
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    public SimpleContainer enderChest() {
        return enderChest;
    }

    public Abilities abilities() {
        return abilities;
    }

    public int foodLevel() {
        return foodLevel;
    }

    public float saturationLevel() {
        return saturationLevel;
    }

    public float exhaustionLevel() {
        return exhaustionLevel;
    }

    public int selectedHotbarSlot() {
        return selectedHotbarSlot;
    }

    public int experienceLevel() {
        return experienceLevel;
    }

    public int totalExperience() {
        return totalExperience;
    }

    public float experienceProgress() {
        return experienceProgress;
    }

    public int score() {
        return score;
    }
}
