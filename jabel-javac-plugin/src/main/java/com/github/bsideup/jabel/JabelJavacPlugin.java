package com.github.bsideup.jabel;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.jvm.ClassFile;
import com.sun.tools.javac.jvm.ClassWriter;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.jvm.StringConcat;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.util.Context;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;

import java.lang.reflect.Field;

import static net.bytebuddy.jar.asm.Opcodes.ASM7;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

public class JabelJavacPlugin implements Plugin {

    @Override
    public String getName() {
        return "jabel";
    }

    @Override
    public void init(JavacTask task, String... args) {
        System.out.println("Initializing Jabel...");
        ByteBuddyAgent.install();

        ByteBuddy byteBuddy = new ByteBuddy();

        byteBuddy
                .redefine(Target.class)
                .method(
                        none()
                                .or(named("hasNestmateAccess"))
                                .or(named("hasStringConcatFactory"))
                                .or(named("hasVirtualPrivateInvoke"))
                )
                .intercept(FixedValue.value(false))
                .make()
                .load(Target.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

        byteBuddy
                .redefine(ClassWriter.class)
                .visit(new AsmVisitorWrapper() {
                    @Override
                    public int mergeWriter(int flags) {
                        return flags;
                    }

                    @Override
                    public int mergeReader(int flags) {
                        return flags;
                    }

                    @Override
                    public ClassVisitor wrap(
                            TypeDescription instrumentedType,
                            ClassVisitor classVisitor,
                            Implementation.Context implementationContext,
                            TypePool typePool,
                            FieldList<FieldDescription.InDefinedShape> fields,
                            MethodList<?> methods,
                            int writerFlags,
                            int readerFlags
                    ) {
                        return new ClassWriterClassVisitor(classVisitor);
                    }
                })
                .make()
                .load(ClassFile.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

        Context context = ((BasicJavacTask) task).getContext();

        Target target = Target.instance(context);

        if (target.compareTo(Target.JDK1_8) > 0) {
            if (target.hasNestmateAccess()) {
                throw new IllegalStateException("Nestmate instrumentation failed!");
            }
            if (target.hasStringConcatFactory()) {
                throw new IllegalStateException("StringConcat instrumentation failed!");
            }
            if (target.hasVirtualPrivateInvoke()) {
                throw new IllegalStateException("Virtual private invoke instrumentation failed!");
            }
        }

        try {
            {
                Field field = Target.class.getDeclaredField("majorVersion");
                field.setAccessible(true);
                field.set(target, Target.JDK1_8.majorVersion);
            }

            {
                Field field = Target.class.getDeclaredField("minorVersion");
                field.setAccessible(true);
                field.set(target, Target.JDK1_8.minorVersion);
            }

            {
                Field field = StringConcat.class.getDeclaredField("concatKey");
                field.setAccessible(true);

                Object key = field.get(null);
                // reset StringConcat
                context.put((Context.Key) key, (Object) null);
            }

            {
                Field field = Gen.class.getDeclaredField("concat");
                field.setAccessible(true);

                field.set(Gen.instance(context), StringConcat.instance(context));
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }

        System.out.println("Jabel initialized");
    }

    private static class ClassWriterClassVisitor extends ClassVisitor {

        // Create boxed constants
        static final Integer PREVIEW_MINOR_VERSION = ClassFile.PREVIEW_MINOR_VERSION;

        static final Integer JAVA_MAGIC = ClassFile.JAVA_MAGIC;

        ClassWriterClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM7, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            if ("writeClassFile".equals(name)) {
                return new MethodVisitor(ASM7, methodVisitor) {

                    int stage = 0;

                    @Override
                    public void visitLdcInsn(Object value) {
                        switch (stage) {
                            case 0:
                                if (JAVA_MAGIC.equals(value)) {
                                    stage = 1;
                                }
                                break;
                            case 1:
                                if (PREVIEW_MINOR_VERSION.equals(value)) {
                                    stage = 2;
                                    value = Target.JDK1_8.minorVersion;
                                }
                        }

                        super.visitLdcInsn(value);
                    }
                };
            }
            return methodVisitor;
        }
    }
}
