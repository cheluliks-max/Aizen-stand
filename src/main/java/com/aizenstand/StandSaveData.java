package com.aizenstand;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Remembers (in the world save) which players have already been pierced by the Stand Arrow. */
public class StandSaveData extends PersistentState {
    private final Set<UUID> owners = new HashSet<>();

    public static final PersistentState.Type<StandSaveData> TYPE =
            new PersistentState.Type<>(StandSaveData::new, StandSaveData::fromNbt, null);

    public static StandSaveData get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, AizenStandMod.MOD_ID + "_owners");
    }

    public static StandSaveData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        StandSaveData data = new StandSaveData();
        NbtList list = nbt.getList("owners", NbtElement.STRING_TYPE);
        for (int i = 0; i < list.size(); i++) {
            try {
                data.owners.add(UUID.fromString(list.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (UUID id : owners) {
            list.add(NbtString.of(id.toString()));
        }
        nbt.put("owners", list);
        return nbt;
    }

    public boolean has(UUID id) {
        return owners.contains(id);
    }

    public void add(UUID id) {
        if (owners.add(id)) {
            markDirty();
        }
    }

    public void remove(UUID id) {
        if (owners.remove(id)) {
            markDirty();
        }
    }
}
