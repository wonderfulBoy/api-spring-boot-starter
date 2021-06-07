package com.sun.tools.javadoc;

import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.SourcePosition;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticType;
import com.sun.tools.javac.util.JavacMessages;
import com.sun.tools.javac.util.Log;

import java.io.PrintWriter;
import java.util.Locale;

public class Messager extends Log implements DocErrorReporter {
    public static final SourcePosition NOPOS = null;
    static final PrintWriter defaultErrWriter = new PrintWriter(System.err);
    static final PrintWriter defaultWarnWriter = new PrintWriter(System.err);
    static final PrintWriter defaultNoticeWriter = new PrintWriter(System.out);
    final String programName;
    private final JavacMessages messages;
    private final JCDiagnostic.Factory javadocDiags;
    private Locale locale;

    protected Messager(Context context, String programName) {
        this(context, programName, defaultErrWriter, defaultWarnWriter, defaultNoticeWriter);
    }

    @SuppressWarnings("deprecation")
    protected Messager(Context context,
                       String programName,
                       PrintWriter errWriter,
                       PrintWriter warnWriter,
                       PrintWriter noticeWriter) {
        super(context, errWriter, warnWriter, noticeWriter);
        messages = JavacMessages.instance(context);
        messages.add("com.sun.tools.javadoc.resources.javadoc");
        javadocDiags = new JCDiagnostic.Factory(messages, "javadoc");
        this.programName = programName;
    }

    public static Messager instance0(Context context) {
        Log instance = context.get(logKey);
        if (instance == null || !(instance instanceof Messager))
            throw new InternalError("no messager instance!");
        return (Messager) instance;
    }

    public static void preRegister(Context context,
                                   final String programName) {
        context.put(logKey, new Context.Factory<Log>() {
            public Log make(Context c) {
                return new Messager(c,
                        programName);
            }
        });
    }

    public static void preRegister(Context context,
                                   final String programName,
                                   final PrintWriter errWriter,
                                   final PrintWriter warnWriter,
                                   final PrintWriter noticeWriter) {
        context.put(logKey, new Context.Factory<Log>() {
            public Log make(Context c) {
                return new Messager(c,
                        programName,
                        errWriter,
                        warnWriter,
                        noticeWriter);
            }
        });
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    String getText(String key, Object... args) {
        return messages.getLocalizedString(locale, key, args);
    }

    public void printError(String msg) {
        printError(null, msg);
    }

    public void printError(SourcePosition pos, String msg) {
        if (diagListener != null) {
            report(DiagnosticType.ERROR, pos, msg);
            return;
        }

        if (nerrors < MaxErrors) {
            String prefix = (pos == null) ? programName : pos.toString();
            errWriter.println(prefix + ": " + getText("javadoc.error") + " - " + msg);
            errWriter.flush();
            prompt();
            nerrors++;
        }
    }

    public void printWarning(String msg) {
        printWarning(null, msg);
    }

    public void printWarning(SourcePosition pos, String msg) {
        if (diagListener != null) {
            report(DiagnosticType.WARNING, pos, msg);
            return;
        }

        if (nwarnings < MaxWarnings) {
            String prefix = (pos == null) ? programName : pos.toString();
            warnWriter.println(prefix + ": " + getText("javadoc.warning") + " - " + msg);
            warnWriter.flush();
            nwarnings++;
        }
    }

    public void printNotice(String msg) {
        printNotice(null, msg);
    }

    public void printNotice(SourcePosition pos, String msg) {
        if (diagListener != null) {
            report(DiagnosticType.NOTE, pos, msg);
            return;
        }

        if (pos == null)
            noticeWriter.println(msg);
        else
            noticeWriter.println(pos + ": " + msg);
        noticeWriter.flush();
    }

    public void error(SourcePosition pos, String key, Object... args) {
        printError(pos, getText(key, args));
    }

    public void warning(SourcePosition pos, String key, Object... args) {
        printWarning(pos, getText(key, args));
    }

    public void notice(String key, Object... args) {
        printNotice(getText(key, args));
    }

    public int nerrors() {
        return nerrors;
    }

    public int nwarnings() {
        return nwarnings;
    }

    public void exitNotice() {
        if (nerrors > 0) {
            notice((nerrors > 1) ? "main.errors" : "main.error",
                    "" + nerrors);
        }
        if (nwarnings > 0) {
            notice((nwarnings > 1) ? "main.warnings" : "main.warning",
                    "" + nwarnings);
        }
    }

    public void exit() {
        throw new ExitJavadoc();
    }

    private void report(DiagnosticType type, SourcePosition pos, String msg) {
        switch (type) {
            case ERROR:
            case WARNING:
                Object prefix = (pos == null) ? programName : pos;
                report(javadocDiags.create(type, null, null, "msg", prefix, msg));
                break;

            case NOTE:
                String key = (pos == null) ? "msg" : "pos.msg";
                report(javadocDiags.create(type, null, null, key, pos, msg));
                break;

            default:
                throw new IllegalArgumentException(type.toString());
        }
    }

    public class ExitJavadoc extends Error {
        private static final long serialVersionUID = 0;
    }
}
