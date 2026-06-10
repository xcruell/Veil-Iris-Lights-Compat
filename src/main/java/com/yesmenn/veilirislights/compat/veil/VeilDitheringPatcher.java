package com.yesmenn.veilirislights.compat.veil;

import io.github.douira.glsl_transformer.ast.node.external_declaration.FunctionDefinition;
import io.github.douira.glsl_transformer.ast.query.RootSupplier;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.JobParameters;
import io.github.douira.glsl_transformer.ast.transform.SingleASTTransformer;
import com.yesmenn.veilirislights.VeilIrisLights;

/**
 * Adds screen-door dithering to translucent Veil fragments when a shaderpack
 * has no compatible translucent program.
 */
final class VeilDitheringPatcher {

    private VeilDitheringPatcher() {}

    public static String applyFragmentDithering(String fragmentSource) {
        return FragmentPatcher.patch(fragmentSource, "_veil_fragColor.a");
    }

    private static final class ParseParams implements JobParameters {}

    private static final class FragmentPatcher {

        private FragmentPatcher() {}

        private static final String IGN_FUNC = """
            float _veil_noise(vec2 p) {
                return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)) + frameTimeCounter));
            }""";

        private static String patch(String fragmentSource, String alphaSource) {
            try {
                final SingleASTTransformer<ParseParams> t;
                t = new SingleASTTransformer<>() {{
                    setRootSupplier(RootSupplier.PREFIX_UNORDERED_ED_EXACT);
                }};
                t.setTransformation((tree, root, params) -> {
                    var mainDef = tree.getOneMainDefinitionBody().getParent();
                    if (root.identifierIndex.get("frameTimeCounter").isEmpty()) {
                        tree.parseAndInjectNode(t, ASTInjectionPoint.BEFORE_DECLARATIONS,
                                "uniform float frameTimeCounter;");
                    }
                    if (mainDef instanceof FunctionDefinition) {
                        int mainIdx = tree.getChildren().indexOf(mainDef);
                        tree.getChildren().add(mainIdx,
                                t.parseExternalDeclaration(root, IGN_FUNC));
                        }
                    tree.appendMainFunctionBody(t,
                            "if (" + alphaSource + " <= _veil_noise(gl_FragCoord.xy)) discard;");
                });
                return t.transform(fragmentSource, new ParseParams());
            } catch (Exception e) {
                VeilIrisLights.LOGGER.warn("Veil dithering patch failed", e);
                return fragmentSource;
            }
        }
    }
}
