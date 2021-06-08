package com.github.api.sun.tools.javac.util;

import com.github.api.sun.tools.javac.code.Lint.LintCategory;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import java.util.EnumSet;

public class Warner {
    protected boolean warned = false;
    private DiagnosticPosition pos = null;
    private EnumSet<LintCategory> nonSilentLintSet = EnumSet.noneOf(LintCategory.class);
    private EnumSet<LintCategory> silentLintSet = EnumSet.noneOf(LintCategory.class);

    public Warner(DiagnosticPosition pos) {
        this.pos = pos;
    }

    public Warner() {
        this(null);
    }

    public DiagnosticPosition pos() {
        return pos;
    }

    public void warn(LintCategory lint) {
        nonSilentLintSet.add(lint);
    }

    public void silentWarn(LintCategory lint) {
        silentLintSet.add(lint);
    }

    public boolean hasSilentLint(LintCategory lint) {
        return silentLintSet.contains(lint);
    }

    public boolean hasNonSilentLint(LintCategory lint) {
        return nonSilentLintSet.contains(lint);
    }

    public boolean hasLint(LintCategory lint) {
        return hasSilentLint(lint) ||
                hasNonSilentLint(lint);
    }

    public void clear() {
        nonSilentLintSet.clear();
        silentLintSet.clear();
        this.warned = false;
    }
}
