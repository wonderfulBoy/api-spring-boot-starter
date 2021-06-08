package com.sun.tools.javac.main;

import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.tools.doclint.DocLint;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.file.CacheFSInfo;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.jvm.Profile;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.processing.AnnotationProcessingError;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.Log.PrefixKind;
import com.sun.tools.javac.util.ServiceLoader;
import com.sun.tools.javac.util.Log.WriterKind;

import javax.annotation.processing.Processor;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;

import static com.sun.tools.javac.main.Option.*;

public class Main {

    public static final String javacBundleName =
            "com.sun.tools.javac.resources.javac";
    public Log log;
    public Set<File> filenames = null;
    public ListBuffer<String> classnames = null;
    String ownName;
    PrintWriter out;
    boolean apiMode;
    private Option[] recognizedOptions =
            Option.getJavaCompilerOptions().toArray(new Option[0]);
    private Options options = null;
    private OptionHelper optionHelper = new OptionHelper() {
        @Override
        public String get(Option option) {
            return options.get(option);
        }

        @Override
        public void put(String name, String value) {
            options.put(name, value);
        }

        @Override
        public void remove(String name) {
            options.remove(name);
        }

        @Override
        public Log getLog() {
            return log;
        }

        @Override
        public String getOwnName() {
            return ownName;
        }

        @Override
        public void error(String key, Object... args) {
            Main.this.error(key, args);
        }

        @Override
        public void addFile(File f) {
            filenames.add(f);
        }

        @Override
        public void addClassName(String s) {
            classnames.append(s);
        }
    };
    private JavaFileManager fileManager;

    public Main(String name) {
        this(name, new PrintWriter(System.err, true));
    }

    public Main(String name, PrintWriter out) {
        this.ownName = name;
        this.out = out;
    }

    void error(String key, Object... args) {
        if (apiMode) {
            String msg = log.localize(PrefixKind.JAVAC, key, args);
            throw new PropagatedException(new IllegalStateException(msg));
        }
        warning(key, args);
        log.printLines(PrefixKind.JAVAC, "msg.usage", ownName);
    }

    void warning(String key, Object... args) {
        log.printRawLines(ownName + ": " + log.localize(PrefixKind.JAVAC, key, args));
    }

    public Option getOption(String flag) {
        for (Option option : recognizedOptions) {
            if (option.matches(flag))
                return option;
        }
        return null;
    }

    public void setOptions(Options options) {
        if (options == null)
            throw new NullPointerException();
        this.options = options;
    }

    public void setAPIMode(boolean apiMode) {
        this.apiMode = apiMode;
    }

    public Collection<File> processArgs(String[] flags) {
        return processArgs(flags, null);
    }

    public Collection<File> processArgs(String[] flags, String[] classNames) {
        int ac = 0;
        while (ac < flags.length) {
            String flag = flags[ac];
            ac++;
            Option option = null;
            if (flag.length() > 0) {


                int firstOptionToCheck = flag.charAt(0) == '-' ? 0 : recognizedOptions.length - 1;
                for (int j = firstOptionToCheck; j < recognizedOptions.length; j++) {
                    if (recognizedOptions[j].matches(flag)) {
                        option = recognizedOptions[j];
                        break;
                    }
                }
            }
            if (option == null) {
                error("err.invalid.flag", flag);
                return null;
            }
            if (option.hasArg()) {
                if (ac == flags.length) {
                    error("err.req.arg", flag);
                    return null;
                }
                String operand = flags[ac];
                ac++;
                if (option.process(optionHelper, flag, operand))
                    return null;
            } else {
                if (option.process(optionHelper, flag))
                    return null;
            }
        }
        if (options.get(PROFILE) != null && options.get(BOOTCLASSPATH) != null) {
            error("err.profile.bootclasspath.conflict");
            return null;
        }
        if (this.classnames != null && classNames != null) {
            this.classnames.addAll(Arrays.asList(classNames));
        }
        if (!checkDirectory(D))
            return null;
        if (!checkDirectory(S))
            return null;
        String sourceString = options.get(SOURCE);
        Source source = (sourceString != null)
                ? Source.lookup(sourceString)
                : Source.DEFAULT;
        String targetString = options.get(TARGET);
        Target target = (targetString != null)
                ? Target.lookup(targetString)
                : Target.DEFAULT;


        if (Character.isDigit(target.name.charAt(0))) {
            if (target.compareTo(source.requiredTarget()) < 0) {
                if (targetString != null) {
                    if (sourceString == null) {
                        warning("warn.target.default.source.conflict",
                                targetString,
                                source.requiredTarget().name);
                    } else {
                        warning("warn.source.target.conflict",
                                sourceString,
                                source.requiredTarget().name);
                    }
                    return null;
                } else {
                    target = source.requiredTarget();
                    options.put("-target", target.name);
                }
            } else {
                if (targetString == null && !source.allowGenerics()) {
                    target = Target.JDK1_4;
                    options.put("-target", target.name);
                }
            }
        }
        String profileString = options.get(PROFILE);
        if (profileString != null) {
            Profile profile = Profile.lookup(profileString);
            if (!profile.isValid(target)) {
                warning("warn.profile.target.conflict", profileString, target.name);
                return null;
            }
        }

        String showClass = options.get("showClass");
        if (showClass != null) {
            if (showClass.equals("showClass"))
                showClass = "com.sun.tools.javac.Main";
            showClass(showClass);
        }
        options.notifyListeners();
        return filenames;
    }

    private boolean checkDirectory(Option option) {
        String value = options.get(option);
        if (value == null)
            return true;
        File file = new File(value);
        if (!file.exists()) {
            error("err.dir.not.found", value);
            return false;
        }
        if (!file.isDirectory()) {
            error("err.file.not.directory", value);
            return false;
        }
        return true;
    }

    public Result compile(String[] args) {
        Context context = new Context();
        JavacFileManager.preRegister(context);
        Result result = compile(args, context);
        if (fileManager instanceof JavacFileManager) {

            ((JavacFileManager) fileManager).close();
        }
        return result;
    }

    public Result compile(String[] args, Context context) {
        return compile(args, context, List.nil(), null);
    }

    public Result compile(String[] args,
                          Context context,
                          List<JavaFileObject> fileObjects,
                          Iterable<? extends Processor> processors) {
        return compile(args, null, context, fileObjects, processors);
    }

    public Result compile(String[] args,
                          String[] classNames,
                          Context context,
                          List<JavaFileObject> fileObjects,
                          Iterable<? extends Processor> processors) {
        context.put(Log.outKey, out);
        log = Log.instance(context);
        if (options == null)
            options = Options.instance(context);
        filenames = new LinkedHashSet<File>();
        classnames = new ListBuffer<String>();
        JavaCompiler comp = null;

        try {
            if (args.length == 0
                    && (classNames == null || classNames.length == 0)
                    && fileObjects.isEmpty()) {
                Option.HELP.process(optionHelper, "-help");
                return Result.CMDERR;
            }
            Collection<File> files;
            try {
                files = processArgs(CommandLine.parse(args), classNames);
                if (files == null) {

                    return Result.CMDERR;
                } else if (files.isEmpty() && fileObjects.isEmpty() && classnames.isEmpty()) {

                    if (options.isSet(HELP)
                            || options.isSet(X)
                            || options.isSet(VERSION)
                            || options.isSet(FULLVERSION))
                        return Result.OK;
                    if (JavaCompiler.explicitAnnotationProcessingRequested(options)) {
                        error("err.no.source.files.classes");
                    } else {
                        error("err.no.source.files");
                    }
                    return Result.CMDERR;
                }
            } catch (java.io.FileNotFoundException e) {
                warning("err.file.not.found", e.getMessage());
                return Result.SYSERR;
            }
            boolean forceStdOut = options.isSet("stdout");
            if (forceStdOut) {
                log.flush();
                log.setWriters(new PrintWriter(System.out, true));
            }

            boolean batchMode = (options.isUnset("nonBatchMode")
                    && System.getProperty("nonBatchMode") == null);
            if (batchMode)
                CacheFSInfo.preRegister(context);


            String plugins = options.get(PLUGIN);
            if (plugins != null) {
                JavacProcessingEnvironment pEnv = JavacProcessingEnvironment.instance(context);
                ClassLoader cl = pEnv.getProcessorClassLoader();
                ServiceLoader<Plugin> sl = ServiceLoader.load(Plugin.class, cl);
                Set<List<String>> pluginsToCall = new LinkedHashSet<List<String>>();
                for (String plugin : plugins.split("\\x00")) {
                    pluginsToCall.add(List.from(plugin.split("\\s+")));
                }
                JavacTask task = null;
                Iterator<Plugin> iter = sl.iterator();
                while (iter.hasNext()) {
                    Plugin plugin = iter.next();
                    for (List<String> p : pluginsToCall) {
                        if (plugin.getName().equals(p.head)) {
                            pluginsToCall.remove(p);
                            try {
                                if (task == null)
                                    task = JavacTask.instance(pEnv);
                                plugin.init(task, p.tail.toArray(new String[p.tail.size()]));
                            } catch (Throwable ex) {
                                if (apiMode)
                                    throw new RuntimeException(ex);
                                pluginMessage(ex);
                                return Result.SYSERR;
                            }
                        }
                    }
                }
                for (List<String> p : pluginsToCall) {
                    log.printLines(PrefixKind.JAVAC, "msg.plugin.not.found", p.head);
                }
            }
            comp = JavaCompiler.instance(context);

            String xdoclint = options.get(XDOCLINT);
            String xdoclintCustom = options.get(XDOCLINT_CUSTOM);
            if (xdoclint != null || xdoclintCustom != null) {
                Set<String> doclintOpts = new LinkedHashSet<String>();
                if (xdoclint != null)
                    doclintOpts.add(DocLint.XMSGS_OPTION);
                if (xdoclintCustom != null) {
                    for (String s : xdoclintCustom.split("\\s+")) {
                        if (s.isEmpty())
                            continue;
                        doclintOpts.add(s.replace(XDOCLINT_CUSTOM.text, DocLint.XMSGS_CUSTOM_PREFIX));
                    }
                }
                if (!(doclintOpts.size() == 1
                        && doclintOpts.iterator().next().equals(DocLint.XMSGS_CUSTOM_PREFIX + "none"))) {
                    JavacTask t = BasicJavacTask.instance(context);

                    doclintOpts.add(DocLint.XIMPLICIT_HEADERS + "2");
                    new DocLint().init(t, doclintOpts.toArray(new String[doclintOpts.size()]));
                    comp.keepComments = true;
                }
            }
            fileManager = context.get(JavaFileManager.class);
            if (!files.isEmpty()) {

                comp = JavaCompiler.instance(context);
                List<JavaFileObject> otherFiles = List.nil();
                JavacFileManager dfm = (JavacFileManager) fileManager;
                for (JavaFileObject fo : dfm.getJavaFileObjectsFromFiles(files))
                    otherFiles = otherFiles.prepend(fo);
                for (JavaFileObject fo : otherFiles)
                    fileObjects = fileObjects.prepend(fo);
            }
            comp.compile(fileObjects,
                    classnames.toList(),
                    processors);
            if (log.expectDiagKeys != null) {
                if (log.expectDiagKeys.isEmpty()) {
                    log.printRawLines("all expected diagnostics found");
                    return Result.OK;
                } else {
                    log.printRawLines("expected diagnostic keys not found: " + log.expectDiagKeys);
                    return Result.ERROR;
                }
            }
            if (comp.errorCount() != 0)
                return Result.ERROR;
        } catch (IOException ex) {
            ioMessage(ex);
            return Result.SYSERR;
        } catch (OutOfMemoryError ex) {
            resourceMessage(ex);
            return Result.SYSERR;
        } catch (StackOverflowError ex) {
            resourceMessage(ex);
            return Result.SYSERR;
        } catch (FatalError ex) {
            feMessage(ex);
            return Result.SYSERR;
        } catch (AnnotationProcessingError ex) {
            if (apiMode)
                throw new RuntimeException(ex.getCause());
            apMessage(ex);
            return Result.SYSERR;
        } catch (ClientCodeException ex) {


            throw new RuntimeException(ex.getCause());
        } catch (PropagatedException ex) {
            throw ex.getCause();
        } catch (Throwable ex) {


            if (comp == null || comp.errorCount() == 0 ||
                    options == null || options.isSet("dev"))
                bugMessage(ex);
            return Result.ABNORMAL;
        } finally {
            if (comp != null) {
                try {
                    comp.close();
                } catch (ClientCodeException ex) {
                    throw new RuntimeException(ex.getCause());
                }
            }
            filenames = null;
            options = null;
        }
        return Result.OK;
    }

    void bugMessage(Throwable ex) {
        log.printLines(PrefixKind.JAVAC, "msg.bug", JavaCompiler.version());
        ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    void feMessage(Throwable ex) {
        log.printRawLines(ex.getMessage());
        if (ex.getCause() != null && options.isSet("dev")) {
            ex.getCause().printStackTrace(log.getWriter(WriterKind.NOTICE));
        }
    }

    void ioMessage(Throwable ex) {
        log.printLines(PrefixKind.JAVAC, "msg.io");
        ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    void resourceMessage(Throwable ex) {
        log.printLines(PrefixKind.JAVAC, "msg.resource");
        ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    void apMessage(AnnotationProcessingError ex) {
        log.printLines(PrefixKind.JAVAC, "msg.proc.annotation.uncaught.exception");
        ex.getCause().printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    void pluginMessage(Throwable ex) {
        log.printLines(PrefixKind.JAVAC, "msg.plugin.uncaught.exception");
        ex.printStackTrace(log.getWriter(WriterKind.NOTICE));
    }

    void showClass(String className) {
        PrintWriter pw = log.getWriter(WriterKind.NOTICE);
        pw.println("javac: show class: " + className);
        URL url = getClass().getResource('/' + className.replace('.', '/') + ".class");
        if (url == null)
            pw.println("  class not found");
        else {
            pw.println("  " + url);
            try {
                final String algorithm = "MD5";
                byte[] digest;
                MessageDigest md = MessageDigest.getInstance(algorithm);
                DigestInputStream in = new DigestInputStream(url.openStream(), md);
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    do {
                        n = in.read(buf);
                    } while (n > 0);
                    digest = md.digest();
                } finally {
                    in.close();
                }
                StringBuilder sb = new StringBuilder();
                for (byte b : digest)
                    sb.append(String.format("%02x", b));
                pw.println("  " + algorithm + " checksum: " + sb);
            } catch (Exception e) {
                pw.println("  cannot compute digest: " + e);
            }
        }
    }

    public enum Result {
        OK(0),
        ERROR(1),
        CMDERR(2),
        SYSERR(3),
        ABNORMAL(4);

        public final int exitCode;

        Result(int exitCode) {
            this.exitCode = exitCode;
        }

        public boolean isOK() {
            return (exitCode == 0);
        }
    }
}
