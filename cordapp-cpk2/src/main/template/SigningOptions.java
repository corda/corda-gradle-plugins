package @root_package@.signing;

import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;

import static org.gradle.api.tasks.PathSensitivity.RELATIVE;

/**
 * !!! GENERATED FILE - DO NOT EDIT !!!
 * See cordapp-cpk2/src/main/template/SigningOptions.java instead.
 * <p>
 * Options for ANT task "signjar".
 */
public class SigningOptions {
    // Defaults to META-INF/certificates/cordadevcodesign.p12 keystore with Corda development key
    private static final String DEFAULT_ALIAS = "cordacodesign";
    private static final String DEFAULT_STOREPASS = "cordacadevpass";
    private static final String DEFAULT_STORETYPE = "PKCS12";
    private static final String DEFAULT_SIGFILE = "cordapp";
    public static final String DEFAULT_KEYSTORE = "META-INF/certificates/cordadevcodesign.p12";
    public static final String DEFAULT_KEYSTORE_FILE = "cordadevcakeys";
    public static final String DEFAULT_KEYSTORE_EXTENSION = ".p12";
    public static final String SYSTEM_PROPERTY_PREFIX = "signing.";

    private final Property<String> alias;
    private final Property<String> storePassword;
    private final Property<URI> keyStore;
    private final Property<String> storeType;
    private final Property<String> keyPassword;
    private final Property<String> signatureFileName;
    private final Property<Boolean> verbose;
    private final Property<Boolean> strict;
    private final Property<Boolean> internalSF;
    private final Property<Boolean> sectionsOnly;
    private final Property<Boolean> lazy;
    private final Property<String> maxMemory;
    private final Property<Boolean> preserveLastModified;
    private final Property<URI> tsaUrl;
    private final Property<String> tsaCert;
    private final Property<String> tsaProxyHost;
    private final Property<Integer> tsaProxyPort;
    private final RegularFileProperty executable;
    private final Property<Boolean> force;
    private final Property<String> signatureAlgorithm;
    private final Property<String> digestAlgorithm;
    private final Property<String> tsaDigestAlgorithm;
    private final MapProperty<String, String> _signJarOptions;

    @Inject
    public SigningOptions(@NotNull ObjectFactory objects, @NotNull ProviderFactory providers) {
        alias = objects.property(String.class).convention(
           providers.systemProperty(SYSTEM_PROPERTY_PREFIX + Key.ALIAS).orElse(DEFAULT_ALIAS)
        );
        storePassword = objects.property(String.class).convention(
            providers.systemProperty(SYSTEM_PROPERTY_PREFIX + Key.STOREPASS).orElse(DEFAULT_STOREPASS)
        );
        keyStore = objects.property(URI.class).convention(
            providers.systemProperty(SYSTEM_PROPERTY_PREFIX + Key.KEYSTORE).map(URI::create)
        );
        storeType = objects.property(String.class).convention(
            providers.systemProperty(SYSTEM_PROPERTY_PREFIX + Key.STORETYPE).orElse(DEFAULT_STORETYPE)
        );
        keyPassword = objects.property(String.class).convention(
            providers.systemProperty(SYSTEM_PROPERTY_PREFIX + Key.KEYPASS).orElse(storePassword)
        );
        signatureFileName = objects.property(String.class).convention(
            providers.systemProperty(SYSTEM_PROPERTY_PREFIX + Key.SIGFILE).orElse(alias.map(aliasValue ->
                DEFAULT_ALIAS.equals(aliasValue) ? DEFAULT_SIGFILE : aliasValue
            ))
        );
        verbose = objects.property(Boolean.class).convention(false);
        strict = objects.property(Boolean.class).convention(false);
        internalSF = objects.property(Boolean.class).convention(false);
        sectionsOnly = objects.property(Boolean.class).convention(false);
        lazy = objects.property(Boolean.class).convention(false);
        maxMemory = objects.property(String.class);
        preserveLastModified = objects.property(Boolean.class).convention(false);
        tsaUrl = objects.property(URI.class);
        tsaCert = objects.property(String.class);
        tsaProxyHost = objects.property(String.class);
        tsaProxyPort = objects.property(Integer.class);
        executable = objects.fileProperty();
        force = objects.property(Boolean.class).convention(false);
        signatureAlgorithm = objects.property(String.class);
        digestAlgorithm = objects.property(String.class);
        tsaDigestAlgorithm = objects.property(String.class);
        _signJarOptions = objects.mapProperty(String.class, String.class);
        _signJarOptions.put(Key.ALIAS, alias);
        _signJarOptions.put(Key.STOREPASS, storePassword);
        _signJarOptions.put(Key.STORETYPE, storeType);
        _signJarOptions.put(Key.KEYPASS, keyPassword);
        _signJarOptions.put(Key.SIGFILE, signatureFileName);
        _signJarOptions.put(Key.VERBOSE, verbose.map(Object::toString));
        _signJarOptions.put(Key.STRICT, strict.map(Object::toString));
        _signJarOptions.put(Key.INTERNALSF, internalSF.map(Object::toString));
        _signJarOptions.put(Key.SECTIONSONLY, sectionsOnly.map(Object::toString));
        _signJarOptions.put(Key.LAZY, lazy.map(Object::toString));
        _signJarOptions.put(Key.PRESERVELASTMODIFIED, preserveLastModified.map(Object::toString));
        _signJarOptions.put(Key.FORCE, force.map(Object::toString));
    }

    /** Option keys for ANT task. */
    public static final class Key {
        private Key() {
        }

        public static final String JAR = "jar";
        public static final String ALIAS = "alias";
        public static final String STOREPASS = "storepass";
        public static final String KEYSTORE = "keystore";
        public static final String STORETYPE = "storetype";
        public static final String KEYPASS = "keypass";
        public static final String SIGFILE = "sigfile";
        public static final String SIGNEDJAR = "signedjar";
        public static final String VERBOSE = "verbose";
        public static final String STRICT = "strict";
        public static final String INTERNALSF = "internalsf";
        public static final String SECTIONSONLY = "sectionsonly";
        public static final String LAZY = "lazy";
        public static final String MAXMEMORY = "maxmemory";
        public static final String PRESERVELASTMODIFIED = "preservelastmodified";
        public static final String TSACERT = "tsacert";
        public static final String TSAURL = "tsaurl";
        public static final String TSAPROXYHOST = "tsaproxyhost";
        public static final String TSAPROXYPORT = "tsaproxyport";
        public static final String EXECUTABLE = "executable";
        public static final String FORCE = "force";
        public static final String SIGALG = "sigalg";
        public static final String DIGESTALG = "digestalg";
        public static final String TSADIGESTALG = "tsadigestalg";
    }

    @Input
    public Property<String> getAlias() {
        return alias;
    }

    @Internal
    public Property<String> getStorePassword() {
        return storePassword;
    }

    @Optional
    @Input
    public Property<URI> getKeyStore() {
        return keyStore;
    }

    public void setKeyStore(@Nullable RegularFile value) {
        setKeyStore(value == null ? null : value.getAsFile());
    }

    public void setKeyStore(@Nullable File value) {
        keyStore.set(value == null ? null : value.getAbsoluteFile().toURI());
    }

    public void setKeyStore(@Nullable String value) {
        keyStore.set(value == null ? null : URI.create(value));
    }

    @Internal
    public Property<String> getStoreType() {
        return storeType;
    }

    @Internal
    public Property<String> getKeyPassword() {
        return keyPassword;
    }

    @Input
    public Property<String> getSignatureFileName() {
        return signatureFileName;
    }

    @Console
    public Property<Boolean> getVerbose() {
        return verbose;
    }

    @Input
    public Property<Boolean> getStrict() {
        return strict;
    }

    @Input
    public Property<Boolean> getInternalSF() {
        return internalSF;
    }

    @Input
    public Property<Boolean> getSectionsOnly() {
        return sectionsOnly;
    }

    @Input
    public Property<Boolean> getLazy() {
        return lazy;
    }

    @Internal
    public Property<String> getMaxMemory() {
        return maxMemory;
    }

    @Input
    public Property<Boolean> getPreserveLastModified() {
        return preserveLastModified;
    }

    @Optional
    @Input
    public Property<URI> getTsaUrl() {
        return tsaUrl;
    }

    public void setTsaUrl(@Nullable String value) {
        tsaUrl.set(value == null ? null : URI.create(value));
    }

    @Optional
    @Input
    public Property<String> getTsaCert() {
        return tsaCert;
    }

    @Optional
    @Input
    public Property<String> getTsaProxyHost() {
        return tsaProxyHost;
    }

    @Optional
    @Input
    public Property<Integer> getTsaProxyPort() {
        return tsaProxyPort;
    }

    @Optional
    @InputFile
    @PathSensitive(RELATIVE)
    public RegularFileProperty getExecutable() {
        return executable;
    }

    @Input
    public Property<Boolean> getForce() {
        return force;
    }

    @Optional
    @Input
    public Property<String> getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    @Optional
    @Input
    public Property<String> getDigestAlgorithm() {
        return digestAlgorithm;
    }

    @Optional
    @Input
    public Property<String> getTsaDigestAlgorithm() {
        return tsaDigestAlgorithm;
    }

    public SigningOptions values(SigningOptions options) {
        // DO NOT COPY THE FOLLOWING PROPERTIES!
        // - signatureFileName
        alias.set(options.getAlias());
        storePassword.set(options.getStorePassword());
        keyStore.set(options.getKeyStore());
        storeType.set(options.getStoreType());
        keyPassword.set(options.getKeyPassword());
        verbose.set(options.getVerbose());
        strict.set(options.getStrict());
        internalSF.set(options.getInternalSF());
        sectionsOnly.set(options.getSectionsOnly());
        lazy.set(options.getLazy());
        maxMemory.set(options.getMaxMemory());
        preserveLastModified.set(options.getPreserveLastModified());
        tsaUrl.set(options.getTsaUrl());
        tsaCert.set(options.getTsaCert());
        tsaDigestAlgorithm.set(options.getTsaDigestAlgorithm());
        tsaProxyHost.set(options.getTsaProxyHost());
        tsaProxyPort.set(options.getTsaProxyPort());
        executable.set(options.getExecutable());
        force.set(options.getForce());
        signatureAlgorithm.set(options.getSignatureAlgorithm());
        digestAlgorithm.set(options.getDigestAlgorithm());
        return this;
    }

    protected static void setOptional(Map<String, String> map, String key, Property<?> value) {
        if (value.isPresent()) {
            map.put(key, value.get().toString());
        }
    }

    protected static void setOptional(Map<String, String> map, String key, RegularFileProperty value) {
        if (value.isPresent()) {
            map.put(key, value.getAsFile().get().getAbsolutePath());
        }
    }

    /**
     * @return options as map.
     */
    @Internal
    public Provider<? extends Map<String, String>> getSignJarOptions() {
        return _signJarOptions.map(opts -> {
            final Map<String, String> result = new LinkedHashMap<>(opts);
            setOptional(result, Key.KEYSTORE, keyStore);
            setOptional(result, Key.MAXMEMORY, maxMemory);
            setOptional(result, Key.TSAURL, tsaUrl);
            setOptional(result, Key.TSACERT, tsaCert);
            setOptional(result, Key.TSAPROXYHOST, tsaProxyHost);
            setOptional(result, Key.TSAPROXYPORT, tsaProxyPort);
            setOptional(result, Key.EXECUTABLE, executable);
            setOptional(result, Key.SIGALG, signatureAlgorithm);
            setOptional(result, Key.DIGESTALG, digestAlgorithm);
            setOptional(result, Key.TSADIGESTALG, tsaDigestAlgorithm);
            return result;
        });
    }
}
