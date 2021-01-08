package net.corda.flask.common;

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

public class SplitManifestStringListTest {

    private static class TestCaseProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                Arguments.of("", Collections.emptyList()),
                Arguments.of("\"", Arrays.asList("\"")),
                Arguments.of("\\\\\"", Arrays.asList("\\\"")),
                Arguments.of("-Xmx8G", Arrays.asList("-Xmx8G")),
                Arguments.of("\"-Xmx8G\"", Arrays.asList("\"-Xmx8G\"")),
                Arguments.of("-Xmx8G -javaagent:agent-1.0.jar", Arrays.asList("-Xmx8G", "-javaagent:agent-1.0.jar")),
                Arguments.of("-Xmx8G\\ -javaagent:agent-1.0.jar", Arrays.asList("-Xmx8G -javaagent:agent-1.0.jar")),
                Arguments.of("-Dsome.property=\"some\tvalue\"", Arrays.asList("-Dsome.property=\"some\tvalue\"")),
                Arguments.of("-Dsome.property=\"some\\tvalue\"", Arrays.asList("-Dsome.property=\"some\tvalue\"")),
                Arguments.of("-Dsome.property=\"some\nvalue\"", Arrays.asList("-Dsome.property=\"some\nvalue\"")),
                Arguments.of("-Dsome.property=\"some\\nvalue\"", Arrays.asList("-Dsome.property=\"some\nvalue\"")),
                Arguments.of("-Dsome.property=\"some value\"", Arrays.asList("-Dsome.property=\"some", "value\"")),
                Arguments.of("-Dsome.property=\"some\\ value\"", Arrays.asList("-Dsome.property=\"some value\""))
            );
        }
    }

    @DisplayName("Split manifest string list test")
    @ParameterizedTest(name="String: {0}")
    @ArgumentsSource(TestCaseProvider.class)
    void test(String quotedString, Object expectedResult) {
        if(expectedResult instanceof List) {
            Assertions.assertEquals(expectedResult, ManifestEscape.splitManifestStringList(quotedString));
        } else if(expectedResult instanceof Class && Throwable.class.isAssignableFrom((Class<?>)expectedResult)) {
            Assertions.assertThrows((Class<? extends Throwable>) expectedResult, () -> ManifestEscape.splitManifestStringList(quotedString));
        } else {
            throw new IllegalArgumentException(String.format("Unsupported test argument '%s'", expectedResult));
        }
    }
}
