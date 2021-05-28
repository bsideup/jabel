package com.github.bsideup.jabel;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class JabelCompilerPlugin implements Plugin {

    @Override
    public void init(JavacTask task, String... args) {
        Instrumentation instrumentation = ByteBuddyAgent.install();

        JavaModule jabelModule = JavaModule.ofType(Jabel.class);
        HashMap<String, Set<JavaModule>> extraExports = new HashMap<>();
        extraExports.put("com.sun.tools.javac.code", Collections.singleton(jabelModule));
        extraExports.put("com.sun.tools.javac.parser", Collections.singleton(jabelModule));
        Map<String, Set<JavaModule>> extraOpens = Collections.singletonMap(
                "com.sun.tools.javac.code", Collections.singleton(jabelModule)
        );
        JavaModule.ofType(JavacTask.class)
                .modify(
                        instrumentation,
                        Collections.emptySet(),
                        extraExports,
                        extraOpens,
                        Collections.emptySet(),
                        Collections.emptyMap()
                );

        Jabel.init();
    }

    @Override
    public String getName() {
        return "jabel";
    }

    // Make it auto start on Java 14+
    public boolean autoStart() {
        return true;
    }
}
