package net.corda.testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PropertiesTest {
    private static String username = "me";
    private static String password = "me";
    private static String cordaType = "corda-project";
    private static String branch = "mine";
    private static String targetBranch = "master";

    @BeforeEach
    public void setUp() {
        System.setProperty("git.branch", branch);
        System.setProperty("git.target.branch", targetBranch);
        System.setProperty("artifactory.username", username);
        System.setProperty("artifactory.password", password);
    }

    @AfterEach
    public void tearDown() {
        System.setProperty("git.branch", "");
        System.setProperty("git.target.branch", "");
        System.setProperty("artifactory.username", "");
        System.setProperty("artifactory.password", "");
    }

    @Test
    public void cordaType() {
    Properties.setRootProjectType(cordaType);
        Assertions.assertEquals(cordaType, Properties.getRootProjectType());
    }

    @Test
    public void getUsername() {
        Assertions.assertEquals(username, Properties.getUsername());
    }

    @Test
    public void getPassword() {
        Assertions.assertEquals(password, Properties.getPassword());
    }

    @Test
    public void getGitBranch() {
        Assertions.assertEquals(branch, Properties.getGitBranch());
    }

    @Test
    public void getTargetGitBranch() {
        Assertions.assertEquals(targetBranch, Properties.getTargetGitBranch());
    }

    @Test
    public void getPublishJunitTests() {
        Assertions.assertFalse(Properties.getPublishJunitTests());
        System.setProperty("publish.junit", "true");
        Assertions.assertTrue(Properties.getPublishJunitTests());
    }
}