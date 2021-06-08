package com.sun.tools.javac.util;

import com.sun.tools.javac.api.DiagnosticFormatter;

import java.util.Set;

public class ForwardingConfiguration implements DiagnosticFormatter.Configuration {

    protected DiagnosticFormatter.Configuration configuration;

    public ForwardingConfiguration(DiagnosticFormatter.Configuration configuration) {
        this.configuration = configuration;
    }

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
