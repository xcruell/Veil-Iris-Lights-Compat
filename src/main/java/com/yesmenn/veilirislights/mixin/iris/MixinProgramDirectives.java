package com.yesmenn.veilirislights.mixin.iris;

import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.yesmenn.veilirislights.accessors.ProgramDirectivesAccessor;

import java.util.Optional;

@Mixin(value = ProgramDirectives.class,remap = false)
public class MixinProgramDirectives implements ProgramDirectivesAccessor {
    @Unique
    private AlphaTest veilIrisLightsAlphaTestOverride;

    @Override
    public void veilIrisLights$setAlphaTestOverride(AlphaTest alphaTest) {
        veilIrisLightsAlphaTestOverride = alphaTest;
    }

    @Inject(method = "getAlphaTestOverride", at = @At("HEAD"), cancellable = true)
    private void injectAlphaTestOverride(CallbackInfoReturnable<Optional<AlphaTest>> cir){
        if (veilIrisLightsAlphaTestOverride != null) {
            cir.setReturnValue(Optional.of(veilIrisLightsAlphaTestOverride));
        }
    }
}
