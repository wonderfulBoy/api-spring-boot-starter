package com.sun.tools.javac.main;

import com.sun.source.util.TaskEvent;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.comp.CompileStates.CompileState;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.parser.Parser;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.Log.WriterKind;

import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.util.*;

import static com.sun.tools.javac.code.TypeTag.CLASS;
import static com.sun.tools.javac.main.Option.*;
import static com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag.RECOVERABLE;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

public class JavaCompiler {

    protected static final Context.Key<JavaCompiler> compilerKey =
            new Context.Key<JavaCompiler>();
    private static final String versionRBName = "com.sun.tools.javac.resources.version";
    private static final CompilePolicy DEFAULT_COMPILE_POLICY = CompilePolicy.BY_TODO;
    private static ResourceBundle versionRB;
    protected final Name completionFailureName;
    public Log log;
    public boolean verbose;
    public boolean sourceOutput;
    public boolean stubOutput;
    public boolean attrParseOnly;
    public boolean printFlat;
    public String encoding;
    public boolean lineDebugInfo;
    public boolean genEndPos;
    public boolean verboseCompilePolicy;
    public CompileState shouldStopPolicyIfError;
    public CompileState shouldStopPolicyIfNoError;
    public Todo todo;
    public List<Closeable> closeables = List.nil();
    public boolean keepComments = false;
    public long elapsed_msec = 0;
    protected TreeMaker make;
    protected ClassReader reader;
    protected ClassWriter writer;
    protected JNIWriter jniWriter;
    protected Enter enter;
    protected Symtab syms;
    protected Source source;
    protected Gen gen;
    protected Names names;
    protected Attr attr;
    protected Check chk;
    protected Flow flow;
    protected TransTypes transTypes;
    protected Lower lower;
    protected Annotate annotate;
    protected Types types;
    protected JavaFileManager fileManager;
    protected ParserFactory parserFactory;
    protected MultiTaskListener taskListener;
    protected JavaCompiler delegateCompiler;
    protected Options options;
    protected Context context;
    protected boolean annotationProcessingOccurred;
    protected boolean implicitSourceFilesRead;
    protected CompileStates compileStates;
    protected boolean devVerbose;
    protected boolean processPcks;
    protected boolean werror;
    protected boolean explicitAnnotationProcessingRequested = false;
    protected CompilePolicy compilePolicy;
    protected ImplicitSourcePolicy implicitSourcePolicy;
    protected Set<JavaFileObject> inputFiles = new HashSet<JavaFileObject>();
    protected boolean needRootClasses = false;
    JCDiagnostic.Factory diagFactory;
    protected final ClassReader.SourceCompleter thisCompleter =
            new ClassReader.SourceCompleter() {
                @Override
                public void complete(ClassSymbol sym) throws CompletionFailure {
                    JavaCompiler.this.complete(sym);
                }
            };
    boolean relax;
    boolean processAnnotations = false;
    Log.DeferredDiagnosticHandler deferredDiagnosticHandler;
    HashMap<Env<AttrContext>, Queue<Pair<Env<AttrContext>, JCClassDecl>>> desugaredEnvs =
            new HashMap<Env<AttrContext>, Queue<Pair<Env<AttrContext>, JCClassDecl>>>();
    private boolean hasBeenUsed = false;
    private long start_msec = 0;
    private List<JCClassDecl> rootClasses;
    private JavacProcessingEnvironment procEnvImpl = null;

    public JavaCompiler(Context context) {
        this.context = context;
        context.put(compilerKey, this);

        if (context.get(JavaFileManager.class) == null)
            JavacFileManager.preRegister(context);
        names = Names.instance(context);
        log = Log.instance(context);
        diagFactory = JCDiagnostic.Factory.instance(context);
        reader = ClassReader.instance(context);
        make = TreeMaker.instance(context);
        writer = ClassWriter.instance(context);
        jniWriter = JNIWriter.instance(context);
        enter = Enter.instance(context);
        todo = Todo.instance(context);
        fileManager = context.get(JavaFileManager.class);
        parserFactory = ParserFactory.instance(context);
        compileStates = CompileStates.instance(context);
        try {

            syms = Symtab.instance(context);
        } catch (CompletionFailure ex) {

            log.error("cant.access", ex.sym, ex.getDetailValue());
            if (ex instanceof ClassReader.BadClassFile)
                throw new Abort();
        }
        source = Source.instance(context);
        Target target = Target.instance(context);
        attr = Attr.instance(context);
        chk = Check.instance(context);
        gen = Gen.instance(context);
        flow = Flow.instance(context);
        transTypes = TransTypes.instance(context);
        lower = Lower.instance(context);
        annotate = Annotate.instance(context);
        types = Types.instance(context);
        taskListener = MultiTaskListener.instance(context);
        reader.sourceCompleter = thisCompleter;
        options = Options.instance(context);
        verbose = options.isSet(VERBOSE);
        sourceOutput = options.isSet(PRINTSOURCE);
        stubOutput = options.isSet("-stubs");
        relax = options.isSet("-relax");
        printFlat = options.isSet("-printflat");
        attrParseOnly = options.isSet("-attrparseonly");
        encoding = options.get(ENCODING);
        lineDebugInfo = options.isUnset(G_CUSTOM) ||
                options.isSet(G_CUSTOM, "lines");
        genEndPos = options.isSet(XJCOV) ||
                context.get(DiagnosticListener.class) != null;
        devVerbose = options.isSet("dev");
        processPcks = options.isSet("process.packages");
        werror = options.isSet(WERROR);
        if (source.compareTo(Source.DEFAULT) < 0) {
            if (options.isUnset(XLINT_CUSTOM, "-" + LintCategory.OPTIONS.option)) {
                if (fileManager instanceof BaseFileManager) {
                    if (((BaseFileManager) fileManager).isDefaultBootClassPath())
                        log.warning(LintCategory.OPTIONS, "source.no.bootclasspath", source.name);
                }
            }
        }
        checkForObsoleteOptions(target);
        verboseCompilePolicy = options.isSet("verboseCompilePolicy");
        if (attrParseOnly)
            compilePolicy = CompilePolicy.ATTR_ONLY;
        else
            compilePolicy = CompilePolicy.decode(options.get("compilePolicy"));
        implicitSourcePolicy = ImplicitSourcePolicy.decode(options.get("-implicit"));
        completionFailureName =
                options.isSet("failcomplete")
                        ? names.fromString(options.get("failcomplete"))
                        : null;
        shouldStopPolicyIfError =
                options.isSet("shouldStopPolicy")
                        ? CompileState.valueOf(options.get("shouldStopPolicy"))
                        : options.isSet("shouldStopPolicyIfError")
                        ? CompileState.valueOf(options.get("shouldStopPolicyIfError"))
                        : CompileState.INIT;
        shouldStopPolicyIfNoError =
                options.isSet("shouldStopPolicyIfNoError")
                        ? CompileState.valueOf(options.get("shouldStopPolicyIfNoError"))
                        : CompileState.GENERATE;
        if (options.isUnset("oldDiags"))
            log.setDiagnosticFormatter(RichDiagnosticFormatter.instance(context));
    }

    public static JavaCompiler instance(Context context) {
        JavaCompiler instance = context.get(compilerKey);
        if (instance == null)
            instance = new JavaCompiler(context);
        return instance;
    }

    public static String version() {
        return version("release");
    }

    public static String fullVersion() {
        return version("full");
    }

    private static String version(String key) {
        if (versionRB == null) {
            try {
                versionRB = ResourceBundle.getBundle(versionRBName);
            } catch (MissingResourceException e) {
                return Log.getLocalizedString("version.not.available");
            }
        }
        try {
            return versionRB.getString(key);
        } catch (MissingResourceException e) {
            return Log.getLocalizedString("version.not.available");
        }
    }

    static boolean explicitAnnotationProcessingRequested(Options options) {
        return
                options.isSet(PROCESSOR) ||
                        options.isSet(PROCESSORPATH) ||
                        options.isSet(PROC, "only") ||
                        options.isSet(XPRINT);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static long elapsed(long then) {
        return now() - then;
    }

    private void checkForObsoleteOptions(Target target) {


        boolean obsoleteOptionFound = false;
        if (options.isUnset(XLINT_CUSTOM, "-" + LintCategory.OPTIONS.option)) {
            if (source.compareTo(Source.JDK1_5) <= 0) {
                log.warning(LintCategory.OPTIONS, "option.obsolete.source", source.name);
                obsoleteOptionFound = true;
            }
            if (target.compareTo(Target.JDK1_5) <= 0) {
                log.warning(LintCategory.OPTIONS, "option.obsolete.target", target.name);
                obsoleteOptionFound = true;
            }
            if (obsoleteOptionFound)
                log.warning(LintCategory.OPTIONS, "option.obsolete.suppression");
        }
    }

    protected boolean shouldStop(CompileState cs) {
        CompileState shouldStopPolicy = (errorCount() > 0 || unrecoverableError())
                ? shouldStopPolicyIfError
                : shouldStopPolicyIfNoError;
        return cs.isAfter(shouldStopPolicy);
    }

    public int errorCount() {
        if (delegateCompiler != null && delegateCompiler != this)
            return delegateCompiler.errorCount();
        else {
            if (werror && log.nerrors == 0 && log.nwarnings > 0) {
                log.error("warnings.and.werror");
            }
        }
        return log.nerrors;
    }

    protected final <T> Queue<T> stopIfError(CompileState cs, Queue<T> queue) {
        return shouldStop(cs) ? new ListBuffer<T>() : queue;
    }

    protected final <T> List<T> stopIfError(CompileState cs, List<T> list) {
        return shouldStop(cs) ? List.nil() : list;
    }

    public int warningCount() {
        if (delegateCompiler != null && delegateCompiler != this)
            return delegateCompiler.warningCount();
        else
            return log.nwarnings;
    }

    public CharSequence readSource(JavaFileObject filename) {
        try {
            inputFiles.add(filename);
            return filename.getCharContent(false);
        } catch (IOException e) {
            log.error("error.reading.file", filename, JavacFileManager.getMessage(e));
            return null;
        }
    }

    protected JCCompilationUnit parse(JavaFileObject filename, CharSequence content) {
        long msec = now();
        JCCompilationUnit tree = make.TopLevel(List.nil(),
                null, List.nil());
        if (content != null) {
            if (verbose) {
                log.printVerbose("parsing.started", filename);
            }
            if (!taskListener.isEmpty()) {
                TaskEvent e = new TaskEvent(TaskEvent.Kind.PARSE, filename);
                taskListener.started(e);
                keepComments = true;
                genEndPos = true;
            }
            Parser parser = parserFactory.newParser(content, keepComments(), genEndPos, lineDebugInfo);
            tree = parser.parseCompilationUnit();
            if (verbose) {
                log.printVerbose("parsing.done", Long.toString(elapsed(msec)));
            }
        }
        tree.sourcefile = filename;
        if (content != null && !taskListener.isEmpty()) {
            TaskEvent e = new TaskEvent(TaskEvent.Kind.PARSE, tree);
            taskListener.finished(e);
        }
        return tree;
    }

    protected boolean keepComments() {
        return keepComments || sourceOutput || stubOutput;
    }

    @Deprecated
    public JCCompilationUnit parse(String filename) {
        JavacFileManager fm = (JavacFileManager) fileManager;
        return parse(fm.getJavaFileObjectsFromStrings(List.of(filename)).iterator().next());
    }

    public JCCompilationUnit parse(JavaFileObject filename) {
        JavaFileObject prev = log.useSource(filename);
        try {
            JCCompilationUnit t = parse(filename, readSource(filename));
            if (t.endPositions != null)
                log.setEndPosTable(filename, t.endPositions);
            return t;
        } finally {
            log.useSource(prev);
        }
    }

    public Symbol resolveBinaryNameOrIdent(String name) {
        try {
            Name flatname = names.fromString(name.replace("/", "."));
            return reader.loadClass(flatname);
        } catch (CompletionFailure ignore) {
            return resolveIdent(name);
        }
    }

    public Symbol resolveIdent(String name) {
        if (name.equals(""))
            return syms.errSymbol;
        JavaFileObject prev = log.useSource(null);
        try {
            JCExpression tree = null;
            for (String s : name.split("\\.", -1)) {
                if (!SourceVersion.isIdentifier(s))
                    return syms.errSymbol;
                tree = (tree == null) ? make.Ident(names.fromString(s))
                        : make.Select(tree, names.fromString(s));
            }
            JCCompilationUnit toplevel =
                    make.TopLevel(List.nil(), null, List.nil());
            toplevel.packge = syms.unnamedPackage;
            return attr.attribIdent(tree, toplevel);
        } finally {
            log.useSource(prev);
        }
    }

    JavaFileObject printSource(Env<AttrContext> env, JCClassDecl cdef) throws IOException {
        JavaFileObject outFile
                = fileManager.getJavaFileForOutput(CLASS_OUTPUT,
                cdef.sym.flatname.toString(),
                JavaFileObject.Kind.SOURCE,
                null);
        if (inputFiles.contains(outFile)) {
            log.error(cdef.pos(), "source.cant.overwrite.input.file", outFile);
            return null;
        } else {
            BufferedWriter out = new BufferedWriter(outFile.openWriter());
            try {
                new Pretty(out, true).printUnit(env.toplevel, cdef);
                if (verbose)
                    log.printVerbose("wrote.file", outFile);
            } finally {
                out.close();
            }
            return outFile;
        }
    }

    JavaFileObject genCode(Env<AttrContext> env, JCClassDecl cdef) throws IOException {
        try {
            if (gen.genClass(env, cdef) && (errorCount() == 0))
                return writer.writeClass(cdef.sym);
        } catch (ClassWriter.PoolOverflow ex) {
            log.error(cdef.pos(), "limit.pool");
        } catch (ClassWriter.StringOverflow ex) {
            log.error(cdef.pos(), "limit.string.overflow",
                    ex.value.substring(0, 20));
        } catch (CompletionFailure ex) {
            chk.completionError(cdef.pos(), ex);
        }
        return null;
    }

    public void complete(ClassSymbol c) throws CompletionFailure {
        if (completionFailureName == c.fullname) {
            throw new CompletionFailure(c, "user-selected completion failure by class name");
        }
        JCCompilationUnit tree;
        JavaFileObject filename = c.classfile;
        JavaFileObject prev = log.useSource(filename);
        try {
            tree = parse(filename, filename.getCharContent(false));
        } catch (IOException e) {
            log.error("error.reading.file", filename, JavacFileManager.getMessage(e));
            tree = make.TopLevel(List.nil(), null, List.nil());
        } finally {
            log.useSource(prev);
        }
        if (!taskListener.isEmpty()) {
            TaskEvent e = new TaskEvent(TaskEvent.Kind.ENTER, tree);
            taskListener.started(e);
        }
        enter.complete(List.of(tree), c);
        if (!taskListener.isEmpty()) {
            TaskEvent e = new TaskEvent(TaskEvent.Kind.ENTER, tree);
            taskListener.finished(e);
        }
        if (enter.getEnv(c) == null) {
            boolean isPkgInfo =
                    tree.sourcefile.isNameCompatible("package-info",
                            JavaFileObject.Kind.SOURCE);
            if (isPkgInfo) {
                if (enter.getEnv(tree.packge) == null) {
                    JCDiagnostic diag =
                            diagFactory.fragment("file.does.not.contain.package",
                                    c.location());
                    throw reader.new BadClassFile(c, filename, diag);
                }
            } else {
                JCDiagnostic diag =
                        diagFactory.fragment("file.doesnt.contain.class",
                                c.getQualifiedName());
                throw reader.new BadClassFile(c, filename, diag);
            }
        }
        implicitSourceFilesRead = true;
    }

    public void compile(List<JavaFileObject> sourceFileObject)
            throws Throwable {
        compile(sourceFileObject, List.nil(), null);
    }

    public void compile(List<JavaFileObject> sourceFileObjects,
                        List<String> classnames,
                        Iterable<? extends Processor> processors) {
        if (processors != null && processors.iterator().hasNext())
            explicitAnnotationProcessingRequested = true;


        if (hasBeenUsed)
            throw new AssertionError("attempt to reuse JavaCompiler");
        hasBeenUsed = true;


        options.put(XLINT_CUSTOM.text + "-" + LintCategory.OPTIONS.option, "true");
        options.remove(XLINT_CUSTOM.text + LintCategory.OPTIONS.option);
        start_msec = now();
        try {
            initProcessAnnotations(processors);

            delegateCompiler =
                    processAnnotations(
                            enterTrees(stopIfError(CompileState.PARSE, parseFiles(sourceFileObjects))),
                            classnames);
            delegateCompiler.compile2();
            delegateCompiler.close();
            elapsed_msec = delegateCompiler.elapsed_msec;
        } catch (Abort ex) {
            if (devVerbose)
                ex.printStackTrace(System.err);
        } finally {
            if (procEnvImpl != null)
                procEnvImpl.close();
        }
    }

    private void compile2() {
        try {
            switch (compilePolicy) {
                case ATTR_ONLY:
                    attribute(todo);
                    break;
                case CHECK_ONLY:
                    flow(attribute(todo));
                    break;
                case SIMPLE:
                    generate(desugar(flow(attribute(todo))));
                    break;
                case BY_FILE: {
                    Queue<Queue<Env<AttrContext>>> q = todo.groupByFile();
                    while (!q.isEmpty() && !shouldStop(CompileState.ATTR)) {
                        generate(desugar(flow(attribute(q.remove()))));
                    }
                }
                break;
                case BY_TODO:
                    while (!todo.isEmpty())
                        generate(desugar(flow(attribute(todo.remove()))));
                    break;
                default:
                    Assert.error("unknown compile policy");
            }
        } catch (Abort ex) {
            if (devVerbose)
                ex.printStackTrace(System.err);
        }
        if (verbose) {
            elapsed_msec = elapsed(start_msec);
            log.printVerbose("total", Long.toString(elapsed_msec));
        }
        reportDeferredDiagnostics();
        if (!log.hasDiagnosticListener()) {
            printCount("error", errorCount());
            printCount("warn", warningCount());
        }
    }

    public List<JCCompilationUnit> parseFiles(Iterable<JavaFileObject> fileObjects) {
        if (shouldStop(CompileState.PARSE))
            return List.nil();

        ListBuffer<JCCompilationUnit> trees = new ListBuffer<>();
        Set<JavaFileObject> filesSoFar = new HashSet<JavaFileObject>();
        for (JavaFileObject fileObject : fileObjects) {
            if (!filesSoFar.contains(fileObject)) {
                filesSoFar.add(fileObject);
                trees.append(parse(fileObject));
            }
        }
        return trees.toList();
    }

    public List<JCCompilationUnit> enterTreesIfNeeded(List<JCCompilationUnit> roots) {
        if (shouldStop(CompileState.ATTR))
            return List.nil();
        return enterTrees(roots);
    }

    public List<JCCompilationUnit> enterTrees(List<JCCompilationUnit> roots) {

        if (!taskListener.isEmpty()) {
            for (JCCompilationUnit unit : roots) {
                TaskEvent e = new TaskEvent(TaskEvent.Kind.ENTER, unit);
                taskListener.started(e);
            }
        }
        enter.main(roots);
        if (!taskListener.isEmpty()) {
            for (JCCompilationUnit unit : roots) {
                TaskEvent e = new TaskEvent(TaskEvent.Kind.ENTER, unit);
                taskListener.finished(e);
            }
        }


        if (needRootClasses || sourceOutput || stubOutput) {
            ListBuffer<JCClassDecl> cdefs = new ListBuffer<>();
            for (JCCompilationUnit unit : roots) {
                for (List<JCTree> defs = unit.defs;
                     defs.nonEmpty();
                     defs = defs.tail) {
                    if (defs.head instanceof JCClassDecl)
                        cdefs.append((JCClassDecl) defs.head);
                }
            }
            rootClasses = cdefs.toList();
        }


        for (JCCompilationUnit unit : roots) {
            inputFiles.add(unit.sourcefile);
        }
        return roots;
    }

    public void initProcessAnnotations(Iterable<? extends Processor> processors) {


        if (options.isSet(PROC, "none")) {
            processAnnotations = false;
        } else if (procEnvImpl == null) {
            procEnvImpl = JavacProcessingEnvironment.instance(context);
            procEnvImpl.setProcessors(processors);
            processAnnotations = procEnvImpl.atLeastOneProcessor();
            if (processAnnotations) {
                options.put("save-parameter-names", "save-parameter-names");
                reader.saveParameterNames = true;
                keepComments = true;
                genEndPos = true;
                if (!taskListener.isEmpty())
                    taskListener.started(new TaskEvent(TaskEvent.Kind.ANNOTATION_PROCESSING));
                deferredDiagnosticHandler = new Log.DeferredDiagnosticHandler(log);
            } else {
                procEnvImpl.close();
            }
        }
    }

    public JavaCompiler processAnnotations(List<JCCompilationUnit> roots) {
        return processAnnotations(roots, List.nil());
    }

    public JavaCompiler processAnnotations(List<JCCompilationUnit> roots,
                                           List<String> classnames) {
        if (shouldStop(CompileState.PROCESS)) {


            if (unrecoverableError()) {
                deferredDiagnosticHandler.reportDeferredDiagnostics();
                log.popDiagnosticHandler(deferredDiagnosticHandler);
                return this;
            }
        }


        if (!processAnnotations) {


            if (options.isSet(PROC, "only")) {
                log.warning("proc.proc-only.requested.no.procs");
                todo.clear();
            }

            if (!classnames.isEmpty()) {
                log.error("proc.no.explicit.annotation.processing.requested",
                        classnames);
            }
            Assert.checkNull(deferredDiagnosticHandler);
            return this;
        }
        Assert.checkNonNull(deferredDiagnosticHandler);
        try {
            List<ClassSymbol> classSymbols = List.nil();
            List<PackageSymbol> pckSymbols = List.nil();
            if (!classnames.isEmpty()) {


                if (!explicitAnnotationProcessingRequested()) {
                    log.error("proc.no.explicit.annotation.processing.requested",
                            classnames);
                    deferredDiagnosticHandler.reportDeferredDiagnostics();
                    log.popDiagnosticHandler(deferredDiagnosticHandler);
                    return this;
                } else {
                    boolean errors = false;
                    for (String nameStr : classnames) {
                        Symbol sym = resolveBinaryNameOrIdent(nameStr);
                        if (sym == null ||
                                (sym.kind == Kinds.PCK && !processPcks) ||
                                sym.kind == Kinds.ABSENT_TYP) {
                            log.error("proc.cant.find.class", nameStr);
                            errors = true;
                            continue;
                        }
                        try {
                            if (sym.kind == Kinds.PCK)
                                sym.complete();
                            if (sym.exists()) {
                                if (sym.kind == Kinds.PCK)
                                    pckSymbols = pckSymbols.prepend((PackageSymbol) sym);
                                else
                                    classSymbols = classSymbols.prepend((ClassSymbol) sym);
                                continue;
                            }
                            Assert.check(sym.kind == Kinds.PCK);
                            log.warning("proc.package.does.not.exist", nameStr);
                            pckSymbols = pckSymbols.prepend((PackageSymbol) sym);
                        } catch (CompletionFailure e) {
                            log.error("proc.cant.find.class", nameStr);
                            errors = true;
                            continue;
                        }
                    }
                    if (errors) {
                        deferredDiagnosticHandler.reportDeferredDiagnostics();
                        log.popDiagnosticHandler(deferredDiagnosticHandler);
                        return this;
                    }
                }
            }
            try {
                JavaCompiler c = procEnvImpl.doProcessing(context, roots, classSymbols, pckSymbols,
                        deferredDiagnosticHandler);
                if (c != this)
                    annotationProcessingOccurred = c.annotationProcessingOccurred = true;

                return c;
            } finally {
                procEnvImpl.close();
            }
        } catch (CompletionFailure ex) {
            log.error("cant.access", ex.sym, ex.getDetailValue());
            deferredDiagnosticHandler.reportDeferredDiagnostics();
            log.popDiagnosticHandler(deferredDiagnosticHandler);
            return this;
        }
    }

    private boolean unrecoverableError() {
        if (deferredDiagnosticHandler != null) {
            for (JCDiagnostic d : deferredDiagnosticHandler.getDiagnostics()) {
                if (d.getKind() == JCDiagnostic.Kind.ERROR && !d.isFlagSet(RECOVERABLE))
                    return true;
            }
        }
        return false;
    }

    boolean explicitAnnotationProcessingRequested() {
        return
                explicitAnnotationProcessingRequested ||
                        explicitAnnotationProcessingRequested(options);
    }

    public Queue<Env<AttrContext>> attribute(Queue<Env<AttrContext>> envs) {
        ListBuffer<Env<AttrContext>> results = new ListBuffer<>();
        while (!envs.isEmpty())
            results.append(attribute(envs.remove()));
        return stopIfError(CompileState.ATTR, results);
    }

    public Env<AttrContext> attribute(Env<AttrContext> env) {
        if (compileStates.isDone(env, CompileState.ATTR))
            return env;
        if (verboseCompilePolicy)
            printNote("[attribute " + env.enclClass.sym + "]");
        if (verbose)
            log.printVerbose("checking.attribution", env.enclClass.sym);
        if (!taskListener.isEmpty()) {
            TaskEvent e = new TaskEvent(TaskEvent.Kind.ANALYZE, env.toplevel, env.enclClass.sym);
            taskListener.started(e);
        }
        JavaFileObject prev = log.useSource(
                env.enclClass.sym.sourcefile != null ?
                        env.enclClass.sym.sourcefile :
                        env.toplevel.sourcefile);
        try {
            attr.attrib(env);
            if (errorCount() > 0 && !shouldStop(CompileState.ATTR)) {


                attr.postAttr(env.tree);
            }
            compileStates.put(env, CompileState.ATTR);
            if (rootClasses != null && rootClasses.contains(env.enclClass)) {


                reportPublicApi(env.enclClass.sym);
            }
        } finally {
            log.useSource(prev);
        }
        return env;
    }

    public void reportPublicApi(ClassSymbol sym) {

    }

    public Queue<Env<AttrContext>> flow(Queue<Env<AttrContext>> envs) {
        ListBuffer<Env<AttrContext>> results = new ListBuffer<>();
        for (Env<AttrContext> env : envs) {
            flow(env, results);
        }
        return stopIfError(CompileState.FLOW, results);
    }

    public Queue<Env<AttrContext>> flow(Env<AttrContext> env) {
        ListBuffer<Env<AttrContext>> results = new ListBuffer<>();
        flow(env, results);
        return stopIfError(CompileState.FLOW, results);
    }

    protected void flow(Env<AttrContext> env, Queue<Env<AttrContext>> results) {
        try {
            if (shouldStop(CompileState.FLOW))
                return;
            if (relax || compileStates.isDone(env, CompileState.FLOW)) {
                results.add(env);
                return;
            }
            if (verboseCompilePolicy)
                printNote("[flow " + env.enclClass.sym + "]");
            JavaFileObject prev = log.useSource(
                    env.enclClass.sym.sourcefile != null ?
                            env.enclClass.sym.sourcefile :
                            env.toplevel.sourcefile);
            try {
                make.at(Position.FIRSTPOS);
                TreeMaker localMake = make.forToplevel(env.toplevel);
                flow.analyzeTree(env, localMake);
                compileStates.put(env, CompileState.FLOW);
                if (shouldStop(CompileState.FLOW))
                    return;
                results.add(env);
            } finally {
                log.useSource(prev);
            }
        } finally {
            if (!taskListener.isEmpty()) {
                TaskEvent e = new TaskEvent(TaskEvent.Kind.ANALYZE, env.toplevel, env.enclClass.sym);
                taskListener.finished(e);
            }
        }
    }

    public Queue<Pair<Env<AttrContext>, JCClassDecl>> desugar(Queue<Env<AttrContext>> envs) {
        ListBuffer<Pair<Env<AttrContext>, JCClassDecl>> results = new ListBuffer<>();
        for (Env<AttrContext> env : envs)
            desugar(env, results);
        return stopIfError(CompileState.FLOW, results);
    }

    protected void desugar(final Env<AttrContext> env, Queue<Pair<Env<AttrContext>, JCClassDecl>> results) {
        if (shouldStop(CompileState.TRANSTYPES))
            return;
        if (implicitSourcePolicy == ImplicitSourcePolicy.NONE
                && !inputFiles.contains(env.toplevel.sourcefile)) {
            return;
        }
        if (compileStates.isDone(env, CompileState.LOWER)) {
            results.addAll(desugaredEnvs.get(env));
            return;
        }

        class ScanNested extends TreeScanner {
            protected boolean hasLambdas;
            Set<Env<AttrContext>> dependencies = new LinkedHashSet<Env<AttrContext>>();

            @Override
            public void visitClassDef(JCClassDecl node) {
                Type st = types.supertype(node.sym.type);
                boolean envForSuperTypeFound = false;
                while (!envForSuperTypeFound && st.hasTag(CLASS)) {
                    ClassSymbol c = st.tsym.outermostClass();
                    Env<AttrContext> stEnv = enter.getEnv(c);
                    if (stEnv != null && env != stEnv) {
                        if (dependencies.add(stEnv)) {
                            boolean prevHasLambdas = hasLambdas;
                            try {
                                scan(stEnv.tree);
                            } finally {

                                hasLambdas = prevHasLambdas;
                            }
                        }
                        envForSuperTypeFound = true;
                    }
                    st = types.supertype(st);
                }
                super.visitClassDef(node);
            }

            @Override
            public void visitLambda(JCLambda tree) {
                hasLambdas = true;
                super.visitLambda(tree);
            }

            @Override
            public void visitReference(JCMemberReference tree) {
                hasLambdas = true;
                super.visitReference(tree);
            }
        }
        ScanNested scanner = new ScanNested();
        scanner.scan(env.tree);
        for (Env<AttrContext> dep : scanner.dependencies) {
            if (!compileStates.isDone(dep, CompileState.FLOW))
                desugaredEnvs.put(dep, desugar(flow(attribute(dep))));
        }


        if (shouldStop(CompileState.TRANSTYPES))
            return;
        if (verboseCompilePolicy)
            printNote("[desugar " + env.enclClass.sym + "]");
        JavaFileObject prev = log.useSource(env.enclClass.sym.sourcefile != null ?
                env.enclClass.sym.sourcefile :
                env.toplevel.sourcefile);
        try {

            JCTree untranslated = env.tree;
            make.at(Position.FIRSTPOS);
            TreeMaker localMake = make.forToplevel(env.toplevel);
            if (env.tree instanceof JCCompilationUnit) {
                if (!(stubOutput || sourceOutput || printFlat)) {
                    if (shouldStop(CompileState.LOWER))
                        return;
                    List<JCTree> pdef = lower.translateTopLevelClass(env, env.tree, localMake);
                    if (pdef.head != null) {
                        Assert.check(pdef.tail.isEmpty());
                        results.add(new Pair<Env<AttrContext>, JCClassDecl>(env, (JCClassDecl) pdef.head));
                    }
                }
                return;
            }
            if (stubOutput) {


                JCClassDecl cdef = (JCClassDecl) env.tree;
                if (untranslated instanceof JCClassDecl &&
                        rootClasses.contains(untranslated) &&
                        ((cdef.mods.flags & (Flags.PROTECTED | Flags.PUBLIC)) != 0 ||
                                cdef.sym.packge().getQualifiedName() == names.java_lang)) {
                    results.add(new Pair<Env<AttrContext>, JCClassDecl>(env, removeMethodBodies(cdef)));
                }
                return;
            }
            if (shouldStop(CompileState.TRANSTYPES))
                return;
            env.tree = transTypes.translateTopLevelClass(env.tree, localMake);
            compileStates.put(env, CompileState.TRANSTYPES);
            if (source.allowLambda() && scanner.hasLambdas) {
                if (shouldStop(CompileState.UNLAMBDA))
                    return;
                env.tree = LambdaToMethod.instance(context).translateTopLevelClass(env, env.tree, localMake);
                compileStates.put(env, CompileState.UNLAMBDA);
            }
            if (shouldStop(CompileState.LOWER))
                return;
            if (sourceOutput) {


                JCClassDecl cdef = (JCClassDecl) env.tree;
                if (untranslated instanceof JCClassDecl &&
                        rootClasses.contains(untranslated)) {
                    results.add(new Pair<Env<AttrContext>, JCClassDecl>(env, cdef));
                }
                return;
            }

            List<JCTree> cdefs = lower.translateTopLevelClass(env, env.tree, localMake);
            compileStates.put(env, CompileState.LOWER);
            if (shouldStop(CompileState.LOWER))
                return;

            for (List<JCTree> l = cdefs; l.nonEmpty(); l = l.tail) {
                JCClassDecl cdef = (JCClassDecl) l.head;
                results.add(new Pair<Env<AttrContext>, JCClassDecl>(env, cdef));
            }
        } finally {
            log.useSource(prev);
        }
    }

    public void generate(Queue<Pair<Env<AttrContext>, JCClassDecl>> queue) {
        generate(queue, null);
    }

    public void generate(Queue<Pair<Env<AttrContext>, JCClassDecl>> queue, Queue<JavaFileObject> results) {
        if (shouldStop(CompileState.GENERATE))
            return;
        boolean usePrintSource = (stubOutput || sourceOutput || printFlat);
        for (Pair<Env<AttrContext>, JCClassDecl> x : queue) {
            Env<AttrContext> env = x.fst;
            JCClassDecl cdef = x.snd;
            if (verboseCompilePolicy) {
                printNote("[generate "
                        + (usePrintSource ? " source" : "code")
                        + " " + cdef.sym + "]");
            }
            if (!taskListener.isEmpty()) {
                TaskEvent e = new TaskEvent(TaskEvent.Kind.GENERATE, env.toplevel, cdef.sym);
                taskListener.started(e);
            }
            JavaFileObject prev = log.useSource(env.enclClass.sym.sourcefile != null ?
                    env.enclClass.sym.sourcefile :
                    env.toplevel.sourcefile);
            try {
                JavaFileObject file;
                if (usePrintSource)
                    file = printSource(env, cdef);
                else {
                    if (fileManager.hasLocation(StandardLocation.NATIVE_HEADER_OUTPUT)
                            && jniWriter.needsHeader(cdef.sym)) {
                        jniWriter.write(cdef.sym);
                    }
                    file = genCode(env, cdef);
                }
                if (results != null && file != null)
                    results.add(file);
            } catch (IOException ex) {
                log.error(cdef.pos(), "class.cant.write",
                        cdef.sym, ex.getMessage());
                return;
            } finally {
                log.useSource(prev);
            }
            if (!taskListener.isEmpty()) {
                TaskEvent e = new TaskEvent(TaskEvent.Kind.GENERATE, env.toplevel, cdef.sym);
                taskListener.finished(e);
            }
        }
    }

    Map<JCCompilationUnit, Queue<Env<AttrContext>>> groupByFile(Queue<Env<AttrContext>> envs) {

        Map<JCCompilationUnit, Queue<Env<AttrContext>>> map = new LinkedHashMap<JCCompilationUnit, Queue<Env<AttrContext>>>();
        for (Env<AttrContext> env : envs) {
            Queue<Env<AttrContext>> sublist = map.get(env.toplevel);
            if (sublist == null) {
                sublist = new ListBuffer<Env<AttrContext>>();
                map.put(env.toplevel, sublist);
            }
            sublist.add(env);
        }
        return map;
    }

    JCClassDecl removeMethodBodies(JCClassDecl cdef) {
        final boolean isInterface = (cdef.mods.flags & Flags.INTERFACE) != 0;
        class MethodBodyRemover extends TreeTranslator {
            @Override
            public void visitMethodDef(JCMethodDecl tree) {
                tree.mods.flags &= ~Flags.SYNCHRONIZED;
                for (JCVariableDecl vd : tree.params)
                    vd.mods.flags &= ~Flags.FINAL;
                tree.body = null;
                super.visitMethodDef(tree);
            }

            @Override
            public void visitVarDef(JCVariableDecl tree) {
                if (tree.init != null && tree.init.type.constValue() == null)
                    tree.init = null;
                super.visitVarDef(tree);
            }

            @Override
            public void visitClassDef(JCClassDecl tree) {
                ListBuffer<JCTree> newdefs = new ListBuffer<>();
                for (List<JCTree> it = tree.defs; it.tail != null; it = it.tail) {
                    JCTree t = it.head;
                    switch (t.getTag()) {
                        case CLASSDEF:
                            if (isInterface ||
                                    (((JCClassDecl) t).mods.flags & (Flags.PROTECTED | Flags.PUBLIC)) != 0 ||
                                    (((JCClassDecl) t).mods.flags & (Flags.PRIVATE)) == 0 && ((JCClassDecl) t).sym.packge().getQualifiedName() == names.java_lang)
                                newdefs.append(t);
                            break;
                        case METHODDEF:
                            if (isInterface ||
                                    (((JCMethodDecl) t).mods.flags & (Flags.PROTECTED | Flags.PUBLIC)) != 0 ||
                                    ((JCMethodDecl) t).sym.name == names.init ||
                                    (((JCMethodDecl) t).mods.flags & (Flags.PRIVATE)) == 0 && ((JCMethodDecl) t).sym.packge().getQualifiedName() == names.java_lang)
                                newdefs.append(t);
                            break;
                        case VARDEF:
                            if (isInterface || (((JCVariableDecl) t).mods.flags & (Flags.PROTECTED | Flags.PUBLIC)) != 0 ||
                                    (((JCVariableDecl) t).mods.flags & (Flags.PRIVATE)) == 0 && ((JCVariableDecl) t).sym.packge().getQualifiedName() == names.java_lang)
                                newdefs.append(t);
                            break;
                        default:
                            break;
                    }
                }
                tree.defs = newdefs.toList();
                super.visitClassDef(tree);
            }
        }
        MethodBodyRemover r = new MethodBodyRemover();
        return r.translate(cdef);
    }

    public void reportDeferredDiagnostics() {
        if (errorCount() == 0
                && annotationProcessingOccurred
                && implicitSourceFilesRead
                && implicitSourcePolicy == ImplicitSourcePolicy.UNSET) {
            if (explicitAnnotationProcessingRequested())
                log.warning("proc.use.implicit");
            else
                log.warning("proc.use.proc.or.implicit");
        }
        chk.reportDeferredDiagnostics();
        if (log.compressedOutput) {
            log.mandatoryNote(null, "compressed.diags");
        }
    }

    public void close() {
        close(true);
    }

    public void close(boolean disposeNames) {
        rootClasses = null;
        reader = null;
        make = null;
        writer = null;
        enter = null;
        if (todo != null)
            todo.clear();
        todo = null;
        parserFactory = null;
        syms = null;
        source = null;
        attr = null;
        chk = null;
        gen = null;
        flow = null;
        transTypes = null;
        lower = null;
        annotate = null;
        types = null;
        log.flush();
        try {
            fileManager.flush();
        } catch (IOException e) {
            throw new Abort(e);
        } finally {
            if (names != null && disposeNames)
                names.dispose();
            names = null;
            for (Closeable c : closeables) {
                try {
                    c.close();
                } catch (IOException e) {


                    JCDiagnostic msg = diagFactory.fragment("fatal.err.cant.close");
                    throw new FatalError(msg, e);
                }
            }
            closeables = List.nil();
        }
    }

    protected void printNote(String lines) {
        log.printRawLines(WriterKind.NOTICE, lines);
    }

    public void printCount(String kind, int count) {
        if (count != 0) {
            String key;
            if (count == 1)
                key = "count." + kind;
            else
                key = "count." + kind + ".plural";
            log.printLines(WriterKind.ERROR, key, String.valueOf(count));
            log.flush(WriterKind.ERROR);
        }
    }

    public void initRound(JavaCompiler prev) {
        genEndPos = prev.genEndPos;
        keepComments = prev.keepComments;
        start_msec = prev.start_msec;
        hasBeenUsed = true;
        closeables = prev.closeables;
        prev.closeables = List.nil();
        shouldStopPolicyIfError = prev.shouldStopPolicyIfError;
        shouldStopPolicyIfNoError = prev.shouldStopPolicyIfNoError;
    }

    protected enum CompilePolicy {

        ATTR_ONLY,

        CHECK_ONLY,

        SIMPLE,

        BY_FILE,

        BY_TODO;

        static CompilePolicy decode(String option) {
            if (option == null)
                return DEFAULT_COMPILE_POLICY;
            else if (option.equals("attr"))
                return ATTR_ONLY;
            else if (option.equals("check"))
                return CHECK_ONLY;
            else if (option.equals("simple"))
                return SIMPLE;
            else if (option.equals("byfile"))
                return BY_FILE;
            else if (option.equals("bytodo"))
                return BY_TODO;
            else
                return DEFAULT_COMPILE_POLICY;
        }
    }

    protected enum ImplicitSourcePolicy {

        NONE,

        CLASS,

        UNSET;

        static ImplicitSourcePolicy decode(String option) {
            if (option == null)
                return UNSET;
            else if (option.equals("none"))
                return NONE;
            else if (option.equals("class"))
                return CLASS;
            else
                return UNSET;
        }
    }
}
