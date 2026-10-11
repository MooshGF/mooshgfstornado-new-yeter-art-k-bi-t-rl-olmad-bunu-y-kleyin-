package com.mooshgf.tornado;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Tornado "capacities". Every class defines size, wind speed, how many blocks it can keep airborne
 * (capacity), the hardest block it can pull out of the ground and how fast it picks things up.
 */
public enum TornadoClass {
    //   id     label            radius height base   capacity hardness  wind  dmg   life   speed pick scour particles
    EF0("ef0", "EF0",             7,  45, 0.30,    20,      0.4, 0.45,   1,  3600, 0.10,  1, false,  18),
    EF1("ef1", "EF1",            10,  65, 0.28,    60,      1.0, 0.65,   2,  4800, 0.12,  2, false,  24),
    EF2("ef2", "EF2",            15,  85, 0.28,   150,      2.5, 0.85,   4,  6000, 0.13,  3, false,  32),
    EF3("ef3", "EF3",            22, 105, 0.30,   300,      5.0, 1.10,   6,  7200, 0.14,  5, false,  44),
    EF4("ef4", "EF4",            32, 130, 0.32,   600,     12.0, 1.40,   9,  9000, 0.15,  8, false,  60),
    EF5("ef5", "EF5",            45, 160, 0.36,  1000,     35.0, 1.80,  14, 10800, 0.16, 12, true,   80),
    DOMINATOR_1("d1", "Dominator I",   60, 190, 0.45,  1500,   45.0, 2.10,  20, 12000, 0.16, 16, true, 100),
    DOMINATOR_2("d2", "Dominator II",  85, 220, 0.50,  2500,   55.0, 2.50,  30, 14400, 0.17, 22, true, 120),
    DOMINATOR_3("d3", "Dominator III",120, 260, 0.55,  4000, 100000, 2.90,  50, 16800, 0.18, 30, true, 140),
    DOMINATOR_4("d4", "Dominator IV", 170, 320, 0.60,  6000, 100000, 3.40, 100, 24000, 0.20, 40, true, 160);

    public final String id;
    public final String label;
    /** Radius of the funnel at its top, in blocks. */
    public final double radius;
    /** Funnel height in blocks. */
    public final double height;
    /** Ground radius as a fraction of the top radius (low = rope/cone, high = wedge). */
    public final double baseRatio;
    /** Max number of blocks the tornado keeps airborne at the same time. */
    public final int capacity;
    /** Max block hardness it can lift (obsidian = 50, bedrock = unbreakable). */
    public final double maxHardness;
    /** Tangential wind speed in blocks per tick. */
    public final double wind;
    public final float damage;
    public final int lifeTicks;
    /** Travel speed in blocks per tick. */
    public final double speed;
    /** Block pick-up attempts per tick. */
    public final int pickup;
    /** Whether it may scour soil/stone (crater the ground). */
    public final boolean scour;
    public final int particles;

    TornadoClass(String id, String label, double radius, double height, double baseRatio, int capacity,
                 double maxHardness, double wind, float damage, int lifeTicks, double speed, int pickup,
                 boolean scour, int particles) {
        this.id = id; this.label = label; this.radius = radius; this.height = height; this.baseRatio = baseRatio;
        this.capacity = capacity; this.maxHardness = maxHardness; this.wind = wind; this.damage = damage;
        this.lifeTicks = lifeTicks; this.speed = speed; this.pickup = pickup; this.scour = scour; this.particles = particles;
    }

    @Nullable
    public static TornadoClass byName(String name) {
        String n = name.toLowerCase(Locale.ROOT).replace("dominator", "d").replace("_", "").replace(" ", "");
        for (TornadoClass c : values()) {
            if (c.id.equals(n)) return c;
        }
        return null;
    }

    public static TornadoClass byOrdinal(int i) {
        TornadoClass[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
