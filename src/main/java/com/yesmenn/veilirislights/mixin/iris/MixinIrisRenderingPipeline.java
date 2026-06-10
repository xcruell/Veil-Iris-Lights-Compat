package com.yesmenn.veilirislights.mixin.iris;


import com.mojang.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.minecraft.client.renderer.ShaderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.yesmenn.veilirislights.accessors.IrisRenderingPipelineAccessor;
import com.yesmenn.veilirislights.compat.light.IrisVeilLightPass;
import com.yesmenn.veilirislights.compat.veil.IrisVeilShaderCache;
import com.google.common.collect.ImmutableSet;

import java.io.IOException;

@Mixin(IrisRenderingPipeline.class)
public abstract class MixinIrisRenderingPipeline implements IrisRenderingPipelineAccessor {

    @Shadow
    @Final
    private RenderTargets renderTargets;

    @Shadow
    @Final
    private ImmutableSet<Integer> flippedAfterTranslucent;

    @Unique
    private ProgramSet programSet;

    @Override
    public ProgramSet getProgramSet(){
        return programSet;
    }

    @Inject(method = "<init>",at = @At("TAIL"),remap = false)
    public void initSet(ProgramSet set, CallbackInfo callbackInfo){
        programSet = set;
        IrisVeilShaderCache.onShaderPackReload();
    }

    @Inject(method = "finalizeLevelRendering", at = @At("HEAD"), remap = false)
    private void veilIrisLights$renderLights(CallbackInfo callbackInfo) {
        IrisVeilLightPass.render(this.renderTargets, this.flippedAfterTranslucent);
    }

    @Inject(method = "destroy", at = @At("HEAD"), remap = false)
    private void veilIrisLights$destroyLightPass(CallbackInfo callbackInfo) {
        IrisVeilLightPass.close();
    }

    @Invoker(remap = false)
    @Override
    public abstract ShaderInstance invokeCreateShader(String name, ProgramSource source, ProgramId programId, AlphaTest fallbackAlpha,
                                                    VertexFormat vertexFormat, FogMode fogMode,
                                                    boolean isIntensity, boolean isFullbright, boolean isGlint,
                                                    boolean isText, boolean isIE) throws IOException;

    @Invoker(remap = false)
    @Override
    public abstract ShaderInstance invokeCreateShadowShader(String name, ProgramSource source, ProgramId programId, AlphaTest fallbackAlpha,
                                                            VertexFormat vertexFormat, boolean isIntensity, boolean isFullbright,
                                                            boolean isText, boolean isIE) throws IOException;

}
