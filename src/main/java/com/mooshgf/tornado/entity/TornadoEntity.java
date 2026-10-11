package com.mooshgf.tornado.entity;

import com.mooshgf.tornado.TornadoClass;
import com.mooshgf.tornado.TornadoConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.network.NetworkHooks;

/**
 * A tornado. Server side: moves, lifts blocks (as falling-block "debris" up to its capacity), flings entities.
 * Client side: draws the whole funnel using vanilla smoke particles.
 */
public class TornadoEntity extends Entity {
    public static final String DEBRIS_TAG = "mooshgf_debris";
    private static final EntityDataAccessor<Integer> DATA_CLASS = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_FADE = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);

    private final List<FallingBlockEntity> debris = new ArrayList<>();
    private float heading;
    private int ropeTicks = -1;
    private int age;
    private Vec3 target;
    private double gx, gz;

    public TornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.heading = this.random.nextFloat() * Mth.TWO_PI;
    }

    // ---------------------------------------------------------------- data
    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_CLASS, TornadoClass.EF2.ordinal());
        this.entityData.define(DATA_FADE, 1.0F);
    }

    public TornadoClass getTornadoClass() {
        return TornadoClass.byOrdinal(this.entityData.get(DATA_CLASS));
    }

    public void setTornadoClass(TornadoClass c) {
        this.entityData.set(DATA_CLASS, c.ordinal());
    }

    public float getFade() {
        return this.entityData.get(DATA_FADE);
    }

    /** Steer the tornado towards a point (used by supercells). */
    public void steerTo(Vec3 point) {
        this.target = point;
    }

    public void beginRopeOut() {
        if (this.ropeTicks < 0) this.ropeTicks = 0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("Class")) this.entityData.set(DATA_CLASS, tag.getInt("Class"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Class", this.entityData.get(DATA_CLASS));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return true;
    }

    // ---------------------------------------------------------------- geometry
    /** Growth multiplier: grows in over 6 s and shrinks while roping out. */
    private float scale() {
        float grow = Math.min(1.0F, this.tickCount / 120.0F);
        return grow * getFade();
    }

    /** Funnel radius at height h above the ground. */
    public double radiusAt(double h, TornadoClass c, float scale) {
        double t = Mth.clamp(h / (c.height * scale), 0.0D, 1.0D);
        return c.radius * scale * (c.baseRatio + (1.0D - c.baseRatio) * Math.pow(t, 1.5D));
    }

    /** Horizontal centre of the funnel at height h (leans backwards and wobbles). */
    private Vec3 centerAt(double h, TornadoClass c, float scale) {
        double t = Mth.clamp(h / (c.height * scale), 0.0D, 1.0D);
        double lean = t * t * c.height * scale * 0.22D;
        double wobble = Math.sin(this.tickCount * 0.05D + t * 4.0D) * c.radius * scale * 0.12D * t;
        return new Vec3(getX() - Math.cos(this.heading) * lean - Math.sin(this.heading) * wobble, getY() + h,
                getZ() - Math.sin(this.heading) * lean + Math.cos(this.heading) * wobble);
    }

    // ---------------------------------------------------------------- tick
    @Override
    public void tick() {
        super.tick();
        this.age++;
        TornadoClass c = getTornadoClass();
        if (this.level().isClientSide) {
            clientTick(c, scale());
        } else {
            serverTick((ServerLevel) this.level(), c);
        }
    }

    private void serverTick(ServerLevel sl, TornadoClass c) {
        if (this.ropeTicks < 0 && this.age > c.lifeTicks) this.ropeTicks = 0;
        if (this.ropeTicks >= 0) {
            this.ropeTicks++;
            float fade = 1.0F - this.ropeTicks / 240.0F;
            if (fade <= 0.0F) {
                releaseAll(c);
                this.discard();
                return;
            }
            if (Math.abs(fade - getFade()) > 0.01F) this.entityData.set(DATA_FADE, fade);
        }
        float scale = scale();
        if (scale < 0.05F) return;

        move(sl, c, scale);
        if (TornadoConfig.blockDamage) pickUpBlocks(sl, c, scale);
        updateDebris(c, scale);
        if (this.tickCount % 2 == 0) affectEntities(sl, c, scale);

        if (this.tickCount % 40 == 0) {
            float vol = (float) Math.min(40.0D, 6.0D + c.radius * 0.18D);
            sl.playSound(null, getX(), getY() + 6.0D, getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, vol, 0.5F);
            sl.playSound(null, getX(), getY() + 6.0D, getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, vol * 0.5F, 0.35F + this.random.nextFloat() * 0.2F);
        }
    }

    private void move(ServerLevel sl, TornadoClass c, float scale) {
        double dx, dz, speed = c.speed;
        if (this.target != null) {
            double tx = this.target.x - getX(), tz = this.target.z - getZ();
            double d = Math.sqrt(tx * tx + tz * tz);
            this.heading = (float) Math.atan2(tz, tx);
            speed = Math.min(speed * 1.6D, d * 0.05D);
        } else {
            if (this.tickCount % 40 == 0) this.heading += (this.random.nextFloat() - 0.5F) * 0.7F;
        }
        dx = Math.cos(this.heading) * speed;
        dz = Math.sin(this.heading) * speed;
        double nx = getX() + dx, nz = getZ() + dz;
        BlockPos np = BlockPos.containing(nx, getY(), nz);
        if (!sl.hasChunkAt(np)) return;
        double ground = sl.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(nx), Mth.floor(nz));
        double ny = getY() + (ground - getY()) * 0.08D;
        this.setPos(nx, ny, nz);
    }

    private static boolean isTerrain(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD)
                || s.is(BlockTags.TERRACOTTA) || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE);
    }

    private void pickUpBlocks(ServerLevel sl, TornadoClass c, float scale) {
        this.debris.removeIf(e -> !e.isAlive());
        if (this.debris.size() >= c.capacity) return;
        int attempts = Math.max(1, Math.round(c.pickup * scale));
        double reach = radiusAt(0.0D, c, scale) * 1.9D + 3.0D;
        for (int i = 0; i < attempts && this.debris.size() < c.capacity; i++) {
            double ang = this.random.nextDouble() * Mth.TWO_PI;
            double dist = Math.sqrt(this.random.nextDouble()) * reach;
            int bx = Mth.floor(getX() + Math.cos(ang) * dist);
            int bz = Mth.floor(getZ() + Math.sin(ang) * dist);
            if (!sl.hasChunkAt(new BlockPos(bx, 64, bz))) continue;
            int top = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) - 1;
            int depth = 1 + this.random.nextInt(1 + c.ordinal() / 3);
            BlockPos pos = new BlockPos(bx, top - this.random.nextInt(depth), bz);
            BlockState state = sl.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) continue;
            float hardness = state.getDestroySpeed(sl, pos);
            if (hardness < 0.0F || hardness > c.maxHardness) continue;
            if (!c.scour && isTerrain(state)) continue;
            FallingBlockEntity fe = FallingBlockEntity.fall(sl, pos, state);
            fe.dropItem = false;
            fe.addTag(DEBRIS_TAG);
            fe.setHurtsEntities(2.0F, 40);
            fe.setDeltaMovement(0.0D, 0.3D, 0.0D);
            this.debris.add(fe);
        }
    }

    private void updateDebris(TornadoClass c, float scale) {
        double wind = c.wind * TornadoConfig.windScale;
        Iterator<FallingBlockEntity> it = this.debris.iterator();
        while (it.hasNext()) {
            FallingBlockEntity b = it.next();
            if (!b.isAlive()) { it.remove(); continue; }
            double h = b.getY() - getY();
            Vec3 ctr = centerAt(Math.max(0.0D, h), c, scale);
            double dx = b.getX() - ctr.x, dz = b.getZ() - ctr.z;
            double d = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
            double tx = -dz / d, tz = dx / d;
            if (h > c.height * scale * 0.85D || this.random.nextInt(900) == 0) {
                b.setDeltaMovement(tx * wind * 1.4D + dx / d * wind * 0.8D, 0.2D, tz * wind * 1.4D + dz / d * wind * 0.8D);
                b.hasImpulse = true;
                it.remove();
                continue;
            }
            double r = radiusAt(Math.max(0.0D, h), c, scale);
            double inward = Mth.clamp((r * 0.7D - d) * 0.06D, -0.6D, 0.6D);
            double vy = 0.25D + wind * 0.22D;
            b.setDeltaMovement(tx * wind * 0.9D + dx / d * inward, vy, tz * wind * 0.9D + dz / d * inward);
            b.hasImpulse = true;
            b.time = 1;
            b.fallDistance = 0.0F;
        }
    }

    private void releaseAll(TornadoClass c) {
        for (FallingBlockEntity b : this.debris) {
            if (b.isAlive()) b.setDeltaMovement(b.getDeltaMovement().add((this.random.nextDouble() - 0.5D) * 0.6D, 0.0D, (this.random.nextDouble() - 0.5D) * 0.6D));
        }
        this.debris.clear();
    }

    private void affectEntities(ServerLevel sl, TornadoClass c, float scale) {
        double wind = c.wind * TornadoConfig.windScale;
        double R = c.radius * scale * 1.3D + 4.0D;
        AABB box = new AABB(getX() - R, getY() - 2.0D, getZ() - R, getX() + R, getY() + c.height * scale, getZ() + R);
        List<Entity> list = sl.getEntities(this, box, e -> !(e instanceof TornadoEntity) && !(e instanceof SupercellEntity)
                && !e.getTags().contains(DEBRIS_TAG) && !(e instanceof Player p && (p.isSpectator() || (p.isCreative() && p.getAbilities().flying))));
        for (Entity e : list) {
            double h = e.getY() - getY();
            Vec3 ctr = centerAt(Math.max(0.0D, h), c, scale);
            double dx = e.getX() - ctr.x, dz = e.getZ() - ctr.z;
            double d = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
            double reach = radiusAt(Math.max(0.0D, h), c, scale) * 1.4D + 4.0D;
            if (d > reach) continue;
            double strength = 1.0D - d / reach;
            Vec3 v = e.getDeltaMovement();
            double nx = v.x + (-dz / d * wind * 0.14D - dx / d * 0.05D) * strength;
            double nz = v.z + (dx / d * wind * 0.14D - dz / d * 0.05D) * strength;
            double ny = v.y + (0.04D + wind * 0.05D) * strength;
            double max = wind * 1.4D;
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > max) { nx *= max / len; ny *= max / len; nz *= max / len; }
            e.setDeltaMovement(nx, ny, nz);
            e.hurtMarked = true;
            if (TornadoConfig.entityDamage && e instanceof LivingEntity le && strength > 0.55D && this.tickCount % 10 == 0) {
                le.hurt(sl.damageSources().generic(), (float) (c.damage * strength));
            }
        }
    }

    // ---------------------------------------------------------------- client visuals (vanilla smoke)
    private void clientTick(TornadoClass c, float scale) {
        if (scale < 0.02F) return;
        Level lv = this.level();
        Player near = lv.getNearestPlayer(this, 400.0D);
        double distFactor = 1.0D;
        if (near != null) distFactor = Mth.clamp(1.4D - near.distanceTo(this) / (c.radius * 6.0D + 60.0D), 0.25D, 1.0D);
        int n = (int) Math.max(2, c.particles * scale * distFactor * TornadoConfig.particleScale);
        double H = c.height * scale;
        double spin = this.tickCount * 0.45D;
        for (int i = 0; i < n; i++) {
            double t = Math.pow(this.random.nextDouble(), 1.3D);
            double h = t * H;
            double r = radiusAt(h, c, scale);
            double ang = this.random.nextDouble() * Mth.TWO_PI + spin * (1.0D - t * 0.4D);
            Vec3 ctr = centerAt(h, c, scale);
            double rr = r * (0.7D + 0.3D * this.random.nextDouble());
            double px = ctr.x + Math.cos(ang) * rr, pz = ctr.z + Math.sin(ang) * rr;
            double tv = 0.18D + c.wind * 0.06D;
            double vx = -Math.sin(ang) * tv, vz = Math.cos(ang) * tv;
            int roll = this.random.nextInt(10);
            if (roll < 6) lv.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, px, ctr.y, pz, vx, 0.05D, vz);
            else if (roll < 8) lv.addAlwaysVisibleParticle(ParticleTypes.SMOKE, true, px, ctr.y, pz, vx, 0.08D, vz);
            else lv.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, px, ctr.y, pz, vx * 0.5D, 0.02D, vz * 0.5D);
        }
        // ground dust ring and debris cloud
        double r0 = radiusAt(0.0D, c, scale);
        int ring = Math.max(2, (int) (n * 0.4D));
        for (int i = 0; i < ring; i++) {
            double ang = this.random.nextDouble() * Mth.TWO_PI;
            double rr = r0 * (0.8D + this.random.nextDouble() * 1.8D);
            double px = getX() + Math.cos(ang) * rr, pz = getZ() + Math.sin(ang) * rr;
            lv.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, px, getY() + this.random.nextDouble() * 3.0D, pz,
                    -Math.sin(ang) * 0.25D, 0.1D, Math.cos(ang) * 0.25D);
        }
        // condensation cap at the top
        for (int i = 0; i < Math.max(1, n / 6); i++) {
            double ang = this.random.nextDouble() * Mth.TWO_PI;
            Vec3 top = centerAt(H, c, scale);
            double rr = radiusAt(H, c, scale) * (1.0D + this.random.nextDouble() * 0.6D);
            lv.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, top.x + Math.cos(ang) * rr, top.y, top.z + Math.sin(ang) * rr,
                    -Math.sin(ang) * 0.3D, 0.0D, Math.cos(ang) * 0.3D);
        }
    }
}
