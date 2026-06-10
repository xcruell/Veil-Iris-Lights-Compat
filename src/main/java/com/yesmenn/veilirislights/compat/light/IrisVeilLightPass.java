package com.yesmenn.veilirislights.compat.light;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.systems.RenderSystem;
import foundry.veil.api.client.color.Colorc;
import foundry.veil.api.client.registry.LightTypeRegistry;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.impl.client.render.light.VoxelShadowGrid;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.uniforms.CameraUniforms;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import com.yesmenn.veilirislights.VeilIrisLights;
import com.yesmenn.veilirislights.compat.veil.VoxelShadowGridState;
import com.yesmenn.veilirislights.config.LightRenderConfig;

import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.List;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_3D;
import static org.lwjgl.opengl.GL12C.GL_TEXTURE_BINDING_3D;
import static org.lwjgl.opengl.GL13C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL14C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;

public final class IrisVeilLightPass {
    private static final float SHADERPACK_LIGHT_GAIN = 3.0F;

    private static final int MAX_POINT_LIGHTS = 48;
    private static final int MAX_AREA_LIGHTS = 16;
    private static final float[] POINT_POSITIONS = new float[MAX_POINT_LIGHTS * 4];
    private static final float[] POINT_COLORS = new float[MAX_POINT_LIGHTS * 4];
    private static final float[] AREA_POSITIONS = new float[MAX_AREA_LIGHTS * 4];
    private static final float[] AREA_DIRECTIONS = new float[MAX_AREA_LIGHTS * 4];
    private static final float[] AREA_COLORS = new float[MAX_AREA_LIGHTS * 4];
    private static final int[] VIEWPORT = new int[4];

    private static int program;
    private static int framebuffer;
    private static int vertexArray;
    private static int attachedColorTexture;
    private static UniformLocations uniforms;
    private static boolean initializationFailed;
    private static boolean hookLogged;
    private static boolean targetsLogged;
    private static int lastPointCount = -1;
    private static int lastAreaCount = -1;
    private static boolean lastOcclusionEnabled;

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
            VeilIrisLights.LOGGER.debug("Veil Iris light pass is active");
        }
        if (pointHandles.size() != lastPointCount || areaHandles.size() != lastAreaCount) {
            lastPointCount = pointHandles.size();
            lastAreaCount = areaHandles.size();
            VeilIrisLights.LOGGER.debug(
                    "Veil Iris light pass sees {} point light(s) and {} area light(s)",
                    lastPointCount,
                    lastAreaCount);
        }
        if (pointHandles.isEmpty() && areaHandles.isEmpty()) {
            return;
        }

        LightRenderConfig config = LightRenderConfig.get();
        boolean hasOccludedLights = config.quality.voxelShadows()
                && hasOccludedLights(pointHandles, areaHandles);
        lastOcclusionEnabled = hasOccludedLights;
        if (hasOccludedLights) {
            VoxelShadowGrid.setup();
        }
        boolean hasUsableVoxelGrid = hasOccludedLights && VoxelShadowGridState.isReady();

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
            VeilIrisLights.LOGGER.debug(
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
        glActiveTexture(GL_TEXTURE0);
        int oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        glActiveTexture(GL_TEXTURE1);
        int oldVoxelTexture = glGetInteger(GL_TEXTURE_BINDING_3D);
        glActiveTexture(oldActiveTexture);
        boolean oldBlend = glIsEnabled(GL_BLEND);
        boolean oldDepthTest = glIsEnabled(GL_DEPTH_TEST);
        boolean oldCull = glIsEnabled(GL_CULL_FACE);
        int oldBlendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
        int oldBlendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        int oldBlendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        int oldBlendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        int oldBlendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        int oldBlendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        glGetIntegerv(GL_VIEWPORT, VIEWPORT);

        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        if (attachedColorTexture != colorTexture) {
            glFramebufferTexture2D(
                    GL_DRAW_FRAMEBUFFER,
                    GL_COLOR_ATTACHMENT0,
                    GL_TEXTURE_2D,
                    colorTexture,
                    0);
            attachedColorTexture = colorTexture;
            if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                VeilIrisLights.LOGGER.error("Veil Iris light framebuffer is incomplete");
                restoreState(oldFramebuffer, oldProgram, oldVertexArray, oldActiveTexture, oldTexture,
                        oldVoxelTexture,
                        oldBlend, oldDepthTest, oldCull, oldBlendEquationRgb, oldBlendEquationAlpha,
                        oldBlendSrcRgb, oldBlendDstRgb, oldBlendSrcAlpha, oldBlendDstAlpha, VIEWPORT);
                return;
            }
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
        glUniform1i(uniforms.depth(), 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_3D, hasUsableVoxelGrid ? VoxelShadowGrid.getTextureId() : 0);
        glUniform1i(uniforms.blockGrid(), 1);
        Vector3f gridOrigin = new Vector3f(VoxelShadowGrid.getUniformGridPos());
        glUniform3f(uniforms.gridOrigin(),
                gridOrigin.x, gridOrigin.y, gridOrigin.z);
        glUniform1i(uniforms.hasBlockGrid(), hasUsableVoxelGrid ? 1 : 0);
        glUniform1i(uniforms.detailedNormals(), config.quality.detailedNormals() ? 1 : 0);

        Matrix4f inverseViewProjection = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferProjection())
                .mul(CapturedRenderingState.INSTANCE.getGbufferModelView())
                .invert();
        Vector3d cameraPosition = CameraUniforms.getUnshiftedCameraPosition();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrix = stack.mallocFloat(16);
            inverseViewProjection.get(matrix);
            glUniformMatrix4fv(uniforms.inverseViewProjection(), false, matrix);
        }
        glUniform3f(uniforms.cameraPosition(),
                (float) cameraPosition.x, (float) cameraPosition.y, (float) cameraPosition.z);
        glUniform2f(uniforms.inverseViewSize(),
                1.0F / renderTargets.getCurrentWidth(),
                1.0F / renderTargets.getCurrentHeight());
        glUniform1f(uniforms.colorStrength(), (float) config.colorStrength);
        glUniform1f(uniforms.colorSaturation(), (float) config.colorSaturation);
        glUniform1f(uniforms.neutralLift(), (float) config.neutralLift);
        glUniform1f(uniforms.luminanceBoostLimit(), (float) config.luminanceBoostLimit);

        uploadPointLights(pointHandles, (float) config.exposure);
        uploadAreaLights(areaHandles, (float) config.exposure);

        glBindVertexArray(vertexArray);
        glDrawArrays(GL_TRIANGLES, 0, 3);

        restoreState(oldFramebuffer, oldProgram, oldVertexArray, oldActiveTexture, oldTexture,
                oldVoxelTexture,
                oldBlend, oldDepthTest, oldCull, oldBlendEquationRgb, oldBlendEquationAlpha,
                oldBlendSrcRgb, oldBlendDstRgb, oldBlendSrcAlpha, oldBlendDstAlpha, VIEWPORT);
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
        attachedColorTexture = 0;
        uniforms = null;
        initializationFailed = false;
        hookLogged = false;
        targetsLogged = false;
        lastPointCount = -1;
        lastAreaCount = -1;
        lastOcclusionEnabled = false;
    }

    public static void appendDebugInfo(List<String> lines) {
        LightRenderConfig config = LightRenderConfig.get();
        lines.add("");
        lines.add("[Veil Iris Lights] " + (hookLogged ? "active" : "waiting"));
        lines.add("Point lights: " + Math.max(lastPointCount, 0) + "/" + MAX_POINT_LIGHTS
                + ", area lights: " + Math.max(lastAreaCount, 0) + "/" + MAX_AREA_LIGHTS);
        lines.add("Quality: " + config.quality.name().toLowerCase()
                + ", voxel occlusion: " + (lastOcclusionEnabled ? "active" : "inactive"));
        lines.add(String.format(
                "Exposure: %.2f, color: %.2f, saturation: %.2f",
                config.exposure,
                config.colorStrength,
                config.colorSaturation));
        lines.add(String.format(
                "Neutral: %.2f, luminance: %.2f",
                config.neutralLift,
                config.luminanceBoostLimit));
    }

    private static void uploadPointLights(Collection<? extends LightRenderHandle<PointLightData>> handles,
                                          float exposure) {
        int count = 0;
        for (LightRenderHandle<PointLightData> handle : handles) {
            if (!handle.isValid() || count >= MAX_POINT_LIGHTS) {
                continue;
            }
            PointLightData light = handle.getLightData();
            Vector3dc position = light.getPosition();
            Colorc color = light.getColor();
            int index = count * 4;
            POINT_POSITIONS[index] = (float) position.x();
            POINT_POSITIONS[index + 1] = (float) position.y();
            POINT_POSITIONS[index + 2] = (float) position.z();
            POINT_POSITIONS[index + 3] = light.getRadius();
            POINT_COLORS[index] = color.red() * light.getBrightness() * exposure * SHADERPACK_LIGHT_GAIN;
            POINT_COLORS[index + 1] = color.green() * light.getBrightness() * exposure * SHADERPACK_LIGHT_GAIN;
            POINT_COLORS[index + 2] = color.blue() * light.getBrightness() * exposure * SHADERPACK_LIGHT_GAIN;
            POINT_COLORS[index + 3] = light.isOcclusionEnabled() ? 1.0F : 0.0F;
            count++;
        }
        glUniform1i(uniforms.pointCount(), count);
        if (count > 0) {
            glUniform4fv(uniforms.pointPositionRadius(), POINT_POSITIONS);
            glUniform4fv(uniforms.pointColor(), POINT_COLORS);
        }
    }

    private static void uploadAreaLights(Collection<? extends LightRenderHandle<AreaLightData>> handles,
                                         float exposure) {
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
            AREA_POSITIONS[index] = (float) position.x;
            AREA_POSITIONS[index + 1] = (float) position.y;
            AREA_POSITIONS[index + 2] = (float) position.z;
            AREA_POSITIONS[index + 3] = light.getDistance();
            AREA_DIRECTIONS[index] = direction.x;
            AREA_DIRECTIONS[index + 1] = direction.y;
            AREA_DIRECTIONS[index + 2] = direction.z;
            AREA_DIRECTIONS[index + 3] = (float) Math.cos(light.getAngle());
            AREA_COLORS[index] = color.red() * light.getBrightness() * exposure * SHADERPACK_LIGHT_GAIN;
            AREA_COLORS[index + 1] = color.green() * light.getBrightness() * exposure * SHADERPACK_LIGHT_GAIN;
            AREA_COLORS[index + 2] = color.blue() * light.getBrightness() * exposure * SHADERPACK_LIGHT_GAIN;
            AREA_COLORS[index + 3] = light.isOcclusionEnabled() ? 1.0F : 0.0F;
            count++;
        }
        glUniform1i(uniforms.areaCount(), count);
        if (count > 0) {
            glUniform4fv(uniforms.areaPositionDistance(), AREA_POSITIONS);
            glUniform4fv(uniforms.areaDirectionAngle(), AREA_DIRECTIONS);
            glUniform4fv(uniforms.areaColor(), AREA_COLORS);
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
        uniforms = UniformLocations.create(program);
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
                                     int activeTexture, int texture, int voxelTexture, boolean blend,
                                     boolean depthTest, boolean cull,
                                     int blendEquationRgb, int blendEquationAlpha,
                                     int blendSrcRgb, int blendDstRgb,
                                     int blendSrcAlpha, int blendDstAlpha,
                                     int[] viewport) {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebufferId);
        glUseProgram(programId);
        glBindVertexArray(vertexArrayId);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_3D, voxelTexture);
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

    private static boolean hasOccludedLights(
            Collection<? extends LightRenderHandle<PointLightData>> pointHandles,
            Collection<? extends LightRenderHandle<AreaLightData>> areaHandles) {
        for (LightRenderHandle<PointLightData> handle : pointHandles) {
            if (handle.isValid() && handle.getLightData().isOcclusionEnabled()) {
                return true;
            }
        }
        for (LightRenderHandle<AreaLightData> handle : areaHandles) {
            if (handle.isValid() && handle.getLightData().isOcclusionEnabled()) {
                return true;
            }
        }
        return false;
    }

    private record UniformLocations(
            int depth,
            int blockGrid,
            int inverseViewProjection,
            int cameraPosition,
            int inverseViewSize,
            int gridOrigin,
            int hasBlockGrid,
            int detailedNormals,
            int colorStrength,
            int colorSaturation,
            int neutralLift,
            int luminanceBoostLimit,
            int pointCount,
            int pointPositionRadius,
            int pointColor,
            int areaCount,
            int areaPositionDistance,
            int areaDirectionAngle,
            int areaColor) {

        private static UniformLocations create(int program) {
            return new UniformLocations(
                    glGetUniformLocation(program, "uDepth"),
                    glGetUniformLocation(program, "uBlockGrid"),
                    glGetUniformLocation(program, "uInverseViewProjection"),
                    glGetUniformLocation(program, "uCameraPosition"),
                    glGetUniformLocation(program, "uInverseViewSize"),
                    glGetUniformLocation(program, "uGridOrigin"),
                    glGetUniformLocation(program, "uHasBlockGrid"),
                    glGetUniformLocation(program, "uDetailedNormals"),
                    glGetUniformLocation(program, "uColorStrength"),
                    glGetUniformLocation(program, "uColorSaturation"),
                    glGetUniformLocation(program, "uNeutralLift"),
                    glGetUniformLocation(program, "uLuminanceBoostLimit"),
                    glGetUniformLocation(program, "uPointCount"),
                    glGetUniformLocation(program, "uPointPositionRadius"),
                    glGetUniformLocation(program, "uPointColor"),
                    glGetUniformLocation(program, "uAreaCount"),
                    glGetUniformLocation(program, "uAreaPositionDistance"),
                    glGetUniformLocation(program, "uAreaDirectionAngle"),
                    glGetUniformLocation(program, "uAreaColor"));
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
            uniform sampler3D uBlockGrid;
            uniform mat4 uInverseViewProjection;
            uniform vec3 uCameraPosition;
            uniform vec2 uInverseViewSize;
            uniform vec3 uGridOrigin;
            uniform bool uHasBlockGrid;
            uniform bool uDetailedNormals;
            uniform float uColorStrength;
            uniform float uColorSaturation;
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

            vec3 reconstructWorldPosition(vec2 texCoord, float depth) {
                vec4 clip = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
                vec4 world = uInverseViewProjection * clip;
                return world.xyz / world.w + uCameraPosition;
            }

            vec3 surfaceNormal(vec3 worldPosition) {
                vec3 dx;
                vec3 dy;
                if (uDetailedNormals) {
                    vec2 horizontalOffset = vec2(uInverseViewSize.x, 0.0);
                    vec2 verticalOffset = vec2(0.0, uInverseViewSize.y);
                    vec3 left = reconstructWorldPosition(
                        vTexCoord - horizontalOffset,
                        texture(uDepth, vTexCoord - horizontalOffset).r) - worldPosition;
                    vec3 right = reconstructWorldPosition(
                        vTexCoord + horizontalOffset,
                        texture(uDepth, vTexCoord + horizontalOffset).r) - worldPosition;
                    vec3 down = reconstructWorldPosition(
                        vTexCoord - verticalOffset,
                        texture(uDepth, vTexCoord - verticalOffset).r) - worldPosition;
                    vec3 up = reconstructWorldPosition(
                        vTexCoord + verticalOffset,
                        texture(uDepth, vTexCoord + verticalOffset).r) - worldPosition;
                    dx = dot(left, left) < dot(right, right) ? -left : right;
                    dy = dot(down, down) < dot(up, up) ? -down : up;
                } else {
                    dx = dFdx(worldPosition);
                    dy = dFdy(worldPosition);
                }

                vec3 normal = normalize(cross(dx, dy));
                if (length(dx) > 4.0 || length(dy) > 4.0) {
                    normal = normalize(uCameraPosition - worldPosition);
                }
                if (dot(normal, uCameraPosition - worldPosition) < 0.0) {
                    normal = -normal;
                }
                vec3 absoluteNormal = abs(normal);
                float dominantAxis = max(absoluteNormal.x, max(absoluteNormal.y, absoluteNormal.z));
                if (dominantAxis > 0.92) {
                    normal = sign(normal) * step(vec3(dominantAxis - 0.0001), absoluteNormal);
                }
                return normal;
            }

            float surfaceResponse(vec3 normal, vec3 directionToLight) {
                float lambert = max(dot(normal, directionToLight), 0.0);
                return 0.15 * smoothstep(0.0, 0.08, lambert)
                    + 0.85 * sqrt(lambert);
            }

            vec3 normalizeLightColor(vec3 color) {
                float peak = max(color.r, max(color.g, color.b));
                if (peak <= 0.0001) {
                    return vec3(0.0);
                }
                vec3 hue = color / peak;
                float luminance = dot(hue, vec3(0.2126, 0.7152, 0.0722));
                float requestedBoost = luminance > 0.0001
                    ? max(0.7152 / luminance, 1.0)
                    : 1.0;
                float boostRange = max(uLuminanceBoostLimit - 1.0, 0.0);
                float boostDelta = requestedBoost - 1.0;
                float boost = boostRange > 0.0001
                    ? 1.0 + boostRange * boostDelta / (boostRange + boostDelta)
                    : 1.0;
                return hue * boost * peak;
            }

            vec3 applySaturation(vec3 color, float saturation) {
                float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
                vec3 chroma = color - vec3(luminance);
                if (saturation <= 1.0) {
                    return vec3(luminance) + chroma * saturation;
                }

                float minimumChroma = min(chroma.r, min(chroma.g, chroma.b));
                float maximumScale = minimumChroma < -0.000001
                    ? luminance / -minimumChroma
                    : saturation;
                float availableExtra = max(maximumScale - 1.0, 0.0);
                float requestedExtra = saturation - 1.0;
                float appliedExtra = availableExtra > 0.000001
                    ? requestedExtra / (1.0 + requestedExtra / availableExtra)
                    : 0.0;
                return vec3(luminance) + chroma * (1.0 + appliedExtra);
            }

            bool insideGrid(ivec3 cell) {
                ivec3 local = cell - ivec3(uGridOrigin);
                return all(greaterThanEqual(local, ivec3(0)))
                    && all(lessThan(local, ivec3(64)));
            }

            bool clipRayToGrid(inout vec3 start, vec3 direction, float rayLength) {
                vec3 gridMinimum = uGridOrigin;
                vec3 gridMaximum = uGridOrigin + vec3(64.0);
                vec3 safeDirection = vec3(
                    abs(direction.x) > 0.00001 ? direction.x : 0.00001,
                    abs(direction.y) > 0.00001 ? direction.y : 0.00001,
                    abs(direction.z) > 0.00001 ? direction.z : 0.00001
                );
                vec3 first = (gridMinimum - start) / safeDirection;
                vec3 second = (gridMaximum - start) / safeDirection;
                vec3 nearAxis = min(first, second);
                vec3 farAxis = max(first, second);
                float nearDistance = max(max(nearAxis.x, nearAxis.y), nearAxis.z);
                float farDistance = min(min(farAxis.x, farAxis.y), farAxis.z);
                if (farDistance < max(nearDistance, 0.0) || nearDistance >= rayLength) {
                    return false;
                }
                if (nearDistance > 0.0) {
                    start += direction * (nearDistance + 0.001);
                }
                return true;
            }

            float shadowVisibility(vec3 worldPosition, vec3 normal, vec3 lightPosition, bool occluded) {
                if (!occluded || !uHasBlockGrid) {
                    return 1.0;
                }

                vec3 start = worldPosition + normal * 0.08;
                vec3 ray = lightPosition - start;
                float rayLength = length(ray);
                if (rayLength <= 0.25) {
                    return 1.0;
                }

                vec3 direction = ray / rayLength;
                if (!insideGrid(ivec3(floor(start)))
                        && !clipRayToGrid(start, direction, rayLength)) {
                    return 1.0;
                }
                ray = lightPosition - start;
                rayLength = length(ray);
                if (rayLength <= 0.0001) {
                    return 1.0;
                }
                direction = ray / rayLength;
                ivec3 cell = ivec3(floor(start));
                ivec3 lightCell = ivec3(floor(lightPosition));
                ivec3 stepDirection = ivec3(
                    direction.x >= 0.0 ? 1 : -1,
                    direction.y >= 0.0 ? 1 : -1,
                    direction.z >= 0.0 ? 1 : -1
                );
                vec3 inverseDirection = 1.0 / max(abs(direction), vec3(0.00001));
                vec3 nextBoundary = vec3(cell) + vec3(
                    stepDirection.x > 0 ? 1.0 : 0.0,
                    stepDirection.y > 0 ? 1.0 : 0.0,
                    stepDirection.z > 0 ? 1.0 : 0.0
                );
                vec3 sideDistance = abs((nextBoundary - start) * inverseDirection);
                vec3 deltaDistance = inverseDirection;
                float visibility = 1.0;

                for (int step = 0; step < 64; step++) {
                    float traveled;
                    if (sideDistance.x <= sideDistance.y && sideDistance.x <= sideDistance.z) {
                        traveled = sideDistance.x;
                        sideDistance.x += deltaDistance.x;
                        cell.x += stepDirection.x;
                    } else if (sideDistance.y <= sideDistance.z) {
                        traveled = sideDistance.y;
                        sideDistance.y += deltaDistance.y;
                        cell.y += stepDirection.y;
                    } else {
                        traveled = sideDistance.z;
                        sideDistance.z += deltaDistance.z;
                        cell.z += stepDirection.z;
                    }

                    if (traveled >= rayLength - 0.2 || all(equal(cell, lightCell))) {
                        return visibility;
                    }
                    if (!insideGrid(cell)) {
                        return visibility;
                    }

                    ivec3 local = cell - ivec3(uGridOrigin);
                    float occupancy = texelFetch(uBlockGrid, local, 0).r;
                    if (occupancy >= 0.99) {
                        return 0.0;
                    }
                    visibility *= 1.0 - occupancy * 0.75;
                    if (visibility <= 0.04) {
                        return 0.0;
                    }
                }
                return visibility;
            }

            void main() {
                float depth = texture(uDepth, vTexCoord).r;
                if (depth >= 0.999999) {
                    discard;
                }

                vec3 worldPosition = reconstructWorldPosition(vTexCoord, depth);
                vec3 normal = surfaceNormal(worldPosition);
                vec3 lightSum = vec3(0.0);

                for (int i = 0; i < MAX_POINT_LIGHTS; i++) {
                    if (i >= uPointCount) {
                        break;
                    }
                    vec3 delta = uPointPositionRadius[i].xyz - worldPosition;
                    float radius = uPointPositionRadius[i].w;
                    float distanceToLight = length(delta);
                    if (distanceToLight <= 0.0001) {
                        continue;
                    }
                    float falloff = max(1.0 - distanceToLight / radius, 0.0);
                    falloff *= falloff;
                    if (falloff <= 0.0) {
                        continue;
                    }
                    vec3 directionToLight = delta / distanceToLight;
                    float surface = surfaceResponse(normal, directionToLight);
                    if (surface <= 0.0) {
                        continue;
                    }
                    float visibility = shadowVisibility(
                        worldPosition,
                        normal,
                        uPointPositionRadius[i].xyz,
                        uPointColor[i].a > 0.5
                    );
                    lightSum += normalizeLightColor(uPointColor[i].rgb)
                        * falloff * surface * visibility;
                }

                for (int i = 0; i < MAX_AREA_LIGHTS; i++) {
                    if (i >= uAreaCount) {
                        break;
                    }
                    vec3 fromLight = worldPosition - uAreaPositionDistance[i].xyz;
                    float distanceToLight = length(fromLight);
                    float distanceLimit = uAreaPositionDistance[i].w;
                    if (distanceToLight <= 0.0001) {
                        continue;
                    }
                    vec3 direction = normalize(fromLight);
                    float cone = dot(direction, normalize(uAreaDirectionAngle[i].xyz));
                    float edge = uAreaDirectionAngle[i].w;
                    float coneFalloff = smoothstep(edge, min(edge + 0.08, 1.0), cone);
                    float distanceFalloff = max(1.0 - distanceToLight / distanceLimit, 0.0);
                    if (coneFalloff <= 0.0 || distanceFalloff <= 0.0) {
                        continue;
                    }
                    vec3 directionToLight = -direction;
                    float surface = surfaceResponse(normal, directionToLight);
                    if (surface <= 0.0) {
                        continue;
                    }
                    float visibility = shadowVisibility(
                        worldPosition,
                        normal,
                        uAreaPositionDistance[i].xyz,
                        uAreaColor[i].a > 0.5
                    );
                    lightSum += normalizeLightColor(uAreaColor[i].rgb)
                        * coneFalloff * distanceFalloff * distanceFalloff
                        * surface * visibility;
                }

                float energy = length(lightSum) * 0.57735026919;
                float intensity = 1.0 - exp(-energy);
                vec3 compressedLight = energy > 0.0001
                    ? lightSum * (intensity / energy)
                    : vec3(0.0);
                vec3 rawColoredLift = compressedLight * uColorStrength;
                float rawLuminance = dot(rawColoredLift, vec3(0.2126, 0.7152, 0.0722));
                vec3 coloredLift = rawColoredLift / (1.0 + rawLuminance * 0.5);
                coloredLift = applySaturation(coloredLift, uColorSaturation);
                vec3 neutralLift = vec3(1.0 - exp(-intensity * uNeutralLift));
                fragColor = vec4(coloredLift + neutralLift, 0.0);
            }
            """;
}
