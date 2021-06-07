package com.sun.tools.javac.api;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import javax.annotation.processing.Processor;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.Collection;
import java.util.Locale;

public class BasicJavacTask extends JavacTask {
    protected Context context;
    private TaskListener taskListener;

    public BasicJavacTask(Context c, boolean register) {
        context = c;
        if (register)
            context.put(JavacTask.class, this);
    }

    public static JavacTask instance(Context context) {
        JavacTask instance = context.get(JavacTask.class);
        if (instance == null)
            instance = new BasicJavacTask(context, true);
        return instance;
    }

    @Override
    public Iterable<? extends CompilationUnitTree> parse() throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public Iterable<? extends Element> analyze() throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public Iterable<? extends JavaFileObject> generate() throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public void setTaskListener(TaskListener tl) {
        MultiTaskListener mtl = MultiTaskListener.instance(context);
        if (taskListener != null)
            mtl.remove(taskListener);
        if (tl != null)
            mtl.add(tl);
        taskListener = tl;
    }

    @Override
    public void addTaskListener(TaskListener taskListener) {
        MultiTaskListener mtl = MultiTaskListener.instance(context);
        mtl.add(taskListener);
    }

    @Override
    public void removeTaskListener(TaskListener taskListener) {
        MultiTaskListener mtl = MultiTaskListener.instance(context);
        mtl.remove(taskListener);
    }

    public Collection<TaskListener> getTaskListeners() {
        MultiTaskListener mtl = MultiTaskListener.instance(context);
        return mtl.getTaskListeners();
    }

    @Override
    public TypeMirror getTypeMirror(Iterable<? extends Tree> path) {
        Tree last = null;
        for (Tree node : path)
            last = node;
        return ((JCTree) last).type;
    }

    @Override
    public Elements getElements() {
        return JavacElements.instance(context);
    }

    @Override
    public Types getTypes() {
        return JavacTypes.instance(context);
    }

    public void setProcessors(Iterable<? extends Processor> processors) {
        throw new IllegalStateException();
    }

    public void setLocale(Locale locale) {
        throw new IllegalStateException();
    }

    public Boolean call() {
        throw new IllegalStateException();
    }

    public Context getContext() {
        return context;
    }

    public void updateContext(Context newContext) {
        context = newContext;
    }
}
