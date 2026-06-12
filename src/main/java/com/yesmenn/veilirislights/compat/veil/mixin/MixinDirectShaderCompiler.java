package com.yesmenn.veilirislights.compat.veil.mixin;

import foundry.veil.api.client.render.shader.compiler.CompiledShader;
import foundry.veil.api.client.render.shader.compiler.VeilShaderSource;
import foundry.veil.impl.client.render.shader.compiler.DirectShaderCompiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.yesmenn.veilirislights.VeilIrisLights;
import com.yesmenn.veilirislights.compat.veil.IrisVeilShaderCache;

import static org.lwjgl.opengl.GL20C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20C.GL_VERTEX_SHADER;

@Mixin(value = DirectShaderCompiler.class, remap = false)
public class MixinDirectShaderCompiler {

    @Inject(method = "compile(ILfoundry/veil/api/client/render/shader/compiler/VeilShaderSource;)Lfoundry/veil/api/client/render/shader/compiler/CompiledShader;",
            at = @At("HEAD"))
    private void veilIrisLights$captureVertexSource(int type, VeilShaderSource source,
                                             CallbackInfoReturnable<CompiledShader> cir) {
        if (type != GL_VERTEX_SHADER && type != GL_FRAGMENT_SHADER) return;
        if (source.sourceId() == null) return;
        if (source.sourceCode() == null || source.sourceCode().isEmpty()) return;

        String src = source.sourceCode();
        if (type == GL_VERTEX_SHADER) {
            boolean hasGetVelocity = src.contains("getVelocity");
            boolean hasOffset = src.contains("offset");
            VeilIrisLights.LOGGER.trace("[VeilHook] Captured vertex source for '{}': {} chars, hasGetVelocity={}, hasOffset={}",
                source.sourceId(), src.length(), hasGetVelocity, hasOffset);
            IrisVeilShaderCache.storeProcessedVertexSource(source.sourceId(), source.sourceCode());
        } else {
            VeilIrisLights.LOGGER.trace("[VeilHook] Captured fragment source for '{}': {} chars",
                source.sourceId(), src.length());
            IrisVeilShaderCache.storeProcessedFragmentSource(source.sourceId(), source.sourceCode());
        }
    }
}
