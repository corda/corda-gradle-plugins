package net.corda.gradle.flask;

import lombok.SneakyThrows;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceException;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;

final class HeartbeatAgentResource implements ReadableResource {
    static final ReadableResource instance = new HeartbeatAgentResource();

    private final URL url;

    private HeartbeatAgentResource() {
        url = getClass().getResource(String.format("/META-INF/%s", getDisplayName()));
    }

    @Override
    @Nonnull
    @SneakyThrows
    public InputStream read() throws ResourceException {
        return url.openStream();
    }

    @Override
    public String getDisplayName() {
        return getBaseName() + ".jar";
    }

    @Override
    @SneakyThrows
    public URI getURI() {
        return url.toURI();
    }

    @Override
    public String getBaseName() {
        return "flask-heartbeat-agent";
    }
}