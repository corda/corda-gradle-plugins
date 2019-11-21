package net.corda.testing;

import okhttp3.*;
import org.apache.commons.compress.utils.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Used by TestArtifacts
 */
public class Artifactory {

    //<editor-fold desc="Statics">
    private static final Logger LOG = LoggerFactory.getLogger(Artifactory.class);

    private static String authorization() {
        return Credentials.basic(Properties.getUsername(), Properties.getPassword());
    }

    /**
     * Construct the URL in a style that Artifactory prefers.
     *
     * @param baseUrl   e.g. https://software.r3.com/artifactory/corda-releases/net/corda/corda/
     * @param theTag    e.g. 4.3-RC0
     * @param artifact  e.g. corda
     * @param extension e.g. jar
     * @return full URL to artifact.
     */
    private static String getFullUrl(@NotNull final String baseUrl,
                                     @NotNull final String theTag,
                                     @NotNull final String artifact,
                                     @NotNull final String extension) {
        return baseUrl + "/" + theTag + "/" + getFileName(artifact, extension, theTag);
    }

    /**
     * @param artifact  e.g. corda
     * @param extension e.g. jar
     * @param theTag    e.g. 4.3
     * @return e.g. corda-4.3.jar
     */
    static String getFileName(@NotNull final String artifact,
                                      @NotNull final String extension,
                                      @Nullable final String theTag) {
        StringBuilder sb = new StringBuilder().append(artifact);
        if (theTag != null) {
            sb.append("-").append(theTag);
        }
        sb.append(".").append(extension);
        return sb.toString();
    }
    //</editor-fold>

    /**
     * Get the unit tests, synchronous get.
     * See https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-RetrieveLatestArtifact
     *
     * @return true if successful, false otherwise.
     */
    boolean get(@NotNull final String baseUrl,
                @NotNull final String theTag,
                @NotNull final String artifact,
                @NotNull final String extension,
                @NotNull final OutputStream outputStream) {
        final String url = getFullUrl(baseUrl, theTag, artifact, extension);
        final Request request = new Request.Builder()
                .addHeader("Authorization", authorization())
                .url(url)
                .build();

        final OkHttpClient client = new OkHttpClient();

        try (Response response = client.newCall(request).execute()) {
            handleResponse(response);
            if (response.body() != null) {
                outputStream.write(response.body().bytes());
            } else {
                LOG.warn("Response body was empty");
            }
        } catch (IOException e) {
            LOG.warn("Unable to execute GET via REST");
            LOG.debug("Exception", e);
            return false;
        }

        LOG.warn("Ok.  REST GET successful");

        return true;
    }

    /**
     * Post an artifact, synchronous PUT
     * See https://www.jfrog.com/confluence/display/RTF/Artifactory+REST+API#ArtifactoryRESTAPI-DeployArtifact
     *
     * @return true if successful
     */
    boolean put(@NotNull final String baseUrl,
                @NotNull final String theTag,
                @NotNull final String artifact,
                @NotNull final String extension,
                @NotNull final InputStream inputStream) {
        final MediaType contentType = MediaType.parse("application/zip, application/octet-stream");
        final String url = getFullUrl(baseUrl, theTag, artifact, extension);

        final OkHttpClient client = new OkHttpClient();

        byte[] bytes;

        try {
            bytes = IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            LOG.warn("Unable to execute PUT tests via REST: ", e);
            return false;
        }

        final Request request = new Request.Builder()
                .addHeader("Authorization", authorization())
                .url(url)
                .put(RequestBody.create(contentType, bytes))
                .build();

        try (Response response = client.newCall(request).execute()) {
            handleResponse(response);
        } catch (IOException e) {
            LOG.warn("Unable to execute PUT via REST: ", e);
            return false;
        }

        return true;
    }

    private void handleResponse(@NotNull final Response response) throws IOException {
        if (response.isSuccessful()) return;

        LOG.warn("Bad response from server: {}", response.toString());
        LOG.warn(response.toString());
        if (response.code() == 401) {
            throw new IOException("Not authorized - incorrect credentials?");
        }

        throw new IOException(response.message());
    }
}
