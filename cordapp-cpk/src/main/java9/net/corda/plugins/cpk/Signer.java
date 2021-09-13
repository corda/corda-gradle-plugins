package net.corda.plugins.cpk;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import jdk.security.jarsigner.JarSigner;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.ALIAS;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.DIGESTALG;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.INTERNALSF;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.KEYPASS;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.KEYSTORE;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.PRESERVELASTMODIFIED;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.SECTIONSONLY;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.SIGALG;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.SIGFILE;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.SIGNEDJAR;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.STOREPASS;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.STORETYPE;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.TSADIGESTALG;
import static net.corda.plugins.cpk.signing.SigningOptions.Key.TSAURL;

@SuppressWarnings("unused")
final class Signer {
    private static final String TSA_DIGEST_ALGORITHM = "tsaDigestAlg";
    private static final String SECTIONS_ONLY = "sectionsonly";
    private static final String INTERNAL_SF = "internalsf";

    private final Task task;

    Signer(@NotNull Task task) {
        this.task = task;
    }

    void sign(@NotNull Path jarFile, @NotNull Map<String, String> options) {
        try {
            new Factory(task.getLogger(), options).configure().sign(jarFile);
        } catch (IOException | GeneralSecurityException e) {
            throw new InvalidUserDataException(e.getMessage(), e);
        }
    }

    private static final class Factory {
        private final Map<String, String> options;
        private final Logger logger;

        Factory(@NotNull Logger logger, @NotNull Map<String, String> options) {
            this.options = options;
            this.logger = logger;
        }

        @NotNull
        Writer configure() throws GeneralSecurityException, IOException {
            final JarSigner.Builder builder = new JarSigner.Builder(getPrivateKey());
            configure(builder::digestAlgorithm, options.get(DIGESTALG));
            configure(builder::signatureAlgorithm, options.get(SIGALG));
            configure(builder::signerName, options.get(SIGFILE));
            final String tsaUrl = options.get(TSAURL);
            if (tsaUrl != null) {
                builder.tsa(URI.create(tsaUrl));
            }
            final String tsaDigestAlgorithm = options.get(TSADIGESTALG);
            if (tsaDigestAlgorithm != null) {
                builder.setProperty(TSA_DIGEST_ALGORITHM, tsaDigestAlgorithm);
            }
            final String sectionsOnly = options.get(SECTIONSONLY);
            if (sectionsOnly != null) {
                builder.setProperty(SECTIONS_ONLY, sectionsOnly);
            }
            final String internalSF = options.get(INTERNALSF);
            if (internalSF != null) {
                builder.setProperty(INTERNAL_SF, internalSF);
            }
            return new Writer(builder.build());
        }

        private PrivateKeyEntry getPrivateKey() throws IOException, GeneralSecurityException {
            final String storeType = getRequired(STORETYPE, "Missing Keystore type.");
            final String storePassword = getRequired(STOREPASS, "Missing keystore password.");
            final String storePath = getRequired(KEYSTORE, "Missing keystore for signing key.");
            final String keyPassword = getRequired(KEYPASS, "Missing signing key password.");
            final String alias = getRequired(ALIAS, "Missing signing key alias.");

            final KeyStore keystore = KeyStore.getInstance(storeType);
            try (InputStream input = Files.newInputStream(Paths.get(storePath), READ)) {
                keystore.load(input, storePassword.toCharArray());
            }
            return (PrivateKeyEntry) keystore.getEntry(alias, new PasswordProtection(keyPassword.toCharArray()));
        }

        @NotNull
        private String getRequired(@NotNull String key, @NotNull String errorMessage) {
            final String value = options.get(key);
            if (value == null) {
                throw new InvalidUserDataException(errorMessage);
            }
            return value;
        }

        private static void configure(ConfigOperation<String> operation, String value) throws GeneralSecurityException {
            if (value != null) {
                operation.accept(value);
            }
        }

        private final class Writer {
            private final JarSigner jarSigner;

            Writer(@NotNull JarSigner jarSigner) {
                this.jarSigner = jarSigner;
            }

            void sign(@NotNull Path jarFile) throws IOException {
                final String signedJar = options.get(SIGNEDJAR);
                if (signedJar == null) {
                    final FileAttribute<Set<PosixFilePermission>> permissions = PosixFilePermissions.asFileAttribute(Files.getPosixFilePermissions(jarFile));
                    final Path signedFile = Files.createTempFile(jarFile.getParent(), "signed-", ".tmp", permissions);
                    sign(jarFile, signedFile);
                    Files.move(signedFile, jarFile, REPLACE_EXISTING);
                } else {
                    final Path signedFile = Paths.get(signedJar);
                    sign(jarFile, signedFile);
                }
            }

            private void sign(@NotNull Path jarFile, @NotNull Path signedFile) throws IOException {
                try (
                    ZipFile input = new ZipFile(jarFile.toFile());
                    OutputStream output = new BufferedOutputStream(Files.newOutputStream(signedFile, TRUNCATE_EXISTING))
                ) {
                    jarSigner.sign(input, output);
                }
                if (Boolean.parseBoolean(options.get(PRESERVELASTMODIFIED))) {
                    Files.setLastModifiedTime(signedFile, Files.getLastModifiedTime(jarFile));
                }
            }
        }
    }

    @FunctionalInterface
    interface ConfigOperation<T> {
        void accept(T value) throws GeneralSecurityException;
    }
}
