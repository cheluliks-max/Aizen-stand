package com.aizenstand;

import com.aizenstand.net.StateSyncPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * All server-side logic of the stand: ownership, summoning (G), reiatsu, the passive reiatsu pressure,
 * Hado #90 Kurohitsugi and Hado #99 Goryutenmetsu.
 * <p>
 * Every number you may want to tweak is in the "BALANCE" block below.
 */
public final class StandManager {
    // ===================================================================== BALANCE
    public static final int MAX_REIATSU = 100;
    static final float REGEN_IDLE = 0.20F;          // reiatsu per tick while the stand is dismissed
    static final float REGEN_SUMMONED = 0.08F;      // reiatsu per tick while the stand is out

    static final double PRESSURE_RADIUS = 12.0;     // blocks

    static final float COST_HADO_90 = 35F;
    static final int COOLDOWN_HADO_90 = 160;        // ticks (20 ticks = 1 second)
    static final int CHANT_HADO_90 = 40;
    static final double RANGE_HADO_90 = 40.0;
    static final int COFFIN_TICKS = 80;
    static final double COFFIN_RADIUS = 2.4;
    static final double COFFIN_HEIGHT = 7.0;
    static final float COFFIN_PULSE_DAMAGE = 2.5F;  // every 10 ticks
    static final float COFFIN_FINAL_DAMAGE = 6.0F;

    static final float COST_HADO_99 = 80F;
    static final int COOLDOWN_HADO_99 = 500;
    static final int CHANT_HADO_99 = 70;
    static final double DRAGON_RANGE = 45.0;
    static final double DRAGON_SPEED = 2.2;         // blocks per tick
    static final double DRAGON_HIT_RADIUS = 2.6;
    static final float DRAGON_DAMAGE = 20F;
    static final float DRAGON_EXPLOSION_POWER = 4.0F;
    static final boolean EXPLOSION_BREAKS_BLOCKS = false;   // set to true if you want craters
    // =====================================================================

    private static final DustParticleEffect BLACK = new DustParticleEffect(new Vector3f(0.03F, 0.0F, 0.06F), 2.2F);
    private static final DustParticleEffect VIOLET = new DustParticleEffect(new Vector3f(0.62F, 0.30F, 1.0F), 1.6F);
    private static final DustParticleEffect[] DRAGON_COLORS = {
            new DustParticleEffect(new Vector3f(1.0F, 0.25F, 0.15F), 2.0F),
            new DustParticleEffect(new Vector3f(0.25F, 0.55F, 1.0F), 2.0F),
            new DustParticleEffect(new Vector3f(0.35F, 1.0F, 0.35F), 2.0F),
            new DustParticleEffect(new Vector3f(1.0F, 0.92F, 0.25F), 2.0F),
            new DustParticleEffect(new Vector3f(0.85F, 0.45F, 1.0F), 2.0F)
    };

    private static final Map<UUID, PlayerData> DATA = new HashMap<>();
    private static final List<Coffin> COFFINS = new ArrayList<>();
    private static final List<Dragons> DRAGONS = new ArrayList<>();

    private StandManager() {
    }

    private static final class PlayerData {
        boolean hasStand;
        boolean summoned;
        float reiatsu = MAX_REIATSU;
        int cd90;
        int cd99;
        int castType;       // 0 = nothing, 1 = hado 90, 2 = hado 99
        int castTimer;
        UUID standId;
        boolean dirty = true;
        int sentReiatsu = -1, sentCd90 = -1, sentCd99 = -1;
        boolean sentHas, sentSummoned;
    }

    // ===================================================================== public API
    public static boolean hasStand(ServerPlayerEntity player) {
        return data(player).hasStand;
    }

    public static boolean isSummoned(UUID id) {
        PlayerData d = DATA.get(id);
        return d != null && d.summoned;
    }

    private static PlayerData data(ServerPlayerEntity p) {
        return DATA.computeIfAbsent(p.getUuid(), id -> {
            PlayerData d = new PlayerData();
            MinecraftServer server = p.getServer();
            d.hasStand = server != null && StandSaveData.get(server).has(id);
            return d;
        });
    }

    public static void onJoin(ServerPlayerEntity p) {
        DATA.remove(p.getUuid());
        data(p).dirty = true;
    }

    public static void onDisconnect(ServerPlayerEntity p) {
        PlayerData d = DATA.remove(p.getUuid());
        if (d != null) {
            discardStand(p, d);
        }
    }

    public static void onServerStopped() {
        DATA.clear();
        COFFINS.clear();
        DRAGONS.clear();
    }

    /** Called when the Stand Arrow is used. */
    public static void awaken(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        PlayerData d = data(p);
        d.hasStand = true;
        d.reiatsu = MAX_REIATSU;
        d.dirty = true;
        StandSaveData.get(w.getServer()).add(p.getUuid());

        if (p.getHealth() > 4.0F) {
            p.damage(w.getDamageSources().magic(), 2.0F);   // the arrow pierces you
        }
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0));
        sound(w, p.getPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0F, 0.6F);
        sound(w, p.getPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, 1.0F, 0.5F);
        w.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 50, 0.4, 0.8, 0.4, 0.15);
        w.spawnParticles(VIOLET, p.getX(), p.getY() + 1.0, p.getZ(), 40, 0.6, 0.9, 0.6, 0.0);
        p.sendMessage(Text.translatable("message.aizen_stand.awakened"), false);
        p.sendMessage(Text.translatable("message.aizen_stand.awakened_hint"), true);
    }

    /** Handles a key press sent by the client (G / hado keys). */
    public static void handleAction(ServerPlayerEntity p, int action) {
        PlayerData d = data(p);
        switch (action) {
            case 0 -> toggleSummon(p, d);
            case 1 -> startCast(p, d, 1);
            case 2 -> startCast(p, d, 2);
            default -> {
            }
        }
    }

    // ===================================================================== summon / dismiss
    private static void toggleSummon(ServerPlayerEntity p, PlayerData d) {
        if (!d.hasStand) {
            p.sendMessage(Text.translatable("message.aizen_stand.no_stand"), true);
            return;
        }
        if (d.summoned) {
            dismiss(p, d);
        } else if (p.isAlive() && !p.isSpectator()) {
            d.summoned = true;
            spawnStand(p, d);
            ServerWorld w = p.getServerWorld();
            sound(w, p.getPos(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.0F, 0.7F);
            sound(w, p.getPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 0.8F, 0.8F);
            w.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 1.0, 0.5, 0.1);
            p.sendMessage(Text.translatable("message.aizen_stand.summoned"), true);
        }
        d.dirty = true;
    }

    private static void dismiss(ServerPlayerEntity p, PlayerData d) {
        cancelCast(p, d);
        d.summoned = false;
        discardStand(p, d);
        ServerWorld w = p.getServerWorld();
        sound(w, p.getPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.8F, 1.0F);
        w.spawnParticles(ParticleTypes.POOF, p.getX(), p.getY() + 1.0, p.getZ(), 15, 0.4, 0.8, 0.4, 0.02);
        p.sendMessage(Text.translatable("message.aizen_stand.dismissed"), true);
    }

    private static void spawnStand(ServerPlayerEntity p, PlayerData d) {
        ServerWorld w = p.getServerWorld();
        AizenStandEntity stand = new AizenStandEntity(AizenStandMod.AIZEN_STAND, w);
        stand.setOwner(p.getUuid());
        stand.setPosition(p.getX(), p.getY(), p.getZ());
        stand.setYaw(p.getHeadYaw());
        w.spawnEntity(stand);
        d.standId = stand.getUuid();
    }

    private static void discardStand(ServerPlayerEntity p, PlayerData d) {
        if (d.standId != null && p.getServer() != null) {
            for (ServerWorld world : p.getServer().getWorlds()) {
                Entity e = world.getEntity(d.standId);
                if (e != null) {
                    e.discard();
                }
            }
        }
        d.standId = null;
    }

    private static AizenStandEntity findStand(ServerPlayerEntity p, PlayerData d) {
        if (d.standId == null) {
            return null;
        }
        Entity e = p.getServerWorld().getEntity(d.standId);
        return e instanceof AizenStandEntity s && !s.isRemoved() ? s : null;
    }

    // ===================================================================== main tick
    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            tickPlayer(server, p);
        }
        tickCoffins();
        tickDragons();
    }

    private static void tickPlayer(MinecraftServer server, ServerPlayerEntity p) {
        PlayerData d = data(p);
        if (d.cd90 > 0) d.cd90--;
        if (d.cd99 > 0) d.cd99--;
        d.reiatsu = Math.min(MAX_REIATSU, d.reiatsu + (d.summoned ? REGEN_SUMMONED : REGEN_IDLE));

        if (d.summoned) {
            if (!p.isAlive() || p.isSpectator()) {
                cancelCast(p, d);
                d.summoned = false;
                discardStand(p, d);
            } else {
                AizenStandEntity stand = findStand(p, d);
                if (stand == null) {
                    spawnStand(p, d);       // first time in a new dimension, or after /kill
                }
                if (server.getTicks() % 10 == 0) {
                    applyPressure(p);
                }
                tickCast(p, d, server.getTicks());
            }
        }
        sync(p, d);
    }

    private static void sync(ServerPlayerEntity p, PlayerData d) {
        int r = (int) d.reiatsu;
        int c90 = (d.cd90 + 4) / 5 * 5;
        int c99 = (d.cd99 + 4) / 5 * 5;
        if (!d.dirty && r == d.sentReiatsu && c90 == d.sentCd90 && c99 == d.sentCd99
                && d.hasStand == d.sentHas && d.summoned == d.sentSummoned) {
            return;
        }
        d.dirty = false;
        d.sentReiatsu = r;
        d.sentCd90 = c90;
        d.sentCd99 = c99;
        d.sentHas = d.hasStand;
        d.sentSummoned = d.summoned;
        ServerPlayNetworking.send(p, new StateSyncPayload(d.hasStand, d.summoned, r, c90, c99));
    }

    // ===================================================================== reiatsu pressure
    private static boolean canTarget(ServerPlayerEntity owner, LivingEntity e) {
        if (e == owner || !e.isAlive() || e.isSpectator() || e instanceof ArmorStandEntity) {
            return false;
        }
        if (e instanceof ServerPlayerEntity sp && sp.isCreative()) {
            return false;
        }
        return !(e instanceof TameableEntity t && owner.getUuid().equals(t.getOwnerUuid()));
    }

    /** Everything near the owner is crushed by overwhelming spiritual pressure. */
    private static void applyPressure(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Box area = p.getBoundingBox().expand(PRESSURE_RADIUS);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, area, x -> canTarget(p, x))) {
            double dist = e.distanceTo(p);
            if (dist > PRESSURE_RADIUS) {
                continue;
            }
            int amp = dist < 4.0 ? 2 : dist < 8.0 ? 1 : 0;
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, amp, false, false, true));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 25, dist < 6.0 ? 1 : 0, false, false, true));
            if (dist < 5.0) {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 25, 1, false, false, true));
            }
            if (dist < 8.0 && !e.isOnGround()) {
                e.addVelocity(0.0, -0.12, 0.0);         // knees buckle, you get pushed down
                e.velocityModified = true;
            }
        }
        // visual: a pulsing violet ring on the ground
        double radius = 1.5 + (p.getServer().getTicks() % 40) / 40.0 * (PRESSURE_RADIUS - 1.5);
        for (int i = 0; i < 14; i++) {
            double a = Math.PI * 2 * i / 14 + p.getServer().getTicks() * 0.05;
            w.spawnParticles(VIOLET, p.getX() + Math.cos(a) * radius, p.getY() + 0.15, p.getZ() + Math.sin(a) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        w.spawnParticles(VIOLET, p.getX(), p.getY() + 1.0, p.getZ(), 6, 0.5, 0.9, 0.5, 0.0);
    }

    // ===================================================================== casting
    private static void startCast(ServerPlayerEntity p, PlayerData d, int type) {
        if (!d.hasStand) {
            p.sendMessage(Text.translatable("message.aizen_stand.no_stand"), true);
            return;
        }
        if (!d.summoned) {
            p.sendMessage(Text.translatable("message.aizen_stand.need_summon"), true);
            return;
        }
        if (d.castType != 0) {
            return;
        }
        int cd = type == 1 ? d.cd90 : d.cd99;
        float cost = type == 1 ? COST_HADO_90 : COST_HADO_99;
        if (cd > 0) {
            p.sendMessage(Text.translatable("message.aizen_stand.cooldown", (cd + 19) / 20), true);
            return;
        }
        if (d.reiatsu < cost) {
            p.sendMessage(Text.translatable("message.aizen_stand.no_reiatsu", (int) cost), true);
            return;
        }
        d.castType = type;
        d.castTimer = type == 1 ? CHANT_HADO_90 : CHANT_HADO_99;
        AizenStandEntity stand = findStand(p, d);
        if (stand != null) {
            stand.setCasting(true);
        }
        broadcast(p, Text.translatable(type == 1 ? "chant.aizen_stand.hado90.1" : "chant.aizen_stand.hado99.1"));
        sound(p.getServerWorld(), p.getPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.2F, 0.6F);
    }

    private static void cancelCast(ServerPlayerEntity p, PlayerData d) {
        d.castType = 0;
        d.castTimer = 0;
        AizenStandEntity stand = findStand(p, d);
        if (stand != null) {
            stand.setCasting(false);
        }
    }

    private static void tickCast(ServerPlayerEntity p, PlayerData d, int serverTick) {
        if (d.castType == 0) {
            return;
        }
        AizenStandEntity stand = findStand(p, d);
        if (stand == null) {
            cancelCast(p, d);
            return;
        }
        ServerWorld w = p.getServerWorld();
        int type = d.castType;
        int total = type == 1 ? CHANT_HADO_90 : CHANT_HADO_99;
        d.castTimer--;

        // charging effect: energy spirals into the raised hand
        Vec3d hand = handPos(p);
        double prog = 1.0 - (double) d.castTimer / total;
        double radius = 1.6 * (1.0 - prog) + 0.15;
        for (int i = 0; i < 3; i++) {
            double a = serverTick * 0.55 + i * Math.PI * 2 / 3;
            w.spawnParticles(type == 1 ? BLACK : VIOLET,
                    hand.x + Math.cos(a) * radius, hand.y + Math.sin(a * 0.7) * radius * 0.6, hand.z + Math.sin(a) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        w.spawnParticles(ParticleTypes.END_ROD, hand.x, hand.y, hand.z, 1, 0.1, 0.1, 0.1, 0.01);

        if (d.castTimer == total / 2) {
            broadcast(p, Text.translatable(type == 1 ? "chant.aizen_stand.hado90.2" : "chant.aizen_stand.hado99.2"));
        }
        if (d.castTimer <= 0) {
            d.dirty = true;
            if (type == 1) {
                d.reiatsu -= COST_HADO_90;
                d.cd90 = COOLDOWN_HADO_90;
                broadcast(p, Text.translatable("chant.aizen_stand.hado90.3"));
                fireKurohitsugi(p);
            } else {
                d.reiatsu -= COST_HADO_99;
                d.cd99 = COOLDOWN_HADO_99;
                broadcast(p, Text.translatable("chant.aizen_stand.hado99.3"));
                fireGoryutenmetsu(p);
            }
            d.castType = 0;
            stand.setCasting(false);
        }
    }

    private static Vec3d handPos(ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0F);
        return p.getEyePos().add(look.multiply(1.3)).add(0.0, -0.35, 0.0);
    }

    // ===================================================================== Hado #90 Kurohitsugi
    private static final class Coffin {
        final ServerWorld world;
        final Vec3d center;
        final UUID owner;
        final List<UUID> victims = new ArrayList<>();
        int age;

        Coffin(ServerWorld world, Vec3d center, UUID owner) {
            this.world = world;
            this.center = center;
            this.owner = owner;
        }
    }

    private static void fireKurohitsugi(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(p.getRotationVec(1.0F).multiply(RANGE_HADO_90));

        BlockHitResult blockHit = w.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        boolean blockMiss = blockHit.getType() == HitResult.Type.MISS;
        Vec3d limit = blockMiss ? end : blockHit.getPos();

        LivingEntity best = null;
        double bestDist = eye.squaredDistanceTo(limit);
        Box search = new Box(eye, limit).expand(1.5);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, search, x -> canTarget(p, x))) {
            Optional<Vec3d> hit = e.getBoundingBox().expand(0.5).raycast(eye, limit);
            if (hit.isPresent()) {
                double dd = eye.squaredDistanceTo(hit.get());
                if (dd < bestDist) {
                    bestDist = dd;
                    best = e;
                }
            }
        }

        Vec3d center;
        if (best != null) {
            center = new Vec3d(best.getX(), best.getY(), best.getZ());
        } else if (blockMiss) {
            // nothing in range: drop the coffin onto the ground below the end of the ray
            BlockHitResult down = w.raycast(new RaycastContext(end, end.add(0.0, -40.0, 0.0),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            center = down.getType() == HitResult.Type.MISS ? end : down.getPos();
        } else {
            center = blockHit.getPos();
        }

        Coffin coffin = new Coffin(w, center, p.getUuid());
        double r = COFFIN_RADIUS + 0.8;
        Box zone = new Box(center.x - r, center.y - 0.5, center.z - r, center.x + r, center.y + COFFIN_HEIGHT, center.z + r);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, zone, x -> canTarget(p, x))) {
            coffin.victims.add(e.getUuid());
        }
        COFFINS.add(coffin);

        // dark beam from the hand to the coffin
        Vec3d from = handPos(p);
        Vec3d delta = center.add(0.0, 1.0, 0.0).subtract(from);
        int steps = Math.max(1, (int) (delta.length() / 0.6));
        for (int i = 0; i <= steps; i++) {
            Vec3d pt = from.add(delta.multiply((double) i / steps));
            w.spawnParticles(BLACK, pt.x, pt.y, pt.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        sound(w, p.getPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.0F, 1.3F);
        sound(w, center, SoundEvents.BLOCK_ANVIL_LAND, 1.5F, 0.5F);
    }

    private static DamageSource magicSource(ServerWorld w, UUID ownerId) {
        Entity owner = ownerId == null ? null : w.getPlayerByUuid(ownerId);
        return owner != null ? w.getDamageSources().indirectMagic(owner, owner) : w.getDamageSources().magic();
    }

    private static void tickCoffins() {
        Iterator<Coffin> it = COFFINS.iterator();
        while (it.hasNext()) {
            Coffin c = it.next();
            ServerWorld w = c.world;
            c.age++;
            double grow = Math.min(1.0, c.age / 8.0);
            double h = COFFIN_HEIGHT * grow;
            double r = COFFIN_RADIUS;
            Vec3d o = c.center;

            // the black box: four dark pillars, a cloud inside, and lids
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    w.spawnParticles(BLACK, o.x + sx * r, o.y + h / 2, o.z + sz * r, 7, 0.06, h / 2 * 0.9, 0.06, 0.0);
                }
            }
            w.spawnParticles(BLACK, o.x, o.y + h / 2, o.z, 22, r * 0.6, h / 2 * 0.8, r * 0.6, 0.0);
            if (c.age % 3 == 0) {
                w.spawnParticles(BLACK, o.x, o.y + 0.1, o.z, 12, r * 0.8, 0.05, r * 0.8, 0.0);
                w.spawnParticles(BLACK, o.x, o.y + h, o.z, 12, r * 0.8, 0.05, r * 0.8, 0.0);
                w.spawnParticles(VIOLET, o.x, o.y + h / 2, o.z, 5, r * 0.7, h / 2 * 0.7, r * 0.7, 0.0);
            }

            DamageSource src = magicSource(w, c.owner);
            boolean pulse = c.age % 10 == 0 && c.age < COFFIN_TICKS;
            Iterator<UUID> vit = c.victims.iterator();
            while (vit.hasNext()) {
                Entity e = w.getEntity(vit.next());
                if (!(e instanceof LivingEntity victim) || !victim.isAlive()) {
                    vit.remove();
                    continue;
                }
                // hold the victim inside the coffin
                double dx = o.x - victim.getX(), dz = o.z - victim.getZ();
                double horiz = Math.hypot(dx, dz);
                if (horiz > 0.8) {
                    victim.setVelocity(dx / horiz * 0.35, 0.0, dz / horiz * 0.35);
                } else {
                    victim.setVelocity(0.0, 0.0, 0.0);
                }
                victim.velocityModified = true;
                if (c.age % 5 == 0) {
                    victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 12, 6, false, false, true));
                    victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 12, 1, false, false, true));
                }
                if (pulse) {
                    victim.damage(src, COFFIN_PULSE_DAMAGE);
                    w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, victim.getX(), victim.getBodyY(0.5), victim.getZ(), 4, 0.3, 0.3, 0.3, 0.1);
                }
            }

            if (c.age >= COFFIN_TICKS) {
                // the coffin shatters
                for (UUID id : c.victims) {
                    Entity e = w.getEntity(id);
                    if (e instanceof LivingEntity victim && victim.isAlive()) {
                        victim.damage(src, COFFIN_FINAL_DAMAGE);
                    }
                }
                w.spawnParticles(ParticleTypes.LARGE_SMOKE, o.x, o.y + h / 2, o.z, 60, r * 0.7, h / 2 * 0.8, r * 0.7, 0.08);
                w.spawnParticles(ParticleTypes.EXPLOSION, o.x, o.y + h / 2, o.z, 6, r * 0.6, h / 3, r * 0.6, 0.0);
                sound(w, o, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5F, 0.7F);
                it.remove();
            }
        }
    }

    // ===================================================================== Hado #99 Goryutenmetsu
    private static final class Dragons {
        final ServerWorld world;
        final UUID owner;
        final Vec3d origin, dir, u, v;
        final Set<UUID> hit = new HashSet<>();
        double dist;
        int age;

        Dragons(ServerWorld world, UUID owner, Vec3d origin, Vec3d dir) {
            this.world = world;
            this.owner = owner;
            this.origin = origin;
            this.dir = dir;
            Vec3d up = Math.abs(dir.y) > 0.98 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
            this.u = dir.crossProduct(up).normalize();
            this.v = dir.crossProduct(this.u).normalize();
        }
    }

    private static void fireGoryutenmetsu(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Vec3d dir = p.getRotationVec(1.0F).normalize();
        DRAGONS.add(new Dragons(w, p.getUuid(), handPos(p), dir));
        sound(w, p.getPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 2.0F, 0.8F);
        sound(w, p.getPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.5F, 0.7F);
    }

    private static void tickDragons() {
        Iterator<Dragons> it = DRAGONS.iterator();
        while (it.hasNext()) {
            Dragons g = it.next();
            ServerWorld w = g.world;
            g.age++;
            double prev = g.dist;
            g.dist += DRAGON_SPEED;
            Vec3d a = g.origin.add(g.dir.multiply(prev));
            Vec3d b = g.origin.add(g.dir.multiply(g.dist));
            ServerPlayerEntity owner = (ServerPlayerEntity) w.getPlayerByUuid(g.owner);

            // five dragons spiral around the axis
            for (int k = 0; k < 5; k++) {
                double theta0 = Math.PI * 2 * k / 5 + g.age * 0.6;
                for (int s = 0; s < 6; s++) {
                    double ds = g.dist - s * 0.9;
                    if (ds < 0) {
                        break;
                    }
                    double theta = theta0 - s * 0.45;
                    double rad = 1.2 + 0.3 * Math.sin(g.age * 0.5 + k);
                    Vec3d c = g.origin.add(g.dir.multiply(ds))
                            .add(g.u.multiply(Math.cos(theta) * rad))
                            .add(g.v.multiply(Math.sin(theta) * rad));
                    w.spawnParticles(DRAGON_COLORS[k], c.x, c.y, c.z, s == 0 ? 3 : 1, 0.08, 0.08, 0.08, 0.0);
                    if (s == 0) {
                        w.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0.05, 0.05, 0.05, 0.02);
                    }
                }
            }

            // damage everything the dragons pass through
            DamageSource src = magicSource(w, g.owner);
            Vec3d ab = b.subtract(a);
            double abLen2 = Math.max(ab.lengthSquared(), 1.0E-6);
            Box zone = new Box(a, b).expand(DRAGON_HIT_RADIUS);
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, zone,
                    x -> x != owner && x.isAlive() && !(x instanceof ArmorStandEntity) && !x.isSpectator()
                            && !(x instanceof ServerPlayerEntity sp && sp.isCreative())
                            && !(x instanceof TameableEntity t && g.owner.equals(t.getOwnerUuid())))) {
                if (g.hit.contains(e.getUuid())) {
                    continue;
                }
                Vec3d center = e.getPos().add(0.0, e.getHeight() / 2.0, 0.0);
                double t = MathHelper.clamp(center.subtract(a).dotProduct(ab) / abLen2, 0.0, 1.0);
                Vec3d closest = a.add(ab.multiply(t));
                if (closest.squaredDistanceTo(center) <= DRAGON_HIT_RADIUS * DRAGON_HIT_RADIUS) {
                    g.hit.add(e.getUuid());
                    e.damage(src, DRAGON_DAMAGE);
                    e.setOnFireFor(4);
                    e.addVelocity(g.dir.x * 1.0, 0.35, g.dir.z * 1.0);
                    e.velocityModified = true;
                }
            }

            // stop at a wall or at maximum range -> explode
            BlockHitResult wall = w.raycast(new RaycastContext(a, b,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, ShapeContext.absent()));
            boolean blocked = wall.getType() != HitResult.Type.MISS;
            if (blocked || g.dist >= DRAGON_RANGE) {
                Vec3d at = blocked ? wall.getPos() : b;
                World.ExplosionSourceType type = EXPLOSION_BREAKS_BLOCKS
                        ? World.ExplosionSourceType.BLOCK : World.ExplosionSourceType.NONE;
                w.createExplosion(owner, at.x, at.y, at.z, DRAGON_EXPLOSION_POWER, type);
                for (int k = 0; k < 5; k++) {
                    w.spawnParticles(DRAGON_COLORS[k], at.x, at.y, at.z, 25, 1.6, 1.6, 1.6, 0.0);
                }
                it.remove();
            }
        }
    }

    // ===================================================================== helpers
    private static void broadcast(ServerPlayerEntity p, Text text) {
        for (ServerPlayerEntity other : p.getServerWorld().getPlayers(x -> x.squaredDistanceTo(p) < 24.0 * 24.0)) {
            other.sendMessage(text, true);
        }
    }

    private static void sound(ServerWorld w, Vec3d pos, SoundEvent event, float volume, float pitch) {
        w.playSound(null, pos.x, pos.y, pos.z, event, SoundCategory.PLAYERS, volume, pitch);
    }

    // some vanilla sounds are exposed as registry entries in 1.21.x, so accept both kinds
    private static void sound(ServerWorld w, Vec3d pos, RegistryEntry<SoundEvent> event, float volume, float pitch) {
        w.playSound(null, pos.x, pos.y, pos.z, event, SoundCategory.PLAYERS, volume, pitch);
    }
}
