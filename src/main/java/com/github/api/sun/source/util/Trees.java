package com.github.api.sun.source.util;

import com.github.api.sun.source.tree.*;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler.CompilationTask;
import java.lang.reflect.Method;

@jdk.Exported
public abstract class Trees {
    public static Trees instance(CompilationTask task) {
        String taskClassName = task.getClass().getName();
        if (!taskClassName.equals("com.github.api.sun.tools.javac.api.JavacTaskImpl")
                && !taskClassName.equals("com.github.api.sun.tools.javac.api.BasicJavacTask"))
            throw new IllegalArgumentException();
        return getJavacTrees(CompilationTask.class, task);
    }

    public static Trees instance(ProcessingEnvironment env) {
        if (!env.getClass().getName().equals("com.github.api.sun.tools.javac.processing.JavacProcessingEnvironment"))
            throw new IllegalArgumentException();
        return getJavacTrees(ProcessingEnvironment.class, env);
    }

    static Trees getJavacTrees(Class<?> argType, Object arg) {
        try {
            ClassLoader cl = arg.getClass().getClassLoader();
            Class<?> c = Class.forName("com.github.api.sun.tools.javac.api.JavacTrees", false, cl);
            argType = Class.forName(argType.getName(), false, cl);
            Method m = c.getMethod("instance", argType);
            return (Trees) m.invoke(null, new Object[]{arg});
        } catch (Throwable e) {
            throw new AssertionError(e);
        }
    }

    public abstract SourcePositions getSourcePositions();

    public abstract Tree getTree(Element element);

    public abstract ClassTree getTree(TypeElement element);

    public abstract MethodTree getTree(ExecutableElement method);

    public abstract Tree getTree(Element e, AnnotationMirror a);

    public abstract Tree getTree(Element e, AnnotationMirror a, AnnotationValue v);

    public abstract TreePath getPath(CompilationUnitTree unit, Tree node);

    public abstract TreePath getPath(Element e);

    public abstract TreePath getPath(Element e, AnnotationMirror a);

    public abstract TreePath getPath(Element e, AnnotationMirror a, AnnotationValue v);

    public abstract Element getElement(TreePath path);

    public abstract TypeMirror getTypeMirror(TreePath path);

    public abstract Scope getScope(TreePath path);

    public abstract String getDocComment(TreePath path);

    public abstract boolean isAccessible(Scope scope, TypeElement type);

    public abstract boolean isAccessible(Scope scope, Element member, DeclaredType type);

    public abstract TypeMirror getOriginalType(ErrorType errorType);

    public abstract void printMessage(Diagnostic.Kind kind, CharSequence msg, Tree t, CompilationUnitTree root);

    public abstract TypeMirror getLub(CatchTree tree);
}
