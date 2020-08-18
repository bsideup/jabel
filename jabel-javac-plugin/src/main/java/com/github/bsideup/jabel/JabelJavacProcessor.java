package com.github.bsideup.jabel;

import com.sun.tools.javac.code.Preview;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.JavacParser;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper.ForDeclaredMethods;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;

import javax.annotation.processing.Completion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class JabelJavacProcessor implements Processor {

    private static final Set<Source.Feature> ENABLED_FEATURES = Stream
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

    static {
        // log that we've started, otherwise if there's certain types of errors, no output is seen at all from Jabel (e.g. errors compiling our code)
        // helps for verifying Jabel is being picked up correctly from project settings
        logInfo("Jabel static initialising ByteBuddy");

        ByteBuddyAgent.install();
        ByteBuddy byteBuddy = new ByteBuddy();

        sourceLevelCheck(byteBuddy);

//        previewFeatureChecks(byteBuddy);

        featureMinLevelChecks();

        logInfo("Jabel ByteBuddy initialisation complete");
    }

    /**
     * For all the features in {@link #ENABLED_FEATURES}, override the min JDK level, reducing it to 8
     *
     * @see #ENABLED_FEATURES
     */
    private static void featureMinLevelChecks() {
        logInfo("Disabling feature min level checks");
        try {
            Field field = Source.Feature.class.getDeclaredField("minLevel");
            field.setAccessible(true);

            for (Source.Feature feature : ENABLED_FEATURES) {
                field.set(feature, Source.JDK8);
                if (!feature.allowedInSource(Source.JDK8)) {
                    throw new IllegalStateException(feature.name() + " minLevel instrumentation failed!");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Intercept calls to {@link JavacParser#checkSourceLevel}
     *
     * @see JavacParser#checkSourceLevel
     * @see JavaTokenizer#checkSourceLevel
     * @see CheckSourceLevelAdvice
     */
    private static void sourceLevelCheck(ByteBuddy byteBuddy) {
        logInfo("Disabling source level check");
        for (Class<?> clazz : Arrays.asList(JavacParser.class, JavaTokenizer.class)) {
            ForDeclaredMethods checkSourceLevelMethod = Advice.to(CheckSourceLevelAdvice.class)
                    .on(named("checkSourceLevel").and(takesArguments(2)));
            byteBuddy
                    .redefine(clazz)
                    .visit(checkSourceLevelMethod)
                    .make()
                    .load(clazz.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
        }
    }

    /**
     * Intercept call return from {@link Preview#isPreview}
     * 
     * @see Preview#isPreview
     * @see IsPreviewAdvice
     */
    private static void previewFeatureChecks(ByteBuddy byteBuddy) {
        logInfo("Disabling preview feature flag checks");
        Class<Preview> previewClass = Preview.class;
        ForDeclaredMethods isPreviewMethod = Advice.to(IsPreviewAdvice.class).on(named("isPreview"));
        byteBuddy.redefine(previewClass)
                .visit(isPreviewMethod)
                .make()
                .load(previewClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
    }

    static private void logInfo(String msg) {
        System.out.println(msg);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }

    @Override
    public void init(ProcessingEnvironment processingEnv) {
        logInfo(
                ENABLED_FEATURES.stream()
                        .map(Enum::name)
                        .collect(Collectors.joining(
                                "\n\t- ",
                                "Jabel: initialized. Enabled features: \n\t- ",
                                "\n"
                        ))
        );
    }

    @Override
    public Set<String> getSupportedOptions() {
        return emptySet();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return emptySet();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        return false;
    }

    @Override
    public Iterable<? extends Completion> getCompletions(Element element, AnnotationMirror annotation, ExecutableElement member, String userText) {
        return emptySet();
    }
}
