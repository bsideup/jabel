package com.github.bsideup.jabel;

import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Source.Feature;
import net.bytebuddy.asm.Advice;

class AllowedInSourceAdvice {

    @Advice.OnMethodEnter
    static void allowedInSource(
            @Advice.This Feature feature,
            @Advice.Argument(value = 0, readOnly = false) Source source
    ) {
        String featureName = feature.name();
        if (false
                || featureName.equals("PRIVATE_SAFE_VARARGS")
                || featureName.equals("SWITCH_EXPRESSION")
                || featureName.equals("SWITCH_RULE")
                || featureName.equals("SWITCH_MULTIPLE_CASE_LABELS")
                || featureName.equals("LOCAL_VARIABLE_TYPE_INFERENCE")
                || featureName.equals("VAR_SYNTAX_IMPLICIT_LAMBDAS")
                || featureName.equals("DIAMOND_WITH_ANONYMOUS_CLASS_CREATION")
                || featureName.equals("EFFECTIVELY_FINAL_VARIABLES_IN_TRY_WITH_RESOURCES")
                || featureName.equals("TEXT_BLOCKS")
                || featureName.equals("PATTERN_MATCHING_IN_INSTANCEOF")
                || featureName.equals("REIFIABLE_TYPES_INSTANCEOF")
        ) {
            //noinspection UnusedAssignment
            source = Source.DEFAULT;
        }
    }
}
