package net.corda.plugins.javac.quasar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class SuspendableDatabase {
    private static final String PREFIX = "META-INF/";
    private static final String SUSPENDABLES_FILE = "suspendables";
    public static final String SUSPENDABLE_SUPERS_FILE = "suspendable-supers";

    private final Set<String> suspendableMethods = new HashSet<>();
    private final Set<String> suspendableClasses = new HashSet<>();


    public SuspendableDatabase(ClassLoader classLoader) {
        readFiles(classLoader, SUSPENDABLES_FILE, suspendableMethods, suspendableClasses);
    }

    private void readFiles(ClassLoader classLoader, String fileName, Set<String> set, Set<String> classSet) {
        try {
            for (Enumeration<URL> susFiles = getResources(classLoader, PREFIX + fileName); susFiles.hasMoreElements();) {
                URL file = susFiles.nextElement();
                parse(file, set, classSet);
            }
        } catch (IOException e) {
            // silently ignore
        }
    }

    public boolean isSuspendable(String className, String methodName, String methodDesc) {
        return (suspendableMethods.contains(className + '.' + methodName + methodDesc)
                || suspendableMethods.contains(className + '.' + methodName)
                || suspendableClasses.contains(className));
    }

    private static Enumeration<URL> getResources(ClassLoader cl, String resources) throws IOException {
        return cl != null ? cl.getResources(resources) : ClassLoader.getSystemResources(resources);
    }

    private static void parse(URL file, Set<String> set, Set<String> classSet) {
        try (InputStream is = file.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")))) {
            String line;

            for (int linenum = 1; (line = reader.readLine()) != null; linenum++) {
                final String s = line.trim();
                if (s.isEmpty())
                    continue;
                if (s.charAt(0) == '#')
                    continue;
                final int index = s.lastIndexOf('.');
                if (index <= 0) {
                    System.err.println("Can't parse line " + linenum + " in " + file + ": " + line);
                    continue;
                }
                final String className = s.substring(0, index).replace('.', '/');
                final String methodName = s.substring(index + 1);
                final String fullName = className + '.' + methodName;

                if (methodName.equals("*")) {
                    if (classSet != null)
                        classSet.add(className);
                } else
                    set.add(fullName);
            }
        } catch (IOException e) {
            // silently ignore
        }
    }
}
