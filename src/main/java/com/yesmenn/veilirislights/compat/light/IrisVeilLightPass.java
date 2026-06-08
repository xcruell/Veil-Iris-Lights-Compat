package com.yesmenn.veilirislights.compat.light;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.color.Colorc;
import foundry.veil.api.client.registry.LightTypeRegistry;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import com.yesmenn.veilirislights.VeilIrisLights;
import com.yesmenn.veilirislights.config.LightRenderConfig;

import java.nio.FloatBuffer;
import java.util.Collection;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL14C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;

public final class IrisVeilLightPass {

    private static final int MAX_POINT_LIGHTS = 48;
    private static final int MAX_AREA_LIGHTS = 16;
    private static int program;
    private static int framebuffer;
    private static int vertexArray;
    private static boolean initializationFailed;
    private static boolean hookLogged;
    private static boolean targetsLogged;
    private static int lastPointCount = -1;
    private static int lastAreaCount = -1;

    private IrisVeilLightPass() {
    }

    public static void render(RenderTargets renderTargets, ImmutableSet<Integer> flippedAfterTranslucent) {
        RenderSystem.assertOnRenderThread();

        Collection<? extends LightRenderHandle<PointLightData>> pointHandles =
                VeilRenderSystem.renderer().getLightRenderer().getLights(LightTypeRegistry.POINT.get());
        Collection<? extends LightRenderHandle<AreaLightData>> areaHandles =
                VeilRenderSystem.renderer().getLightRenderer().getLights(LightTypeRegistry.AREA.get());
        if (!hookLogged) {
            hookLogged = true;
            VeilIrisLights.LOGGER.info("Veil Iris light pass is active");
        }
        if (pointHandles.size() != lastPointCount || areaHandles.size() != lastAreaCount) {
            lastPointCount = pointHandles.size();
            lastAreaCount = areaHandles.size();
            VeilIrisLights.LOGGER.info(
                    "Veil Iris light pass sees {} point light(s) and {} area light(s)",
                    lastPointCount,
                    lastAreaCount);
        }
        if (pointHandles.isEmpty() && areaHandles.isEmpty()) {
            return;
        }

        ensureInitialized();
        if (initializationFailed || program == 0) {
            return;
        }

        RenderTarget colorTarget = renderTargets.getOrCreate(0);
        int colorTexture = flippedAfterTranslucent.contains(0)
                ? colorTarget.getAltTexture()
                : colorTarget.getMainTexture();
        if (!targetsLogged) {
            targetsLogged = true;
            VeilIrisLights.LOGGER.info(
                    "Veil Iris targets: color={}, depth={}, flipped={}, size={}x{}",
                    colorTexture,
                    renderTargets.getDepthTexture(),
                    flippedAfterTranslucent.contains(0),
                    renderTargets.getCurrentWidth(),
                    renderTargets.getCurrentHeight());
        }

        int oldFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int oldVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int oldActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        int oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        boolean oldBlend = glIsEnabled(GL_BLEND);
        boolean oldDepthTest = glIsEnabled(GL_DEPTH_TEST);
        boolean oldCull = glIsEnabled(GL_CULL_FACE);
        int oldBlendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
        int oldBlendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        int oldBlendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        int oldBlendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        int oldBlendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        int oldBlendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        int[] oldViewport = new int[4];
        glGetIntegerv(GL_VIEWPORT, oldViewport);

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, colorTexture, 0);
        if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            VeilIrisLights.LOGGER.error("Veil Iris light framebuffer is incomplete");
            restoreState(oldFramebuffer, oldProgram, oldVertexArray, oldActiveTexture, oldTexture,
                    oldBlend, oldDepthTest, oldCull, oldBlendEquationRgb, oldBlendEquationAlpha,
                    oldBlendSrcRgb, oldBlendDstRgb, oldBlendSrcAlpha, oldBlendDstAlpha, oldViewport);
            return;
        }
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        glViewport(0, 0, renderTargets.getCurrentWidth(), renderTargets.getCurrentHeight());

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        glBlendFuncSeparate(GL_DST_COLOR, GL_ONE, GL_ZERO, GL_ONE);

        glUseProgram(program);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, renderTargets.getDepthTexture());
        glUniform1i(glGetUniformLocation(program, "uDepth"), 0);

        Matrix4f inverseViewProjection = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferProjection())
                .mul(CapturedRenderingState.INSTANCE.getGbufferModelView())
                .invert();
        Vector3d cameraPosition = CameraUniforms.getUnshiftedCameraPosition();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(16);
            inverseViewProjection.get(matrix);
            glUniformMatrix4fv(glGetUniformLocation(program, "uInverseViewProjection"), false, matrix);
        }
        glUniform3f(glGetUniformLocation(program, "uCameraPosition"),
                (float) cameraPosition.x, (float) cameraPosition.y, (float) cameraPosition.z);
        LightRenderConfig config = LightRenderConfig.get();
        glUniform1f(glGetUniformLocation(program, "uColorStrength"), (float) config.colorStrength);
        glUniform1f(glGetUniformLocation(program, "uNeutralLift"), (float) config.neutralLift);
        glUniform1f(glGetUniformLocation(program, "uLuminanceBoostLimit"), (float) config.luminanceBoostLimit);

        uploadPointLights(pointHandles, (float) config.exposure);
        uploadAreaLights(areaHandles, (float) config.exposure);

        glBindVertexArray(vertexArray);
        glDrawArrays(GL_TRIANGLES, 0, 3);

        restoreState(oldFramebuffer, oldProgram, oldVertexArray, oldActiveTexture, oldTexture,
                oldBlend, oldDepthTest, oldCull, oldBlendEquationRgb, oldBlendEquationAlpha,
                oldBlendSrcRgb, oldBlendDstRgb, oldBlendSrcAlpha, oldBlendDstAlpha, oldViewport);
    }

    public static void close() {
        if (program != 0) {
            glDeleteProgram(program);
            program = 0;
        }
        if (framebuffer != 0) {
            glDeleteFramebuffers(framebuffer);
            framebuffer = 0;
        }
        if (vertexArray != 0) {
            glDeleteVertexArrays(vertexArray);
            vertexArray = 0;
        }
        initializationFailed = false;
        hookLogged = false;
        targetsLogged = false;
        lastPointCount = -1;
        lastAreaCount = -1;
    }

    private static void uploadPointLights(Collection<? extends LightRenderHandle<PointLightData>> handles,
                                          float exposure) {
        float[] positionsAndRadii = new float[MAX_POINT_LIGHTS * 4];
        float[] colors = new float[MAX_POINT_LIGHTS * 4];
        int count = 0;
        for (LightRenderHandle<PointLightData> handle : handles) {
            if (!handle.isValid() || count >= MAX_POINT_LIGHTS) {
                continue;
            }
            PointLightData light = handle.getLightData();
            Vector3d position = new Vector3d(light.getPosition());
            Colorc color = light.getColor();
            int index = count * 4;
            positionsAndRadii[index] = (float) position.x;
            positionsAndRadii[index + 1] = (float) position.y;
            positionsAndRadii[index + 2] = (float) position.z;
            positionsAndRadii[index + 3] = light.getRadius();
            colors[index] = color.red() * light.getBrightness() * exposure;
            colors[index + 1] = color.green() * light.getBrightness() * exposure;
            colors[index + 2] = color.blue() * light.getBrightness() * exposure;
            count++;
        }
        glUniform1i(glGetUniformLocation(program, "uPointCount"), count);
        if (count > 0) {
            glUniform4fv(glGetUniformLocation(program, "uPointPositionRadius"), positionsAndRadii);
            glUniform4fv(glGetUniformLocation(program, "uPointColor"), colors);
        }
    }

    private static void uploadAreaLights(Collection<? extends LightRenderHandle<AreaLightData>> handles,
                                         float exposure) {
        float[] positionsAndDistances = new float[MAX_AREA_LIGHTS * 4];
        float[] directionsAndAngles = new float[MAX_AREA_LIGHTS * 4];
        float[] colors = new float[MAX_AREA_LIGHTS * 4];
        int count = 0;
        for (LightRenderHandle<AreaLightData> handle : handles) {
            if (!handle.isValid() || count >= MAX_AREA_LIGHTS) {
                continue;
            }
            AreaLightData light = handle.getLightData();
            Vector3d position = light.getPosition();
            Vector3f direction = new Quaternionf(light.getOrientation())
                    .transform(new Vector3f(0.0F, 0.0F, -1.0F))
                    .normalize();
            Colorc color = light.getColor();
            int index = count * 4;
            positionsAndDistances[index] = (float) position.x;
            positionsAndDistances[index + 1] = (float) position.y;
            positionsAndDistances[index + 2] = (float) position.z;
            positionsAndDistances[index + 3] = light.getDistance();
            directionsAndAngles[index] = direction.x;
            directionsAndAngles[index + 1] = direction.y;
            directionsAndAngles[index + 2] = direction.z;
            directionsAndAngles[index + 3] = (float) Math.cos(light.getAngle());
            colors[index] = color.red() * light.getBrightness() * exposure;
            colors[index + 1] = color.green() * light.getBrightness() * exposure;
            colors[index + 2] = color.blue() * light.getBrightness() * exposure;
            count++;
        }
        glUniform1i(glGetUniformLocation(program, "uAreaCount"), count);
        if (count > 0) {
            glUniform4fv(glGetUniformLocation(program, "uAreaPositionDistance"), positionsAndDistances);
            glUniform4fv(glGetUniformLocation(program, "uAreaDirectionAngle"), directionsAndAngles);
            glUniform4fv(glGetUniformLocation(program, "uAreaColor"), colors);
        }
    }

    private static void ensureInitialized() {
        if (program != 0 || initializationFailed) {
            return;
        }
        int vertexShader = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragmentShader = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        if (vertexShader == 0 || fragmentShader == 0) {
            initializationFailed = true;
            return;
        }

        program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            VeilIrisLights.LOGGER.error("Failed to link Veil Iris light shader: {}", glGetProgramInfoLog(program));
            glDeleteProgram(program);
            program = 0;
            initializationFailed = true;
            return;
        }

        framebuffer = glGenFramebuffers();
        vertexArray = glGenVertexArrays();
    }

    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            VeilIrisLights.LOGGER.error("Failed to compile Veil Iris light shader: {}", glGetShaderInfoLog(shader));
            glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static void restoreState(int framebufferId, int programId, int vertexArrayId,
                                     int activeTexture, int texture, boolean blend,
                                     boolean depthTest, boolean cull,
                                     int blendEquationRgb, int blendEquationAlpha,
                                     int blendSrcRgb, int blendDstRgb,
                                     int blendSrcAlpha, int blendDstAlpha,
                                     int[] viewport) {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebufferId);
        glUseProgram(programId);
        glBindVertexArray(vertexArrayId);
        glActiveTexture(activeTexture);
        glBindTexture(GL_TEXTURE_2D, texture);
        glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        setEnabled(GL_BLEND, blend);
        setEnabled(GL_DEPTH_TEST, depthTest);
        setEnabled(GL_CULL_FACE, cull);
    }

    private static void setEnabled(int capability, boolean enabled) {
        if (enabled) {
            glEnable(capability);
        } else {
            glDisable(capability);
        }
    }

    private static final String VERTEX_SHADER = """
            #version 150
            out vec2 vTexCoord;

            void main() {
                vec2 position = vec2(
                    (gl_VertexID == 1) ? 3.0 : -1.0,
                    (gl_VertexID == 2) ? 3.0 : -1.0
                );
                vTexCoord = position * 0.5 + 0.5;
                gl_Position = vec4(position, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 150

            const int MAX_POINT_LIGHTS = 48;
            const int MAX_AREA_LIGHTS = 16;

            uniform sampler2D uDepth;
            uniform mat4 uInverseViewProjection;
            uniform vec3 uCameraPosition;
            uniform float uColorStrength;
            uniform float uNeutralLift;
            uniform float uLuminanceBoostLimit;

            uniform int uPointCount;
            uniform vec4 uPointPositionRadius[MAX_POINT_LIGHTS];
            uniform vec4 uPointColor[MAX_POINT_LIGHTS];

            uniform int uAreaCount;
            uniform vec4 uAreaPositionDistance[MAX_AREA_LIGHTS];
            uniform vec4 uAreaDirectionAngle[MAX_AREA_LIGHTS];
            uniform vec4 uAreaColor[MAX_AREA_LIGHTS];

            in vec2 vTexCoord;
            out vec4 fragColor;

            vec3 reconstructWorldPosition(float depth) {
                vec4 clip = vec4(vTexCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
                vec4 world = uInverseViewProjection * clip;
                return world.xyz / world.w + uCameraPosition;
            }

            void main() {
                float depth = texture(uDepth, vTexCoord).r;
                if (depth >= 0.999999) {
                    discard;
                }

                vec3 worldPosition = reconstructWorldPosition(depth);
                vec3 lightSum = vec3(0.0);

                for (int i = 0; i < MAX_POINT_LIGHTS; i++) {
                    if (i >= uPointCount) {
                        break;
                    }
                    vec3 delta = uPointPositionRadius[i].xyz - worldPosition;
                    float radius = uPointPositionRadius[i].w;
                    float distanceToLight = length(delta);
                    float falloff = max(1.0 - distanceToLight / radius, 0.0);
                    falloff *= falloff;
                    lightSum += uPointColor[i].rgb * falloff;
                }

                for (int i = 0; i < MAX_AREA_LIGHTS; i++) {
                    if (i >= uAreaCount) {
                        break;
                    }
                    vec3 fromLight = worldPosition - uAreaPositionDistance[i].xyz;
                    float distanceToLight = length(fromLight);
                    float distanceLimit = uAreaPositionDistance[i].w;
                    vec3 direction = normalize(fromLight);
                    float cone = dot(direction, normalize(uAreaDirectionAngle[i].xyz));
                    float edge = uAreaDirectionAngle[i].w;
                    float coneFalloff = smoothstep(edge, min(edge + 0.08, 1.0), cone);
                    float distanceFalloff = max(1.0 - distanceToLight / distanceLimit, 0.0);
                    lightSum += uAreaColor[i].rgb * coneFalloff * distanceFalloff * distanceFalloff;
                }

                float peak = max(lightSum.r, max(lightSum.g, lightSum.b));
                vec3 hue = peak > 0.0001 ? lightSum / peak : vec3(0.0);
                float perceivedLuminance = dot(hue, vec3(0.2126, 0.7152, 0.0722));
                if (perceivedLuminance > 0.0001) {
                    hue *= min(0.7152 / perceivedLuminance, uLuminanceBoostLimit);
                }
                float intensity = 1.0 - exp(-peak);
                vec3 coloredLift = hue * intensity * uColorStrength;
                vec3 neutralLift = vec3(intensity * uNeutralLift);
                fragColor = vec4(coloredLift + neutralLift, 0.0);
            }
            """;
}
