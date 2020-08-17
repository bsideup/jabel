package com.github.bsideup.jabel;

import com.sun.tools.javac.code.Preview;
import com.sun.tools.javac.code.Source;
import net.bytebuddy.asm.Advice;

public class IsPreviewAdvice {

    static final boolean debug = false;

    /**
     * Force javac to think that no features are preview features - on {@link Preview#isPreview} exit, override the
     * return value to always be false.
     */
    @Advice.OnMethodExit
    static public boolean isPreview(Source.Feature feature) {
        if (debug) {
            /**
             * Can't use {@link org.apache.maven.plugin.logging.Log} from instrumented javac classes.
             */
            System.out.println("Feature being set to NOT preview: " + feature);
        }
        return false;
    }
}
