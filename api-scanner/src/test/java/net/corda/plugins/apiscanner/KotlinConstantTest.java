package net.corda.plugins.apiscanner;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
class KotlinConstantTest {
    private GradleProject testProject;

    @BeforeAll
    void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "kotlin-constant")
            .withResource("kotlin.gradle")
            .build();
    }

    @Test
    void testConstantField() throws IOException {
        assertThat(testProject.getApiLines())
            .containsSequence(
                "public final class net.corda.example.HasConstantField extends java.lang.Object",
                "  public <init>()",
                "  @NotNull",
                "  public static final net.corda.example.HasConstantField$Companion Companion",
                "  @NotNull",
                "  public static final String stringValue = \"Goodbye, Cruel World\"",
                "##"
            ).containsSequence(
                "public static final class net.corda.example.HasConstantField$Companion extends java.lang.Object",
                "  public <init>(kotlin.jvm.internal.DefaultConstructorMarker)",
                "##"
            );
    }
}
