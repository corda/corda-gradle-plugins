package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class KotlinAnnotationsTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "kotlin-annotations");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    private String[] expectedClassWithDeprecatedFunctions = new String[] {
        "public final class net.corda.example.HasDeprecatedFunctions extends java.lang.Object",
        "  public <init>()",
        "  @NotNull",
        "  public final String doSomething()",
        "##"
    };

    private String[] expectedClassWithJvmField = new String[] {
        "public final class net.corda.example.HasJvmField extends java.lang.Object",
        "  public <init>()",
        "  @NotNull",
        "  public final String stringValue = \"Hello World\"",
        "##"
    };

    private String[] expectedClassWithJvmStaticFunction = new String[]{
        "public final class net.corda.example.HasJvmStaticFunction extends java.lang.Object",
        "  public <init>()",
        "  public static final void doThing(String)",
        "  public static final net.corda.example.HasJvmStaticFunction$Companion Companion",
        "##"
    };

    private String[] expectedClassWithJvmStaticFunctionCompanion = new String[] {
        "public static final class net.corda.example.HasJvmStaticFunction$Companion extends java.lang.Object",
        "  public final void doThing(String)",
        "##"
    };

    private String[] expectedClassWithOverloadedConstructor = new String[] {
        "public final class net.corda.example.HasOverloadedConstructor extends java.lang.Object",
        "  public <init>()",
        "  public <init>(String)",
        "  public <init>(String, String)",
        "  public <init>(String, String, int)",
        "  @NotNull",
        "  public final String getNotNullable()",
        "  @Nullable",
        "  public final String getNullable()",
        "  public final int getNumber()",
        "##"
    };

    @Test
    public void testKotlinAnnotations() throws IOException {
        assertThat(Files.readAllLines(testProject.getApi()))
            .containsSequence(expectedClassWithDeprecatedFunctions)
            .containsSequence(expectedClassWithJvmField)
            .containsSequence(expectedClassWithJvmStaticFunction)
            .containsSequence(expectedClassWithJvmStaticFunctionCompanion)
            .containsSequence(expectedClassWithOverloadedConstructor);
    }
}
