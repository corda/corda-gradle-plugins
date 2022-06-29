package net.corda.plugins.cpk.xml;

import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import static javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD;
import static javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA;
import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;
import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;

/**
 * Workaround for a Kotlin compiler bug.
 * When Gradle runs on Java 11, the Kotlin compiler finds the {@link XMLConstants}
 * class inside {@code gradle-api-$version.jar} rather than the JDK's built-in
 * class. Unfortunately, Gradle's version predates JAXP 1.5, and so is missing
 * {@link XMLConstants#ACCESS_EXTERNAL_DTD}, {@link XMLConstants#ACCESS_EXTERNAL_SCHEMA}
 * and {@link XMLConstants#ACCESS_EXTERNAL_STYLESHEET}.
 * The Java compiler does not have this problem.
 */
final class XMLFactory {
    /**
     * Create a JAXP SchemaFactory.
     * Disable any features that may be flagged by a security audit.
     */
    @NotNull
    static SchemaFactory createSchemaFactory() throws SAXException {
        SchemaFactory factory = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
        factory.setFeature(FEATURE_SECURE_PROCESSING, true);
        disableProperty(factory, ACCESS_EXTERNAL_SCHEMA);
        disableProperty(factory, ACCESS_EXTERNAL_DTD);
        return factory;
    }

    private static void disableProperty(@NotNull SchemaFactory factory, String propertyName) {
        try {
            factory.setProperty(propertyName, "");
        } catch (SAXException e) {
            // Property not supported.
        }
    }
}
