package com.github.api.sun.source.util;

import com.github.api.sun.source.doctree.DocCommentTree;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.JavaCompiler.CompilationTask;

@jdk.Exported
public abstract class DocTrees extends Trees {
    public static DocTrees instance(CompilationTask task) {
        return (DocTrees) Trees.instance(task);
    }

    public static DocTrees instance(ProcessingEnvironment env) {
        if (!env.getClass().getName().equals("com.github.api.sun.tools.javac.processing.JavacProcessingEnvironment"))
            throw new IllegalArgumentException();
        return (DocTrees) getJavacTrees(ProcessingEnvironment.class, env);
    }

    public abstract DocCommentTree getDocCommentTree(TreePath path);

    public abstract Element getElement(DocTreePath path);

    public abstract DocSourcePositions getSourcePositions();

    public abstract void printMessage(Diagnostic.Kind kind, CharSequence msg,
                                      com.github.api.sun.source.doctree.DocTree t,
                                      DocCommentTree c,
                                      com.github.api.sun.source.tree.CompilationUnitTree root);
}
