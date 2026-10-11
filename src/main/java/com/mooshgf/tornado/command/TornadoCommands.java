package com.mooshgf.tornado.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mooshgf.tornado.ModEntities;
import com.mooshgf.tornado.TornadoClass;
import com.mooshgf.tornado.TornadoConfig;
import com.mooshgf.tornado.entity.SupercellEntity;
import com.mooshgf.tornado.entity.TornadoEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * /mooshgfstornado
 *   (no args)                       summon an EF3 tornado in front of you
 *   tornado <class> [pos]           summon a tornado: ef0..ef5, d1..d4
 *   dominator <1-4> [pos]           summon Dominator I-IV
 *   supercell [stage] [class] [pos] summon a supercell (stage 0 developing, 1 mature, 2 tornadic, 3 dissipating)
 *   capacity                        list the capacity of every tornado class
 *   list / clear                    list or remove storms
 *   config <blocks|entities|warnings|wind|particles> <value>
 */
public class TornadoCommands {
    private static final SuggestionProvider<CommandSourceStack> CLASSES = (ctx, b) -> {
        List<String> ids = new ArrayList<>();
        for (TornadoClass c : TornadoClass.values()) ids.add(c.id);
        return SharedSuggestionProvider.suggest(ids, b);
    };

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("mooshgfstornado")
                .executes(ctx -> spawnTornado(ctx.getSource(), TornadoClass.EF3, front(ctx.getSource())))
                .then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
                .then(Commands.literal("capacity").executes(ctx -> capacity(ctx.getSource())))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("clear").executes(ctx -> clear(ctx.getSource())))
                .then(Commands.literal("tornado")
                        .then(Commands.argument("class", StringArgumentType.word()).suggests(CLASSES)
                                .executes(ctx -> spawnTornado(ctx.getSource(), parseClass(ctx), front(ctx.getSource())))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> spawnTornado(ctx.getSource(), parseClass(ctx), Vec3Argument.getVec3(ctx, "pos"))))))
                .then(Commands.literal("dominator")
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 4))
                                .executes(ctx -> spawnTornado(ctx.getSource(), dominator(ctx), front(ctx.getSource())))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(ctx -> spawnTornado(ctx.getSource(), dominator(ctx), Vec3Argument.getVec3(ctx, "pos"))))))
                .then(Commands.literal("supercell")
                        .executes(ctx -> spawnSupercell(ctx.getSource(), 0, TornadoClass.EF3, front(ctx.getSource())))
                        .then(Commands.argument("stage", IntegerArgumentType.integer(0, 3))
                                .executes(ctx -> spawnSupercell(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "stage"), TornadoClass.EF3, front(ctx.getSource())))
                                .then(Commands.argument("class", StringArgumentType.word()).suggests(CLASSES)
                                        .executes(ctx -> spawnSupercell(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "stage"), parseClass(ctx), front(ctx.getSource())))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> spawnSupercell(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "stage"), parseClass(ctx), Vec3Argument.getVec3(ctx, "pos")))))))
                .then(Commands.literal("config")
                        .then(Commands.literal("blocks").then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
                            TornadoConfig.blockDamage = BoolArgumentType.getBool(ctx, "value");
                            return msg(ctx.getSource(), "Block damage: " + TornadoConfig.blockDamage);
                        })))
                        .then(Commands.literal("entities").then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
                            TornadoConfig.entityDamage = BoolArgumentType.getBool(ctx, "value");
                            return msg(ctx.getSource(), "Entity damage: " + TornadoConfig.entityDamage);
                        })))
                        .then(Commands.literal("warnings").then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
                            TornadoConfig.warnings = BoolArgumentType.getBool(ctx, "value");
                            return msg(ctx.getSource(), "Warnings: " + TornadoConfig.warnings);
                        })))
                        .then(Commands.literal("wind").then(Commands.argument("value", DoubleArgumentType.doubleArg(0.0D, 5.0D)).executes(ctx -> {
                            TornadoConfig.windScale = DoubleArgumentType.getDouble(ctx, "value");
                            return msg(ctx.getSource(), "Wind scale: " + TornadoConfig.windScale);
                        })))
                        .then(Commands.literal("particles").then(Commands.argument("value", DoubleArgumentType.doubleArg(0.1D, 3.0D)).executes(ctx -> {
                            TornadoConfig.particleScale = DoubleArgumentType.getDouble(ctx, "value");
                            return msg(ctx.getSource(), "Particle scale: " + TornadoConfig.particleScale);
                        })))));
    }

    private static TornadoClass parseClass(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "class");
        TornadoClass c = TornadoClass.byName(name);
        if (c == null) throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("Unknown class '" + name + "'. Use ef0-ef5 or d1-d4.")).create();
        return c;
    }

    private static TornadoClass dominator(CommandContext<CommandSourceStack> ctx) {
        return TornadoClass.byName("d" + IntegerArgumentType.getInteger(ctx, "level"));
    }

    /** A point ~ (radius + 20) blocks in front of the command source, so the storm does not spawn on top of you. */
    private static Vec3 front(CommandSourceStack src) {
        Vec3 pos = src.getPosition();
        float yaw = src.getRotation().y;
        double dist = 40.0D;
        double rad = Math.toRadians(yaw);
        return new Vec3(pos.x - Math.sin(rad) * dist, pos.y, pos.z + Math.cos(rad) * dist);
    }

    private static Vec3 onGround(ServerLevel level, Vec3 p) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(p.x), Mth.floor(p.z));
        return new Vec3(p.x, y, p.z);
    }

    private static int msg(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), true);
        return 1;
    }

    private static int spawnTornado(CommandSourceStack src, TornadoClass c, Vec3 at) {
        ServerLevel level = src.getLevel();
        Vec3 p = onGround(level, at);
        TornadoEntity t = new TornadoEntity(ModEntities.TORNADO.get(), level);
        t.setTornadoClass(c);
        t.setPos(p.x, p.y, p.z);
        level.addFreshEntity(t);
        return msg(src, "Summoned " + c.label + " tornado at " + Mth.floor(p.x) + ", " + Mth.floor(p.y) + ", " + Mth.floor(p.z));
    }

    private static int spawnSupercell(CommandSourceStack src, int stage, TornadoClass c, Vec3 at) {
        ServerLevel level = src.getLevel();
        Vec3 p = onGround(level, at);
        SupercellEntity s = new SupercellEntity(ModEntities.SUPERCELL.get(), level);
        s.setTornadoClass(c);
        s.setPos(p.x, p.y, p.z);
        s.setStage(stage);
        level.addFreshEntity(s);
        return msg(src, "Summoned supercell (stage " + stage + ", tornado class " + c.label + ")");
    }

    private static int help(CommandSourceStack src) {
        msg(src, "/mooshgfstornado                         -> EF3 tornado");
        msg(src, "/mooshgfstornado tornado <ef0-ef5|d1-d4> [pos]");
        msg(src, "/mooshgfstornado dominator <1-4> [pos]");
        msg(src, "/mooshgfstornado supercell [stage 0-3] [class] [pos]");
        msg(src, "/mooshgfstornado capacity | list | clear");
        msg(src, "/mooshgfstornado config <blocks|entities|warnings|wind|particles> <value>");
        return 1;
    }

    private static int capacity(CommandSourceStack src) {
        msg(src, "Class | radius | height | capacity (blocks) | max hardness | wind (b/tick)");
        for (TornadoClass c : TornadoClass.values()) {
            String hard = c.maxHardness > 1000 ? "everything" : String.valueOf(c.maxHardness);
            msg(src, c.label + " | " + (int) c.radius + " | " + (int) c.height + " | " + c.capacity + " | " + hard + " | " + c.wind);
        }
        return 1;
    }

    private static int list(CommandSourceStack src) {
        int n = 0;
        for (Entity e : src.getLevel().getAllEntities()) {
            if (e instanceof TornadoEntity t) {
                n++;
                msg(src, t.getTornadoClass().label + " tornado @ " + Mth.floor(t.getX()) + ", " + Mth.floor(t.getZ()));
            } else if (e instanceof SupercellEntity s) {
                n++;
                msg(src, "Supercell (stage " + s.getStage() + ") @ " + Mth.floor(s.getX()) + ", " + Mth.floor(s.getZ()));
            }
        }
        if (n == 0) msg(src, "No storms active.");
        return n;
    }

    private static int clear(CommandSourceStack src) {
        int n = 0;
        for (Entity e : src.getLevel().getAllEntities()) {
            if (e instanceof TornadoEntity t) {
                t.beginRopeOut();
                n++;
            } else if (e instanceof SupercellEntity s) {
                s.discard();
                n++;
            }
        }
        return msg(src, "Dissipating " + n + " storm(s).") * Math.max(1, n);
    }
}
