/*
 * Quasar: lightweight threads and actors for the JVM.
 * Copyright (c) 2013-2015, Parallel Universe Software Co. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 3.0
 * as published by the Free Software Foundation.
 */
package net.corda.plugins.javac.quasar;

import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;

import javax.tools.Diagnostic;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class MethodSignature {
    public final String className;
    public final String methodName;
    public final String desc;

    public MethodSignature(String className, String methodName, String desc) {
        this.className = className;
        this.methodName = methodName;
        this.desc = desc;
    }

    private static String getSignature(Type type) {
        if (type.isPrimitiveOrVoid()) {
            switch(type.getTag()) {
                case INT:
                    return "I";
                case LONG:
                    return "J";
                case BOOLEAN:
                    return "Z";
                case BYTE:
                    return "B";
                case SHORT:
                    return "S";
                case CHAR:
                    return "C";
                case FLOAT:
                    return "F";
                case DOUBLE:
                    return "D";
                case VOID:
                    return "V";
                default:
                    throw new IllegalStateException("Should never reach here");
            }
        } else {
            switch(type.getTag()) {
                case ARRAY:
                    return "[" + getSignature(((Type.ArrayType) type).elemtype);
                case CLASS:
                    String typeName = type.tsym.getQualifiedName().toString().replace('.', '/');
                    return 'L' + typeName + ';';
                default:
                    throw new IllegalStateException("Should never reach here");
            }
        }
    }

    public static MethodSignature from(Symbol sym) {
        String className = sym.owner.getQualifiedName().toString();
        String methodName = sym.getQualifiedName().toString();
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        Type.MethodType methodType = sym.asType().asMethodType();
        for (Type type : methodType.getParameterTypes()) {
            sb.append(getSignature(type));
        }
        sb.append(')');
        sb.append(getSignature(methodType.getReturnType()));
        return new MethodSignature(className.replace('.', '/'), methodName, sb.toString());
    }
}

public class SuspendableChecker implements Plugin {

    private enum Parameter {
        SuspendableAnnotationMarkers("annotations"),
        SuspendableThrowableMarkers("throwables");

        private static Map<String, Parameter> map;
        static {
            map = EnumSet.allOf(Parameter.class).stream().collect(Collectors.toMap(it -> it.key, Function.identity()));
        }

        public static Parameter from(String key) {
            Parameter result = map.get(key);
            if(result == null) throw new IllegalArgumentException(String.format("Unknown parameter '%s'", key));
            return result;
        }

        private final String key;

        Parameter(String key) {
            this.key = key;
        }
    }

    @Override
    public String getName() {
        return SuspendableChecker.class.getName();
    }

    private List<String> splitClassNames(String classNames) {
        return Arrays.stream(classNames.split(",")).map(String::trim).collect(Collectors.toList());
    }

    private SuspendableDatabase createSuspendableDatabase(String[] args) {
        String defaultSuspendableAnnotation = "co.paralleluniverse.fibers.Suspendable";
        String defaultSuspendableThrowable = "co.paralleluniverse.fibers.SuspendExecution";
        List<String> suspendableAnnotationMarkers = Collections.emptyList();
        List<String> suspendableThrowableMarkers = Collections.emptyList();
        for (String arg : args) {
            int columnIndex = arg.indexOf(':');
            if(columnIndex < 0) continue;
            String key = arg.substring(0, columnIndex);
            Parameter param = Parameter.from(key);
            switch (param) {
                case SuspendableAnnotationMarkers:
                    suspendableAnnotationMarkers = splitClassNames(arg.substring(columnIndex + 1));
                    break;
                case SuspendableThrowableMarkers:
                    suspendableThrowableMarkers = splitClassNames(arg.substring(columnIndex + 1));
                    break;
                default:
                    throw new IllegalArgumentException(String.format("Unknown compiler plugin argument '%s'", arg));
            }
        }
        return new SuspendableDatabase(ClassLoader.getSystemClassLoader(),
                Collections.unmodifiableList(
                    Stream.concat(
                        suspendableAnnotationMarkers.stream(),
                        Stream.of(defaultSuspendableAnnotation)
                    ).distinct().collect(Collectors.toList())),
                Collections.unmodifiableList(
                    Stream.concat(
                        suspendableThrowableMarkers.stream(),
                        Stream.of(defaultSuspendableThrowable)
                    ).distinct().collect(Collectors.toList()))
                );
    }

    @Override
    public void init(JavacTask javacTask, String... args) {
        Trees trees = Trees.instance(javacTask);
        javacTask.addTaskListener(new SuspendableCheckerTaskListener(trees, createSuspendableDatabase(args)));
    }

    private static class SuspendableCheckerTaskListener implements TaskListener {

        private final SuspendableDatabase suspendableDatabase;
        private final Trees trees;

        SuspendableCheckerTaskListener(Trees trees, SuspendableDatabase suspendableDatabase) {
            this.trees = trees;
            this.suspendableDatabase = suspendableDatabase;
        }

        @Override
        public void started(TaskEvent taskEvent) {}

        @Override
        public void finished(TaskEvent taskEvent) {
            if (taskEvent.getKind() != TaskEvent.Kind.ANALYZE) return;
            taskEvent.getCompilationUnit()
                .accept(new SuspendableChecker.Scanner(trees,
                        taskEvent.getCompilationUnit(),
                        suspendableDatabase), null);
        }
    }

    private static class Scanner extends TreeScanner<Void, Void> {

        private final Trees trees;
        private final CompilationUnitTree compilationUnit;
        private final SuspendableDatabase suspendableDatabase;
        private ClassTree classTree = null;
        private MethodTree methodTree = null;
        private boolean suspendable = false;

        Scanner(Trees trees,
                CompilationUnitTree compilationUnit,
                SuspendableDatabase suspendableDatabase) {
            this.trees = trees;
            this.compilationUnit = compilationUnit;
            this.suspendableDatabase = suspendableDatabase;
        }

        @Override
        public Void visitClass(ClassTree classTree, Void aVoid) {
            this.classTree = classTree;
            return super.visitClass(classTree, aVoid);
        }

        @Override
        public Void visitMethod(MethodTree methodTree, Void aVoid) {
            this.methodTree = methodTree;
            suspendable = isSuspendable(methodTree);
            return super.visitMethod(methodTree, aVoid);
        }

        private boolean isSuspendable(MethodTree tree) {
            Symbol sym = ((JCTree.JCMethodDecl) tree).sym;
            return isSuspendable(sym);
        }

        private static Optional<Symbol> findOverridden(Symbol.TypeSymbol typeSymbol, Symbol methodSymbol) {
            return StreamSupport.stream(ASTUtils.memberByName(typeSymbol, methodSymbol.name, s ->
                        Optional.ofNullable(s.type.asMethodType())
                                .map(mt -> Objects.equals(methodSymbol.asType().getParameterTypes(), mt.getParameterTypes()))
                                .orElse(false)
                ).spliterator(), false).findFirst();
        }

        private boolean superSuspendable(Symbol symbol) {
            Symbol.ClassSymbol ownerClass = ((Symbol.ClassSymbol) symbol.owner);
            Stream<Type> supers = Stream.concat(
                    Optional.ofNullable(ownerClass.getSuperclass())
                            .filter(t -> t != Type.noType)
                            .map(Stream::of)
                            .orElse(Stream.empty()),
                    ownerClass.getInterfaces().stream());
            List<Type> supList = supers.collect(Collectors.toList());
            return supList.stream().map(type -> findOverridden(type.asElement(), symbol))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .anyMatch(s -> inSuspendablesFile(s) || superSuspendable(s));
        }

        private boolean inSuspendablesFile(Symbol sym) {
            MethodSignature signature = MethodSignature.from(sym);
            return suspendableDatabase.isSuspendable(signature.className, signature.methodName, signature.desc);
        }

        private boolean isSuspendable(Symbol sym) {
            for(String annotationMarkerClassName : suspendableDatabase.suspendableAnnotationMarkers) {
                boolean isAnnotatedWithSuspendableMarker = sym.getAnnotationMirrors()
                        .stream()
                        .anyMatch(it -> Objects.equals(annotationMarkerClassName, it.getAnnotationType().toString()));
                if(isAnnotatedWithSuspendableMarker) return true;
            }

            for(String throwableMarkerClassName : suspendableDatabase.suspendableExceptionMarkers) {
                boolean throwsSuspendableMarker = sym.asType().getThrownTypes().stream().anyMatch(type ->
                        Objects.equals(throwableMarkerClassName, type.tsym.getQualifiedName().toString())
                );
                if(throwsSuspendableMarker) return true;
            }
            return inSuspendablesFile(sym) || superSuspendable(sym);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree methodInvocationTree, Void aVoid) {
            if (!suspendable) {
                ExpressionTree methodSelect = methodInvocationTree.getMethodSelect();
                Symbol symbol = TreeInfo.symbol((JCTree) methodSelect);
                if (symbol != null && isSuspendable(symbol)) {
                    trees.printMessage(Diagnostic.Kind.ERROR,
                            "Invocation of suspendable method from non suspendable method",
                            methodSelect,
                            compilationUnit);
                }

            }
            return super.visitMethodInvocation(methodInvocationTree, aVoid);
        }
    }
}
