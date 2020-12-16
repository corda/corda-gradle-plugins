package net.corda.plugins.javac.quasar;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;

import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;
import java.util.Objects;

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
        if (type.isPrimitive()) {
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
        for (Type type : sym.asType().asMethodType().getParameterTypes()) {
            sb.append(getSignature(type));
        }
        sb.append(')');
        return new MethodSignature(className.replace('.', '/'), methodName, sb.toString());
    }
}

public class SuspendableChecker implements Plugin {

    private final SuspendableDatabase suspendableDatabase = new SuspendableDatabase(ClassLoader.getSystemClassLoader());

    @Override
    public String getName() {
        return SuspendableChecker.class.getName();
    }

    @Override
    public void init(JavacTask javacTask, String... strings) {
        Trees trees = Trees.instance(javacTask);
        javacTask.addTaskListener(new SuspendableCheckerTaskListener(trees, suspendableDatabase));
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
                .accept(new SuspendableChecker.Scanner(trees, taskEvent.getCompilationUnit(), suspendableDatabase), null);
        }
    }

    private static class Scanner extends TreeScanner<Void, Void> {

        private final Trees trees;
        private final CompilationUnitTree compilationUnit;
        private final SuspendableDatabase suspendableDatabase;
        private ClassTree classTree = null;
        private MethodTree methodTree = null;
        private boolean suspendable = false;

        Scanner(Trees trees, CompilationUnitTree compilationUnit, SuspendableDatabase suspendableDatabase) {
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

        private boolean isSuspendable(Symbol sym) {
            Annotation annotation = sym.getAnnotation(Suspendable.class);
            if(annotation != null) return true;
            boolean throwsSuspendExecution = sym.asType().getThrownTypes().stream().anyMatch(type ->
                    Objects.equals(SuspendExecution.class.getName(), type.tsym.getQualifiedName().toString())
            );
            if(throwsSuspendExecution) return true;
            MethodSignature signature = MethodSignature.from(sym);
            return suspendableDatabase.isSuspendable(signature.className, signature.methodName, signature.desc);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree methodInvocationTree, Void aVoid) {
            if (!suspendable) {
                ExpressionTree methodSelect = methodInvocationTree.getMethodSelect();
                Symbol symbol = TreeInfo.symbol((JCTree) methodSelect);
                if (isSuspendable(symbol)) {
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
