package net.corda.flask.launcher;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

@Getter
@Setter
public class JavaProcessBuilder {

    private static final Logger log = LoggerFactory.getLogger(JavaProcessBuilder.class);

    private static final String PROCESS_BUILDER_PREFIX = "javaProcessBuilder";

    /**
     * Maximum number of characters to be used to create a command line
     * (beyond which a Java argument file will be created instead), the actual limit is OS-specific,
     * the current value is extremely conservative so that it is safe to use on most operating systems
     */
    private static final int COMMAND_LINE_MAX_SIZE = 1024;

    private static final String PATH_SEPARATOR = System.getProperty("path.separator");

    @AllArgsConstructor
    public static class JavaAgent {
        Path jar;
        String args;
    }

    private String mainClassName;

    private Path executableJar;

    private final String javaHome = System.getProperty("java.home");

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
        while(i < strings.size()) {
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
            }
        }
        return sb.toString();
    }

    @SneakyThrows
    public ProcessBuilder build() {
        ArrayList<String> cmd = new ArrayList<>();
        Path javaBin = Paths.get(javaHome, "bin", "java");
        cmd.add(javaBin.toString());
        cmd.addAll(jvmArgs);
        if(!classpath.isEmpty()) {
            cmd.add("-cp");
            cmd.add(String.join(PATH_SEPARATOR, classpath));
        }
        for(Map.Entry<Object, Object> entry : properties.entrySet()) {
            cmd.add(String.format("-D%s=%s", entry.getKey(), entry.getValue()));
        }
        for(JavaAgent javaAgent : javaAgents) {
            StringBuilder sb = new StringBuilder("-javaagent:").append(javaAgent.jar.toString());
            String agentArguments = javaAgent.args;
            if(agentArguments != null) {
                sb.append('=');
                sb.append(agentArguments);
            }
            cmd.add(sb.toString());
        }
        if(executableJar != null) {
            cmd.add("-jar");
            cmd.add(executableJar.toString());
        } else if(mainClassName != null) {
            cmd.add(mainClassName);
        } else {
            throw new IllegalArgumentException(
                    "Either a main class or the path to an executable jar file have to be specified");
        }
        cmd.addAll(cliArgs);

        int cmdLength = 0;
        for(String part : cmd) {
            cmdLength += part.length();
        }
        //Add space between arguments
        cmdLength += cmd.size() - 1;

        if(log.isDebugEnabled()) {
            log.debug("Spawning new process with command line: [{}]",
                    cmd.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")));
        }
        if(cmdLength < COMMAND_LINE_MAX_SIZE || cmd.size() == 1) {
            return new ProcessBuilder(cmd);
        } else {
            Path argumentFile = Files.createTempFile(PROCESS_BUILDER_PREFIX, ".arg");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.delete(argumentFile);
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }));
            log.trace("Using Java argument file '{}'", argumentFile);
            try(Writer writer = Files.newBufferedWriter(argumentFile)) {
                writer.write(generateArgumentFileString(cmd.subList(1, cmd.size())));
            }
            return new ProcessBuilder(cmd.get(0), "@" + argumentFile);
        }
    }
}
