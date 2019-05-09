package net.corda.plugins;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

public class KotlinLambdasTest {
    private GradleProject testProject;

    @BeforeEach
    public void setup(@TempDir Path testProjectDir) throws IOException {
        testProject = new GradleProject(testProjectDir, "kotlin-lambdas").build();
    }

    @Test
    public void testKotlinLambdas() throws IOException {
        assertThat(testProject.getOutput()).contains("net.corda.example.LambdaExpressions$testing$$inlined$schedule$1");
        assertEquals("public final class net.corda.example.LambdaExpressions extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  public final void testing(kotlin.Unit)\n" +
            "##", testProject.getApiText());
    }
}
