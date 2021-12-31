package net.corda.gradle.flask;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceException;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

final class HeartbeatAgentResource implements ReadableResource {
    static final ReadableResource instance = new HeartbeatAgentResource();

    private final URL url;

    private HeartbeatAgentResource() {
        url = getClass().getResource(String.format("/META-INF/%s", getDisplayName()));
    }

    @Override
    @Nonnull
    public InputStream read() throws ResourceException {
        try {
            return url.openStream();
        } catch (IOException e) {
            throw new ResourceException(e.getMessage(), e);
        }
    }

    @Override
    @Nonnull
    public String getDisplayName() {
        return getBaseName() + ".jar";
    }

    @Override
    @Nonnull
    public URI getURI() {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new InvalidUserDataException(e.getMessage(), e);
        }
    }

    @Override
    @Nonnull
    public String getBaseName() {
        return "flask-heartbeat-agent";
    }
}
