package net.corda.plugins.javac.quasar;

import co.paralleluniverse.fibers.SuspendExecution;
import co.paralleluniverse.fibers.Suspendable;
import com.sun.source.tree.*;
import com.sun.source.util.*;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;

import javax.tools.Diagnostic;
import java.lang.annotation.Annotation;

public class SuspendableChecker implements Plugin {

    @Override
    public String getName() {
        return SuspendableChecker.class.getName();
    }

    @Override
    public void init(JavacTask javacTask, String... strings) {
        Trees trees = Trees.instance(javacTask);
        javacTask.addTaskListener(new SuspendableCheckerTaskListener(trees));
    }

    private static class SuspendableCheckerTaskListener implements TaskListener {

        private final Trees trees;

        SuspendableCheckerTaskListener(Trees trees) {
            this.trees = trees;
        }

        @Override
        public void started(TaskEvent taskEvent) {}

        @Override
        public void finished(TaskEvent taskEvent) {
            if (taskEvent.getKind() != TaskEvent.Kind.ANALYZE) return;
            taskEvent.getCompilationUnit()
                .accept(new SuspendableChecker.Scanner(trees, taskEvent.getCompilationUnit()), null);
        }
    }

    private static class Scanner extends TreeScanner<Void, Void> {

        private final Trees trees;
        private final CompilationUnitTree compilationUnit;
        private ClassTree classTree = null;
        private MethodTree methodTree = null;
        private boolean suspendable = false;

        Scanner(Trees trees, CompilationUnitTree compilationUnit) {
            this.trees = trees;
            this.compilationUnit = compilationUnit;
        }

        private boolean isSuspendable(MethodTree tree) {
            boolean annotatedWithSuspendable = tree.getModifiers()
                    .getAnnotations()
                    .stream()
                    .anyMatch(annotationTree ->
                            ((IdentifierTree) annotationTree.getAnnotationType()).getName().contentEquals(Suspendable.class.getName())
                    );
            if(annotatedWithSuspendable) return true;
            for(ExpressionTree expr : tree.getThrows()) {
                if(expr instanceof JCTree.JCIdent) {
                    Symbol symbol = TreeInfo.symbol((JCTree) expr);
                    if(symbol.getQualifiedName().contentEquals(SuspendExecution.class.getName()))
                        return true;
                }
            }
//            Name className = TreeInfo.symbol((JCTree) classTree).getQualifiedName();
//            suspendableDatabase.isSuspendable(className.toString(), tree.getName().toString())
            return false;
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

        @Override
        public Void visitMethodInvocation(MethodInvocationTree methodInvocationTree, Void aVoid) {
            if (!suspendable) {
                ExpressionTree methodSelect = methodInvocationTree.getMethodSelect();
                Symbol symbol = TreeInfo.symbol((JCTree) methodSelect);
                Annotation annotation = symbol.getAnnotation(Suspendable.class);
                if (annotation != null) {
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
