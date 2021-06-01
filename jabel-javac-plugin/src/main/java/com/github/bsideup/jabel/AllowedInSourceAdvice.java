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
        switch (feature.name()) {
            case "PRIVATE_SAFE_VARARGS":
            case "SWITCH_EXPRESSION":
            case "SWITCH_RULE":
            case "SWITCH_MULTIPLE_CASE_LABELS":
            case "LOCAL_VARIABLE_TYPE_INFERENCE":
            case "VAR_SYNTAX_IMPLICIT_LAMBDAS":
            case "DIAMOND_WITH_ANONYMOUS_CLASS_CREATION":
            case "EFFECTIVELY_FINAL_VARIABLES_IN_TRY_WITH_RESOURCES":
            case "TEXT_BLOCKS":
            case "PATTERN_MATCHING_IN_INSTANCEOF":
            case "REIFIABLE_TYPES_INSTANCEOF":
                //noinspection UnusedAssignment
                source = Source.DEFAULT;
                break;
        }
    }
}
