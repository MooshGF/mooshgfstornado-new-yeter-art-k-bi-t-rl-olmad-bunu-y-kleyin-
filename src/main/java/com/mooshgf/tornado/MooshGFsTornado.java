package com.mooshgf.tornado;

import com.mooshgf.tornado.command.TornadoCommands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MooshGFsTornado.MODID)
public class MooshGFsTornado {
    public static final String MODID = "mooshgfstornado";

    public MooshGFsTornado() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(TornadoCommands::onRegisterCommands);
    }
}
