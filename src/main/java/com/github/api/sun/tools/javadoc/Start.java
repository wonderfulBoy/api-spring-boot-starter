package com.github.api.sun.tools.javadoc;

import com.github.api.sun.javadoc.LanguageVersion;
import com.github.api.sun.tools.javac.main.CommandLine;
import com.github.api.sun.tools.javac.util.*;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import static com.github.api.sun.tools.javac.code.Flags.PROTECTED;
import static com.github.api.sun.tools.javac.code.Flags.PUBLIC;

public class Start extends ToolOption.Helper {
    private static final String javadocName = "javadoc";
    private static final String standardDocletClassName =
            "com.github.api.sun.tools.doclets.standard.Standard";
    private final Context context;
    private final String defaultDocletClassName;
    private final ClassLoader docletParentClassLoader;
    private final Messager messager;
    private long defaultFilter = PUBLIC | PROTECTED;
    private DocletInvoker docletInvoker;
    private boolean apiMode;

    Start(String programName,
          PrintWriter errWriter,
          PrintWriter warnWriter,
          PrintWriter noticeWriter,
          String defaultDocletClassName,
          ClassLoader docletParentClassLoader) {
        context = new Context();
        messager = new Messager(context, programName, errWriter, warnWriter, noticeWriter);
        this.defaultDocletClassName = defaultDocletClassName;
        this.docletParentClassLoader = docletParentClassLoader;
    }

    Start(String programName, String defaultDocletClassName) {
        this(programName, defaultDocletClassName, null);
    }

    Start(String programName, String defaultDocletClassName,
          ClassLoader docletParentClassLoader) {
        context = new Context();
        messager = new Messager(context, programName);
        this.defaultDocletClassName = defaultDocletClassName;
        this.docletParentClassLoader = docletParentClassLoader;
    }

    Start(String programName, ClassLoader docletParentClassLoader) {
        this(programName, standardDocletClassName, docletParentClassLoader);
    }

    Start(String programName) {
        this(programName, standardDocletClassName);
    }

    Start() {
        this(javadocName);
    }

    @Override
    void usage() {
        usage(true);
    }

    void usage(boolean exit) {
        usage("main.usage", "-help", null, exit);
    }

    @Override
    void Xusage() {
        Xusage(true);
    }

    void Xusage(boolean exit) {
        usage("main.Xusage", "-X", "main.Xusage.foot", exit);
    }

    private void usage(String main, String doclet, String foot, boolean exit) {
        messager.notice(main);
        if (docletInvoker != null) {
            docletInvoker.optionLength(doclet);
        }
        if (foot != null)
            messager.notice(foot);
        if (exit) exit();
    }

    private void exit() {
        messager.exit();
    }

    int begin(String... argv) {
        boolean ok = begin(null, argv, Collections.emptySet());
        return ok ? 0 : 1;
    }

    public boolean begin(Class<?> docletClass, Iterable<String> options, Iterable<? extends JavaFileObject> fileObjects) {
        Collection<String> opts = new ArrayList<String>();
        for (String opt : options) opts.add(opt);
        return begin(docletClass, opts.toArray(new String[opts.size()]), fileObjects);
    }

    private boolean begin(Class<?> docletClass, String[] options, Iterable<? extends JavaFileObject> fileObjects) {
        boolean failed = false;
        try {
            failed = !parseAndExecute(docletClass, options, fileObjects);
        } catch (Messager.ExitJavadoc exc) {
        } catch (OutOfMemoryError ee) {
            messager.error(Messager.NOPOS, "main.out.of.memory");
            failed = true;
        } catch (ClientCodeException e) {
            throw e;
        } catch (Error ee) {
            ee.printStackTrace(System.err);
            messager.error(Messager.NOPOS, "main.fatal.error");
            failed = true;
        } catch (Exception ee) {
            ee.printStackTrace(System.err);
            messager.error(Messager.NOPOS, "main.fatal.exception");
            failed = true;
        } finally {
            messager.exitNotice();
            messager.flush();
        }
        failed |= messager.nerrors() > 0;
        failed |= rejectWarnings && messager.nwarnings() > 0;
        return !failed;
    }

    private boolean parseAndExecute(
            Class<?> docletClass,
            String[] argv,
            Iterable<? extends JavaFileObject> fileObjects) throws IOException {
        long tm = System.currentTimeMillis();
        ListBuffer<String> javaNames = new ListBuffer<String>();
        try {
            argv = CommandLine.parse(argv);
        } catch (FileNotFoundException e) {
            messager.error(Messager.NOPOS, "main.cant.read", e.getMessage());
            exit();
        } catch (IOException e) {
            e.printStackTrace(System.err);
            exit();
        }
        JavaFileManager fileManager = context.get(JavaFileManager.class);
        setDocletInvoker(docletClass, fileManager, argv);
        compOpts = Options.instance(context);
        compOpts.put("-Xlint:-options", "-Xlint:-options");
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            ToolOption o = ToolOption.get(arg);
            if (o != null) {
                if (o == ToolOption.LOCALE && i > 0)
                    usageError("main.locale_first");
                if (o.hasArg) {
                    oneArg(argv, i++);
                    o.process(this, argv[i]);
                } else {
                    setOption(arg);
                    o.process(this);
                }
            } else if (arg.startsWith("-XD")) {
                String s = arg.substring("-XD".length());
                int eq = s.indexOf('=');
                String key = (eq < 0) ? s : s.substring(0, eq);
                String value = (eq < 0) ? s : s.substring(eq + 1);
                compOpts.put(key, value);
            } else if (arg.startsWith("-")) {
                int optionLength;
                optionLength = docletInvoker.optionLength(arg);
                if (optionLength < 0) {
                    exit();
                } else if (optionLength == 0) {
                    usageError("main.invalid_flag", arg);
                } else {
                    if ((i + optionLength) > argv.length) {
                        usageError("main.requires_argument", arg);
                    }
                    ListBuffer<String> args = new ListBuffer<String>();
                    for (int j = 0; j < optionLength - 1; ++j) {
                        args.append(argv[++i]);
                    }
                    setOption(arg, args.toList());
                }
            } else {
                javaNames.append(arg);
            }
        }
        compOpts.notifyListeners();
        if (javaNames.isEmpty() && subPackages.isEmpty() && isEmpty(fileObjects)) {
            usageError("main.No_packages_or_classes_specified");
        }
        if (!docletInvoker.validOptions(options.toList())) {
            exit();
        }
        JavadocTool comp = JavadocTool.make0(context);
        if (comp == null) return false;
        if (showAccess == null) {
            setFilter(defaultFilter);
        }
        LanguageVersion languageVersion = docletInvoker.languageVersion();
        RootDocImpl root = comp.getRootDocImpl(
                docLocale,
                encoding,
                showAccess,
                javaNames.toList(),
                options.toList(),
                fileObjects,
                breakiterator,
                subPackages.toList(),
                excludedPackages.toList(),
                docClasses,
                languageVersion == null || languageVersion == LanguageVersion.JAVA_1_1,
                quiet);
        comp = null;
        boolean ok = root != null;
        if (ok) ok = docletInvoker.start(root);

        if (compOpts.get("-verbose") != null) {
            tm = System.currentTimeMillis() - tm;
            messager.notice("main.done_in", Long.toString(tm));
        }
        return ok;
    }

    private <T> boolean isEmpty(Iterable<T> iter) {
        return !iter.iterator().hasNext();
    }

    private void setDocletInvoker(Class<?> docletClass, JavaFileManager fileManager, String[] argv) {
        if (docletClass != null) {
            docletInvoker = new DocletInvoker(messager, docletClass, apiMode);

            return;
        }
        String docletClassName = null;
        String docletPath = null;
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            if (arg.equals(ToolOption.DOCLET.opt)) {
                oneArg(argv, i++);
                if (docletClassName != null) {
                    usageError("main.more_than_one_doclet_specified_0_and_1",
                            docletClassName, argv[i]);
                }
                docletClassName = argv[i];
            } else if (arg.equals(ToolOption.DOCLETPATH.opt)) {
                oneArg(argv, i++);
                if (docletPath == null) {
                    docletPath = argv[i];
                } else {
                    docletPath += File.pathSeparator + argv[i];
                }
            }
        }
        if (docletClassName == null) {
            docletClassName = defaultDocletClassName;
        }
        docletInvoker = new DocletInvoker(messager, fileManager,
                docletClassName, docletPath,
                docletParentClassLoader,
                apiMode);
    }

    private void oneArg(String[] args, int index) {
        if ((index + 1) < args.length) {
            setOption(args[index], args[index + 1]);
        } else {
            usageError("main.requires_argument", args[index]);
        }
    }

    @Override
    void usageError(String key, Object... args) {
        messager.error(Messager.NOPOS, key, args);
        usage(true);
    }

    private void setOption(String opt) {
        String[] option = {opt};
        options.append(option);
    }

    private void setOption(String opt, String argument) {
        String[] option = {opt, argument};
        options.append(option);
    }

    private void setOption(String opt, List<String> arguments) {
        String[] args = new String[arguments.length() + 1];
        int k = 0;
        args[k++] = opt;
        for (List<String> i = arguments; i.nonEmpty(); i = i.tail) {
            args[k++] = i.head;
        }
        options.append(args);
    }
}
