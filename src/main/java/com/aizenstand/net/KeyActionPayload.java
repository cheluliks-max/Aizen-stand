package com.aizenstand.net;

import com.aizenstand.AizenStandMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: a key was pressed. action: 0 = summon/dismiss (G), 1 = Hado 90, 2 = Hado 99. */
public record KeyActionPayload(int action) implements CustomPayload {
    public static final CustomPayload.Id<KeyActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(AizenStandMod.MOD_ID, "key_action"));
    public static final PacketCodec<RegistryByteBuf, KeyActionPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, KeyActionPayload::action, KeyActionPayload::new);

    public static final int SUMMON = 0;
    public static final int HADO_90 = 1;
    public static final int HADO_99 = 2;

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
