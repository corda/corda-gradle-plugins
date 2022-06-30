package net.corda.plugins.cpk2;

import org.jetbrains.annotations.NotNull;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;

import static javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD;
import static javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA;
import static javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET;
import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;

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
     * Create a JAXP DocumentBuilderFactory.
     * Disable any features that may be flagged by a security audit.
     * @return a namespace-aware DocumentBuilderFactory.
     * @throws ParserConfigurationException if JAXP does not support "secure processing".
     */
    @NotNull
    static DocumentBuilderFactory createDocumentBuilderFactory() throws ParserConfigurationException  {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(FEATURE_SECURE_PROCESSING, true);
        disableProperty(factory, ACCESS_EXTERNAL_SCHEMA);
        disableProperty(factory, ACCESS_EXTERNAL_DTD);
        factory.setExpandEntityReferences(false);
        factory.setIgnoringComments(true);
        factory.setNamespaceAware(true);
        return factory;
    }

    private static void disableProperty(@NotNull DocumentBuilderFactory factory, String propertyName) {
        try {
            factory.setAttribute(propertyName, "");
        } catch (IllegalArgumentException e) {
            // Property not supported.
        }
    }

    /**
     * Create a JAXP TransformerFactory.
     * Disable any features that may be flagged by a security audit.
     * @return a TransformerFactory.
     * @throws TransformerConfigurationException if JAXP does not support "secure processing".
     */
    @NotNull
    static TransformerFactory createTransformerFactory() throws TransformerConfigurationException  {
        TransformerFactory factory = TransformerFactory.newInstance();
        factory.setFeature(FEATURE_SECURE_PROCESSING, true);
        disableProperty(factory, ACCESS_EXTERNAL_STYLESHEET);
        disableProperty(factory, ACCESS_EXTERNAL_DTD);
        try {
            // Set XML indentation to 4 spaces.
            // This does not seem to be a standard JAXP property!
            factory.setAttribute("indent-number", 4);
        } catch (IllegalArgumentException e) {
            // Property not supported.
        }
        return factory;
    }

    private static void disableProperty(@NotNull TransformerFactory factory, String propertyName) {
        try {
            factory.setAttribute(propertyName, "");
        } catch (IllegalArgumentException e) {
            // Property not supported.
        }
    }
}
