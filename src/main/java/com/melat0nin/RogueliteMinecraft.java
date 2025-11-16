package com.melat0nin;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.entity.vehicle.MinecartChest;
import com.melat0nin.mixin.ChunkMapAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RogueliteMinecraft implements ModInitializer {
    public static final String MOD_ID = "rogueliteminecraft";

    private static final int HOTBAR_SIZE = 9;
    private long lastResetDay = -1;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Roguelite Minecraft mod initialized");

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                long timeOfDay = player.level().getDayTime() % 24000;
                long currentDay = player.level().getDayTime() / 24000;

                // Check if it's morning (time 0-100) and we haven't reset today
                if (timeOfDay < 100 && currentDay != lastResetDay) {
                    LOGGER.debug("Morning detected - Day: {}, Time: {}, Player: {}",
                            currentDay, timeOfDay, player.getName().getString());
                    performMorningReset(player);
                    removeAllDroppedItems(server);
                    removeAllChestLikeObjects(server);
                    lastResetDay = currentDay;
                    LOGGER.info("Morning reset completed for player: {}", player.getName().getString());
                }
            }
        });
    }

    private void performMorningReset(ServerPlayer player) {
        LOGGER.debug("Starting inventory clear for player: {}", player.getName().getString());

        int itemsCleared = 0;

        // Clear inventory except hotbar (slots 0-8)
        for (int i = HOTBAR_SIZE; i < player.getInventory().getContainerSize(); i++) {
            if (!player.getInventory().getItem(i).isEmpty()) {
                itemsCleared++;
            }
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }

        // Clear armor slots (slots 100-103)
        for (int slot : new int[]{100, 101, 102, 103}) {
            if (!player.getInventory().getItem(slot).isEmpty()) {
                itemsCleared++;
            }
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }

        // Clear offhand (slot 106)
        if (!player.getInventory().getItem(106).isEmpty()) {
            itemsCleared++;
        }
        player.getInventory().setItem(106, ItemStack.EMPTY);

        LOGGER.debug("Cleared {} items from player: {}", itemsCleared, player.getName().getString());

        // Notify player
        player.sendSystemMessage(Component.literal("§6A new day. Your inventory has been cleared except for your hotbar. All dropped items, chests, furnaces, chest boats, and minecart chests have been removed."));
    }

    private void removeAllDroppedItems(net.minecraft.server.MinecraftServer server) {
        LOGGER.debug("Starting removal of all dropped items in the world");

        int totalItemsRemoved = 0;

        // Iterate through all loaded dimensions/levels
        for (ServerLevel level : server.getAllLevels()) {
            int itemsInLevel = 0;

            // Create a large bounding box to cover the entire world
            AABB worldBounds = new AABB(
                    -30000000, level.getMinY(), -30000000,
                    30000000, level.getMaxY(), 30000000
            );

            // Get all item entities in this level and remove them
            for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, worldBounds)) {
                itemEntity.discard();
                itemsInLevel++;
            }

            totalItemsRemoved += itemsInLevel;

            if (itemsInLevel > 0) {
                LOGGER.debug("Removed {} dropped items from dimension: {}",
                        itemsInLevel, level.dimension().location());
            }
        }

        LOGGER.info("Total dropped items removed from world: {}", totalItemsRemoved);
    }

    private void removeAllChestLikeObjects(net.minecraft.server.MinecraftServer server) {
        LOGGER.debug("Starting removal of all chest-like items in the world");

        int totalChestsRemoved = 0;
        int totalFurnacesRemoved = 0;
        int totalChestBoatsRemoved = 0;
        int totalMinecartChestsRemoved = 0;

        // Iterate through all loaded dimensions/levels
        for (ServerLevel level : server.getAllLevels()) {
            int chestsInLevel = 0;
            int furnacesInLevel = 0;

            // Get all block entities in the level (only from loaded chunks)
            java.util.List<BlockPos> chestsToRemove = new java.util.ArrayList<>();
            java.util.List<BlockPos> furnacesToRemove = new java.util.ArrayList<>();

            // Use the accessor mixin to get the visible chunk map
            ChunkMapAccessor chunkMapAccessor = (ChunkMapAccessor) level.getChunkSource().chunkMap;

            // Iterate through loaded chunks
            for (ChunkHolder chunkHolder : chunkMapAccessor.getVisibleChunkMap().values()) {
                LevelChunk chunk = chunkHolder.getTickingChunk();
                if (chunk != null) {
                    // Get all block entities in the chunk
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        // Check if it's a chest
                        if (blockEntity instanceof ChestBlockEntity) {
                            chestsToRemove.add(blockEntity.getBlockPos());
                        }
                        // Check if it's a furnace
                        else if (blockEntity instanceof FurnaceBlockEntity) {
                            furnacesToRemove.add(blockEntity.getBlockPos());
                        }
                    }
                }
            }

            // Remove all found chests
            for (BlockPos pos : chestsToRemove) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof ChestBlockEntity chest) {
                    // Clear the chest inventory first (items are automatically destroyed)
                    chest.clearContent();

                    // Remove the chest block
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    chestsInLevel++;
                }
            }

            // Remove all found furnaces
            for (BlockPos pos : furnacesToRemove) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof FurnaceBlockEntity furnace) {
                    // Clear the furnace inventory first (items are automatically destroyed)
                    furnace.clearContent();

                    // Remove the furnace block
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    furnacesInLevel++;
                }
            }

            // Remove chest boats and minecart chests (entities)
            AABB worldBounds = new AABB(
                    -30000000, level.getMinY(), -30000000,
                    30000000, level.getMaxY(), 30000000
            );

            // Remove chest boats
            int chestBoatsInLevel = 0;
            for (ChestBoat chestBoat : level.getEntitiesOfClass(ChestBoat.class, worldBounds)) {
                chestBoat.clearContent(); // Clear inventory
                chestBoat.discard(); // Remove entity
                chestBoatsInLevel++;
            }

            // Remove minecart chests
            int minecartChestsInLevel = 0;
            for (MinecartChest minecartChest : level.getEntitiesOfClass(MinecartChest.class, worldBounds)) {
                minecartChest.clearContent(); // Clear inventory
                minecartChest.discard(); // Remove entity
                minecartChestsInLevel++;
            }

            totalChestsRemoved += chestsInLevel;
            totalFurnacesRemoved += furnacesInLevel;
            totalChestBoatsRemoved += chestBoatsInLevel;
            totalMinecartChestsRemoved += minecartChestsInLevel;

            if (chestsInLevel > 0 || furnacesInLevel > 0 || chestBoatsInLevel > 0 || minecartChestsInLevel > 0) {
                LOGGER.debug("Removed from dimension {}: {} chests, {} furnaces, {} chest boats, {} minecart chests",
                        level.dimension().location(), chestsInLevel, furnacesInLevel, chestBoatsInLevel, minecartChestsInLevel);
            }
        }

        LOGGER.info("Total removed - Chests: {}, Furnaces: {}, Chest Boats: {}, Minecart Chests: {}",
                totalChestsRemoved, totalFurnacesRemoved, totalChestBoatsRemoved, totalMinecartChestsRemoved);
    }
}