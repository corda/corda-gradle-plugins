package net.corda.flask.common;

public class Flask {

    public static class ManifestAttributes {
        public static String MD5_DIGEST = "MD5-Digest";
        public static String APPLICATION_CLASS = "Application-Class";
        public static String JVM_ARGS = "JVM-Args";
        public static String JAVA_AGENTS = "Java-Agents";
    }

    public static class JvmProperties {
        /**
         * If this property is set,i ts value will be appended to the jvm arguments
         * list of the spawned Java process
         */
        public static String JVM_ARGS = "net.corda.flask.jvm.args";

        /**
         * This JVM property can be used to override the path to the flask cache folder
         */
        public static String CACHE_DIR = "net.corda.flask.cache.dir";

        /**
         * This JVM property can be used to always wipe the library cache directory before program startup
         */
        public static String WIPE_CACHE = "net.corda.flask.cache.wipe";
    }
}
