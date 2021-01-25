package net.corda.flask.launcher;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
public class JavaProcessBuilder {

    private static Logger log = LoggerFactory.getLogger(JarCache.class);

    private static final String PROCESS_BUILDER_PREFIX = "javaProcessBuilder";

    @AllArgsConstructor
    public static class JavaAgent {
        Path jar;
        String args;
    }

    private String mainClassName;

    private String javaHome = System.getProperty("java.home");

    private List<String> jvmArgs = new ArrayList<>();

    private List<String> classpath = new ArrayList<>();

    private Properties properties = new Properties();

    private List<String> cliArgs = new ArrayList<>();

    private List<JavaAgent> javaAgents = new ArrayList<>();

    /**
     * Generate the argument file string according to the grammar specified
     * <a href="https://docs.oracle.com/en/java/javase/14/docs/specs/man/java.html#java-command-line-argument-files">here</a>
     * @param strings list of command line arguments to be passed to the spawned JVM
     * @return the Java argument file content as a string
     */
    static String generateArgumentFileString(List<String> strings) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while(true) {
            CharacterIterator it = new StringCharacterIterator(strings.get(i));
            sb.append('"');
            for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
                switch (c) {
                    case '"':
                        sb.append("\\\"");
                        break;
                    case '\r':
                        sb.append("\\r");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case '\\':
                        sb.append("\\\\");
                        break;
                    default:
                        sb.append(c);
                        break;
                }
            }
            sb.append('"');
            if(++i < strings.size()) {
                sb.append(' ');
            } else {
                break;
            }
        }
        return sb.toString();
    }

    @SneakyThrows
    public int exec() {
        ArrayList<String> cmd = new ArrayList<>();
        Path javaBin = Paths.get(javaHome, "bin", "java");
        cmd.add(javaBin.toString());
        cmd.addAll(jvmArgs);
        if(!classpath.isEmpty()) {
            cmd.add("-cp");
            cmd.add(classpath.stream().collect(Collectors.joining(System.getProperty("path.separator"))));
        }
        for(Map.Entry<Object, Object> entry : properties.entrySet()) {
            cmd.add(String.format("-D%s=%s", entry.getKey(), entry.getValue()));
        }
        for(JavaAgent javaAgent : javaAgents) {
            StringBuilder sb = new StringBuilder("-javaagent:" + javaAgent.jar.toString());
            String agentArguments = javaAgent.args;
            if(agentArguments != null) {
                sb.append('=');
                sb.append(agentArguments);
            }
            cmd.add(sb.toString());
        }
        cmd.add(mainClassName);
        cmd.addAll(cliArgs);

        int cmdLength = 0;
        for(String part : cmd) {
            cmdLength += part.length();
        }
        //Add space between arguments
        cmdLength += cmd.size() - 1;

        log.debug("Spawning new process with command line: [{}]",
                cmd.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")));
        if(cmdLength < 1024 || cmd.size() == 1) {
            Process process = new ProcessBuilder(cmd).inheritIO().start();
            try {
                return process.waitFor();
            } finally {
                if(process.isAlive()) {
                    process.destroy();
                }
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        } else {
            Path argumentFile = Files.createTempFile(PROCESS_BUILDER_PREFIX, null);
            log.trace("Spawning Java process using argument file '{}'", argumentFile);
            try(Writer writer = Files.newBufferedWriter(argumentFile)) {
                writer.write(generateArgumentFileString(cmd.subList(1, cmd.size())));
            }
            Process process = new ProcessBuilder(Arrays.asList(cmd.get(0), "@" + argumentFile.toString())).inheritIO().start();
            try {
                return process.waitFor();
            } finally {
                if(process.isAlive()) {
                    process.destroy();
                }
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
                Files.delete(argumentFile);
            }
        }
    }
}
