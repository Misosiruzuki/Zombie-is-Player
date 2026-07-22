package dev.misosiruzuki.zombieisplayer.ai;

import com.mojang.authlib.GameProfile;
import dev.misosiruzuki.zombieisplayer.SmartZombie;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/** Non-hostile survival progression: gather, craft, place stations and smelt. */
public final class SurvivalActionGoal extends Goal {
    private final SmartZombie zombie;
    private BlockPos miningTarget;
    private BlockPos craftingTablePos;
    private BlockPos furnacePos;
    private float miningProgress;
    private int cooldown;

    public SurvivalActionGoal(SmartZombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() { return !zombie.isCombatMode() && zombie.level() instanceof ServerLevel; }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }

    @Override public void stop() {
        clearMining();
        zombie.getNavigation().stop();
    }

    @Override public void tick() {
        if (!(zombie.level() instanceof ServerLevel level)) return;
        if (cooldown > 0) --cooldown;
        if (seekDrop(level)) return;
        refreshStations(level);
        if (operateFurnace(level)) return;
        if (cooldown == 0 && craftProgression(level)) { cooldown = 10; return; }
        if (placeStations(level)) return;
        if (miningTarget == null || !wanted(level.getBlockState(miningTarget))) {
            miningTarget = findResource(level);
            miningProgress = 0;
        }
        if (miningTarget != null) mine(level);
    }

    private boolean seekDrop(ServerLevel level) {
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, zombie.getBoundingBox().inflate(7),
                item -> item.isAlive() && zombie.wantsToPickUp(item.getItem()));
        ItemEntity nearest = drops.stream().min((a,b) -> Double.compare(zombie.distanceToSqr(a), zombie.distanceToSqr(b))).orElse(null);
        if (nearest == null) return false;
        zombie.getNavigation().moveTo(nearest, 1.15);
        zombie.getLookControl().setLookAt(nearest, 20, 20);
        return true;
    }

    private void mine(ServerLevel level) {
        BlockState state = level.getBlockState(miningTarget);
        double reach = zombie.getAttributeValue(ForgeMod.BLOCK_REACH.get());
        if (zombie.getEyePosition().distanceToSqr(miningTarget.getCenter()) > reach * reach) {
            zombie.getNavigation().moveTo(miningTarget.getX()+.5, miningTarget.getY(), miningTarget.getZ()+.5, 1);
            return;
        }
        int tool = bestTool(state);
        if (state.requiresCorrectToolForDrops() && tool < 0 && !zombie.getMainHandItem().isCorrectToolForDrops(state)) {
            clearMining(); return;
        }
        if (tool >= 0) equip(tool);
        FakePlayer player = fake(level);
        miningProgress += state.getDestroyProgress(player, level, miningTarget);
        level.destroyBlockProgress(zombie.getId(), miningTarget, Math.min(9, (int)(miningProgress*10)));
        zombie.getLookControl().setLookAt(miningTarget.getX()+.5, miningTarget.getY()+.5, miningTarget.getZ()+.5, 30, 30);
        zombie.swing(InteractionHand.MAIN_HAND);
        if (miningProgress >= 1 && player.gameMode.destroyBlock(miningTarget)) {
            zombie.setItemSlot(EquipmentSlot.MAINHAND, player.getMainHandItem().copy());
            clearMining(); cooldown = 4;
        }
    }

    private FakePlayer fake(ServerLevel level) {
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(zombie.getUUID(), "[SmartZombie]"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(zombie.getX(), zombie.getY(), zombie.getZ());
        player.setOnGround(zombie.onGround());
        player.setYRot(zombie.getYRot());
        player.setXRot(zombie.getXRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, zombie.getMainHandItem().copy());
        player.removeAllEffects();
        for (MobEffectInstance effect : zombie.getActiveEffects()) {
            player.addEffect(new MobEffectInstance(effect));
        }
        return player;
    }

    private BlockPos findResource(ServerLevel level) {
        BlockPos origin = zombie.blockPosition(), best = null;
        double distance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-12,-4,-12), origin.offset(12,5,12))) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!wanted(state) || (state.requiresCorrectToolForDrops() && bestTool(state)<0
                    && !zombie.getMainHandItem().isCorrectToolForDrops(state))) continue;
            double d = pos.distSqr(origin);
            if (d < distance) { best=pos.immutable(); distance=d; }
        }
        return best;
    }

    private boolean wanted(BlockState state) {
        if (count(s->s.is(ItemTags.LOGS)) < 10 && state.is(BlockTags.LOGS)) return true;
        if (count(s->s.is(Items.COBBLESTONE)||s.is(Items.COBBLED_DEEPSLATE)) < 20
                && (state.is(Blocks.STONE)||state.is(Blocks.DEEPSLATE))) return hasPickaxe();
        if (count(s->s.is(Items.RAW_IRON)) < 8 && (state.is(Blocks.IRON_ORE)||state.is(Blocks.DEEPSLATE_IRON_ORE))) return hasStonePick();
        return count(s->s.is(Items.COAL)||s.is(Items.CHARCOAL)) < 8
                && (state.is(Blocks.COAL_ORE)||state.is(Blocks.DEEPSLATE_COAL_ORE)) && hasPickaxe();
    }

    private int bestTool(BlockState state) {
        int best=-1; float speed=zombie.getMainHandItem().getDestroySpeed(state);
        SimpleContainer inv=zombie.playerData().inventory();
        for(int i=0;i<inv.getContainerSize();i++) {
            ItemStack stack=inv.getItem(i);
            if (state.requiresCorrectToolForDrops()&&!stack.isCorrectToolForDrops(state)) continue;
            if(stack.getDestroySpeed(state)>speed){speed=stack.getDestroySpeed(state);best=i;}
        }
        return best;
    }

    private boolean craftProgression(ServerLevel level) {
        if(count(s->s.is(ItemTags.PLANKS))<16 && craft(level,s->s.is(ItemTags.PLANKS))) return true;
        if(count(s->s.is(Items.STICK))<8 && craft(level,s->s.is(Items.STICK))) return true;
        if(!hasItem(Items.CRAFTING_TABLE)&&craftingTablePos==null&&craft(level,s->s.is(Items.CRAFTING_TABLE))) return true;
        if(!hasPickaxe()&&craft(level,s->s.is(Items.WOODEN_PICKAXE))) return true;
        if(!hasStonePick()&&craft(level,s->s.is(Items.STONE_PICKAXE))) return true;
        if(!hasItem(Items.FURNACE)&&furnacePos==null&&craft(level,s->s.is(Items.FURNACE))) return true;
        Item[] goals={Items.STONE_AXE,Items.STONE_SWORD,Items.IRON_PICKAXE,Items.IRON_SWORD,Items.IRON_AXE,
                Items.SHIELD,Items.BUCKET,Items.BOW,Items.CROSSBOW};
        for(Item item:goals) if(!hasItem(item)&&craft(level,s->s.is(item))) return true;
        if(count(s->s.is(Items.ARROW))<16&&craft(level,s->s.is(Items.ARROW))) return true;
        return false;
    }

    private boolean craft(ServerLevel level, Predicate<ItemStack> output) {
        SimpleContainer inv=zombie.playerData().inventory();
        for(CraftingRecipe recipe:level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result=recipe.getResultItem(level.registryAccess());
            if(result.isEmpty()||!output.test(result)) continue;
            boolean table=!recipe.canCraftInDimensions(2,2);
            List<Integer> slots=ingredients(recipe.getIngredients(),inv);
            if(slots==null) continue;
            if(table&&!reachable(craftingTablePos,Blocks.CRAFTING_TABLE)) {
                if (validStation(craftingTablePos, Blocks.CRAFTING_TABLE)) {
                    zombie.getNavigation().moveTo(craftingTablePos.getX()+.5, craftingTablePos.getY(), craftingTablePos.getZ()+.5, 1);
                    return true;
                }
                continue;
            }
            for(int slot:slots){ItemStack stack=inv.getItem(slot);ItemStack rem=stack.getCraftingRemainingItem();stack.shrink(1);if(!rem.isEmpty())inv.addItem(rem);}
            storeOrDrop(inv, result.copy());
            zombie.swing(InteractionHand.MAIN_HAND);
            level.gameEvent(zombie,GameEvent.BLOCK_ACTIVATE,table?craftingTablePos:zombie.blockPosition());
            return true;
        }
        return false;
    }

    private List<Integer> ingredients(List<Ingredient> ingredients,SimpleContainer inv){
        int[] used=new int[inv.getContainerSize()];List<Integer> result=new ArrayList<>();
        for(Ingredient ingredient:ingredients){if(ingredient.isEmpty())continue;int found=-1;
            for(int i=0;i<inv.getContainerSize();i++)if(ingredient.test(inv.getItem(i))&&inv.getItem(i).getCount()>used[i]){found=i;break;}
            if(found<0)return null;used[found]++;result.add(found);}return result;
    }

    private boolean placeStations(ServerLevel level){
        if(craftingTablePos==null&&hasItem(Items.CRAFTING_TABLE)){craftingTablePos=place(level,Items.CRAFTING_TABLE,Blocks.CRAFTING_TABLE);return craftingTablePos!=null;}
        if(furnacePos==null&&hasItem(Items.FURNACE)){furnacePos=place(level,Items.FURNACE,Blocks.FURNACE);return furnacePos!=null;}return false;
    }

    private BlockPos place(ServerLevel level,Item item,Block block){int slot=find(s->s.is(item));if(slot<0)return null;
        for(Direction dir:Direction.Plane.HORIZONTAL){BlockPos pos=zombie.blockPosition().relative(dir);
            if(level.getBlockState(pos).canBeReplaced()&&level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)){
                level.setBlock(pos,block.defaultBlockState(),3);zombie.playerData().inventory().getItem(slot).shrink(1);
                level.gameEvent(zombie,GameEvent.BLOCK_PLACE,pos);return pos;}}return null;}

    private boolean operateFurnace(ServerLevel level){
        if(!validStation(furnacePos,Blocks.FURNACE))return false;
        BlockEntity be=level.getBlockEntity(furnacePos);if(!(be instanceof AbstractFurnaceBlockEntity furnace))return false;
        boolean hasRaw=find(i->i.is(Items.RAW_IRON))>=0;
        boolean hasFuel=find(i->i.is(Items.COAL)||i.is(Items.CHARCOAL)||i.is(ItemTags.PLANKS)||i.is(ItemTags.LOGS))>=0;
        boolean needsVisit=!furnace.getItem(2).isEmpty()||(furnace.getItem(0).isEmpty()&&hasRaw)
                ||(furnace.getItem(1).isEmpty()&&hasFuel&&(!furnace.getItem(0).isEmpty()||hasRaw));
        if(!reachable(furnacePos,Blocks.FURNACE)){
            if(needsVisit){zombie.getNavigation().moveTo(furnacePos.getX()+.5,furnacePos.getY(),furnacePos.getZ()+.5,1);return true;}
            return false;}
        ItemStack out=furnace.getItem(2);if(!out.isEmpty()){furnace.setItem(2,storeOrDrop(zombie.playerData().inventory(),out.copy()));return true;}
        boolean changed=false;
        if(furnace.getItem(0).isEmpty()){int s=find(i->i.is(Items.RAW_IRON));if(s>=0){furnace.setItem(0,zombie.playerData().inventory().removeItem(s,8));changed=true;}}
        if(furnace.getItem(1).isEmpty()){
            int s=find(i->i.is(Items.COAL)||i.is(Items.CHARCOAL));
            if(s<0)s=find(i->i.is(ItemTags.PLANKS)||i.is(ItemTags.LOGS));
            if(s>=0){furnace.setItem(1,zombie.playerData().inventory().removeItem(s,8));changed=true;}}
        if(changed)furnace.setChanged();return changed;}

    private void refreshStations(ServerLevel level){if(!validStation(craftingTablePos,Blocks.CRAFTING_TABLE))craftingTablePos=findBlock(level,Blocks.CRAFTING_TABLE);
        if(!validStation(furnacePos,Blocks.FURNACE))furnacePos=findBlock(level,Blocks.FURNACE);}
    private BlockPos findBlock(ServerLevel level,Block block){BlockPos o=zombie.blockPosition();for(BlockPos p:BlockPos.betweenClosed(o.offset(-16,-4,-16),o.offset(16,5,16)))if(level.getBlockState(p).is(block))return p.immutable();return null;}
    private boolean validStation(BlockPos p,Block b){return p!=null&&zombie.level().getBlockState(p).is(b);}
    private boolean reachable(BlockPos p,Block b){return p!=null&&zombie.level().getBlockState(p).is(b)&&zombie.getEyePosition().distanceToSqr(p.getCenter())<=Math.pow(zombie.getAttributeValue(ForgeMod.BLOCK_REACH.get()),2);}
    private boolean hasPickaxe(){return has(s->s.canPerformAction(ToolActions.PICKAXE_DIG));}
    private boolean hasStonePick(){return hasItem(Items.STONE_PICKAXE)||hasItem(Items.IRON_PICKAXE)||hasItem(Items.DIAMOND_PICKAXE)||hasItem(Items.NETHERITE_PICKAXE);}
    private boolean hasItem(Item item){return has(s->s.is(item));}
    private boolean has(Predicate<ItemStack> p){return p.test(zombie.getMainHandItem())||p.test(zombie.getOffhandItem())||find(p)>=0;}
    private int count(Predicate<ItemStack> p){int n=p.test(zombie.getMainHandItem())?zombie.getMainHandItem().getCount():0;SimpleContainer i=zombie.playerData().inventory();for(int s=0;s<i.getContainerSize();s++)if(p.test(i.getItem(s)))n+=i.getItem(s).getCount();return n;}
    private int find(Predicate<ItemStack> p){SimpleContainer i=zombie.playerData().inventory();for(int s=0;s<i.getContainerSize();s++)if(p.test(i.getItem(s)))return s;return -1;}
    private ItemStack storeOrDrop(SimpleContainer inventory,ItemStack stack){ItemStack remainder=inventory.addItem(stack);if(!remainder.isEmpty()){zombie.spawnAtLocation(remainder.copy());return ItemStack.EMPTY;}return remainder;}
    private void equip(int slot){SimpleContainer i=zombie.playerData().inventory();ItemStack in=i.getItem(slot),old=zombie.getMainHandItem();i.setItem(slot,old);zombie.setItemSlot(EquipmentSlot.MAINHAND,in);}
    private void clearMining(){if(miningTarget!=null&&zombie.level() instanceof ServerLevel l)l.destroyBlockProgress(zombie.getId(),miningTarget,-1);miningTarget=null;miningProgress=0;}
}
