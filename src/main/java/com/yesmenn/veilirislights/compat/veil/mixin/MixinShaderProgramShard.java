package com.yesmenn.veilirislights.compat.veil.mixin;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.impl.client.render.pipeline.ShaderProgramShard;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.yesmenn.veilirislights.VeilIrisLights;
import com.yesmenn.veilirislights.compat.veil.IrisVeilShaderCache;
import com.yesmenn.veilirislights.compat.veil.RenderStateManager;

/**
 * Intercepts {@link ShaderProgramShard#setupRenderState()} at runtime.
 * When Iris is active, lets Veil set its normal render state first, then
 * switches the active program to an Iris {@link ShaderInstance} that outputs
 * to gbuffer.
 *
 * <p>This mixin approach avoids inheritance issues with the
 * {@code ShaderStateShard} API which varies between Minecraft versions.
 */
@Mixin(value = ShaderProgramShard.class, remap = false)
public class MixinShaderProgramShard {

    @Unique
    private volatile ShaderInstance veilIrisLights$cachedIrisShader;

    @Unique
    private volatile boolean veilIrisLights$cachedIsShadow;

    @Unique
    private volatile int veilIrisLights$lastShaderPackGen = -1;

    @Unique
    private volatile boolean veilIrisLights$creationAttempted;

    @Inject(method = "setupRenderState", at = @At("TAIL"))
    private void veilIrisLights$interceptSetupRenderState(CallbackInfo ci) {
        if (!VeilIrisLights.isShaderPackInUse()) {
            return; // no shaderpack loaded → let Veil handle it
        }

        ResourceLocation shaderPath = ((ShaderProgramShard) (Object) this).getShader();
        if ("veil".equals(shaderPath.getNamespace()) && shaderPath.getPath().startsWith("light/")) {
            return;
        }

        try {
            ShaderInstance irisShader = veilIrisLights$getOrCreateIrisShader(shaderPath);
            if (irisShader != null) {
                irisShader.apply();
                RenderSystem.setShader(() -> irisShader); // register in RenderSystem so draw uses Iris shader

                // During the shadow pass, irisShader.apply() uploads gbuffer
                // matrices by default.  Override with the shadow-specific
                // projection / model-view so the shader writes correct depth.
                if (RenderStateManager.isRenderingShadow()) {
                    veilIrisLights$setShadowMatrices(irisShader);
                }
            }
        } catch (Exception e) {
            VeilIrisLights.LOGGER.warn(
                "VeilIrisLights: failed for '{}', falling back to Veil", shaderPath, e);
            veilIrisLights$creationAttempted = true;
        }
    }

    /**
     * Sets {@code iris_ProjMat}, {@code iris_ModelViewMat}, and
     * {@code iris_NormalMat} on the given shader to the shadow pass
     * matrices tracked by {@link ShadowRenderer}.
     */
    @Unique
    private static void veilIrisLights$setShadowMatrices(ShaderInstance shader) {
        Uniform projUniform = shader.getUniform("iris_ProjMat");
        if (projUniform != null) {
            projUniform.set(ShadowRenderer.PROJECTION);
        }

        Uniform modelViewUniform = shader.getUniform("iris_ModelViewMat");
        if (modelViewUniform != null) {
            modelViewUniform.set(ShadowRenderer.MODELVIEW);
        }

        Uniform normalUniform = shader.getUniform("iris_NormalMat");
        if (normalUniform != null) {
            Matrix4f normalMatrix = new Matrix4f(ShadowRenderer.MODELVIEW);
            normalMatrix.invert();
            normalMatrix.transpose();
            normalUniform.set(new Matrix3f(normalMatrix));
        }
    }

    @Unique
    private ShaderInstance veilIrisLights$getOrCreateIrisShader(ResourceLocation shaderPath) {
        int currentGen = IrisVeilShaderCache.getShaderPackGeneration();
        boolean isShadow = RenderStateManager.isRenderingShadow();

        // Shaderpack changed OR render pass changed (shadow ↔ non-shadow)
        // → invalidate local cache so the correct program is created.
        if (veilIrisLights$lastShaderPackGen != currentGen || veilIrisLights$cachedIsShadow != isShadow) {
            veilIrisLights$cachedIrisShader = null;
            veilIrisLights$creationAttempted = false;
        }

        if (veilIrisLights$creationAttempted && veilIrisLights$lastShaderPackGen == currentGen) {
            return veilIrisLights$cachedIrisShader;
        }

        veilIrisLights$cachedIrisShader = IrisVeilShaderCache.getOrCreate(shaderPath);
        veilIrisLights$creationAttempted = true;
        veilIrisLights$lastShaderPackGen = currentGen;
        veilIrisLights$cachedIsShadow = isShadow;
        return veilIrisLights$cachedIrisShader;
    }
}
