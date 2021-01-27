package net.corda.flask.launcher;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class JavaProcessBuilderTest {

    private static class JavaArgumentFileTestCaseProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(Collections.emptyList(), ""),
                    Arguments.of(Arrays.asList("\""), "\"\\\"\""),
                    Arguments.of(Arrays.asList("a", "b"), "\"a\" \"b\""),
                    Arguments.of(Arrays.asList("a b"), "\"a b\""),
                    Arguments.of(Arrays.asList("-Xmx8G"), "\"-Xmx8G\""),
                    Arguments.of(Arrays.asList("\"-Xmx8G\""), "\"\\\"-Xmx8G\\\"\""),
                    Arguments.of(Arrays.asList("-Xmx8G", "-javaagent:agent-1.0.jar"), "\"-Xmx8G\" \"-javaagent:agent-1.0.jar\""),
                    Arguments.of(Arrays.asList("-Xmx8G -javaagent:agent-1.0.jar"), "\"-Xmx8G -javaagent:agent-1.0.jar\""),
                    Arguments.of(Arrays.asList("-Dsome.property=\"some\tvalue\""), "\"-Dsome.property=\\\"some\\tvalue\\\"\""),
                    Arguments.of(Arrays.asList("-Dsome.property=\"some\nvalue\""), "\"-Dsome.property=\\\"some\\nvalue\\\"\""),
                    Arguments.of(Arrays.asList("-Dsome.property=\"some value\""), "\"-Dsome.property=\\\"some value\\\"\"")
            );
        }
    }

    @DisplayName("Escape string list test")
    @ParameterizedTest(name="String: {0}")
    @ArgumentsSource(JavaArgumentFileTestCaseProvider.class)
    void javaArgumentFileTest(List<String> strings, String expectedResult) {
        Assertions.assertEquals(expectedResult, JavaProcessBuilder.generateArgumentFileString(strings));
    }
}
