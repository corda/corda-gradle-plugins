package net.corda.plugins.javac.quasar;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class PluginTest {

    private static class ClassFile extends SimpleJavaFileObject {

        private ByteArrayOutputStream out;

        public ClassFile(URI uri) {
            super(uri, Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return out = new ByteArrayOutputStream();
        }

        public byte[] getCompiledBinaries() {
            return out.toByteArray();
        }
    }

    private static class SourceFile extends SimpleJavaFileObject {
        public SourceFile(URI uri) {
            super(uri, Kind.SOURCE);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            Reader r = new InputStreamReader(uri.toURL().openStream());
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[0x1000];
            while (true) {
                int read = r.read(buffer);
                if(read < 0) break;
                sb.append(buffer, 0, read);
            }
            return sb.toString();
        }
    }

    private static class FileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {

        private final List<ClassFile> compiled = new ArrayList<>();

        protected FileManager(StandardJavaFileManager fileManager) {
            super(fileManager);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location,
                                                   String className,
                                                   JavaFileObject.Kind kind,
                                                   FileObject sibling) {
            ClassFile result = new ClassFile(URI.create("string://" + className));
            compiled.add(result);
            return result;
        }

        public List<ClassFile> getCompiled() {
            return compiled;
        }
    }

    private Optional<Iterable<Diagnostic<? extends JavaFileObject>>> compile(Iterable<URI> sources) {
        StringWriter output = new StringWriter();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        FileManager fileManager =
                new FileManager(compiler.getStandardFileManager(null, null, null));
        List<JavaFileObject> compilationUnits = StreamSupport.stream(sources.spliterator(), false)
                .map(SourceFile::new).collect(Collectors.toList());
        List<String> arguments = Arrays.asList("-classpath", System.getProperty("test.compilation.classpath"),
                "-Xplugin:" + SuspendableChecker.class.getName());
        final ArrayList<Diagnostic<? extends JavaFileObject>> compilerMessages = new ArrayList<>();
        JavaCompiler.CompilationTask task = compiler.getTask(
                output,
                fileManager,
                compilerMessages::add,
                arguments,
                null,
                compilationUnits);
        if(task.call()) return Optional.empty();
        else return Optional.of(compilerMessages);
    }

    private enum CompilationResult {
        SUCCESS, FAILURE
    }

    private static class TestCaseProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            String prefix = "net/corda/plugins/javac/quasar/test/";
            return Stream.of(
                Arguments.of(prefix + "TestCase1.java", CompilationResult.SUCCESS),
                Arguments.of(prefix + "TestCase2.java", CompilationResult.FAILURE),
                Arguments.of(prefix + "TestCase3.java", CompilationResult.SUCCESS),
                Arguments.of(prefix + "TestCase4.java", CompilationResult.FAILURE),
                Arguments.of(prefix + "TestCase5.java", CompilationResult.FAILURE),
                Arguments.of(prefix + "TestCase6.java", CompilationResult.FAILURE),
                Arguments.of(prefix + "TestCase7.java", CompilationResult.SUCCESS),
                Arguments.of(prefix + "TestCase8.java", CompilationResult.FAILURE),
                Arguments.of(prefix + "TestCase9.java", CompilationResult.FAILURE),
                Arguments.of(prefix + "TestCase10.java", CompilationResult.FAILURE),
                Arguments.of(prefix + "TestCase11.java", CompilationResult.FAILURE),
                Arguments.of(prefix + "TestCase12.java", CompilationResult.FAILURE),
                Arguments.of(prefix + "TestCase13.java", CompilationResult.SUCCESS),
                Arguments.of(prefix + "TestCase14.java", CompilationResult.SUCCESS),
                Arguments.of(prefix + "TestCase15.java", CompilationResult.FAILURE)
            );
        }
    }

    @DisplayName("Display name of container")
    @ParameterizedTest(name="{0}")
    @ArgumentsSource(TestCaseProvider.class)
    public void test(String sourceFilePath, CompilationResult expectedCompilationResult) {
        Optional<Iterable<Diagnostic<? extends JavaFileObject>>> result;
        try {
            ClassLoader cl = getClass().getClassLoader();
            result = compile(Collections.singletonList(cl.getResource(sourceFilePath).toURI()));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        result.ifPresent(diagnostics -> {
            for(Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
                System.err.printf("%s:%s %s\n",
                        diagnostic.getSource().getName(),
                        diagnostic.getLineNumber(),
                        diagnostic.getMessage(Locale.getDefault()));
            }
        });
        switch (expectedCompilationResult) {
            case SUCCESS:
                Assertions.assertFalse(result.isPresent(), "Compilation was expected to succeed but it has failed");
                break;
            case FAILURE:
                Assertions.assertTrue(result.isPresent(), "Compilation was expected to fail but it succeeded");
                Optional<Diagnostic<? extends JavaFileObject>> illegalSuspendableInvocationMessage = StreamSupport.stream(
                        result.get().spliterator(), false)
                .filter( diagnostic ->
                    Objects.equals("Invocation of suspendable method from non suspendable method", diagnostic.getMessage(Locale.ENGLISH))
                ).findFirst();
                Assertions.assertTrue(illegalSuspendableInvocationMessage.isPresent());
                break;
        }
    }
}
