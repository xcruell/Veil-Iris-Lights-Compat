package com.yesmenn.veilirislights;

import com.mojang.logging.LogUtils;
import com.yesmenn.veilirislights.client.VeilIrisLightsClient;
import com.yesmenn.veilirislights.client.screen.LightRenderConfigScreen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(VeilIrisLights.MODID)
public final class VeilIrisLights {
    public static final String MODID = "veil_iris_lights";
    public static final Logger LOGGER = LogUtils.getLogger();

    public VeilIrisLights(IEventBus modBus, ModContainer container) {
        VeilIrisLightsClient.initialize();
        modBus.addListener(VeilIrisLightsClient::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(VeilIrisLightsClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(VeilIrisLightsClient::onDebugText);
        IConfigScreenFactory configScreenFactory =
                (ignored, parent) -> new LightRenderConfigScreen(parent);
        container.registerExtensionPoint(IConfigScreenFactory.class, configScreenFactory);
    }

    public static boolean isShaderPackInUse() {
        try {
            return net.irisshaders.iris.Iris.getIrisConfig().areShadersEnabled()
                    && net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
