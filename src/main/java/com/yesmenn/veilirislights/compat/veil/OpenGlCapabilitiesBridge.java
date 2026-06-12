package com.yesmenn.veilirislights.compat.veil;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

public final class OpenGlCapabilitiesBridge {

    private static volatile GLCapabilities renderCapabilities;

    private OpenGlCapabilitiesBridge() {
    }

    public static void captureRenderThreadCapabilities() {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }

        try {
            renderCapabilities = GL.getCapabilities();
        } catch (IllegalStateException ignored) {
        }
    }

    public static GLCapabilities currentOrRenderThreadCapabilities() {
        try {
            return GL.getCapabilities();
        } catch (IllegalStateException exception) {
            GLCapabilities capabilities = renderCapabilities;
            if (capabilities != null) {
                return capabilities;
            }
            throw exception;
        }
    }
}
