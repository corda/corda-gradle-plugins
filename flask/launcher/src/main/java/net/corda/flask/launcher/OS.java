package net.corda.flask.launcher;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum OS {
    WINDOWS("windows"),
    MACOS("macos"),
    LINUX("linux"),
    SOLARIS("solaris"),
    BSD("bsd"),
    AIX("aix"),
    HP_UX("hp-ux"),
    UNIX("unix"),
    POSIX("posix"),
    VMS("vms");

    private final String value;

    OS(String value) {
        this.value = value;
    }

    public static OS current;
    public static boolean isUnix;
    public static boolean isWindows;
    public static boolean isMac;

    static {
        String osName = System.getProperty("os.name").toLowerCase();
        for(OS os : values()) {
            if(Objects.equals(osName, os.value)) {
                current = os;
            }
        }
        if(current == null) throw new IllegalArgumentException(String.format("Unrecognized OS '%s'", osName));
        isUnix = Stream.of(LINUX, SOLARIS, BSD, AIX, HP_UX).collect(Collectors.toSet()).contains(current);
        isWindows = current == WINDOWS;
        isMac = current == MACOS;
    }
}
