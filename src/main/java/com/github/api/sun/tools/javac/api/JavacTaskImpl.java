package com.github.api.sun.tools.javac.api;

import com.github.api.sun.source.tree.CompilationUnitTree;
import com.github.api.sun.source.tree.Tree;
import com.github.api.sun.source.util.JavacTask;
import com.github.api.sun.tools.javac.code.Symbol.ClassSymbol;
import com.github.api.sun.tools.javac.code.Symbol.TypeSymbol;
import com.github.api.sun.tools.javac.code.Type;
import com.github.api.sun.tools.javac.comp.Attr;
import com.github.api.sun.tools.javac.comp.AttrContext;
import com.github.api.sun.tools.javac.comp.Env;
import com.github.api.sun.tools.javac.file.JavacFileManager;
import com.github.api.sun.tools.javac.main.CommandLine;
import com.github.api.sun.tools.javac.main.JavaCompiler;
import com.github.api.sun.tools.javac.main.Main;
import com.github.api.sun.tools.javac.model.JavacElements;
import com.github.api.sun.tools.javac.model.JavacTypes;
import com.github.api.sun.tools.javac.parser.Parser;
import com.github.api.sun.tools.javac.parser.ParserFactory;
import com.github.api.sun.tools.javac.tree.JCTree;
import com.github.api.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.github.api.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.github.api.sun.tools.javac.tree.JCTree.Tag;
import com.github.api.sun.tools.javac.tree.TreeInfo;
import com.github.api.sun.tools.javac.util.List;
import com.github.api.sun.tools.javac.util.*;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.nio.CharBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class JavacTaskImpl extends BasicJavacTask {
    private final AtomicBoolean used = new AtomicBoolean();
    private Main compilerMain;
    private JavaCompiler compiler;
    private Locale locale;
    private String[] args;
    private String[] classNames;
    private List<JavaFileObject> fileObjects;
    private Map<JavaFileObject, JCCompilationUnit> notYetEntered;
    private ListBuffer<Env<AttrContext>> genList;
    private Iterable<? extends Processor> processors;
    private Main.Result result = null;
    private boolean parsed = false;

    JavacTaskImpl(Main compilerMain,
                  String[] args,
                  String[] classNames,
                  Context context,
                  List<JavaFileObject> fileObjects) {
        super(null, false);
        this.compilerMain = compilerMain;
        this.args = args;
        this.classNames = classNames;
        this.context = context;
        this.fileObjects = fileObjects;
        setLocale(Locale.getDefault());
        compilerMain.getClass();
        args.getClass();
        fileObjects.getClass();
    }

    JavacTaskImpl(Main compilerMain,
                  Iterable<String> args,
                  Context context,
                  Iterable<String> classes,
                  Iterable<? extends JavaFileObject> fileObjects) {
        this(compilerMain, toArray(args), toArray(classes), context, toList(fileObjects));
    }

    static private String[] toArray(Iterable<String> iter) {
        ListBuffer<String> result = new ListBuffer<String>();
        if (iter != null)
            for (String s : iter)
                result.append(s);
        return result.toArray(new String[result.length()]);
    }

    static private List<JavaFileObject> toList(Iterable<? extends JavaFileObject> fileObjects) {
        if (fileObjects == null)
            return List.nil();
        ListBuffer<JavaFileObject> result = new ListBuffer<JavaFileObject>();
        for (JavaFileObject fo : fileObjects)
            result.append(fo);
        return result.toList();
    }

    public Main.Result doCall() {
        if (!used.getAndSet(true)) {
            initContext();
            notYetEntered = new HashMap<JavaFileObject, JCCompilationUnit>();
            compilerMain.setAPIMode(true);
            result = compilerMain.compile(args, classNames, context, fileObjects, processors);
            cleanup();
            return result;
        } else {
            throw new IllegalStateException("multiple calls to method 'call'");
        }
    }

    public Boolean call() {
        return doCall().isOK();
    }

    public void setProcessors(Iterable<? extends Processor> processors) {
        processors.getClass();
        if (used.get())
            throw new IllegalStateException();
        this.processors = processors;
    }

    public void setLocale(Locale locale) {
        if (used.get())
            throw new IllegalStateException();
        this.locale = locale;
    }

    private void prepareCompiler() throws IOException {
        if (used.getAndSet(true)) {
            if (compiler == null)
                throw new IllegalStateException();
        } else {
            initContext();
            compilerMain.log = Log.instance(context);
            compilerMain.setOptions(Options.instance(context));
            compilerMain.filenames = new LinkedHashSet<File>();
            Collection<File> filenames = compilerMain.processArgs(CommandLine.parse(args), classNames);
            if (filenames != null && !filenames.isEmpty())
                throw new IllegalArgumentException("Malformed arguments " + toString(filenames, " "));
            compiler = JavaCompiler.instance(context);
            compiler.keepComments = true;
            compiler.genEndPos = true;
            compiler.initProcessAnnotations(processors);
            notYetEntered = new HashMap<JavaFileObject, JCCompilationUnit>();
            for (JavaFileObject file : fileObjects)
                notYetEntered.put(file, null);
            genList = new ListBuffer<Env<AttrContext>>();
            args = null;
            classNames = null;
        }
    }

    <T> String toString(Iterable<T> items, String sep) {
        String currSep = "";
        StringBuilder sb = new StringBuilder();
        for (T item : items) {
            sb.append(currSep);
            sb.append(item.toString());
            currSep = sep;
        }
        return sb.toString();
    }

    private void initContext() {
        context.put(JavacTask.class, this);
        context.put(Locale.class, locale);
    }

    void cleanup() {
        if (compiler != null)
            compiler.close();
        compiler = null;
        compilerMain = null;
        args = null;
        classNames = null;
        context = null;
        fileObjects = null;
        notYetEntered = null;
    }

    public JavaFileObject asJavaFileObject(File file) {
        JavacFileManager fm = (JavacFileManager) context.get(JavaFileManager.class);
        return fm.getRegularFile(file);
    }

    public Iterable<? extends CompilationUnitTree> parse() throws IOException {
        try {
            prepareCompiler();
            List<JCCompilationUnit> units = compiler.parseFiles(fileObjects);
            for (JCCompilationUnit unit : units) {
                JavaFileObject file = unit.getSourceFile();
                if (notYetEntered.containsKey(file))
                    notYetEntered.put(file, unit);
            }
            return units;
        } finally {
            parsed = true;
            if (compiler != null && compiler.log != null)
                compiler.log.flush();
        }
    }

    public Iterable<? extends TypeElement> enter() throws IOException {
        return enter(null);
    }

    public Iterable<? extends TypeElement> enter(Iterable<? extends CompilationUnitTree> trees)
            throws IOException {
        if (trees == null && notYetEntered != null && notYetEntered.isEmpty())
            return List.nil();
        prepareCompiler();
        ListBuffer<JCCompilationUnit> roots = null;
        if (trees == null) {
            if (notYetEntered.size() > 0) {
                if (!parsed)
                    parse();
                for (JavaFileObject file : fileObjects) {
                    JCCompilationUnit unit = notYetEntered.remove(file);
                    if (unit != null) {
                        if (roots == null)
                            roots = new ListBuffer<JCCompilationUnit>();
                        roots.append(unit);
                    }
                }
                notYetEntered.clear();
            }
        } else {
            for (CompilationUnitTree cu : trees) {
                if (cu instanceof JCCompilationUnit) {
                    if (roots == null)
                        roots = new ListBuffer<JCCompilationUnit>();
                    roots.append((JCCompilationUnit) cu);
                    notYetEntered.remove(cu.getSourceFile());
                } else
                    throw new IllegalArgumentException(cu.toString());
            }
        }
        if (roots == null)
            return List.nil();
        try {
            List<JCCompilationUnit> units = compiler.enterTrees(roots.toList());
            if (notYetEntered.isEmpty())
                compiler = compiler.processAnnotations(units);
            ListBuffer<TypeElement> elements = new ListBuffer<TypeElement>();
            for (JCCompilationUnit unit : units) {
                for (JCTree node : unit.defs) {
                    if (node.hasTag(Tag.CLASSDEF)) {
                        JCClassDecl cdef = (JCClassDecl) node;
                        if (cdef.sym != null)
                            elements.append(cdef.sym);
                    }
                }
            }
            return elements.toList();
        } finally {
            compiler.log.flush();
        }
    }

    @Override
    public Iterable<? extends Element> analyze() throws IOException {
        return analyze(null);
    }

    public Iterable<? extends Element> analyze(Iterable<? extends TypeElement> classes) throws IOException {
        enter(null);
        final ListBuffer<Element> results = new ListBuffer<Element>();
        try {
            if (classes == null) {
                handleFlowResults(compiler.flow(compiler.attribute(compiler.todo)), results);
            } else {
                Filter f = new Filter() {
                    public void process(Env<AttrContext> env) {
                        handleFlowResults(compiler.flow(compiler.attribute(env)), results);
                    }
                };
                f.run(compiler.todo, classes);
            }
        } finally {
            compiler.log.flush();
        }
        return results;
    }

    private void handleFlowResults(Queue<Env<AttrContext>> queue, ListBuffer<Element> elems) {
        for (Env<AttrContext> env : queue) {
            switch (env.tree.getTag()) {
                case CLASSDEF:
                    JCClassDecl cdef = (JCClassDecl) env.tree;
                    if (cdef.sym != null)
                        elems.append(cdef.sym);
                    break;
                case TOPLEVEL:
                    JCCompilationUnit unit = (JCCompilationUnit) env.tree;
                    if (unit.packge != null)
                        elems.append(unit.packge);
                    break;
            }
        }
        genList.addAll(queue);
    }

    @Override
    public Iterable<? extends JavaFileObject> generate() throws IOException {
        return generate(null);
    }

    public Iterable<? extends JavaFileObject> generate(Iterable<? extends TypeElement> classes) throws IOException {
        final ListBuffer<JavaFileObject> results = new ListBuffer<JavaFileObject>();
        try {
            analyze(null);
            if (classes == null) {
                compiler.generate(compiler.desugar(genList), results);
                genList.clear();
            } else {
                Filter f = new Filter() {
                    public void process(Env<AttrContext> env) {
                        compiler.generate(compiler.desugar(ListBuffer.of(env)), results);
                    }
                };
                f.run(genList, classes);
            }
            if (genList.isEmpty()) {
                compiler.reportDeferredDiagnostics();
                cleanup();
            }
        } finally {
            if (compiler != null)
                compiler.log.flush();
        }
        return results;
    }

    public TypeMirror getTypeMirror(Iterable<? extends Tree> path) {
        Tree last = null;
        for (Tree node : path)
            last = node;
        return ((JCTree) last).type;
    }

    public JavacElements getElements() {
        if (context == null)
            throw new IllegalStateException();
        return JavacElements.instance(context);
    }

    public JavacTypes getTypes() {
        if (context == null)
            throw new IllegalStateException();
        return JavacTypes.instance(context);
    }

    public Iterable<? extends Tree> pathFor(CompilationUnitTree unit, Tree node) {
        return TreeInfo.pathFor((JCTree) node, (JCCompilationUnit) unit).reverse();
    }

    public Type parseType(String expr, TypeElement scope) {
        if (expr == null || expr.equals(""))
            throw new IllegalArgumentException();
        compiler = JavaCompiler.instance(context);
        JavaFileObject prev = compiler.log.useSource(null);
        ParserFactory parserFactory = ParserFactory.instance(context);
        Attr attr = Attr.instance(context);
        try {
            CharBuffer buf = CharBuffer.wrap((expr + "\u0000").toCharArray(), 0, expr.length());
            Parser parser = parserFactory.newParser(buf, false, false, false);
            JCTree tree = parser.parseType();
            return attr.attribType(tree, (TypeSymbol) scope);
        } finally {
            compiler.log.useSource(prev);
        }
    }

    abstract class Filter {
        void run(Queue<Env<AttrContext>> list, Iterable<? extends TypeElement> classes) {
            Set<TypeElement> set = new HashSet<TypeElement>();
            for (TypeElement item : classes)
                set.add(item);
            ListBuffer<Env<AttrContext>> defer = new ListBuffer<>();
            while (list.peek() != null) {
                Env<AttrContext> env = list.remove();
                ClassSymbol csym = env.enclClass.sym;
                if (csym != null && set.contains(csym.outermostClass()))
                    process(env);
                else
                    defer = defer.append(env);
            }
            list.addAll(defer);
        }

        abstract void process(Env<AttrContext> env);
    }
}
