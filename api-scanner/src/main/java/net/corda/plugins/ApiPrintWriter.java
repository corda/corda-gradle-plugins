package net.corda.plugins;

import io.github.lukehutch.fastclasspathscanner.scanner.ClassInfo;
import io.github.lukehutch.fastclasspathscanner.scanner.FieldInfo;
import io.github.lukehutch.fastclasspathscanner.scanner.MethodInfo;
import io.github.lukehutch.fastclasspathscanner.typesignature.TypeSignature;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;

public class ApiPrintWriter extends PrintWriter {
    ApiPrintWriter(File file, String encoding) throws FileNotFoundException, UnsupportedEncodingException {
        super(file, encoding);
    }

    public void println(ClassInfo classInfo, int modifierMask, List<String> filteredAnnotations) {
        super.append(asAnnotations(filteredAnnotations, ""));
        super.append(Modifier.toString(classInfo.getClassRef().getModifiers() & modifierMask));
        if (classInfo.isAnnotation()) {
            /*
             * Annotation declaration.
             */
            super.append(" @interface ").print(classInfo.getClassName());
        } else if (classInfo.isStandardClass()) {
            /*
             * Class declaration.
             */
            super.append(" class ").print(classInfo.getClassName());
            Set<ClassInfo> superclasses = classInfo.getDirectSuperclasses();
            if (!superclasses.isEmpty()) {
                super.append(" extends ").print(stringOf(superclasses));
            }
            Set<ClassInfo> interfaces = classInfo.getDirectlyImplementedInterfaces();
            if (!interfaces.isEmpty()) {
                super.append(" implements ").print(stringOf(interfaces));
            }
        } else {
            /*
             * Interface declaration.
             */
            super.append(" interface ").print(classInfo.getClassName());
            Set<ClassInfo> superinterfaces = classInfo.getDirectSuperinterfaces();
            if (!superinterfaces.isEmpty()) {
                super.append(" extends ").print(stringOf(superinterfaces));
            }
        }
        super.println();
    }

    public void println(MethodInfo method, String indentation) {
        super.append(asAnnotations(method.getAnnotationNames(), indentation));
        super.append(indentation);
        if (method.getModifiersStr() != null) {
            super.append(method.getModifiersStr()).append(' ');
        }
        if (!method.isConstructor()) {
            super.append(removeQualifierFromBaseTypes(method.getResultTypeStr())).append(' ');
        }
        super.append(method.getMethodName()).append('(');
        LinkedList<String> paramTypes = method
            .getTypeSignature()
            .getParameterTypeSignatures()
            .stream()
            .map(ApiPrintWriter::removeQualifierFromBaseTypes)
            .collect(toCollection(LinkedList::new));
        //if parameter is varargs, remove the array [] qualifier and replace with ellipsis
        if (method.isVarArgs() && !paramTypes.isEmpty()) {
            String vararg = paramTypes.removeLast();
            paramTypes.add(vararg.substring(0, vararg.length() - 2) + "...");
        }
        super.append(paramTypes.stream().collect(joining(", ")));
        super.println(')');
    }

    public void println(FieldInfo field, String indentation) {
        super.append(asAnnotations(field.getAnnotationNames(), indentation));
        super.append(indentation)
            .append(field.getModifierStr())
            .append(' ')
            .append(removeQualifierFromBaseTypes(field.getTypeStr()))
            .append(' ');
        super.append(field.getFieldName());
        if (field.getConstFinalValue() != null) {
            super.append(" = ");
            if (field.getConstFinalValue() instanceof String) {
                super.append('"').append(field.getConstFinalValue().toString()).append('"');
            } else if (field.getConstFinalValue() instanceof Character) {
                super.append('\'').append(field.getConstFinalValue().toString()).append('\'');
            } else {
                super.append(field.getConstFinalValue().toString());
            }
        }
        super.println();
    }

    private static String asAnnotations(Collection<String> items, String indentation) {
        if (items.isEmpty()) {
            return "";
        }
        return items.stream().map(ApiPrintWriter::removePackageName).collect(joining(System.lineSeparator() + indentation + '@', indentation + "@", System.lineSeparator()));
    }

    private static String removePackageName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static String stringOf(Collection<ClassInfo> items) {
        return items.stream().map(ClassInfo::getClassName).collect(joining(", "));
    }

    private static String removeQualifierFromBaseTypes(String className) {
        return className.replace("java.lang.", "");
    }

    private static String removeQualifierFromBaseTypes(TypeSignature typeSignature) {
        return removeQualifierFromBaseTypes(typeSignature.toString());
    }
}
