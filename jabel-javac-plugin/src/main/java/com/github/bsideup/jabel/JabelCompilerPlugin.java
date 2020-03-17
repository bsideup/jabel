package com.github.bsideup.jabel;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.*;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

public class JabelCompilerPlugin implements Plugin {

    static final Set<Source.Feature> ENABLED_FEATURES = Stream
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
                    "REIFIABLE_TYPES_INSTANCEOF",
                    "RECORDS"
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

    @Override
    public String getName() {
        return "jabel";
    }

    @Override
    public void init(JavacTask task, String... args) {
        ByteBuddyAgent.install();

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

            for (Source.Feature feature : ENABLED_FEATURES) {
                field.set(feature, Source.JDK8);
                if (!feature.allowedInSource(Source.JDK8)) {
                    throw new IllegalStateException(feature.name() + " minLevel instrumentation failed!");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // TODO use Log
        System.out.println(
                ENABLED_FEATURES.stream()
                        .map(Enum::name)
                        .collect(Collectors.joining(
                                "\n\t- ",
                                "Jabel: initialized. Enabled features: \n\t- ",
                                "\n"
                        ))
        );

        Context context = ((JavacTaskImpl) task).getContext();

        task.addTaskListener(new RecordsRetrofittingTaskListener(context));
    }

    public boolean autoStart() {
        return true;
    }

    private static class RecordsRetrofittingTaskListener implements TaskListener {

        final TreeMaker make;

        final Symtab syms;

        final Names names;

        final Log log;

        TreeScanner<Void, Void> recordsScanner = new TreeScanner<Void, Void>() {
            @Override
            public Void visitClass(ClassTree node, Void aVoid) {
                if (!"RECORD".equals(node.getKind().toString())) {
                    return super.visitClass(node, aVoid);
                }

                JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) node;

                if (classDecl.extending == null) {
                    // Prevent implicit "extends java.lang.Record"
                    classDecl.extending = make.Type(syms.objectType);
                }

                {
                    Name methodName = names.toString;
                    List<Type> argTypes = List.nil();
                    if (!containsMethod(classDecl, methodName)) {
                        JCTree.JCMethodDecl methodDecl = make.MethodDef(
                                new Symbol.MethodSymbol(
                                        Flags.PUBLIC,
                                        methodName,
                                        new Type.MethodType(
                                                argTypes,
                                                syms.stringType,
                                                List.nil(),
                                                syms.methodClass
                                        ),
                                        syms.objectType.tsym
                                ),
                                make.Block(0, generateToString(classDecl))
                        );
                        classDecl.defs = classDecl.defs.append(methodDecl);
                    }
                }

                {
                    Name methodName = names.hashCode;
                    List<Type> argTypes = List.nil();
                    if (!containsMethod(classDecl, methodName)) {
                        classDecl.defs = classDecl.defs.append(make.MethodDef(
                                new Symbol.MethodSymbol(
                                        Flags.PUBLIC,
                                        methodName,
                                        new Type.MethodType(
                                                argTypes,
                                                syms.intType,
                                                List.nil(),
                                                syms.methodClass
                                        ),
                                        syms.objectType.tsym
                                ),
                                make.Block(0, generateHashCode(classDecl))
                        ));
                    }
                }

                {
                    Name methodName = names.equals;
                    List<Type> argTypes = List.of(syms.objectType);
                    if (!containsMethod(classDecl, methodName)) {
                        Symbol.MethodSymbol methodSymbol = new Symbol.MethodSymbol(
                                Flags.PUBLIC | Flags.FINAL,
                                methodName,
                                new Type.MethodType(
                                        argTypes,
                                        syms.booleanType,
                                        List.nil(),
                                        syms.methodClass
                                ),
                                syms.objectType.tsym
                        );
                        Symbol.VarSymbol firstParameter = methodSymbol.params().head;

                        JCTree.JCMethodDecl methodDecl = make.MethodDef(
                                methodSymbol,
                                make.Block(0, generateEquals(classDecl, firstParameter.name))
                        );
                        // THIS ONE IS IMPORTANT! Otherwise, Flow.AssignAnalyzer#visitVarDef will have track=false
                        methodDecl.params.head.pos = classDecl.pos;
                        classDecl.defs = classDecl.defs.append(methodDecl);
                    }
                }
                return super.visitClass(node, aVoid);
            }

            private boolean containsMethod(JCTree.JCClassDecl classDecl, Name name) {
                return classDecl.defs.stream()
                        .filter(JCTree.JCMethodDecl.class::isInstance)
                        .map(JCTree.JCMethodDecl.class::cast)
                        .anyMatch(def -> {
                            if (def.getName() != name) {
                                return false;
                            }

                            if (name == names.equals) {
                                if (def.params.size() != 1) {
                                    return false;
                                }
                                // TODO match arguments
                            }

                            return true;
                        });
            }
        };

        public RecordsRetrofittingTaskListener(Context context) {
            make = TreeMaker.instance(context);
            syms = Symtab.instance(context);
            names = Names.instance(context);
            log = Log.instance(context);
        }

        @Override
        public void started(TaskEvent e) {
            switch (e.getKind()) {
                case ENTER:
                    recordsScanner.scan(e.getCompilationUnit(), null);
                    new TreeScanner<Void, Void>() {
                        @Override
                        public Void visitClass(ClassTree node, Void aVoid) {
                            if ("RECORD".equals(node.getKind().toString())) {
                                JCTree.JCClassDecl classDecl = (JCTree.JCClassDecl) node;

                                if (classDecl.extending == null) {
                                    // Prevent implicit "extends java.lang.Record"
                                    classDecl.extending = make.Type(syms.objectType);
                                }
                            }
                            return super.visitClass(node, aVoid);
                        }
                    }.scan(e.getCompilationUnit(), null);
                    break;
                case ANALYZE:
                    new TreeScanner<Void, Void>() {
                        @Override
                        public Void visitClass(ClassTree node, Void aVoid) {
                            if ("RECORD".equals(node.getKind().toString())) {
                                if (
                                        node.getModifiers().getAnnotations().stream()
                                                .noneMatch(annotation -> {
                                                    Type type = ((JCTree.JCAnnotation) annotation).type;
                                                    return Desugar.class.getName().equals(type.toString());
                                                })
                                ) {
                                    log.error(
                                            ((JCTree.JCClassDecl) node).pos(),
                                            new JCDiagnostic.Error(
                                                    "jabel",
                                                    "missing.desugar.on.record",
                                                    "Must be annotated with @Desugar"
                                            )
                                    );
                                }
                            }
                            return super.visitClass(node, aVoid);
                        }
                    }.scan(e.getCompilationUnit(), null);
            }
        }

        @Override
        public void finished(TaskEvent e) {
        }

        private List<JCTree.JCStatement> generateToString(JCTree.JCClassDecl classDecl) {
            // TODO check that it matches the original toString implementation
            JCTree.JCExpression stringBuilder = make.NewClass(
                    null,
                    null,
                    make.QualIdent(syms.stringBuilderType.tsym),
                    List.of(make.Literal(classDecl.name + "[")),
                    null
            );

            Stream<JCTree.JCVariableDecl> fields = classDecl.getMembers().stream()
                    .filter(JCTree.JCVariableDecl.class::isInstance)
                    .map(JCTree.JCVariableDecl.class::cast);
            for (
                    Iterator<JCTree.JCVariableDecl> iterator = fields.iterator();
                    iterator.hasNext();
            ) {
                JCTree.JCVariableDecl fieldDecl = iterator.next();
                Name fieldName = fieldDecl.name;

                stringBuilder = make.App(
                        make.Select(stringBuilder, names.append).setType(syms.stringBuilderType),
                        List.of(make.Literal(fieldName + "="))
                );

                stringBuilder = make.App(
                        make.Select(stringBuilder, names.append).setType(syms.stringBuilderType),
                        List.of(
                                make.Select(
                                        make.This(Type.noType),
                                        fieldName
                                )
                        )
                );

                if (iterator.hasNext()) {
                    stringBuilder = make.App(
                            make.Select(stringBuilder, names.append).setType(syms.stringBuilderType),
                            List.of(make.Literal(","))
                    );
                }
            }

            stringBuilder = make.App(
                    make.Select(stringBuilder, names.append).setType(syms.stringBuilderType),
                    List.of(make.Literal("]"))
            );

            return List.of(make.Return(
                    make.App(
                            make.Select(stringBuilder, names.toString).setType(syms.stringType)
                    )
            ));
        }

        private List<JCTree.JCStatement> generateEquals(JCTree.JCClassDecl classDecl, Name otherName) {
            ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();

            // if (o == this) return true;
            {
                statements.add(make.If(
                        make.Binary(
                                JCTree.Tag.EQ,
                                make.This(Type.noType),
                                make.Ident(otherName)
                        ),
                        make.Return(make.Literal(true)),
                        null
                ));
            }

            // if (o == null) return false;
            {
                statements.add(make.If(
                        make.Binary(
                                JCTree.Tag.EQ,
                                make.Ident(otherName),
                                make.Literal(TypeTag.BOT, null)
                        ),
                        make.Return(make.Literal(false)),
                        null
                ));
            }

            // if (o.getClass() != getClass()) return false;
            {
                statements.add(make.If(
                        make.Binary(
                                JCTree.Tag.EQ,
                                make.App(make.Select(make.Ident(otherName), names.getClass).setType(syms.classType)),
                                make.App(make.Select(make.This(Type.noType), names.getClass).setType(syms.classType))
                        ),
                        make.Block(0, List.nil()),
                        make.Return(make.Literal(false))
                ));
            }

            // fields
            {
                for (JCTree member : classDecl.getMembers()) {
                    if (!(member instanceof JCTree.JCVariableDecl)) {
                        continue;
                    }
                    JCTree.JCVariableDecl fieldDecl = (JCTree.JCVariableDecl) member;

                    // TODO primitive cmp
                    // TODO deepEquals for array fields
                    statements.add(make.If(
                            make.App(
                                    make.Select(
                                            make.QualIdent(syms.objectsType.tsym),
                                            names.equals
                                    ).setType(syms.objectsType),
                                    List.of(
                                            make.Select(make.TypeCast(make.Ident(classDecl.name), make.Ident(otherName)), fieldDecl.name),
                                            make.Select(make.This(Type.noType), fieldDecl.name)
                                    )
                            ),
                            make.Block(0, List.nil()),
                            make.Return(make.Literal(false))
                    ));
                }
            }

            statements.add(make.Return(make.Literal(true)));
            return statements.toList();
        }

        private List<JCTree.JCStatement> generateHashCode(JCTree.JCClassDecl classDecl) {
            // TODO "inline" Objects.hashCode to avoid allocations
            ListBuffer<JCTree.JCExpression> args = new ListBuffer<>();
            for (JCTree member : classDecl.getMembers()) {
                if (!(member instanceof JCTree.JCVariableDecl)) {
                    continue;
                }
                JCTree.JCVariableDecl fieldDecl = (JCTree.JCVariableDecl) member;

                args.add(make.Select(make.This(Type.noType), fieldDecl.name));
            }

            return List.of(
                    make.Return(make.App(
                            make.Select(make.Ident(syms.objectsType.tsym), names.fromString("hash")).setType(syms.intType),
                            List.of(make.NewArray(
                                    make.Ident(syms.objectType.tsym),
                                    List.nil(),
                                    args.toList()
                            ))
                    ))
            );
        }
    }
}
