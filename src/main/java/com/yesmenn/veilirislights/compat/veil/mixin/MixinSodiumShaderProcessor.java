package com.yesmenn.veilirislights.compat.veil.mixin;

import com.yesmenn.veilirislights.compat.veil.OpenGlCapabilitiesBridge;
import foundry.veil.impl.client.render.shader.processor.SodiumShaderProcessor;
import org.lwjgl.opengl.GLCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SodiumShaderProcessor.class, remap = false)
public abstract class MixinSodiumShaderProcessor {

    @Redirect(
            method = "modify",
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/opengl/GL;getCapabilities()Lorg/lwjgl/opengl/GLCapabilities;"))
    private static GLCapabilities veilIrisLights$useRenderThreadCapabilities() {
        return OpenGlCapabilitiesBridge.currentOrRenderThreadCapabilities();
    }
}
