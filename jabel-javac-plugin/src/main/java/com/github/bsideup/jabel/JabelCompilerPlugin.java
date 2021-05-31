package com.github.bsideup.jabel;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.MemberSubstitution;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.pool.TypePool;

import java.util.Arrays;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class JabelCompilerPlugin implements Plugin {

    @Override
    public void init(JavacTask task, String... args) {
        ByteBuddyAgent.install();

        ByteBuddy byteBuddy = new ByteBuddy();

        ClassLoader classLoader = JavacTask.class.getClassLoader();
        ClassFileLocator classFileLocator = ClassFileLocator.ForClassLoader.of(classLoader);
        TypePool typePool = TypePool.ClassLoading.of(classLoader);

        for (String className : Arrays.asList(
                "com.sun.tools.javac.parser.JavacParser",
                "com.sun.tools.javac.parser.JavaTokenizer"
        )) {
            byteBuddy
                    .redefine(
                            typePool.describe(className).resolve(),
                            classFileLocator
                    )
                    .visit(
                            Advice.to(CheckSourceLevelAdvice.class)
                                    .on(named("checkSourceLevel").and(takesArguments(2)))
                    )
                    .make()
                    .load(classLoader, ClassReloadingStrategy.fromInstalledAgent());
        }

        for (String className : Arrays.asList(
                "com.sun.tools.javac.comp.Check",
                "com.sun.tools.javac.parser.JavacParser",
                "com.sun.tools.javac.comp.Attr",
                "com.sun.tools.javac.comp.Resolve"
        )) {
            byteBuddy
                    .redefine(
                            typePool.describe(className).resolve(),
                            classFileLocator
                    )
                    .visit(
                            MemberSubstitution.relaxed()
                                    .field(named("allowRecords"))
                                    .onRead()
                                    .replaceWith(ConstantMemberSubstitution.of(true))
                                    .on(any())
                    )
                    .make()
                    .load(classLoader, ClassReloadingStrategy.fromInstalledAgent());
        }

        byteBuddy
                .redefine(
                        typePool.describe("com.sun.tools.javac.code.Source$Feature").resolve(),
                        classFileLocator
                )
                .visit(
                        Advice.to(AllowedInSourceAdvice.class)
                                .on(named("allowedInSource").and(takesArguments(1)))
                )
                .make()
                .load(classLoader, ClassReloadingStrategy.fromInstalledAgent());

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
}
