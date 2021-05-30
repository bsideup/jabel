package com.github.bsideup.jabel;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.JavacParser;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class JabelCompilerPlugin implements Plugin {

    @Override
    public void init(JavacTask task, String... args) {
        Instrumentation instrumentation = ByteBuddyAgent.install();

        JavaModule jabelModule = JavaModule.ofType(JabelCompilerPlugin.class);
        JavaModule.ofType(JavacTask.class).modify(
                instrumentation,
                Collections.emptySet(),
                new HashMap<String, Set<JavaModule>>() {{
                    put("com.sun.tools.javac.code", Collections.singleton(jabelModule));
                    put("com.sun.tools.javac.parser", Collections.singleton(jabelModule));
                }},
                Collections.singletonMap(
                        "com.sun.tools.javac.code", Collections.singleton(jabelModule)
                ),
                Collections.emptySet(),
                Collections.emptyMap()
        );

        Set<Source.Feature> enabledFeatures = Stream
                .of(
                        "PRIVATE_SAFE_VARARGS",

                        "SWITCH_EXPRESSION",
                        "SWITCH_RULE",
                        "SWITCH_MULTIPLE_CASE_LABELS",

                        "LOCAL_VARIABLE_TYPE_INFERENCE",
                        "VAR_SYNTAX_IMPLICIT_LAMBDAS",

                        "DIAMOND_WITH_ANONYMOUS_CLASS_CREATION",

                        "EFFECTIVELY_FINAL_VARIABLES_IN_TRY_WITH_RESOURCES",

                        "TEXT_BLOCKS",

                        "PATTERN_MATCHING_IN_INSTANCEOF",
                        "REIFIABLE_TYPES_INSTANCEOF"
                )
                .map(name -> {
                    try {
                        return Source.Feature.valueOf(name);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        ByteBuddy byteBuddy = new ByteBuddy();

        for (Class<?> clazz : Arrays.asList(JavacParser.class, JavaTokenizer.class)) {
            byteBuddy
                    .redefine(clazz)
                    .visit(
                            Advice.to(CheckSourceLevelAdvice.class)
                                    .on(named("checkSourceLevel").and(takesArguments(2)))
                    )
                    .make()
                    .load(clazz.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
        }

        try {
            Field field = Source.Feature.class.getDeclaredField("minLevel");
            field.setAccessible(true);

            for (Source.Feature feature : enabledFeatures) {
                field.set(feature, Source.JDK8);
                if (!feature.allowedInSource(Source.JDK8)) {
                    throw new IllegalStateException(feature.name() + " minLevel instrumentation failed!");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        System.out.println(
                enabledFeatures.stream()
                        .map(Enum::name)
                        .collect(Collectors.joining(
                                "\n\t- ",
                                "Jabel: initialized. Enabled features: \n\t- ",
                                "\n"
                        ))
        );
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
