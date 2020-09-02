package net.corda.plugins.apiscanner;

import io.github.classgraph.*;
import nonapi.io.github.classgraph.types.TypeUtils;

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
class JsonApiPrintWriter extends PrintWriter implements ApiWriter {
    private static final int METHOD_MASK = Modifier.methodModifiers() | Modifier.TRANSIENT;

    JsonApiPrintWriter(File file, String encoding) throws FileNotFoundException, UnsupportedEncodingException {
        super(file, encoding);
    }

    public void println(ClassInfo classInfo, int modifierMask, List<String> filteredAnnotations) {
        append(asAnnotations(filteredAnnotations));

        // Next line outputs
        // public
        // protected
        // private
        // abstract
        // static
        // final
        // transient
        // volatile
        // synchronized
        // native
        // strictfp
        // interface
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

    public void println(MethodInfo method, AnnotationInfoList visibleAnnotations, String indentation) {
        append(asAnnotations(visibleAnnotations.getNames()));
        append(indentation).append(pureModifiersFor(method)).append(' ');
        if (!method.isConstructor()) {
            append(removeQualifierFromBaseTypes(method.getTypeSignatureOrTypeDescriptor().getResultType())).append(' ');
        }
        append(method.getName()).append('(');
        LinkedList<String> paramTypes = Arrays.stream(method.getParameterInfo())
            .map(MethodParameterInfo::getTypeSignatureOrTypeDescriptor)
            .map(JsonApiPrintWriter::removeQualifierFromBaseTypes)
            .collect(toCollection(LinkedList::new));
        //if parameter is varargs, remove the array [] qualifier and replace with ellipsis
        if (method.isVarArgs() && !paramTypes.isEmpty()) {
            String vararg = paramTypes.removeLast();
            paramTypes.add(vararg.substring(0, vararg.length() - 2) + "...");
        }
        append(String.join(", ", paramTypes));
        println(')');
    }

    public void println(FieldInfo field, AnnotationInfoList visibleAnnotations, String indentation) {
        append(asAnnotations(visibleAnnotations.getNames()))
            .append(indentation)
            .append(field.getModifierStr())
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

    @Override
    public void endOfClass() {
        throw new UnsupportedOperationException();
    }

    private static String asAnnotations(Collection<String> items) {
        // DONE
        if (items.isEmpty()) {
            return "\"annotations\": [],";
        }
        return String.format("\"annotations\": [%s],",
                items.stream()
                        .map(className -> String.format("\"%s\"", removePackageName(className)))
                        .collect(joining(",")));
    }

    private static String pureModifiersFor(MethodInfo method) {
        StringBuilder builder = new StringBuilder();
        TypeUtils.modifiersToString(method.getModifiers() & METHOD_MASK, METHOD, false, builder);
        return builder.toString();
    }

    private static String removePackageName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static String stringOf(Collection<ClassInfo> items) {
        return items.stream().map(ClassInfo::getName).sorted().collect(joining(", "));
    }

    private static String removeQualifierFromBaseTypes(String className) {
        return className.replace("java.lang.", "");
    }

    private static String removeQualifierFromBaseTypes(TypeSignature typeSignature) {
        return removeQualifierFromBaseTypes(typeSignature.toString());
    }
}
