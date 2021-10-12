package net.corda.plugins.apiscanner;

import io.github.classgraph.AnnotationInfoList;
import io.github.classgraph.BaseTypeSignature;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ClassRefTypeSignature;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.TypeSignature;
import io.github.classgraph.TypeVariableSignature;
import nonapi.io.github.classgraph.types.TypeUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toCollection;
import static nonapi.io.github.classgraph.types.TypeUtils.ModifierType.METHOD;

@SuppressWarnings("SameParameterValue")
class ApiPrintWriter extends PrintWriter {
    private static final int METHOD_MASK = Modifier.methodModifiers() | Modifier.TRANSIENT;

    ApiPrintWriter(File file, String encoding) throws FileNotFoundException, UnsupportedEncodingException {
        super(file, encoding);
    }

    void println(@Nonnull ClassInfo classInfo, int modifierMask, List<String> filteredAnnotations) {
        append(asAnnotations(filteredAnnotations, ""));
        append(Modifier.toString(classInfo.loadClass().getModifiers() & modifierMask));
        if (classInfo.isAnnotation()) {
            /*
             * Annotation declaration.
             */
            append(" @interface ").print(classInfo.getName());
        } else if (classInfo.isStandardClass()) {
            /*
             * Class declaration.
             */
            append(" class ").print(classInfo.getName());
            append(" extends ");
            ClassInfo superclass = classInfo.getSuperclass();
            print(superclass == null ? "java.lang.Object" : superclass.getName());
            ClassInfoList interfaces = classInfo.getInterfaces().getImplementedInterfaces().directOnly();
            if (!interfaces.isEmpty()) {
                append(" implements ").print(stringOf(interfaces));
            }
        } else {
            /*
             * Interface declaration.
             */
            append(" interface ").print(classInfo.getName());
            ClassInfoList superinterfaces = classInfo.getInterfaces().directOnly();
            if (!superinterfaces.isEmpty()) {
                append(" extends ").print(stringOf(superinterfaces));
            }
        }
        println();
    }

    void println(@Nonnull MethodInfo method, @Nonnull AnnotationInfoList visibleAnnotations, String indentation) {
        append(asAnnotations(visibleAnnotations.getNames(), indentation));
        append(indentation).append(pureModifiersFor(method)).append(' ');
        if (!method.isConstructor()) {
            append(removeQualifierFromBaseTypes(method.getTypeSignatureOrTypeDescriptor().getResultType())).append(' ');
        }
        append(method.getName()).append('(');
        LinkedList<String> paramTypes = Arrays.stream(method.getParameterInfo())
            .map(MethodParameterInfo::getTypeSignatureOrTypeDescriptor)
            .map(ApiPrintWriter::removeQualifierFromBaseTypes)
            .collect(toCollection(LinkedList::new));
        //if parameter is varargs, remove the array [] qualifier and replace with ellipsis
        if (method.isVarArgs() && !paramTypes.isEmpty()) {
            String vararg = paramTypes.removeLast();
            paramTypes.add(vararg.substring(0, vararg.length() - 2) + "...");
        }
        append(String.join(", ", paramTypes));
        println(')');
    }

    void println(@Nonnull FieldInfo field, @Nonnull AnnotationInfoList visibleAnnotations, String indentation) {
        append(asAnnotations(visibleAnnotations.getNames(), indentation))
            .append(indentation)
            .append(field.getModifiersStr())
            .append(' ')
            .append(removeQualifierFromBaseTypes(field.getTypeSignatureOrTypeDescriptor()))
            .append(' ')
            .append(field.getName());
        Object constantInitializer = field.getConstantInitializerValue();
        if (constantInitializer != null) {
            append(" = ");
            if (constantInitializer instanceof String) {
                append('"').append(constantInitializer.toString()).append('"');
            } else if (constantInitializer instanceof Character) {
                append('\'').append(constantInitializer.toString()).append('\'');
            } else {
                append(constantInitializer.toString());
            }
        }
        println();
    }

    private static String asAnnotations(@Nonnull Collection<String> items, String indentation) {
        if (items.isEmpty()) {
            return "";
        }
        return items.stream().map(ApiPrintWriter::removePackageName)
            .collect(joining(System.lineSeparator() + indentation + '@', indentation + '@', System.lineSeparator()));
    }

    @Nonnull
    private static String pureModifiersFor(@Nonnull MethodInfo method) {
        StringBuilder builder = new StringBuilder();
        TypeUtils.modifiersToString(method.getModifiers() & METHOD_MASK, METHOD, false, builder);
        return builder.toString();
    }

    @Nonnull
    private static String removePackageName(@Nonnull String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    // We cannot trust the output of TypeSignature::toString not to change.
    private static String stringOf(TypeSignature sig) {
        if (sig instanceof BaseTypeSignature) {
            return ((BaseTypeSignature) sig).getTypeStr();
        } else if (sig instanceof ClassRefTypeSignature) {
            return ((ClassRefTypeSignature) sig).getFullyQualifiedClassName();
        } else if (sig instanceof TypeVariableSignature) {
            return ((TypeVariableSignature) sig).getName();
        } else {
            return sig.toString();
        }
    }

    private static String stringOf(@Nonnull Collection<ClassInfo> items) {
        return items.stream().map(ClassInfo::getName).sorted().collect(joining(", "));
    }

    @Nonnull
    private static String removeQualifierFromBaseTypes(@Nonnull String className) {
        return className.replace("java.lang.", "");
    }

    @Nonnull
    private static String removeQualifierFromBaseTypes(@Nonnull TypeSignature typeSignature) {
        return removeQualifierFromBaseTypes(stringOf(typeSignature));
    }
}
