package net.corda.plugins.apiscanner;

import io.github.classgraph.AnnotationInfoList;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.FieldInfo;
import io.github.classgraph.MethodInfo;

import java.io.Closeable;
import java.util.List;

interface ApiWriter extends Closeable {
    void println(ClassInfo classInfo, int modifierMask, List<String> filteredAnnotations);
    void println(MethodInfo method, AnnotationInfoList visibleAnnotations, String indentation);
    void println(FieldInfo field, AnnotationInfoList visibleAnnotations, String indentation);
    void endOfClass();
}
