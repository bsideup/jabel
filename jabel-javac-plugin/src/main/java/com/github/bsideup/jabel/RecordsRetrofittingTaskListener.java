package com.github.bsideup.jabel;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;
import java.util.Iterator;
import java.util.stream.Stream;

class RecordsRetrofittingTaskListener implements TaskListener {

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

                            // TODO find a better way?
                            JCTree.JCVariableDecl param = def.params.get(0);
                            switch (param.getType().toString()) {
                                case "java.lang.Object":
                                case "Object":
                                    return true;
                                default:
                                    return false;
                            }
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
                new MandatoryDesugarAnnotationTreeScanner(log, e.getCompilationUnit()).scan(e.getCompilationUnit(), null);
        }
    }

    @Override
    public void finished(TaskEvent e) {
    }

    private Stream<JCTree.JCVariableDecl> getRecordComponents(JCTree.JCClassDecl classDecl) {
        return classDecl.getMembers().stream()
                .filter(JCTree.JCVariableDecl.class::isInstance)
                .map(JCTree.JCVariableDecl.class::cast)
                .filter(it -> !it.getModifiers().getFlags().contains(Modifier.STATIC));
    }

    private List<JCTree.JCStatement> generateToString(JCTree.JCClassDecl classDecl) {
        JCTree.JCExpression stringBuilder = make.NewClass(
                null,
                null,
                make.QualIdent(syms.stringBuilderType.tsym),
                List.of(make.Literal(classDecl.name + "[")),
                null
        );

        for (
                Iterator<JCTree.JCVariableDecl> iterator = getRecordComponents(classDecl).iterator();
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
            for (
                    Iterator<JCTree.JCVariableDecl> iterator = getRecordComponents(classDecl).iterator();
                    iterator.hasNext();
            ) {
                JCTree.JCVariableDecl fieldDecl = iterator.next();

                JCTree.JCExpression myFieldAccess = make.Select(make.This(Type.noType), fieldDecl.name);
                JCTree.JCExpression otherFieldAccess = make.Select(
                        make.TypeCast(make.Ident(classDecl.name), make.Ident(otherName)),
                        fieldDecl.name
                );

                final JCTree.JCExpression condition;
                if (fieldDecl.getType() instanceof JCTree.JCPrimitiveTypeTree) {
                    condition = make.Binary(JCTree.Tag.EQ, otherFieldAccess, myFieldAccess);
                } else {
                    condition = make.App(
                            // call Objects.equals
                            make.Select(
                                    make.QualIdent(syms.objectsType.tsym),
                                    names.equals
                            ).setType(syms.objectsType),
                            List.of(otherFieldAccess, myFieldAccess)
                    );
                }
                statements.add(make.If(
                        condition,
                        make.Block(0, List.nil()),
                        make.Return(make.Literal(false))
                ));
            }
        }

        statements.add(make.Return(make.Literal(true)));
        return statements.toList();
    }

    private List<JCTree.JCStatement> generateHashCode(JCTree.JCClassDecl classDecl) {
        ListBuffer<JCTree.JCExpression> expressions = new ListBuffer<>();

        for (
                Iterator<JCTree.JCVariableDecl> iterator = getRecordComponents(classDecl).iterator();
                iterator.hasNext();
        ) {
            JCTree.JCVariableDecl fieldDecl = iterator.next();

            JCTree fType = fieldDecl.getType();

            JCTree.JCExpression myFieldAccess = make.Select(make.This(Type.noType), fieldDecl.name);

            if (fType instanceof JCTree.JCPrimitiveTypeTree) {
                switch (((JCTree.JCPrimitiveTypeTree) fType).getPrimitiveTypeKind()) {
                    case LONG:
                        expressions.append(longToIntForHashCode(myFieldAccess));
                        break;
                    case FLOAT:
                        /* this.fieldName != 0f ? Float.floatToIntBits(this.fieldName) : 0 */
                        expressions.append(
                                make.Conditional(
                                        make.Binary(JCTree.Tag.NE, myFieldAccess, make.Literal(0f)),
                                        make.App(
                                                make.Select(
                                                        make.Ident(names.fromString("Float")),
                                                        names.fromString("floatToIntBits")).setType(syms.intType),
                                                List.of(myFieldAccess)
                                        ),
                                        make.Literal(TypeTag.INT, 0)
                                )
                        );
                        break;
                    case DOUBLE:
                        /* longToIntForHashCode(Double.doubleToLongBits(this.fieldName)) */
                        expressions.append(
                                longToIntForHashCode(
                                        make.App(
                                                make.Select(
                                                        make.Ident(names.fromString("Double")),
                                                        names.fromString("doubleToLongBits")).setType(syms.intType),
                                                List.of(myFieldAccess)
                                        )
                                )
                        );
                        break;
                    default:
                    case BYTE:
                    case SHORT:
                    case INT:
                    case CHAR:
                        /* just the field */
                        expressions.append(myFieldAccess);
                        break;
                }
            } else if (fType instanceof JCTree.JCArrayTypeTree) {
                expressions.append(
                        make.App(
                                make.Select(
                                        make.Select(
                                                make.Select(
                                                        make.Ident(names.fromString("java")),
                                                        names.fromString("util")
                                                ),
                                                names.fromString("Arrays")
                                        ),
                                        names.fromString("hashCode")
                                ).setType(syms.intType),
                                List.of(myFieldAccess)
                        )
                );
            } else {
                /* (this.fieldName != null ? this.fieldName.hashCode() : 0) */
                expressions.append(
                        make.Conditional(
                                make.Binary(JCTree.Tag.NE, myFieldAccess, make.Literal(TypeTag.BOT, null)),
                                make.App(make.Select(myFieldAccess, names.hashCode).setType(syms.intType)),
                                make.Literal(0)
                        )
                );
            }
        }

        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();

        Name resultName = names.fromString("result");

        statements.append(
                make.VarDef(
                        make.Modifiers(0L),
                        resultName,
                        make.TypeIdent(syms.intType.getTag()),
                        make.Literal(0)
                )
        );
        for (JCTree.JCExpression expression : expressions) {
            // result = 31 * result + ${expr}
            statements.append(make.Exec(
                    make.Assign(
                            make.Ident(resultName),
                            make.Binary(
                                    JCTree.Tag.PLUS,
                                    make.Binary(JCTree.Tag.MUL, make.Literal(TypeTag.INT, 31), make.Ident(resultName)),
                                    expression
                            )
                    )
            ));
        }

        statements.append(make.Return(make.Ident(resultName)));
        return statements.toList();
    }

    public JCTree.JCExpression longToIntForHashCode(JCTree.JCExpression ref) {
        /* (int) (ref ^ ref >>> 32) */
        return make.TypeCast(
                make.TypeIdent(syms.intType.getTag()),
                make.Parens(
                        make.Binary(
                                JCTree.Tag.BITXOR,
                                ref,
                                make.Parens(make.Binary(JCTree.Tag.USR, ref, make.Literal(32)))
                        )
                )
        );
    }

    private static class MandatoryDesugarAnnotationTreeScanner extends TreeScanner<Void, Void> {

        private final Log log;

        private final CompilationUnitTree compilationUnit;

        public MandatoryDesugarAnnotationTreeScanner(Log log, CompilationUnitTree compilationUnit) {
            this.log = log;
            this.compilationUnit = compilationUnit;
        }

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
                    JavaFileObject oldSource = log.useSource(compilationUnit.getSourceFile());
                    try {
                        log.error(
                                (JCTree.JCClassDecl) node,
                                new JCDiagnostic.Error(
                                        "jabel",
                                        "missing.desugar.on.record",
                                        "Must be annotated with @Desugar"
                                )
                        );
                    } finally {
                        log.useSource(oldSource);
                    }
                }
            }
            return super.visitClass(node, aVoid);
        }
    }
}
