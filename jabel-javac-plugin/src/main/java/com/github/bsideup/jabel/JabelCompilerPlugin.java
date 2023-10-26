package com.github.bsideup.jabel;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JavacMessages;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.dynamic.scaffold.MethodGraph;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

import java.util.*;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class JabelCompilerPlugin implements Plugin {
    static {
        Map<String, AsmVisitorWrapper> visitors = new HashMap<String, AsmVisitorWrapper>() {{
            // Disable the preview feature check
            AsmVisitorWrapper checkSourceLevelAdvice = Advice.to(CheckSourceLevelAdvice.class)
                    .on(named("checkSourceLevel").and(takesArguments(2)));

            // Allow features that were introduced together with Records (local enums, static inner members, ...)
            AsmVisitorWrapper allowRecordsEraFeaturesAdvice = new FieldAccessStub("allowRecords", true);

            put("com.sun.tools.javac.parser.JavacParser",
                    new AsmVisitorWrapper.Compound(
                            checkSourceLevelAdvice,
                            allowRecordsEraFeaturesAdvice
                    )
            );
            put("com.sun.tools.javac.parser.JavaTokenizer", checkSourceLevelAdvice);

            put("com.sun.tools.javac.comp.Check", allowRecordsEraFeaturesAdvice);
            put("com.sun.tools.javac.comp.Attr", allowRecordsEraFeaturesAdvice);
            put("com.sun.tools.javac.comp.Resolve", allowRecordsEraFeaturesAdvice);

            // Lower the source requirement for supported features
            put(
                    "com.sun.tools.javac.code.Source$Feature",
                    Advice.to(AllowedInSourceAdvice.class)
                            .on(named("allowedInSource").and(takesArguments(1)))
            );
        }};

        try {
            ByteBuddyAgent.install();
        } catch (Exception e) {
            ByteBuddyAgent.install(
                    new ByteBuddyAgent.AttachmentProvider.Compound(
                            ByteBuddyAgent.AttachmentProvider.ForJ9Vm.INSTANCE,
                            ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm.JVM_ROOT,
                            ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm.JDK_ROOT,
                            ByteBuddyAgent.AttachmentProvider.ForStandardToolsJarVm.MACINTOSH,
                            ByteBuddyAgent.AttachmentProvider.ForUserDefinedToolsJar.INSTANCE,
                            ByteBuddyAgent.AttachmentProvider.ForEmulatedAttachment.INSTANCE
                    )
            );
        }

        ByteBuddy byteBuddy = new ByteBuddy()
                .with(MethodGraph.Compiler.ForDeclaredMethods.INSTANCE);

        ClassLoader classLoader = JavacTask.class.getClassLoader();
        ClassFileLocator classFileLocator = ClassFileLocator.ForClassLoader.of(classLoader);
        TypePool typePool = TypePool.ClassLoading.of(classLoader);

        visitors.forEach((className, visitor) -> {
            byteBuddy
                    .decorate(
                            typePool.describe(className).resolve(),
                            classFileLocator
                    )
                    .visit(visitor)
                    .make()
                    .load(classLoader, ClassReloadingStrategy.fromInstalledAgent());
        });

        JavaModule jabelModule = JavaModule.ofType(JabelCompilerPlugin.class);
        ClassInjector.UsingInstrumentation.redefineModule(
                ByteBuddyAgent.getInstrumentation(),
                JavaModule.ofType(JavacTask.class),
                Collections.emptySet(),
                Collections.emptyMap(),
                new HashMap<String, java.util.Set<JavaModule>>() {{
                    put("com.sun.tools.javac.api", Collections.singleton(jabelModule));
                    put("com.sun.tools.javac.tree", Collections.singleton(jabelModule));
                    put("com.sun.tools.javac.code", Collections.singleton(jabelModule));
                    put("com.sun.tools.javac.util", Collections.singleton(jabelModule));
                }},
                Collections.emptySet(),
                Collections.emptyMap()
        );
    }

    @Override
    public void init(JavacTask task, String... args) {
        Context context = ((BasicJavacTask) task).getContext();
        JavacMessages.instance(context).add(locale -> new ResourceBundle() {
            @Override
            protected Object handleGetObject(String key) {
                return "{0}";
            }

            @Override
            public Enumeration<String> getKeys() {
                return Collections.enumeration(Arrays.asList("missing.desugar.on.record"));
            }
        });

        task.addTaskListener(new RecordsRetrofittingTaskListener(context));

        System.out.println("Jabel: initialized");
    }

    @Override
    public String getName() {
        return "jabel";
    }

    // Make it auto start on Java 14+
    public boolean autoStart() {
        return true;
    }

    static class AllowedInSourceAdvice {

        @Advice.OnMethodEnter
        static void allowedInSource(
                @Advice.This Source.Feature feature,
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
                case "RECORDS":
                    //noinspection UnusedAssignment
                    source = Source.DEFAULT;
                    break;
            }
        }
    }

    static class CheckSourceLevelAdvice {

        @Advice.OnMethodEnter
        static void checkSourceLevel(
                @Advice.Argument(value = 1, readOnly = false) Source.Feature feature
        ) {
            if (feature.allowedInSource(Source.JDK8)) {
                if (Source.MIN.compareTo(Source.JDK8) < 0) {
                  //noinspection UnusedAssignment
                  feature = Source.Feature.LAMBDA;
                } else {
                  //noinspection UnusedAssignment
                  feature = Source.Feature.RECORDS;
                }
            }
        }
    }

    private static class FieldAccessStub extends AsmVisitorWrapper.AbstractBase {

        final String fieldName;

        final Object value;

        public FieldAccessStub(String fieldName, Object value) {
            this.fieldName = fieldName;
            this.value = value;
        }

        @Override
        public ClassVisitor wrap(TypeDescription instrumentedType, ClassVisitor classVisitor, Implementation.Context implementationContext, TypePool typePool, FieldList<FieldDescription.InDefinedShape> fields, MethodList<?> methods, int writerFlags, int readerFlags) {
            return new ClassVisitor(Opcodes.ASM9, classVisitor) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                    return new MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            if (opcode == Opcodes.GETFIELD && fieldName.equalsIgnoreCase(name)) {
                                super.visitInsn(Opcodes.POP);
                                super.visitLdcInsn(value);
                            } else {
                                super.visitFieldInsn(opcode, owner, name, descriptor);
                            }
                        }
                    };
                }
            };
        }
    }
}
