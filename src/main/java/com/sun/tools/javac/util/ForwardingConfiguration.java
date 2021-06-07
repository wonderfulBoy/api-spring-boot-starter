package com.sun.tools.javac.util;

import com.sun.tools.javac.api.DiagnosticFormatter;

import java.util.Set;

/**
 * A delegated formatter configuration delegates all configurations settings
 * to an underlying configuration object (aka the delegated configuration).
 */
public class ForwardingConfiguration implements DiagnosticFormatter.Configuration {

    /**
     * The configurationr object to which the forwarding configuration delegates some settings
     */
    protected DiagnosticFormatter.Configuration configuration;

    public ForwardingConfiguration(DiagnosticFormatter.Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Returns the underlying delegated configuration.
     *
     * @return delegated configuration
     */
    public DiagnosticFormatter.Configuration getDelegatedConfiguration() {
        return configuration;
    }

    public int getMultilineLimit(MultilineLimit limit) {
        return configuration.getMultilineLimit(limit);
    }

    public Set<DiagnosticPart> getVisible() {
        return configuration.getVisible();
    }

    public void setVisible(Set<DiagnosticPart> diagParts) {
        configuration.setVisible(diagParts);
    }

    public void setMultilineLimit(MultilineLimit limit, int value) {
        configuration.setMultilineLimit(limit, value);
    }
}
