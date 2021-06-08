package com.sun.tools.javac.util;

import com.sun.tools.javac.api.DiagnosticFormatter;
import com.sun.tools.javac.api.DiagnosticFormatter.Configuration.DiagnosticPart;
import com.sun.tools.javac.api.DiagnosticFormatter.Configuration.MultilineLimit;
import com.sun.tools.javac.api.Formattable;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Printer;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.CapturedType;
import com.sun.tools.javac.file.BaseFileObject;
import com.sun.tools.javac.jvm.Profile;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCParens;
import com.sun.tools.javac.tree.Pretty;

import javax.tools.JavaFileObject;
import java.util.*;

import static com.sun.tools.javac.util.JCDiagnostic.DiagnosticType.FRAGMENT;

public abstract class AbstractDiagnosticFormatter implements DiagnosticFormatter<JCDiagnostic> {

    protected JavacMessages messages;
    protected int depth = 0;
    private SimpleConfiguration config;
    private List<Type> allCaptured = List.nil();
    protected Printer printer = new Printer() {
        @Override
        protected String localize(Locale locale, String key, Object... args) {
            return AbstractDiagnosticFormatter.this.localize(locale, key, args);
        }

        @Override
        protected String capturedVarId(CapturedType t, Locale locale) {
            return "" + (allCaptured.indexOf(t) + 1);
        }

        @Override
        public String visitCapturedType(CapturedType t, Locale locale) {
            if (!allCaptured.contains(t)) {
                allCaptured = allCaptured.append(t);
            }
            return super.visitCapturedType(t, locale);
        }
    };

    protected AbstractDiagnosticFormatter(JavacMessages messages, SimpleConfiguration config) {
        this.messages = messages;
        this.config = config;
    }

    public String formatKind(JCDiagnostic d, Locale l) {
        switch (d.getType()) {
            case FRAGMENT:
                return "";
            case NOTE:
                return localize(l, "compiler.note.note");
            case WARNING:
                return localize(l, "compiler.warn.warning");
            case ERROR:
                return localize(l, "compiler.err.error");
            default:
                throw new AssertionError("Unknown diagnostic type: " + d.getType());
        }
    }

    @Override
    public String format(JCDiagnostic d, Locale locale) {
        allCaptured = List.nil();
        return formatDiagnostic(d, locale);
    }

    protected abstract String formatDiagnostic(JCDiagnostic d, Locale locale);

    public String formatPosition(JCDiagnostic d, PositionKind pk, Locale l) {
        Assert.check(d.getPosition() != Position.NOPOS);
        return String.valueOf(getPosition(d, pk));
    }

    private long getPosition(JCDiagnostic d, PositionKind pk) {
        switch (pk) {
            case START:
                return d.getIntStartPosition();
            case END:
                return d.getIntEndPosition();
            case LINE:
                return d.getLineNumber();
            case COLUMN:
                return d.getColumnNumber();
            case OFFSET:
                return d.getIntPosition();
            default:
                throw new AssertionError("Unknown diagnostic position: " + pk);
        }
    }

    public String formatSource(JCDiagnostic d, boolean fullname, Locale l) {
        JavaFileObject fo = d.getSource();
        if (fo == null)
            throw new IllegalArgumentException();
        if (fullname)
            return fo.getName();
        else if (fo instanceof BaseFileObject)
            return ((BaseFileObject) fo).getShortName();
        else
            return BaseFileObject.getSimpleName(fo);
    }

    protected Collection<String> formatArguments(JCDiagnostic d, Locale l) {
        ListBuffer<String> buf = new ListBuffer<String>();
        for (Object o : d.getArgs()) {
            buf.append(formatArgument(d, o, l));
        }
        return buf.toList();
    }

    protected String formatArgument(JCDiagnostic d, Object arg, Locale l) {
        if (arg instanceof JCDiagnostic) {
            String s = null;
            depth++;
            try {
                s = formatMessage((JCDiagnostic) arg, l);
            } finally {
                depth--;
            }
            return s;
        } else if (arg instanceof JCExpression) {
            return expr2String((JCExpression) arg);
        } else if (arg instanceof Iterable<?>) {
            return formatIterable(d, (Iterable<?>) arg, l);
        } else if (arg instanceof Type) {
            return printer.visit((Type) arg, l);
        } else if (arg instanceof Symbol) {
            return printer.visit((Symbol) arg, l);
        } else if (arg instanceof JavaFileObject) {
            return ((JavaFileObject) arg).getName();
        } else if (arg instanceof Profile) {
            return ((Profile) arg).name;
        } else if (arg instanceof Formattable) {
            return ((Formattable) arg).toString(l, messages);
        } else {
            return String.valueOf(arg);
        }
    }

    private String expr2String(JCExpression tree) {
        switch (tree.getTag()) {
            case PARENS:
                return expr2String(((JCParens) tree).expr);
            case LAMBDA:
            case REFERENCE:
            case CONDEXPR:
                return Pretty.toSimpleString(tree);
            default:
                Assert.error("unexpected tree kind " + tree.getKind());
                return null;
        }
    }

    protected String formatIterable(JCDiagnostic d, Iterable<?> it, Locale l) {
        StringBuilder sbuf = new StringBuilder();
        String sep = "";
        for (Object o : it) {
            sbuf.append(sep);
            sbuf.append(formatArgument(d, o, l));
            sep = ",";
        }
        return sbuf.toString();
    }

    protected List<String> formatSubdiagnostics(JCDiagnostic d, Locale l) {
        List<String> subdiagnostics = List.nil();
        int maxDepth = config.getMultilineLimit(MultilineLimit.DEPTH);
        if (maxDepth == -1 || depth < maxDepth) {
            depth++;
            try {
                int maxCount = config.getMultilineLimit(MultilineLimit.LENGTH);
                int count = 0;
                for (JCDiagnostic d2 : d.getSubdiagnostics()) {
                    if (maxCount == -1 || count < maxCount) {
                        subdiagnostics = subdiagnostics.append(formatSubdiagnostic(d, d2, l));
                        count++;
                    } else
                        break;
                }
            } finally {
                depth--;
            }
        }
        return subdiagnostics;
    }

    protected String formatSubdiagnostic(JCDiagnostic parent, JCDiagnostic sub, Locale l) {
        return formatMessage(sub, l);
    }

    protected String formatSourceLine(JCDiagnostic d, int nSpaces) {
        StringBuilder buf = new StringBuilder();
        DiagnosticSource source = d.getDiagnosticSource();
        int pos = d.getIntPosition();
        if (d.getIntPosition() == Position.NOPOS)
            throw new AssertionError();
        String line = (source == null ? null : source.getLine(pos));
        if (line == null)
            return "";
        buf.append(indent(line, nSpaces));
        int col = source.getColumnNumber(pos, false);
        if (config.isCaretEnabled()) {
            buf.append("\n");
            for (int i = 0; i < col - 1; i++) {
                buf.append((line.charAt(i) == '\t') ? "\t" : " ");
            }
            buf.append(indent("^", nSpaces));
        }
        return buf.toString();
    }

    protected String formatLintCategory(JCDiagnostic d, Locale l) {
        LintCategory lc = d.getLintCategory();
        if (lc == null)
            return "";
        return localize(l, "compiler.warn.lintOption", lc.option);
    }

    protected String localize(Locale l, String key, Object... args) {
        return messages.getLocalizedString(l, key, args);
    }

    public boolean displaySource(JCDiagnostic d) {
        return config.getVisible().contains(DiagnosticPart.SOURCE) &&
                d.getType() != FRAGMENT &&
                d.getIntPosition() != Position.NOPOS;
    }

    public boolean isRaw() {
        return false;
    }

    protected String indentString(int nSpaces) {
        String spaces = "                        ";
        if (nSpaces <= spaces.length())
            return spaces.substring(0, nSpaces);
        else {
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < nSpaces; i++)
                buf.append(" ");
            return buf.toString();
        }
    }

    protected String indent(String s, int nSpaces) {
        String indent = indentString(nSpaces);
        StringBuilder buf = new StringBuilder();
        String nl = "";
        for (String line : s.split("\n")) {
            buf.append(nl);
            buf.append(indent + line);
            nl = "\n";
        }
        return buf.toString();
    }

    public SimpleConfiguration getConfiguration() {
        return config;
    }

    public Printer getPrinter() {
        return printer;
    }

    public void setPrinter(Printer printer) {
        this.printer = printer;
    }

}
