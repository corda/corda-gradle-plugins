package net.corda.flask.launcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaProcessBuilderTest {

    private static class JavaArgumentFileTestCaseProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    Arguments.of(emptyList(), ""),
                    Arguments.of(singletonList("\""), "\"\\\"\""),
                    Arguments.of(asList("a", "b"), "\"a\" \"b\""),
                    Arguments.of(singletonList("a b"), "\"a b\""),
                    Arguments.of(singletonList("-Xmx8G"), "\"-Xmx8G\""),
                    Arguments.of(singletonList("\"-Xmx8G\""), "\"\\\"-Xmx8G\\\"\""),
                    Arguments.of(asList("-Xmx8G", "-javaagent:agent-1.0.jar"), "\"-Xmx8G\" \"-javaagent:agent-1.0.jar\""),
                    Arguments.of(singletonList("-Xmx8G -javaagent:agent-1.0.jar"), "\"-Xmx8G -javaagent:agent-1.0.jar\""),
                    Arguments.of(singletonList("-Dsome.property=\"some\tvalue\""), "\"-Dsome.property=\\\"some\\tvalue\\\"\""),
                    Arguments.of(singletonList("-Dsome.property=\"some\nvalue\""), "\"-Dsome.property=\\\"some\\nvalue\\\"\""),
                    Arguments.of(singletonList("-Dsome.property=\"some value\""), "\"-Dsome.property=\\\"some value\\\"\"")
            );
        }
    }

    @DisplayName("Escape string list test")
    @ParameterizedTest(name="String: {0}")
    @ArgumentsSource(JavaArgumentFileTestCaseProvider.class)
    void javaArgumentFileTest(List<String> strings, String expectedResult) {
        assertEquals(expectedResult, JavaProcessBuilder.generateArgumentFileString(strings));
    }
}
