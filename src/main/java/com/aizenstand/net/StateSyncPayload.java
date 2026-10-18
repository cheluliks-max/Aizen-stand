package com.aizenstand.net;

import com.aizenstand.AizenStandMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client: everything the HUD needs. Cooldowns are in ticks. */
public record StateSyncPayload(boolean hasStand, boolean summoned, int reiatsu, int cooldown90, int cooldown99)
        implements CustomPayload {
    public static final CustomPayload.Id<StateSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(AizenStandMod.MOD_ID, "state_sync"));
    public static final PacketCodec<RegistryByteBuf, StateSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, StateSyncPayload::hasStand,
            PacketCodecs.BOOL, StateSyncPayload::summoned,
            PacketCodecs.VAR_INT, StateSyncPayload::reiatsu,
            PacketCodecs.VAR_INT, StateSyncPayload::cooldown90,
            PacketCodecs.VAR_INT, StateSyncPayload::cooldown99,
            StateSyncPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
