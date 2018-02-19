package net.corda.plugins;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.scanner.ClassInfo;
import io.github.lukehutch.fastclasspathscanner.scanner.FieldInfo;
import io.github.lukehutch.fastclasspathscanner.scanner.MethodInfo;
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.stream.StreamSupport;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

@SuppressWarnings("unused")
public class ScanApi extends DefaultTask {
    private static final int CLASS_MASK = Modifier.classModifiers();
    private static final int INTERFACE_MASK = Modifier.interfaceModifiers() & ~Modifier.ABSTRACT;
    private static final int METHOD_MASK = Modifier.methodModifiers();
    private static final int FIELD_MASK = Modifier.fieldModifiers();
    private static final int VISIBILITY_MASK = Modifier.PUBLIC | Modifier.PROTECTED;

    private static final String CORDA_INTERNAL = "net.corda.core.CordaInternal";
    private static final Set<String> ANNOTATION_BLACKLIST;
    static {
       Set<String> blacklist = new LinkedHashSet<>();
       blacklist.add("kotlin.jvm.JvmOverloads");
       blacklist.add(CORDA_INTERNAL);
       ANNOTATION_BLACKLIST = unmodifiableSet(blacklist);
    }

    /**
     * This information has been lifted from:
     * @link <a href="https://github.com/JetBrains/kotlin/blob/master/core/runtime.jvm/src/kotlin/Metadata.kt">Metadata.kt</a>
     */
    private static final String KOTLIN_METADATA = "kotlin.Metadata";
    private static final String KOTLIN_CLASSTYPE_METHOD = "k";
    private static final int KOTLIN_SYNTHETIC = 3;

    private final ConfigurableFileCollection sources;
    private final ConfigurableFileCollection classpath;
    private final Set<String> excludeClasses;
    private final File outputDir;
    private boolean verbose;

    public ScanApi() {
        sources = getProject().files();
        classpath = getProject().files();
        excludeClasses = new LinkedHashSet<>();
        outputDir = new File(getProject().getBuildDir(), "api");
    }

    @InputFiles
    public FileCollection getSources() {
        return sources;
    }

    void setSources(FileCollection sources) {
        this.sources.setFrom(sources);
    }

    @CompileClasspath
    @InputFiles
    public FileCollection getClasspath() {
        return classpath;
    }

    void setClasspath(FileCollection classpath) {
        this.classpath.setFrom(classpath);
    }

    @Input
    public Collection<String> getExcludeClasses() {
        return unmodifiableSet(excludeClasses);
    }

    void setExcludeClasses(Collection<String> excludeClasses) {
        this.excludeClasses.clear();
        this.excludeClasses.addAll(excludeClasses);
    }

    @OutputFiles
    public FileCollection getTargets() {
        return getProject().files(
            StreamSupport.stream(sources.spliterator(), false)
                .map(this::toTarget)
                .collect(toList())
        );
    }

    @Input
    public boolean isVerbose() {
        return verbose;
    }

    void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    private File toTarget(File source) {
        return new File(outputDir, source.getName().replaceAll("\\.jar$", ".txt"));
    }

    @TaskAction
    public void scan() {
        try (Scanner scanner = new Scanner(classpath)) {
            for (File source : sources) {
                scanner.scan(source);
            }
        } catch (IOException e) {
            getLogger().error("Failed to write API file", e);
        }
    }

    class Scanner implements Closeable {
        private final URLClassLoader classpathLoader;
        private final Class<? extends Annotation> metadataClass;
        private final Method classTypeMethod;
        private Collection<String> invisibleAnnotations;

        @SuppressWarnings("unchecked")
        Scanner(URLClassLoader classpathLoader) {
            this.classpathLoader = classpathLoader;
            this.invisibleAnnotations = ANNOTATION_BLACKLIST;

            Class<? extends Annotation> kClass;
            Method kMethod;
            try {
                kClass = (Class<Annotation>) Class.forName(KOTLIN_METADATA, true, classpathLoader);
                kMethod = kClass.getDeclaredMethod(KOTLIN_CLASSTYPE_METHOD);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                kClass = null;
                kMethod = null;
            }

            metadataClass = kClass;
            classTypeMethod = kMethod;
        }

        Scanner(FileCollection classpath) throws MalformedURLException {
            this(new URLClassLoader(toURLs(classpath)));
        }

        @Override
        public void close() throws IOException {
            classpathLoader.close();
        }

        void scan(File source) {
            File target = toTarget(source);
            getLogger().info("API file: {}", target.getAbsolutePath());
            try (
                URLClassLoader appLoader = new URLClassLoader(new URL[]{ toURL(source) }, classpathLoader);
                PrintWriter writer = new PrintWriter(target, "UTF-8")
            ) {
                scan(writer, appLoader);
            } catch (IOException e) {
                getLogger().error("API scan has failed", e);
            }
        }

        void scan(PrintWriter writer, ClassLoader appLoader) {
            ScanResult result = new FastClasspathScanner(getScanSpecification())
                .overrideClassLoaders(appLoader)
                .ignoreParentClassLoaders()
                .ignoreMethodVisibility()
                .ignoreFieldVisibility()
                .enableMethodInfo()
                .enableFieldInfo()
                .verbose(verbose)
                .scan();
            setInvisibleAnnotations(result);
            writeApis(writer, result);
        }

        private String[] getScanSpecification() {
            String[] spec = new String[2 + excludeClasses.size()];
            spec[0] = "!";     // Don't blacklist system classes from the output.
            spec[1] = "-dir:"; // Ignore classes on the filesystem.

            int i = 2;
            for (String excludeClass : excludeClasses) {
                spec[i++] = '-' + excludeClass;
            }
            return spec;
        }

        private void setInvisibleAnnotations(ScanResult result) {
            Set<String> invisible = new LinkedHashSet<>(ANNOTATION_BLACKLIST);
            invisible.addAll(result.getNamesOfAnnotationsWithMetaAnnotation(CORDA_INTERNAL));
            invisibleAnnotations = unmodifiableSet(invisible);
        }

        private void writeApis(PrintWriter writer, ScanResult result) {
            Map<String, ClassInfo> allInfo = result.getClassNameToClassInfo();
            result.getNamesOfAllClasses().forEach(className -> {
                if (className.contains(".internal.")) {
                    // These classes belong to internal Corda packages.
                    return;
                }
                if (className.contains("$$inlined$")) {
                    /*
                     * These classes are internally generated by the Kotlin compiler
                     * and are not exposed as part of the public API
                     * TODO: Filter out using EnclosingMethod attribute in classfile
                     */
                    return;
                }
                ClassInfo classInfo = allInfo.get(className);
                if (classInfo.getClassLoaders() == null) {
                    // Ignore classes that belong to one of our target ClassLoader's parents.
                    return;
                }

                if (classInfo.isAnnotation() && !isVisibleAnnotation(className)) {
                    // Exclude these annotations from the output,
                    // e.g. because they're internal to Kotlin or Corda.
                    return;
                }

                Class<?> javaClass = result.classNameToClassRef(className);
                if (!isVisible(javaClass.getModifiers())) {
                    // Excludes private and package-protected classes
                    return;
                }

                int kotlinClassType = getKotlinClassType(javaClass);
                if (kotlinClassType == KOTLIN_SYNTHETIC) {
                    // Exclude classes synthesised by the Kotlin compiler.
                    return;
                }

                writeClass(writer, classInfo, javaClass.getModifiers());
                writeMethods(writer, classInfo.getMethodAndConstructorInfo());
                writeFields(writer, classInfo.getFieldInfo());
                writer.println("##");
            });
        }

        private void writeClass(PrintWriter writer, ClassInfo classInfo, int modifiers) {
            if (classInfo.isAnnotation()) {
                /*
                 * Annotation declaration.
                 */
                writer.append(Modifier.toString(modifiers & INTERFACE_MASK));
                writer.append(" @interface ").print(classInfo);
            } else if (classInfo.isStandardClass()) {
                /*
                 * Class declaration.
                 */
                Names annotationNames = toNames(readClassAnnotationsFor(classInfo));
                if (!annotationNames.visible.isEmpty()) {
                    writer.append(asAnnotations(annotationNames.visible));
                }
                writer.append(Modifier.toString(modifiers & CLASS_MASK));
                writer.append(" class ").print(classInfo);
                Set<ClassInfo> superclasses = classInfo.getDirectSuperclasses();
                if (!superclasses.isEmpty()) {
                    writer.append(" extends ").print(stringOf(superclasses));
                }
                Set<ClassInfo> interfaces = classInfo.getDirectlyImplementedInterfaces();
                if (!interfaces.isEmpty()) {
                    writer.append(" implements ").print(stringOf(interfaces));
                }
            } else {
                /*
                 * Interface declaration.
                 */
                Names annotationNames = toNames(readInterfaceAnnotationsFor(classInfo));
                if (!annotationNames.visible.isEmpty()) {
                    writer.append(asAnnotations(annotationNames.visible));
                }
                writer.append(Modifier.toString(modifiers & INTERFACE_MASK));
                writer.append(" interface ").print(classInfo);
                Set<ClassInfo> superinterfaces = classInfo.getDirectSuperinterfaces();
                if (!superinterfaces.isEmpty()) {
                    writer.append(" extends ").print(stringOf(superinterfaces));
                }
            }
            writer.println();
        }

        private void writeMethods(PrintWriter writer, List<MethodInfo> methods) {
            sort(methods);
            for (MethodInfo method : methods) {
                if (isVisible(method.getAccessFlags()) // Only public and protected methods
                        && isValid(method.getAccessFlags(), METHOD_MASK) // Excludes bridge and synthetic methods
                        && !hasCordaInternal(method.getAnnotationNames()) // Excludes methods annotated as @CordaInternal
                        && !isKotlinInternalScope(method)) {
                    writer.append("  ").println(filterAnnotationsFor(method));
                }
            }
        }

        private void writeFields(PrintWriter output, List<FieldInfo> fields) {
            sort(fields);
            for (FieldInfo field : fields) {
                if (isVisible(field.getAccessFlags()) && isValid(field.getAccessFlags(), FIELD_MASK)) {
                    output.append("  ").println(field);
                }
            }
        }

        private int getKotlinClassType(Class<?> javaClass) {
            if (metadataClass != null) {
                Annotation metadata = javaClass.getAnnotation(metadataClass);
                if (metadata != null) {
                    try {
                        return (int) classTypeMethod.invoke(metadata);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        getLogger().error("Failed to read Kotlin annotation", e);
                    }
                }
            }
            return 0;
        }

        private Names toNames(Collection<ClassInfo> classes) {
            Map<Boolean, List<String>> partitioned = classes.stream()
                .map(ClassInfo::toString)
                .filter(ScanApi::isApplicationClass)
                .collect(partitioningBy(this::isVisibleAnnotation));
            return new Names(partitioned.get(true), partitioned.get(false));
        }

        private Set<ClassInfo> readClassAnnotationsFor(ClassInfo classInfo) {
            Set<ClassInfo> annotations = new HashSet<>(classInfo.getAnnotations());
            annotations.addAll(selectInheritedAnnotations(classInfo.getSuperclasses()));
            annotations.addAll(selectInheritedAnnotations(classInfo.getImplementedInterfaces()));
            return annotations;
        }

        private Set<ClassInfo> readInterfaceAnnotationsFor(ClassInfo classInfo) {
            Set<ClassInfo> annotations = new HashSet<>(classInfo.getAnnotations());
            annotations.addAll(selectInheritedAnnotations(classInfo.getSuperinterfaces()));
            return annotations;
        }

        /**
         * Returns those annotations which have themselves been annotated as "Inherited".
         */
        private List<ClassInfo> selectInheritedAnnotations(Collection<ClassInfo> classes) {
            return classes.stream()
                .flatMap(cls -> cls.getAnnotations().stream())
                .filter(ann -> ann.hasMetaAnnotation(Inherited.class.getName()))
                .collect(toList());
        }

        private MethodInfo filterAnnotationsFor(MethodInfo method) {
            return new MethodInfo(
                method.getClassName(),
                method.getMethodName(),
                method.getAccessFlags(),
                method.getTypeDescriptor(),
                method.getAnnotationNames().stream()
                    .filter(this::isVisibleAnnotation)
                    .collect(toList())
            );
        }

        private boolean isVisibleAnnotation(String annotationName) {
            return !invisibleAnnotations.contains(annotationName);
        }
    }

    private static boolean isKotlinInternalScope(MethodInfo method) {
        return method.getMethodName().indexOf('$') >= 0;
    }

    private static boolean hasCordaInternal(Collection<String> annotationNames) {
        return annotationNames.contains(CORDA_INTERNAL);
    }

    private static boolean isValid(int modifiers, int mask) {
        return (modifiers & mask) == modifiers;
    }

    private static boolean isVisible(int accessFlags) {
        return (accessFlags & VISIBILITY_MASK) != 0;
    }

    private static String stringOf(Collection<ClassInfo> items) {
        return items.stream().map(ClassInfo::toString).collect(joining(", "));
    }

    private static String asAnnotations(Collection<String> items) {
        return items.stream().collect(joining(" @", "@", " "));
    }

    private static boolean isApplicationClass(String typeName) {
        return !typeName.startsWith("java.") && !typeName.startsWith("kotlin.");
    }

    private static URL toURL(File file) throws MalformedURLException {
        return file.toURI().toURL();
    }

    private static URL[] toURLs(Iterable<File> files) throws MalformedURLException {
        List<URL> urls = new LinkedList<>();
        for (File file : files) {
            urls.add(toURL(file));
        }
        return urls.toArray(new URL[urls.size()]);
    }
}

class Names {
    List<String> visible;
    @SuppressWarnings("WeakerAccess") List<String> hidden;

    Names(List<String> visible, List<String> hidden) {
        this.visible = unmodifiable(visible);
        this.hidden = unmodifiable(hidden);
    }

    private static <T> List<T> unmodifiable(List<T> list) {
        return list.isEmpty() ? emptyList() : unmodifiableList(new ArrayList<>(list));
    }
}