package com.github.api.sun.tools.javac.util;

import com.github.api.sun.tools.javac.api.DiagnosticFormatter;
import com.github.api.sun.tools.javac.code.Lint.LintCategory;
import com.github.api.sun.tools.javac.tree.EndPosTable;
import com.github.api.sun.tools.javac.tree.JCTree;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import static com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticType.*;

public class JCDiagnostic implements Diagnostic<JavaFileObject> {

    @Deprecated
    private static DiagnosticFormatter<JCDiagnostic> fragmentFormatter;
    protected final Object[] args;
    private final DiagnosticType type;
    private final DiagnosticSource source;

    private final DiagnosticPosition position;
    private final int line;
    private final int column;
    private final String key;
    private final Set<DiagnosticFlag> flags;
    private final LintCategory lintCategory;
    private DiagnosticFormatter<JCDiagnostic> defaultFormatter;
    protected JCDiagnostic(DiagnosticFormatter<JCDiagnostic> formatter,
                           DiagnosticType dt,
                           LintCategory lc,
                           Set<DiagnosticFlag> flags,
                           DiagnosticSource source,
                           DiagnosticPosition pos,
                           String key,
                           Object... args) {
        if (source == null && pos != null && pos.getPreferredPosition() != Position.NOPOS)
            throw new IllegalArgumentException();
        this.defaultFormatter = formatter;
        this.type = dt;
        this.lintCategory = lc;
        this.flags = flags;
        this.source = source;
        this.position = pos;
        this.key = key;
        this.args = args;
        int n = (pos == null ? Position.NOPOS : pos.getPreferredPosition());
        if (n == Position.NOPOS || source == null)
            line = column = -1;
        else {
            line = source.getLineNumber(n);
            column = source.getColumnNumber(n, true);
        }
    }

    @Deprecated
    public static JCDiagnostic fragment(String key, Object... args) {
        return new JCDiagnostic(getFragmentFormatter(),
                FRAGMENT,
                null,
                EnumSet.noneOf(DiagnosticFlag.class),
                null,
                null,
                "compiler." + FRAGMENT.key + "." + key,
                args);
    }

    @Deprecated
    public static DiagnosticFormatter<JCDiagnostic> getFragmentFormatter() {
        if (fragmentFormatter == null) {
            fragmentFormatter = new BasicDiagnosticFormatter(JavacMessages.getDefaultMessages());
        }
        return fragmentFormatter;
    }

    public DiagnosticType getType() {
        return type;
    }

    public List<JCDiagnostic> getSubdiagnostics() {
        return List.nil();
    }

    public boolean isMultiline() {
        return false;
    }

    public boolean isMandatory() {
        return flags.contains(DiagnosticFlag.MANDATORY);
    }

    public boolean hasLintCategory() {
        return (lintCategory != null);
    }

    public LintCategory getLintCategory() {
        return lintCategory;
    }

    public JavaFileObject getSource() {
        if (source == null)
            return null;
        else
            return source.getFile();
    }

    public DiagnosticSource getDiagnosticSource() {
        return source;
    }

    protected int getIntStartPosition() {
        return (position == null ? Position.NOPOS : position.getStartPosition());
    }

    protected int getIntPosition() {
        return (position == null ? Position.NOPOS : position.getPreferredPosition());
    }

    protected int getIntEndPosition() {
        return (position == null ? Position.NOPOS : position.getEndPosition(source.getEndPosTable()));
    }

    public long getStartPosition() {
        return getIntStartPosition();
    }

    public long getPosition() {
        return getIntPosition();
    }

    public long getEndPosition() {
        return getIntEndPosition();
    }

    public DiagnosticPosition getDiagnosticPosition() {
        return position;
    }

    public long getLineNumber() {
        return line;
    }

    public long getColumnNumber() {
        return column;
    }

    public Object[] getArgs() {
        return args;
    }

    public String getPrefix() {
        return getPrefix(type);
    }

    public String getPrefix(DiagnosticType dt) {
        return defaultFormatter.formatKind(this, Locale.getDefault());
    }

    @Override
    public String toString() {
        return defaultFormatter.format(this, Locale.getDefault());
    }

    public Kind getKind() {
        switch (type) {
            case NOTE:
                return Kind.NOTE;
            case WARNING:
                return flags.contains(DiagnosticFlag.MANDATORY)
                        ? Kind.MANDATORY_WARNING
                        : Kind.WARNING;
            case ERROR:
                return Kind.ERROR;
            default:
                return Kind.OTHER;
        }
    }

    public String getCode() {
        return key;
    }

    public String getMessage(Locale locale) {
        return defaultFormatter.formatMessage(this, locale);
    }

    public void setFlag(DiagnosticFlag flag) {
        flags.add(flag);
        if (type == DiagnosticType.ERROR) {
            switch (flag) {
                case SYNTAX:
                    flags.remove(DiagnosticFlag.RECOVERABLE);
                    break;
                case RESOLVE_ERROR:
                    flags.add(DiagnosticFlag.RECOVERABLE);
                    break;
            }
        }
    }

    public boolean isFlagSet(DiagnosticFlag flag) {
        return flags.contains(flag);
    }

    public enum DiagnosticType {

        FRAGMENT("misc"),

        NOTE("note"),

        WARNING("warn"),

        ERROR("err");
        final String key;

        DiagnosticType(String key) {
            this.key = key;
        }
    }

    public enum DiagnosticFlag {
        MANDATORY,
        RESOLVE_ERROR,
        SYNTAX,
        RECOVERABLE,
        NON_DEFERRABLE,
        COMPRESSED
    }

    public interface DiagnosticPosition {

        JCTree getTree();

        int getStartPosition();

        int getPreferredPosition();

        int getEndPosition(EndPosTable endPosTable);
    }

    public static class Factory {

        protected static final Context.Key<Factory> diagnosticFactoryKey =
                new Context.Key<Factory>();
        final String prefix;
        final Set<DiagnosticFlag> defaultErrorFlags;
        DiagnosticFormatter<JCDiagnostic> formatter;
        protected Factory(Context context) {
            this(JavacMessages.instance(context), "compiler");
            context.put(diagnosticFactoryKey, this);
            final Options options = Options.instance(context);
            initOptions(options);
            options.addListener(new Runnable() {
                public void run() {
                    initOptions(options);
                }
            });
        }

        public Factory(JavacMessages messages, String prefix) {
            this.prefix = prefix;
            this.formatter = new BasicDiagnosticFormatter(messages);
            defaultErrorFlags = EnumSet.of(DiagnosticFlag.MANDATORY);
        }

        public static Factory instance(Context context) {
            Factory instance = context.get(diagnosticFactoryKey);
            if (instance == null)
                instance = new Factory(context);
            return instance;
        }

        private void initOptions(Options options) {
            if (options.isSet("onlySyntaxErrorsUnrecoverable"))
                defaultErrorFlags.add(DiagnosticFlag.RECOVERABLE);
        }

        public JCDiagnostic error(
                DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return create(ERROR, null, defaultErrorFlags, source, pos, key, args);
        }

        public JCDiagnostic mandatoryWarning(
                DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return create(WARNING, null, EnumSet.of(DiagnosticFlag.MANDATORY), source, pos, key, args);
        }

        public JCDiagnostic mandatoryWarning(
                LintCategory lc,
                DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return create(WARNING, lc, EnumSet.of(DiagnosticFlag.MANDATORY), source, pos, key, args);
        }

        public JCDiagnostic warning(
                LintCategory lc, String key, Object... args) {
            return create(WARNING, lc, EnumSet.noneOf(DiagnosticFlag.class), null, null, key, args);
        }

        public JCDiagnostic warning(
                DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return create(WARNING, null, EnumSet.noneOf(DiagnosticFlag.class), source, pos, key, args);
        }

        public JCDiagnostic warning(
                LintCategory lc, DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return create(WARNING, lc, EnumSet.noneOf(DiagnosticFlag.class), source, pos, key, args);
        }

        public JCDiagnostic mandatoryNote(DiagnosticSource source, String key, Object... args) {
            return create(NOTE, null, EnumSet.of(DiagnosticFlag.MANDATORY), source, null, key, args);
        }

        public JCDiagnostic note(String key, Object... args) {
            return create(NOTE, null, EnumSet.noneOf(DiagnosticFlag.class), null, null, key, args);
        }

        public JCDiagnostic note(
                DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return create(NOTE, null, EnumSet.noneOf(DiagnosticFlag.class), source, pos, key, args);
        }

        public JCDiagnostic fragment(String key, Object... args) {
            return create(FRAGMENT, null, EnumSet.noneOf(DiagnosticFlag.class), null, null, key, args);
        }

        public JCDiagnostic create(
                DiagnosticType kind, DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return create(kind, null, EnumSet.noneOf(DiagnosticFlag.class), source, pos, key, args);
        }

        public JCDiagnostic create(
                DiagnosticType kind, LintCategory lc, Set<DiagnosticFlag> flags, DiagnosticSource source, DiagnosticPosition pos, String key, Object... args) {
            return new JCDiagnostic(formatter, kind, lc, flags, source, pos, qualify(kind, key), args);
        }

        protected String qualify(DiagnosticType t, String key) {
            return prefix + "." + t.key + "." + key;
        }
    }

    public static class SimpleDiagnosticPosition implements DiagnosticPosition {
        private final int pos;

        public SimpleDiagnosticPosition(int pos) {
            this.pos = pos;
        }

        public JCTree getTree() {
            return null;
        }

        public int getStartPosition() {
            return pos;
        }

        public int getPreferredPosition() {
            return pos;
        }

        public int getEndPosition(EndPosTable endPosTable) {
            return pos;
        }
    }

    public static class MultilineDiagnostic extends JCDiagnostic {
        private final List<JCDiagnostic> subdiagnostics;

        public MultilineDiagnostic(JCDiagnostic other, List<JCDiagnostic> subdiagnostics) {
            super(other.defaultFormatter,
                    other.getType(),
                    other.getLintCategory(),
                    other.flags,
                    other.getDiagnosticSource(),
                    other.position,
                    other.getCode(),
                    other.getArgs());
            this.subdiagnostics = subdiagnostics;
        }

        @Override
        public List<JCDiagnostic> getSubdiagnostics() {
            return subdiagnostics;
        }

        @Override
        public boolean isMultiline() {
            return true;
        }
    }
}
