package com.mooshgf.tornado.entity;

import com.mooshgf.tornado.ModEntities;
import com.mooshgf.tornado.TornadoClass;
import com.mooshgf.tornado.TornadoConfig;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * A supercell thunderstorm: a rotating cloud deck of smoke particles with a lowered wall cloud, rain,
 * lightning and (at the tornadic stage) a tornado that is born under the mesocyclone.
 * Stages: 0 developing, 1 mature, 2 tornadic, 3 dissipating.
 */
public class SupercellEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(SupercellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CLASS = SynchedEntityData.defineId(SupercellEntity.class, EntityDataSerializers.INT);

    public static final int DEVELOP_TICKS = 1200;
    public static final int MATURE_TICKS = 2400;
    public static final int TORNADIC_TICKS = 6000;
    public static final int DISSIPATE_TICKS = 900;
    private static final double CLOUD_HEIGHT = 90.0D;
    private static final double CLOUD_RADIUS = 85.0D;

    private int age;
    private float heading;
    private UUID tornado;
    private boolean warned;

    public SupercellEntity(EntityType<? extends SupercellEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.heading = this.random.nextFloat() * Mth.TWO_PI;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STAGE, 0);
        this.entityData.define(DATA_CLASS, TornadoClass.EF3.ordinal());
    }

    public int getStage() { return this.entityData.get(DATA_STAGE); }

    public TornadoClass getTornadoClass() { return TornadoClass.byOrdinal(this.entityData.get(DATA_CLASS)); }

    public void setTornadoClass(TornadoClass c) { this.entityData.set(DATA_CLASS, c.ordinal()); }

    /** Jump to a stage (0-3) by fast-forwarding the age. */
    public void setStage(int stage) {
        switch (Mth.clamp(stage, 0, 3)) {
            case 0 -> this.age = 0;
            case 1 -> this.age = DEVELOP_TICKS;
            case 2 -> this.age = DEVELOP_TICKS + MATURE_TICKS;
            default -> this.age = DEVELOP_TICKS + MATURE_TICKS + TORNADIC_TICKS;
        }
        this.entityData.set(DATA_STAGE, Mth.clamp(stage, 0, 3));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) { }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) { }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) { return true; }

    private static int stageForAge(int age) {
        if (age < DEVELOP_TICKS) return 0;
        if (age < DEVELOP_TICKS + MATURE_TICKS) return 1;
        if (age < DEVELOP_TICKS + MATURE_TICKS + TORNADIC_TICKS) return 2;
        return 3;
    }

    @Override
    public void tick() {
        super.tick();
        this.age++;
        if (this.level().isClientSide) clientTick();
        else serverTick((ServerLevel) this.level());
    }

    /** Where the wall cloud (and tornado) sits, relative to the storm centre. */
    private Vec3 wallCloudPos() {
        return new Vec3(getX() + Math.cos(this.heading) * 18.0D, getY(), getZ() + Math.sin(this.heading) * 18.0D);
    }

    private void serverTick(ServerLevel sl) {
        int stage = stageForAge(this.age);
        if (stage != getStage()) this.entityData.set(DATA_STAGE, stage);

        // drift
        if (this.tickCount % 60 == 0) this.heading += (this.random.nextFloat() - 0.5F) * 0.25F;
        double speed = 0.16D;
        double nx = getX() + Math.cos(this.heading) * speed, nz = getZ() + Math.sin(this.heading) * speed;
        if (sl.hasChunkAt(BlockPos.containing(nx, getY(), nz))) {
            double ground = sl.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(nx), Mth.floor(nz));
            this.setPos(nx, getY() + (ground - getY()) * 0.05D, nz);
        }

        if (stage >= 1 && this.tickCount % 400 == 0) sl.setWeatherParameters(0, 12000, true, true);
        if (stage >= 1 && this.random.nextInt(stage == 2 ? 50 : 90) == 0) strikeLightning(sl);

        if (stage == 2) {
            if (!this.warned && TornadoConfig.warnings) {
                this.warned = true;
                for (ServerPlayer p : sl.players()) {
                    if (p.distanceToSqr(this) < 500.0D * 500.0D) {
                        p.displayClientMessage(Component.literal("§c⚠ TORNADO WARNING §f(" + getTornadoClass().label + ") — take shelter!"), true);
                    }
                }
            }
            TornadoEntity t = this.tornado == null ? null : (sl.getEntity(this.tornado) instanceof TornadoEntity te ? te : null);
            if (t == null && this.tornado == null) {
                t = new TornadoEntity(ModEntities.TORNADO.get(), sl);
                t.setTornadoClass(getTornadoClass());
                Vec3 w = wallCloudPos();
                t.setPos(w.x, sl.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(w.x), Mth.floor(w.z)), w.z);
                sl.addFreshEntity(t);
                this.tornado = t.getUUID();
            }
            if (t != null && t.isAlive()) t.steerTo(wallCloudPos());
        } else if (stage == 3 && this.tornado != null) {
            Entity e = sl.getEntity(this.tornado);
            if (e instanceof TornadoEntity t) t.beginRopeOut();
            this.tornado = null;
        }
        if (stage == 3 && this.age > DEVELOP_TICKS + MATURE_TICKS + TORNADIC_TICKS + DISSIPATE_TICKS) this.discard();
    }

    private void strikeLightning(ServerLevel sl) {
        double a = this.random.nextDouble() * Mth.TWO_PI, d = this.random.nextDouble() * 60.0D;
        int x = Mth.floor(getX() + Math.cos(a) * d), z = Mth.floor(getZ() + Math.sin(a) * d);
        if (!sl.hasChunkAt(new BlockPos(x, 64, z))) return;
        LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(new BlockPos(x, sl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z)));
            sl.addFreshEntity(bolt);
        }
    }

    // ---------------------------------------------------------------- client visuals (vanilla smoke)
    private void clientTick() {
        Level lv = this.level();
        int stage = getStage();
        float fade = 1.0F;
        if (stage == 0) fade = 0.35F + 0.65F * Math.min(1.0F, this.tickCount / (float) DEVELOP_TICKS);
        if (stage == 3) fade = Math.max(0.1F, 1.0F - (this.age - (DEVELOP_TICKS + MATURE_TICKS + TORNADIC_TICKS)) / (float) DISSIPATE_TICKS);
        int n = (int) ((50 + stage * 18) * fade * TornadoConfig.particleScale);
        double spin = this.tickCount * 0.012D;
        double baseY = getY() + CLOUD_HEIGHT;
        for (int i = 0; i < n; i++) {
            double rr = Math.sqrt(this.random.nextDouble()) * CLOUD_RADIUS;
            double ang = this.random.nextDouble() * Mth.TWO_PI;
            double swirl = spin * (1.4D - rr / CLOUD_RADIUS) + rr * 0.015D;
            double px = getX() + Math.cos(ang + swirl) * rr, pz = getZ() + Math.sin(ang + swirl) * rr;
            // anvil-shaped, thicker towards the middle, lowered base near the updraft
            double thick = 22.0D * (1.0D - rr / CLOUD_RADIUS) + 4.0D;
            double py = baseY + this.random.nextDouble() * thick - (rr < 25.0D && stage >= 1 ? (25.0D - rr) * 0.5D : 0.0D);
            double tv = 0.12D;
            double vx = -Math.sin(ang + swirl) * tv, vz = Math.cos(ang + swirl) * tv;
            if (this.random.nextInt(4) == 0) lv.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, px, py, pz, vx, 0.0D, vz);
            else lv.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, px, py, pz, vx, 0.0D, vz);
        }
        // wall cloud + rotating funnel precursor beneath the mesocyclone
        if (stage >= 1 && stage <= 2) {
            Vec3 w = wallCloudPos();
            int m = 6 + stage * 4;
            for (int i = 0; i < m; i++) {
                double h = this.random.nextDouble();
                double r = 6.0D + 8.0D * (1.0D - h);
                double ang = this.random.nextDouble() * Mth.TWO_PI + this.tickCount * 0.2D;
                lv.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, w.x + Math.cos(ang) * r, getY() + CLOUD_HEIGHT - 30.0D * h, w.z + Math.sin(ang) * r,
                        -Math.sin(ang) * 0.2D, -0.02D, Math.cos(ang) * 0.2D);
            }
        }
    }
}
