package com.mooshgf.tornado;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The storms are made only of vanilla particles, so the entities use an invisible (no-op) renderer. */
@Mod.EventBusSubscriber(modid = MooshGFsTornado.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.TORNADO.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.SUPERCELL.get(), NoopRenderer::new);
    }
}
