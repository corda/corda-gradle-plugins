package net.corda.flask.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum OS {
    WINDOWS, MACOS, LINUX, SOLARIS, BSD, AIX, HP_UX, VMS, UNKNOWN;

    public static OS current;
    public static boolean isUnix;
    public static boolean isWindows;
    public static boolean isMac;

    static {
        Logger log = LoggerFactory.getLogger(OS.class);
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.startsWith("windows"))
            current = WINDOWS;
        if (osName.startsWith("mac"))
            current = MACOS;
        if (osName.contains("linux"))
            current = LINUX;
        if (osName.contains("solaris") || osName.contains("sunos") || osName.contains("illumos"))
            current = SOLARIS;
        if (osName.contains("bsd"))
            current = BSD;
        if (osName.contains("aix"))
            current = AIX;
        if (osName.contains("hp-ux"))
            current = HP_UX;
        if (osName.contains("vms"))
            current = VMS;
        if(current == null)  {
            log.error("Unrecognized OS '{}'", osName);
        }
        isUnix = Stream.of(LINUX, SOLARIS, BSD, AIX, HP_UX).collect(Collectors.toSet()).contains(current);
        isWindows = current == WINDOWS;
        isMac = current == MACOS;
    }
}
