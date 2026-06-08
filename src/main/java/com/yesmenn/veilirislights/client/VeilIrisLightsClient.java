package com.yesmenn.veilirislights.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yesmenn.veilirislights.client.screen.LightRenderConfigScreen;
import com.yesmenn.veilirislights.compat.light.IrisVeilLightPass;
import com.yesmenn.veilirislights.config.LightRenderConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class VeilIrisLightsClient {

    private static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.veil_iris_lights.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.veil_iris_lights");

    private VeilIrisLightsClient() {
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_CONFIG.consumeClick()) {
            minecraft.setScreen(new LightRenderConfigScreen(minecraft.screen));
        }
    }

    public static void onDebugText(CustomizeGuiOverlayEvent.DebugText event) {
        IrisVeilLightPass.appendDebugInfo(event.getRight());
    }

    public static void initialize() {
        LightRenderConfig.load();
    }
}
