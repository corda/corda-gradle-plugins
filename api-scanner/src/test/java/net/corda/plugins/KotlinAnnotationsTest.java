package net.corda.plugins;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;

import java.io.IOException;

import static org.junit.Assert.*;

public class KotlinAnnotationsTest {
    private final TemporaryFolder testProjectDir = new TemporaryFolder();
    private final GradleProject testProject = new GradleProject(testProjectDir, "kotlin-annotations");

    @Rule
    public TestRule rules = RuleChain.outerRule(testProjectDir).around(testProject);

    @Test
    public void testKotlinAnnotations() throws IOException {
        assertEquals(
            "public final class net.corda.example.HasJvmField extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  @org.jetbrains.annotations.NotNull public final String stringValue = \"Hello World\"\n" +
            "##\n" +
            "public final class net.corda.example.HasJvmStaticFunction extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  public static final void doThing(String)\n" +
            "  public static final net.corda.example.HasJvmStaticFunction$Companion Companion\n" +
            "##\n" +
            "public static final class net.corda.example.HasJvmStaticFunction$Companion extends java.lang.Object\n" +
            "  public final void doThing(String)\n" +
            "##\n" +
            "public final class net.corda.example.HasOverloadedConstructor extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  public <init>(String)\n" +
            "  public <init>(String, String)\n" +
            "  public <init>(String, String, int)\n" +
            "  @org.jetbrains.annotations.NotNull public final String getNotNullable()\n" +
            "  @org.jetbrains.annotations.Nullable public final String getNullable()\n" +
            "  public final int getNumber()\n" +
            "##", CopyUtils.toString(testProject.getApi()));
    }
}
