package com.github.api.sun.tools.javac.util;

import com.github.api.sun.tools.javac.api.DiagnosticFormatter;
import com.github.api.sun.tools.javac.main.Main;
import com.github.api.sun.tools.javac.main.Option;
import com.github.api.sun.tools.javac.tree.EndPosTable;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticType;

import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import static com.github.api.sun.tools.javac.main.Option.*;

public class Log extends AbstractLog {

    public static final Context.Key<Log> logKey
            = new Context.Key<Log>();

    public static final Context.Key<PrintWriter> outKey =
            new Context.Key<PrintWriter>();
    private static boolean useRawMessages = false;
    public boolean promptOnError;
    public boolean emitWarnings;
    public boolean suppressNotes;
    public boolean dumpOnError;

    public boolean multipleErrors;
    public Set<String> expectDiagKeys;
    public boolean compressedOutput;
    public int nerrors = 0;
    public int nwarnings = 0;
    protected PrintWriter errWriter;
    protected PrintWriter warnWriter;
    protected PrintWriter noticeWriter;
    protected int MaxErrors;
    protected int MaxWarnings;
    protected DiagnosticListener<? super JavaFileObject> diagListener;

    private DiagnosticFormatter<JCDiagnostic> diagFormatter;
    private JavacMessages messages;
    private DiagnosticHandler diagnosticHandler;
    private Set<Pair<JavaFileObject, Integer>> recorded = new HashSet<Pair<JavaFileObject, Integer>>();

    protected Log(Context context, PrintWriter errWriter, PrintWriter warnWriter, PrintWriter noticeWriter) {
        super(JCDiagnostic.Factory.instance(context));
        context.put(logKey, this);
        this.errWriter = errWriter;
        this.warnWriter = warnWriter;
        this.noticeWriter = noticeWriter;
        @SuppressWarnings("unchecked")
        DiagnosticListener<? super JavaFileObject> dl =
                context.get(DiagnosticListener.class);
        this.diagListener = dl;
        diagnosticHandler = new DefaultDiagnosticHandler();
        messages = JavacMessages.instance(context);
        messages.add(Main.javacBundleName);
        final Options options = Options.instance(context);
        initOptions(options);
        options.addListener(new Runnable() {
            public void run() {
                initOptions(options);
            }
        });
    }

    protected Log(Context context) {
        this(context, defaultWriter(context));
    }

    protected Log(Context context, PrintWriter defaultWriter) {
        this(context, defaultWriter, defaultWriter, defaultWriter);
    }

    static PrintWriter defaultWriter(Context context) {
        PrintWriter result = context.get(outKey);
        if (result == null)
            context.put(outKey, result = new PrintWriter(System.err));
        return result;
    }

    public static Log instance(Context context) {
        Log instance = context.get(logKey);
        if (instance == null)
            instance = new Log(context);
        return instance;
    }

    public static void printRawLines(PrintWriter writer, String msg) {
        int nl;
        while ((nl = msg.indexOf('\n')) != -1) {
            writer.println(msg.substring(0, nl));
            msg = msg.substring(nl + 1);
        }
        if (msg.length() != 0) writer.println(msg);
    }

    public static String getLocalizedString(String key, Object... args) {
        return JavacMessages.getDefaultLocalizedString(PrefixKind.COMPILER_MISC.key(key), args);
    }

    public static String format(String fmt, Object... args) {
        return String.format(null, fmt, args);
    }

    private void initOptions(Options options) {
        this.dumpOnError = options.isSet(DOE);
        this.promptOnError = options.isSet(PROMPT);
        this.emitWarnings = options.isUnset(XLINT_CUSTOM, "none");
        this.suppressNotes = options.isSet("suppressNotes");
        this.MaxErrors = getIntOption(options, XMAXERRS, getDefaultMaxErrors());
        this.MaxWarnings = getIntOption(options, XMAXWARNS, getDefaultMaxWarnings());
        boolean rawDiagnostics = options.isSet("rawDiagnostics");
        this.diagFormatter = rawDiagnostics ? new RawDiagnosticFormatter(options) :
                new BasicDiagnosticFormatter(options, messages);
        String ek = options.get("expectKeys");
        if (ek != null)
            expectDiagKeys = new HashSet<String>(Arrays.asList(ek.split(", *")));
    }

    private int getIntOption(Options options, Option option, int defaultValue) {
        String s = options.get(option);
        try {
            if (s != null) {
                int n = Integer.parseInt(s);
                return (n <= 0 ? Integer.MAX_VALUE : n);
            }
        } catch (NumberFormatException e) {

        }
        return defaultValue;
    }

    protected int getDefaultMaxErrors() {
        return 100;
    }

    protected int getDefaultMaxWarnings() {
        return 100;
    }

    public boolean hasDiagnosticListener() {
        return diagListener != null;
    }

    public void setEndPosTable(JavaFileObject name, EndPosTable endPosTable) {
        name.getClass();
        getSource(name).setEndPosTable(endPosTable);
    }

    public JavaFileObject currentSourceFile() {
        return source == null ? null : source.getFile();
    }

    public DiagnosticFormatter<JCDiagnostic> getDiagnosticFormatter() {
        return diagFormatter;
    }

    public void setDiagnosticFormatter(DiagnosticFormatter<JCDiagnostic> diagFormatter) {
        this.diagFormatter = diagFormatter;
    }

    public PrintWriter getWriter(WriterKind kind) {
        switch (kind) {
            case NOTICE:
                return noticeWriter;
            case WARNING:
                return warnWriter;
            case ERROR:
                return errWriter;
            default:
                throw new IllegalArgumentException();
        }
    }

    public void setWriter(WriterKind kind, PrintWriter pw) {
        pw.getClass();
        switch (kind) {
            case NOTICE:
                noticeWriter = pw;
                break;
            case WARNING:
                warnWriter = pw;
                break;
            case ERROR:
                errWriter = pw;
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    public void setWriters(PrintWriter pw) {
        pw.getClass();
        noticeWriter = warnWriter = errWriter = pw;
    }

    public void initRound(Log other) {
        this.noticeWriter = other.noticeWriter;
        this.warnWriter = other.warnWriter;
        this.errWriter = other.errWriter;
        this.sourceMap = other.sourceMap;
        this.recorded = other.recorded;
        this.nerrors = other.nerrors;
        this.nwarnings = other.nwarnings;
    }

    public void popDiagnosticHandler(DiagnosticHandler h) {
        Assert.check(diagnosticHandler == h);
        diagnosticHandler = h.prev;
    }

    public void flush() {
        errWriter.flush();
        warnWriter.flush();
        noticeWriter.flush();
    }

    public void flush(WriterKind kind) {
        getWriter(kind).flush();
    }

    protected boolean shouldReport(JavaFileObject file, int pos) {
        if (multipleErrors || file == null)
            return true;
        Pair<JavaFileObject, Integer> coords = new Pair<JavaFileObject, Integer>(file, pos);
        boolean shouldReport = !recorded.contains(coords);
        if (shouldReport)
            recorded.add(coords);
        return shouldReport;
    }

    public void prompt() {
        if (promptOnError) {
            System.err.println(localize("resume.abort"));
            try {
                while (true) {
                    switch (System.in.read()) {
                        case 'a':
                        case 'A':
                            System.exit(-1);
                            return;
                        case 'r':
                        case 'R':
                            return;
                        case 'x':
                        case 'X':
                            throw new AssertionError("user abort");
                        default:
                    }
                }
            } catch (IOException e) {
            }
        }
    }

    private void printErrLine(int pos, PrintWriter writer) {
        String line = (source == null ? null : source.getLine(pos));
        if (line == null)
            return;
        int col = source.getColumnNumber(pos, false);
        printRawLines(writer, line);
        for (int i = 0; i < col - 1; i++) {
            writer.print((line.charAt(i) == '\t') ? "\t" : " ");
        }
        writer.println("^");
        writer.flush();
    }

    public void printNewline() {
        noticeWriter.println();
    }

    public void printNewline(WriterKind wk) {
        getWriter(wk).println();
    }

    public void printLines(String key, Object... args) {
        printRawLines(noticeWriter, localize(key, args));
    }

    public void printLines(PrefixKind pk, String key, Object... args) {
        printRawLines(noticeWriter, localize(pk, key, args));
    }

    public void printLines(WriterKind wk, String key, Object... args) {
        printRawLines(getWriter(wk), localize(key, args));
    }

    public void printLines(WriterKind wk, PrefixKind pk, String key, Object... args) {
        printRawLines(getWriter(wk), localize(pk, key, args));
    }

    public void printRawLines(String msg) {
        printRawLines(noticeWriter, msg);
    }

    public void printRawLines(WriterKind kind, String msg) {
        printRawLines(getWriter(kind), msg);
    }

    public void printVerbose(String key, Object... args) {
        printRawLines(noticeWriter, localize("verbose." + key, args));
    }

    protected void directError(String key, Object... args) {
        printRawLines(errWriter, localize(key, args));
        errWriter.flush();
    }

    public void strictWarning(DiagnosticPosition pos, String key, Object... args) {
        writeDiagnostic(diags.warning(source, pos, key, args));
        nwarnings++;
    }

    public void report(JCDiagnostic diagnostic) {
        diagnosticHandler.report(diagnostic);
    }

    protected void writeDiagnostic(JCDiagnostic diag) {
        if (diagListener != null) {
            diagListener.report(diag);
            return;
        }
        PrintWriter writer = getWriterForDiagnosticType(diag.getType());
        printRawLines(writer, diagFormatter.format(diag, messages.getCurrentLocale()));
        if (promptOnError) {
            switch (diag.getType()) {
                case ERROR:
                case WARNING:
                    prompt();
            }
        }
        if (dumpOnError)
            new RuntimeException().printStackTrace(writer);
        writer.flush();
    }

    @Deprecated
    protected PrintWriter getWriterForDiagnosticType(DiagnosticType dt) {
        switch (dt) {
            case FRAGMENT:
                throw new IllegalArgumentException();
            case NOTE:
                return noticeWriter;
            case WARNING:
                return warnWriter;
            case ERROR:
                return errWriter;
            default:
                throw new Error();
        }
    }

    public String localize(String key, Object... args) {
        return localize(PrefixKind.COMPILER_MISC, key, args);
    }

    public String localize(PrefixKind pk, String key, Object... args) {
        if (useRawMessages)
            return pk.key(key);
        else
            return messages.getLocalizedString(pk.key(key), args);
    }

    private void printRawError(int pos, String msg) {
        if (source == null || pos == Position.NOPOS) {
            printRawLines(errWriter, "error: " + msg);
        } else {
            int line = source.getLineNumber(pos);
            JavaFileObject file = source.getFile();
            if (file != null)
                printRawLines(errWriter,
                        file.getName() + ":" +
                                line + ": " + msg);
            printErrLine(pos, errWriter);
        }
        errWriter.flush();
    }

    public void rawError(int pos, String msg) {
        if (nerrors < MaxErrors && shouldReport(currentSourceFile(), pos)) {
            printRawError(pos, msg);
            prompt();
            nerrors++;
        }
        errWriter.flush();
    }

    public void rawWarning(int pos, String msg) {
        if (nwarnings < MaxWarnings && emitWarnings) {
            printRawError(pos, "warning: " + msg);
        }
        prompt();
        nwarnings++;
        errWriter.flush();
    }

    public enum PrefixKind {
        JAVAC("javac."),
        COMPILER_MISC("compiler.misc.");

        final String value;

        PrefixKind(String v) {
            value = v;
        }

        public String key(String k) {
            return value + k;
        }
    }


    public enum WriterKind {NOTICE, WARNING, ERROR}

    public static abstract class DiagnosticHandler {

        protected DiagnosticHandler prev;

        protected void install(Log log) {
            prev = log.diagnosticHandler;
            log.diagnosticHandler = this;
        }

        public abstract void report(JCDiagnostic diag);
    }

    public static class DiscardDiagnosticHandler extends DiagnosticHandler {
        public DiscardDiagnosticHandler(Log log) {
            install(log);
        }

        public void report(JCDiagnostic diag) {
        }
    }

    public static class DeferredDiagnosticHandler extends DiagnosticHandler {
        private final Filter<JCDiagnostic> filter;
        private Queue<JCDiagnostic> deferred = new ListBuffer<>();

        public DeferredDiagnosticHandler(Log log) {
            this(log, null);
        }

        public DeferredDiagnosticHandler(Log log, Filter<JCDiagnostic> filter) {
            this.filter = filter;
            install(log);
        }

        public void report(JCDiagnostic diag) {
            if (!diag.isFlagSet(JCDiagnostic.DiagnosticFlag.NON_DEFERRABLE) &&
                    (filter == null || filter.accepts(diag))) {
                deferred.add(diag);
            } else {
                prev.report(diag);
            }
        }

        public Queue<JCDiagnostic> getDiagnostics() {
            return deferred;
        }

        public void reportDeferredDiagnostics() {
            reportDeferredDiagnostics(EnumSet.allOf(JCDiagnostic.Kind.class));
        }

        public void reportDeferredDiagnostics(Set<JCDiagnostic.Kind> kinds) {
            JCDiagnostic d;
            while ((d = deferred.poll()) != null) {
                if (kinds.contains(d.getKind()))
                    prev.report(d);
            }
            deferred = null;
        }
    }

    private class DefaultDiagnosticHandler extends DiagnosticHandler {
        public void report(JCDiagnostic diagnostic) {
            if (expectDiagKeys != null)
                expectDiagKeys.remove(diagnostic.getCode());
            switch (diagnostic.getType()) {
                case FRAGMENT:
                    throw new IllegalArgumentException();
                case NOTE:


                    if ((emitWarnings || diagnostic.isMandatory()) && !suppressNotes) {
                        writeDiagnostic(diagnostic);
                    }
                    break;
                case WARNING:
                    if (emitWarnings || diagnostic.isMandatory()) {
                        if (nwarnings < MaxWarnings) {
                            writeDiagnostic(diagnostic);
                            nwarnings++;
                        }
                    }
                    break;
                case ERROR:
                    if (nerrors < MaxErrors
                            && shouldReport(diagnostic.getSource(), diagnostic.getIntPosition())) {
                        writeDiagnostic(diagnostic);
                        nerrors++;
                    }
                    break;
            }
            if (diagnostic.isFlagSet(JCDiagnostic.DiagnosticFlag.COMPRESSED)) {
                compressedOutput = true;
            }
        }
    }
}
