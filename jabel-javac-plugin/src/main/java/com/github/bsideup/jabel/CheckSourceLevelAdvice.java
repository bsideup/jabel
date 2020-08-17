package com.github.bsideup.jabel;

import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.code.Source.Feature;
import net.bytebuddy.asm.Advice;

public class CheckSourceLevelAdvice {

    /**
     * If the {@link com.sun.tools.javac.code.Source.Feature} is allowed in JDK8, make javac think it has the same
     * characteristics as {@link Source.Feature.LAMBDA}
     *
     * @param feature
     */
    @Advice.OnMethodEnter
    public static void checkSourceLevel(
            @Advice.Argument(value = 1, readOnly = false) Feature feature
    ) {
        if (feature.allowedInSource(Source.JDK8)) {
            //noinspection UnusedAssignment
            feature = Source.Feature.LAMBDA;
        }
    }
}
