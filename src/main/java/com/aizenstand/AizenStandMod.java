package com.aizenstand;

import com.aizenstand.net.KeyActionPayload;
import com.aizenstand.net.StateSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class AizenStandMod implements ModInitializer {
    public static final String MOD_ID = "aizen_stand";

    public static Item STAND_ARROW;
    public static EntityType<AizenStandEntity> AIZEN_STAND;

    @Override
    public void onInitialize() {
        STAND_ARROW = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "stand_arrow"),
                new StandArrowItem(new Item.Settings().maxCount(16).rarity(Rarity.EPIC)));

        AIZEN_STAND = Registry.register(Registries.ENTITY_TYPE, Identifier.of(MOD_ID, "aizen_stand"),
                EntityType.Builder.create(AizenStandEntity::new, SpawnGroup.MISC)
                        .dimensions(0.7F, 2.3F)
                        .maxTrackingRange(10)
                        .trackingTickInterval(1)
                        .disableSaving()
                        .disableSummon()
                        .build("aizen_stand"));

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(STAND_ARROW));

        PayloadTypeRegistry.playC2S().register(KeyActionPayload.ID, KeyActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StateSyncPayload.ID, StateSyncPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(KeyActionPayload.ID,
                (payload, context) -> StandManager.handleAction(context.player(), payload.action()));

        ServerTickEvents.END_SERVER_TICK.register(StandManager::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> StandManager.onJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> StandManager.onDisconnect(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> StandManager.onServerStopped());
    }
}
