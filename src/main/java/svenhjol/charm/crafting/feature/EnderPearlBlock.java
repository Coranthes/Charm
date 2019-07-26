package svenhjol.charm.crafting.feature;

import net.minecraft.block.material.Material;
import net.minecraft.entity.monster.SilverfishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import svenhjol.charm.crafting.block.EnderPearlBlockBlock;
import svenhjol.charm.crafting.goal.FormEndermiteGoal;
import svenhjol.meson.Feature;
import svenhjol.meson.helpers.WorldHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EnderPearlBlock extends Feature
{
    public static EnderPearlBlockBlock block;
    public static int hardness;
    public static int range;
    public static boolean showParticles;
    public static ForgeConfigSpec.BooleanValue teleportStabilize;
    public static ForgeConfigSpec.ConfigValue<Double> endermiteChance;

    @Override
    public String getDescription()
    {
        return "A storage block for ender pearls. Eating a chorus fruit will teleport you to a nearby ender pearl block.\n" +
            "If a silverfish burrows into an ender pearl block, it will become an endermite.";
    }

    @Override
    public void configure()
    {
        super.configure();

        endermiteChance = builder
            .comment("Chance (out of 1.0) of a silverfish burrowing into an Ender Pearl Block, creating an Endermite.")
            .define("Silverfish burrowing", 0.8D);

        teleportStabilize = builder
            .comment("If true, eating a Chorus Fruit while in range of an Ender Pearl Block will teleport you to it.")
            .define("Teleport stabilization", true);

        // internal
        hardness = 2;
        range = 8;
        showParticles = true;
    }

    @Override
    public void init()
    {
        super.init();
        block = new EnderPearlBlockBlock();
    }

    /**
     * When player eats Chorus Fruit in range of Ender Pearl Block, get the
     * coordinates of the closest portal block and teleport them to it.
     * @param event Use item event start event
     */
    @SubscribeEvent
    public void onItemUseFinish(LivingEntityUseItemEvent.Start event)
    {
        boolean didTeleport = false;

        if (teleportStabilize.get()
            && event.getEntityLiving() instanceof PlayerEntity
            && event.getItem().getItem() == Items.CHORUS_FRUIT
            && !event.getEntityLiving().getEntityWorld().isRemote
        ) {
            World world = event.getEntityLiving().world;
            PlayerEntity player = (PlayerEntity)event.getEntityLiving();
            Map<Double, BlockPos> positions = new HashMap<>();
            BlockPos playerPos = player.getPosition();
            BlockPos targetPos = null;

            // find the blocks around the player
            BlockPos.getAllInBox(playerPos.add(-range, -range, -range), playerPos.add(range, range, range)).forEach(pos -> {
                if (world.getBlockState(pos).getBlock() == block && !pos.up(1).equals(playerPos)) {

                    // should be able to stand on it
                    if (world.getBlockState(pos.up(1)).getMaterial() == Material.AIR && world.getBlockState(pos.up(2)).getMaterial() == Material.AIR)
                    {
                        positions.put(WorldHelper.getDistanceSq(playerPos, pos.up(1)), pos.up(1));
                    }
                }
            });

            // get the closest position by finding the smallest distance
            if (!positions.isEmpty()) {
                targetPos = positions.get(Collections.min(positions.keySet()));
            }

            if (targetPos != null) {
                double x = targetPos.getX() + 0.5D;
                double y = targetPos.getY();
                double z = targetPos.getZ() + 0.5D;
                if (player.attemptTeleport(x, y, z, true)) {
                    didTeleport = true;

                    // play sound at original location and new location
                    world.playSound(null, x, y, z, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
                    player.playSound(SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, 1.0F, 1.0F);
                }
            }

            if (didTeleport) {
                player.getCooldownTracker().setCooldown(Items.CHORUS_FRUIT, 20);
                event.getItem().shrink(1);
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onEnterChunk(EntityEvent.EnteringChunk event)
    {
        if (event.getEntity() instanceof SilverfishEntity) {
            SilverfishEntity silverfish = (SilverfishEntity)event.getEntity();

            boolean hasGoal = silverfish.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() instanceof FormEndermiteGoal);

            if (!hasGoal) {
                silverfish.goalSelector.addGoal(2, new FormEndermiteGoal(silverfish));
            }
        }
    }

    @Override
    public boolean hasSubscriptions()
    {
        return true;
    }
}