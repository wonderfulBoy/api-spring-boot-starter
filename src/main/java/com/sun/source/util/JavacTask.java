package com.sun.source.util;

import java.io.IOException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
@jdk.Exported
public abstract class JavacTask implements CompilationTask {

    public static JavacTask instance(ProcessingEnvironment processingEnvironment) {
        if (!processingEnvironment.getClass().getName().equals(
                "com.sun.tools.javac.processing.JavacProcessingEnvironment"))
            throw new IllegalArgumentException();
        Context c = ((JavacProcessingEnvironment) processingEnvironment).getContext();
        JavacTask t = c.get(JavacTask.class);
        return (t != null) ? t : new BasicJavacTask(c, true);
    }

    public abstract Iterable<? extends CompilationUnitTree> parse() throws IOException;

    public abstract Iterable<? extends Element> analyze() throws IOException;

    public abstract Iterable<? extends JavaFileObject> generate() throws IOException;

    public abstract void setTaskListener(TaskListener taskListener);

    public abstract void addTaskListener(TaskListener taskListener);

    public abstract void removeTaskListener(TaskListener taskListener);

    public abstract TypeMirror getTypeMirror(Iterable<? extends Tree> path);

    public abstract Elements getElements();

    public abstract Types getTypes();
}
