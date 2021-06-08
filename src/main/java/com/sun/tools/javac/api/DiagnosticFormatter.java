package com.sun.tools.javac.api;

import javax.tools.Diagnostic;
import java.util.Locale;
import java.util.Set;

public interface DiagnosticFormatter<D extends Diagnostic<?>> {
    boolean displaySource(D diag);

    String format(D diag, Locale l);

    String formatMessage(D diag, Locale l);

    String formatKind(D diag, Locale l);

    String formatSource(D diag, boolean fullname, Locale l);

    String formatPosition(D diag, PositionKind pk, Locale l);

    Configuration getConfiguration();

    enum PositionKind {
        START,
        END,
        LINE,
        COLUMN,
        OFFSET
    }

    interface Configuration {
        Set<DiagnosticPart> getVisible();

        void setVisible(Set<DiagnosticPart> visibleParts);

        void setMultilineLimit(MultilineLimit limit, int value);

        int getMultilineLimit(MultilineLimit limit);

        enum DiagnosticPart {
            SUMMARY,
            DETAILS,
            SOURCE,
            SUBDIAGNOSTICS,
            JLS
        }

        enum MultilineLimit {
            DEPTH,
            LENGTH
        }
    }
}
