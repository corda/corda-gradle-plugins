package net.corda.gradle.flask;

import lombok.SneakyThrows;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceException;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;

class HeartbeatAgentResource implements ReadableResource {
    private URL url = getClass().getResource(String.format("/META-INF/%s", getDisplayName()));

    @Override
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

    static final ReadableResource instance = new HeartbeatAgentResource();
}