package com.github.api.sun.tools.javac.util;

import com.github.api.sun.tools.javac.api.DiagnosticFormatter;

import java.util.*;

public class SimpleConfiguration implements DiagnosticFormatter.Configuration {
    protected Map<MultilineLimit, Integer> multilineLimits;
    protected EnumSet<DiagnosticPart> visibleParts;
    protected boolean caretEnabled;

    public SimpleConfiguration(Set<DiagnosticPart> parts) {
        multilineLimits = new HashMap<MultilineLimit, Integer>();
        setVisible(parts);
        setMultilineLimit(MultilineLimit.DEPTH, -1);
        setMultilineLimit(MultilineLimit.LENGTH, -1);
        setCaretEnabled(true);
    }

    @SuppressWarnings("fallthrough")
    public SimpleConfiguration(Options options, Set<DiagnosticPart> parts) {
        this(parts);
        String showSource = null;
        if ((showSource = options.get("showSource")) != null) {
            if (showSource.equals("true"))
                setVisiblePart(DiagnosticPart.SOURCE, true);
            else if (showSource.equals("false"))
                setVisiblePart(DiagnosticPart.SOURCE, false);
        }
        String diagOpts = options.get("diags");
        if (diagOpts != null) {
            Collection<String> args = Arrays.asList(diagOpts.split(","));
            if (args.contains("short")) {
                setVisiblePart(DiagnosticPart.DETAILS, false);
                setVisiblePart(DiagnosticPart.SUBDIAGNOSTICS, false);
            }
            if (args.contains("source"))
                setVisiblePart(DiagnosticPart.SOURCE, true);
            if (args.contains("-source"))
                setVisiblePart(DiagnosticPart.SOURCE, false);
        }
        String multiPolicy = null;
        if ((multiPolicy = options.get("multilinePolicy")) != null) {
            if (multiPolicy.equals("disabled"))
                setVisiblePart(DiagnosticPart.SUBDIAGNOSTICS, false);
            else if (multiPolicy.startsWith("limit:")) {
                String limitString = multiPolicy.substring("limit:".length());
                String[] limits = limitString.split(":");
                try {
                    switch (limits.length) {
                        case 2: {
                            if (!limits[1].equals("*"))
                                setMultilineLimit(MultilineLimit.DEPTH, Integer.parseInt(limits[1]));
                        }
                        case 1: {
                            if (!limits[0].equals("*"))
                                setMultilineLimit(MultilineLimit.LENGTH, Integer.parseInt(limits[0]));
                        }
                    }
                } catch (NumberFormatException ex) {
                    setMultilineLimit(MultilineLimit.DEPTH, -1);
                    setMultilineLimit(MultilineLimit.LENGTH, -1);
                }
            }
        }
        String showCaret = null;
        if (((showCaret = options.get("showCaret")) != null) &&
                showCaret.equals("false"))
            setCaretEnabled(false);
        else
            setCaretEnabled(true);
    }

    public int getMultilineLimit(MultilineLimit limit) {
        return multilineLimits.get(limit);
    }

    public EnumSet<DiagnosticPart> getVisible() {
        return EnumSet.copyOf(visibleParts);
    }

    public void setVisible(Set<DiagnosticPart> diagParts) {
        visibleParts = EnumSet.copyOf(diagParts);
    }

    public void setMultilineLimit(MultilineLimit limit, int value) {
        multilineLimits.put(limit, value < -1 ? -1 : value);
    }

    public void setVisiblePart(DiagnosticPart diagParts, boolean enabled) {
        if (enabled)
            visibleParts.add(diagParts);
        else
            visibleParts.remove(diagParts);
    }

    public boolean isCaretEnabled() {
        return caretEnabled;
    }

    public void setCaretEnabled(boolean caretEnabled) {
        this.caretEnabled = caretEnabled;
    }
}