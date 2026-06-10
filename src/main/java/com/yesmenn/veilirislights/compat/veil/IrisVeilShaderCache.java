package com.yesmenn.veilirislights.compat.veil;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.ShaderManager;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import com.yesmenn.veilirislights.VeilIrisLights;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe cache mapping Veil shader paths to Iris {@link ShaderInstance} objects.
 *
 * <p>Entries are discarded whenever Iris rebuilds its pipeline. Iris owns the
 * generated shader instances and releases their OpenGL resources with the old
 * pipeline.
 */
public final class IrisVeilShaderCache {

    private static final ConcurrentMap<String, ShaderInstance> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, IrisVeilProgramLinker.Params> PARAM_CACHE = new ConcurrentHashMap<>();
    /** Cache of Veil-processed vertex shader source (populated by MixinDirectShaderCompiler). */
    private static final ConcurrentMap<ResourceLocation, String> PROCESSED_VERTEX_SOURCES = new ConcurrentHashMap<>();
    /** Cache of Veil-processed fragment shader source (populated by MixinDirectShaderCompiler). */
    private static final ConcurrentMap<ResourceLocation, String> PROCESSED_FRAGMENT_SOURCES = new ConcurrentHashMap<>();
    private static final IrisVeilProgramLinker LINKER = new IrisVeilProgramLinker();

    /** Monotonically increasing counter; incremented on shaderpack reload. */
    private static volatile int shaderPackGeneration;

    private IrisVeilShaderCache() {
    }

    /**
     * Returns the current shaderpack generation number.
     * Used by the Veil shader-state mixin to detect stale local references.
     */
    public static int getShaderPackGeneration() {
        return shaderPackGeneration;
    }

    /**
     * Gets or creates an Iris {@link ShaderInstance} for the given Veil shader path.
     *
     * <p>When the Iris shadow pass is active (tracked by {@link RenderStateManager}),
     * automatically uses {@link ProgramId#Shadow} so that Veil shaders write to the
     * shadow map rather than the gbuffer.
     *
     * @param shaderPath the Veil shader resource location (e.g. {@code simulated:spring/spring})
     * @return a new or cached Iris ShaderInstance, or {@code null} if creation failed
     */
    public static ShaderInstance getOrCreate(ResourceLocation shaderPath) {
        // Check if the shader is even available (lazy Veil shader compilation)
        ShaderProgram veilProgram = getVeilProgram(shaderPath);
        if (veilProgram == null || !veilProgram.isValid()) {
            return null;
        }

        // During the shadow pass, override to ProgramId.Shadow so the shader
        // writes to the shadow map FBO rather than the gbuffer.
        if (RenderStateManager.isRenderingShadow()) {
            return getOrCreate(shaderPath, ProgramId.Shadow, false);
        }

        String paramsKey = shaderPackFingerprint() + ":" + shaderPath + ":params";
        IrisVeilProgramLinker.Params params = PARAM_CACHE.get(paramsKey);
        if (params == null) {
            params = LINKER.determineParams(veilProgram);
            if (params == null) {
                return null;
            }
            PARAM_CACHE.put(paramsKey, params);
        }

        return getOrCreate(shaderPath, params.programId(), params.useDithering());
    }

    /**
     * Gets or creates an Iris {@link ShaderInstance} for the given Veil shader path
     * with the specified program parameters.
     *
     * @param shaderPath    the Veil shader resource location
     * @param programId     the Iris program ID to use
     * @param useDithering  whether dithering is enabled
     * @return a new or cached Iris ShaderInstance, or {@code null} if creation failed
     */
    public static ShaderInstance getOrCreate(ResourceLocation shaderPath, ProgramId programId, boolean useDithering) {
        // Check if the shader is even available (lazy Veil shader compilation)
        ShaderProgram veilProgram = getVeilProgram(shaderPath);
        if (veilProgram == null || !veilProgram.isValid()) {
            return null;
        }

        String key = shaderPackFingerprint() + ":" + shaderPath + ":" + programId + ":" + (useDithering ? "dither" : "nodither");
        return CACHE.computeIfAbsent(key, k -> {
            ShaderInstance created = LINKER.create(shaderPath, veilProgram, programId, useDithering);
            if (created != null) {
                VeilIrisLights.LOGGER.debug("IrisVeilShaderCache: cached Iris shader for '{}'", shaderPath);
            }
            return created;
        });
    }

    /**
     * Invalidates all cached ShaderInstances. Called on Iris shaderpack reload.
     */
    public static void onShaderPackReload() {
        CACHE.clear();
        shaderPackGeneration++;
        PARAM_CACHE.clear();
        VeilIrisLights.LOGGER.debug("IrisVeilShaderCache: shaderpack reloaded (gen {})", shaderPackGeneration);
    }

    /**
     * Stores a processed Veil vertex shader source, captured by {@code MixinDirectShaderCompiler}
     * during Veil's internal shader compilation. This is the fully-processed GLSL with
     * all {@code #include} directives resolved and all Veil preprocessor transformations applied.
     *
     * @param shaderId   the Veil vertex shader logical ID (e.g. {@code aeronautics:levitite/levitite})
     * @param sourceCode the fully-processed GLSL source code
     */
    public static void storeProcessedVertexSource(ResourceLocation shaderId, String sourceCode) {
        PROCESSED_VERTEX_SOURCES.put(shaderId, sourceCode);
    }

    public static String getProcessedVertexSource(ResourceLocation shaderId) {
        return PROCESSED_VERTEX_SOURCES.get(shaderId);
    }

    public static void storeProcessedFragmentSource(ResourceLocation shaderId, String sourceCode) {
        PROCESSED_FRAGMENT_SOURCES.put(shaderId, sourceCode);
    }

    public static String getProcessedFragmentSource(ResourceLocation shaderId) {
        return PROCESSED_FRAGMENT_SOURCES.get(shaderId);
    }

    /**
     * Clears all cached entries and resets the generation counter.
     */
    public static void clear() {
        CACHE.clear();
        PARAM_CACHE.clear();
        PROCESSED_VERTEX_SOURCES.clear();
        PROCESSED_FRAGMENT_SOURCES.clear();
        shaderPackGeneration = 0;
    }

    private static ShaderProgram getVeilProgram(ResourceLocation shaderPath) {
        try {
            ShaderManager sm = VeilRenderSystem.renderer().getShaderManager();
            return sm.getShader(shaderPath);
        } catch (Exception e) {
            VeilIrisLights.LOGGER.warn("IrisVeilShaderCache: cannot get Veil program for '{}'", shaderPath, e);
            return null;
        }
    }

    private static String shaderPackFingerprint() {
        return "gen" + shaderPackGeneration;
    }
}
