package com.aizenstand;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * The stand itself: a non-living entity that hovers behind the owner's right shoulder.
 * Movement is done on the server; the client only plays the casting pose animation.
 */
public class AizenStandEntity extends Entity {
    private static final TrackedData<Boolean> CASTING =
            DataTracker.registerData(AizenStandEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private UUID ownerUuid;
    private float castAnim;

    public AizenStandEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public void setOwner(UUID owner) {
        this.ownerUuid = owner;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setCasting(boolean casting) {
        this.dataTracker.set(CASTING, casting);
    }

    public boolean isCasting() {
        return this.dataTracker.get(CASTING);
    }

    /** 0 = idle pose, 1 = arm raised for a hado. */
    public float getCastAnim() {
        return castAnim;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(CASTING, false);
    }

    @Override
    public void tick() {
        super.tick();
        castAnim = MathHelper.clamp(castAnim + (isCasting() ? 0.15F : -0.15F), 0.0F, 1.0F);

        World world = getWorld();
        if (world.isClient || !(world instanceof ServerWorld serverWorld)) {
            return;
        }
        PlayerEntity owner = ownerUuid == null ? null : serverWorld.getPlayerByUuid(ownerUuid);
        if (owner == null || !owner.isAlive() || owner.getWorld() != world || !StandManager.isSummoned(ownerUuid)) {
            this.discard();
            return;
        }
        // hover behind the owner's right shoulder
        double yaw = Math.toRadians(owner.getBodyYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);        // forward
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);       // right
        double x = owner.getX() - fx * 1.15 + rx * 0.75;
        double z = owner.getZ() - fz * 1.15 + rz * 0.75;
        double y = owner.getY() + 0.15;
        this.setPosition(x, y, z);
        this.setYaw(owner.getHeadYaw());
        this.setPitch(0.0F);
    }

    /** Wings and halo stick out of the normal hitbox, so widen the box used for culling. */
    @Override
    public Box getVisibilityBoundingBox() {
        return this.getBoundingBox().expand(2.0, 0.5, 2.0);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
