package com.yesmenn.veilirislights.compat.veil;

/**
 * Tracks current render pass state so that {@link IrisVeilProgramLinker}
 * can select the correct Iris program (block vs shadow).
 *
 * <p>Set by {@code MixinShadowRenderer} at the boundaries of
 * {@code ShadowRenderer.renderShadows()}, read by shader creation code.
 */
public final class RenderStateManager {
    private static boolean renderingShadow;

    private RenderStateManager() {
    }

    public static boolean isRenderingShadow() {
        return renderingShadow;
    }

    public static void setRenderingShadow(boolean renderingShadow) {
        RenderStateManager.renderingShadow = renderingShadow;
    }
}
