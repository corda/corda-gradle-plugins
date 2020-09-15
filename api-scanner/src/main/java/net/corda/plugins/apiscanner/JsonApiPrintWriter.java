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
        lastWriteWasField = false;
        lastWriteWasMethod = false;

        append('{');
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
        String modifiers = Arrays.stream(Modifier.toString(classInfo.loadClass().getModifiers() & modifierMask).split(" ")).map(s -> String.format("\"%s\":true", s)).collect(joining(",", "", ","));
        append(modifiers);

        append(String.format("\"name\":\"%s\",", classInfo.getName()));

        if (classInfo.isAnnotation()) {
            /*
             * Annotation declaration.
             */
            append("\"isAnnotation\":true,");
        } else if (classInfo.isStandardClass()) {
            /*
             * Class declaration.
             */
            append("\"isClass\":true,");
            append("\"extends\":[");
            ClassInfo superclass = classInfo.getSuperclass();
            print(superclass == null ? "\"java.lang.Object\"" : String.format("\"%s\"", superclass.getName()));
            append("],");

            ClassInfoList interfaces = classInfo.getInterfaces().getImplementedInterfaces().directOnly();
            append("\"implements\":[").printf("%s],", stringOf(interfaces));
        } else {
            /*
             * Interface declaration.
             */
            append("\"isInterface\":true,");
            ClassInfoList superinterfaces = classInfo.getInterfaces().directOnly();
            append("\"extends\":[").print(stringOf(superinterfaces) + "],");
        }
        println();
        println("\"methods\":[");
    }

    public void println(MethodInfo method, AnnotationInfoList visibleAnnotations, String indentation) {
        // DONE
        if (lastWriteWasMethod)
            println(",");
        println("{");
        append(asAnnotations(visibleAnnotations.getNames()));
        append(pureModifiersFor(method));

        if (!method.isConstructor()) {
            append(String.format("\"returns\":\"%s\",", removeQualifierFromBaseTypes(method.getTypeSignatureOrTypeDescriptor().getResultType())));
        }
        append(String.format("\"name\":\"%s\",", method.getName()));


        LinkedList<String> paramTypes = Arrays.stream(method.getParameterInfo())
            .map(MethodParameterInfo::getTypeSignatureOrTypeDescriptor)
            .map(JsonApiPrintWriter::removeQualifierFromBaseTypes)
            .collect(toCollection(LinkedList::new));
        //if parameter is varargs, remove the array [] qualifier and replace with ellipsis
        if (method.isVarArgs() && !paramTypes.isEmpty()) {
            String vararg = paramTypes.removeLast();
            paramTypes.add(vararg.substring(0, vararg.length() - 2) + "...");
        }
        printf("\"parameters\":[%s]", paramTypes.stream().map(s -> String.format("\"%s\"", s)).collect(joining(",")));
        print("}");
        lastWriteWasMethod = true;
        lastWriteWasField = false;
    }

    private boolean lastWriteWasMethod = false;
    private boolean lastWriteWasField = false;

    public void println(FieldInfo field, AnnotationInfoList visibleAnnotations, String indentation) {
        // DONE

        if (lastWriteWasMethod) {
            println("], \"fields\":[");
            lastWriteWasMethod = false;
        }

        if (lastWriteWasField){
            println(",");
        }

        println("{");
        append(asAnnotations(visibleAnnotations.getNames()));

        // Outputs
        // public
        // private
        // protected
        // abstract
        // static
        // volatile
        // transient
        // final
        // synchronized
        // default
        // synthetic
        // bridge
        // native
        // strictfp
        append(Arrays.stream(field.getModifierStr()
                .split(" "))
                .map(s -> String.format("\"%s\":true", s))
                .collect(joining(",", "", ",")));
        append(String.format("\"type\":\"%s\",", removeQualifierFromBaseTypes(field.getTypeSignatureOrTypeDescriptor())));
        Object constantInitializer = field.getConstantInitializerValue();
        if (constantInitializer != null) {
            append("\"value\":");
            if (constantInitializer instanceof String) {
                append('"').append(constantInitializer.toString()).append('"');
            } else if (constantInitializer instanceof Character) {
                append('\'').append(constantInitializer.toString()).append('\'');
            } else {
                append(constantInitializer.toString());
            }
            append(",");
        }
        append(String.format("\"name\":\"%s\"", field.getName()));
        println("}");

        lastWriteWasField = true;
    }

    @Override
    public void endOfClass() {
        // DONE
        append("]},");
    }

    private static String asAnnotations(Collection<String> items) {
        // DONE
//        if (items.isEmpty()) {
//            return "\"annotations\": [],";
//        }
        return String.format("\"annotations\": [%s],",
                items.stream()
                        .map(className -> String.format("\"%s\"", removePackageName(className)))
                        .collect(joining(",")));
    }

    private static String pureModifiersFor(MethodInfo method) {
        // DONE

        StringBuilder builder = new StringBuilder();

        // Outputs:
        // public
        // private
        // protected
        // abstract
        // static
        // volatile
        // transient
        // final
        // synchronized
        // default
        // synthetic
        // bridge
        // native
        // strictfp
        TypeUtils.modifiersToString(method.getModifiers() & METHOD_MASK, METHOD, false, builder);
        String result = Arrays.stream(builder.toString()
                .split(" "))
                .map(s1 -> String.format("\"%s\":true", s1))
                .collect(joining(",", "", ","));

        return result;
    }

    private static String removePackageName(String className) {
        // DONE
        return className.substring(className.lastIndexOf('.') + 1);
    }

    private static String stringOf(Collection<ClassInfo> items) {
        // DONE
        return items.stream()
                .map(classInfo -> String.format("\"%s\"", classInfo.getName()))
                .sorted()
                .collect(joining(", "));
    }

    private static String removeQualifierFromBaseTypes(String className) {
        // DONE
        return className.replace("java.lang.", "");
    }

    private static String removeQualifierFromBaseTypes(TypeSignature typeSignature) {
        // DONE
        return removeQualifierFromBaseTypes(typeSignature.toString());
    }
}
