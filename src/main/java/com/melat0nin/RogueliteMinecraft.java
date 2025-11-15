package com.melat0nin;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
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
        player.sendSystemMessage(Component.literal("§6A new day. Your inventory has been cleared except for your hotbar and any dropped items have been removed."));
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
}