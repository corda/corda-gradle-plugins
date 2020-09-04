package net.corda.plugins.javac.quasar;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.lang.reflect.Method;
import java.util.stream.Stream;

class JvmSignatureTest {

    private static class TestCaseProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws NoSuchMethodException {
            return Stream.of(
                    Arguments.of(String.class, String.class.getMethod("substring", int.class),
                            "java.lang.String.substring(I)"),
                    Arguments.of(String.class, String.class.getMethod("copyValueOf", char[].class, int.class, int.class),
                            "java.lang.String.copyValueOf([CII)"),
                    Arguments.of(String.class, String.class.getMethod("join", CharSequence.class, Iterable.class),
                            "java.lang.String.join(Ljava/lang/CharSequence;Ljava/lang/Iterable;)")
            );
        }
    }

    @ParameterizedTest
    @ArgumentsSource(TestCaseProvider.class)
    public void test(Class<?> cls, Method method, String expected) {
        JvmTypeSignature signatureGenerator = new JvmTypeSignature(cls);
        Assertions.assertEquals(expected, signatureGenerator.getSignature(method));
    }
}