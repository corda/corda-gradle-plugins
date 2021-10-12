package net.corda.plugins.apiscanner;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class KotlinAnnotationsTest {
    private static GradleProject testProject;

    @BeforeAll
    static void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "kotlin-annotations")
            .withResource("kotlin.gradle")
            .build();
    }

    private static final String[] expectedClassWithDeprecatedFunctions = {
        "public final class net.corda.example.HasDeprecatedFunctions extends java.lang.Object",
        "  public <init>()",
        "  @NotNull",
        "  public final String doSomething()",
        "##"
    };

    private static final String[] expectedClassWithJvmField = {
        "public final class net.corda.example.HasJvmField extends java.lang.Object",
        "  public <init>()",
        "  @NotNull",
        "  public final String stringValue",
        "##"
    };

    private static final String[] expectedClassWithJvmStaticFunction = {
        "public final class net.corda.example.HasJvmStaticFunction extends java.lang.Object",
        "  public <init>()",
        "  public static final void doThing(String)",
        "  @NotNull",
        "  public static final net.corda.example.HasJvmStaticFunction$Companion Companion",
        "##"
    };

    private static final String[] expectedClassWithJvmStaticFunctionCompanion = {
        "public static final class net.corda.example.HasJvmStaticFunction$Companion extends java.lang.Object",
        "  public <init>(kotlin.jvm.internal.DefaultConstructorMarker)",
        "  public final void doThing(String)",
        "##"
    };

    private static final String[] expectedClassWithOverloadedConstructor = {
        "public final class net.corda.example.HasOverloadedConstructor extends java.lang.Object",
        "  public <init>()",
        "  public <init>(String)",
        "  public <init>(String, String)",
        "  public <init>(String, String, int)",
        "  public <init>(String, String, int, int, kotlin.jvm.internal.DefaultConstructorMarker)",
        "  @NotNull",
        "  public final String getNotNullable()",
        "  @Nullable",
        "  public final String getNullable()",
        "  public final int getNumber()",
        "##"
    };

    @Test
    void testDeprecatedAnnotation() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(expectedClassWithDeprecatedFunctions);
    }

    @Test
    void testJvmFieldAnnotation() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(expectedClassWithJvmField);
    }

    @Test
    void testJvmStaticFunctionAnnotation() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(expectedClassWithJvmStaticFunction)
            .containsSequence(expectedClassWithJvmStaticFunctionCompanion);
    }

    @Test
    void testJvmStaticFieldAnnotation() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "public final class net.corda.example.HasJvmStaticField extends java.lang.Object",
                "  public <init>()",
                "  @NotNull",
                "  public static final String getStringValue()",
                "  @NotNull",
                "  public static final net.corda.example.HasJvmStaticField$Companion Companion",
                "##"
            ).containsSequence(
                "public static final class net.corda.example.HasJvmStaticField$Companion extends java.lang.Object",
                "  public <init>(kotlin.jvm.internal.DefaultConstructorMarker)",
                "  @NotNull",
                "  public final String getStringValue()",
                "##"
            );
    }

    @Test
    void testJvmOverloadedAnnotation() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(expectedClassWithOverloadedConstructor);
    }

    @Test
    void testJvmDefaultAnnotation() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "public interface net.corda.example.HasDefaultMethod",
                 "  public void doSomething(String)",
                 "##"
            );
    }

}
