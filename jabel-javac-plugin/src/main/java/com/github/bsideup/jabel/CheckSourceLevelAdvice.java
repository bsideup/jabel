package com.github.bsideup.jabel;

import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Source.Feature;
import net.bytebuddy.asm.Advice;

public class CheckSourceLevelAdvice {

    @Advice.OnMethodEnter
    public static void checkSourceLevelOnExit(
            @Advice.Argument(value = 1, readOnly = false) Feature feature
    ) {
        if (feature.allowedInSource(Source.JDK8)) {
            //noinspection UnusedAssignment
            feature = Source.Feature.LAMBDA;
        }
    }
}
