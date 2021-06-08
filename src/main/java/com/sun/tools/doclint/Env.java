package com.sun.tools.doclint;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.tree.JCTree;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.LinkedHashSet;
import java.util.Set;

public class Env {
    final Messages messages;
    int implicitHeaderLevel = 0;
    Set<String> customTags;
    DocTrees trees;
    Elements elements;
    Types types;
    TypeMirror java_lang_Error;
    TypeMirror java_lang_RuntimeException;
    TypeMirror java_lang_Throwable;
    TypeMirror java_lang_Void;
    TreePath currPath;
    Element currElement;
    DocCommentTree currDocComment;
    AccessKind currAccess;
    Set<? extends ExecutableElement> currOverriddenMethods;

    Env() {
        messages = new Messages(this);
    }

    void init(JavacTask task) {
        init(DocTrees.instance(task), task.getElements(), task.getTypes());
    }

    void init(DocTrees trees, Elements elements, Types types) {
        this.trees = trees;
        this.elements = elements;
        this.types = types;
        java_lang_Error = elements.getTypeElement("java.lang.Error").asType();
        java_lang_RuntimeException = elements.getTypeElement("java.lang.RuntimeException").asType();
        java_lang_Throwable = elements.getTypeElement("java.lang.Throwable").asType();
        java_lang_Void = elements.getTypeElement("java.lang.Void").asType();
    }

    void setImplicitHeaders(int n) {
        implicitHeaderLevel = n;
    }

    void setCustomTags(String cTags) {
        customTags = new LinkedHashSet<String>();
        for (String s : cTags.split(DocLint.TAGS_SEPARATOR)) {
            if (!s.isEmpty())
                customTags.add(s);
        }
    }

    void setCurrent(TreePath path, DocCommentTree comment) {
        currPath = path;
        currDocComment = comment;
        currElement = trees.getElement(currPath);
        currOverriddenMethods = ((JavacTypes) types).getOverriddenMethods(currElement);
        AccessKind ak = AccessKind.PUBLIC;
        for (TreePath p = path; p != null; p = p.getParentPath()) {
            Element e = trees.getElement(p);
            if (e != null && e.getKind() != ElementKind.PACKAGE) {
                ak = min(ak, AccessKind.of(e.getModifiers()));
            }
        }
        currAccess = ak;
    }

    AccessKind getAccessKind() {
        return currAccess;
    }

    long getPos(TreePath p) {
        return ((JCTree) p.getLeaf()).pos;
    }

    long getStartPos(TreePath p) {
        SourcePositions sp = trees.getSourcePositions();
        return sp.getStartPosition(p.getCompilationUnit(), p.getLeaf());
    }

    private <T extends Comparable<T>> T min(T item1, T item2) {
        return (item1 == null) ? item2
                : (item2 == null) ? item1
                : item1.compareTo(item2) <= 0 ? item1 : item2;
    }

    public enum AccessKind {
        PRIVATE,
        PACKAGE,
        PROTECTED,
        PUBLIC;

        static boolean accepts(String opt) {
            for (AccessKind g : values())
                if (opt.equals(g.name().toLowerCase())) return true;
            return false;
        }

        static AccessKind of(Set<Modifier> mods) {
            if (mods.contains(Modifier.PUBLIC))
                return AccessKind.PUBLIC;
            else if (mods.contains(Modifier.PROTECTED))
                return AccessKind.PROTECTED;
            else if (mods.contains(Modifier.PRIVATE))
                return AccessKind.PRIVATE;
            else
                return AccessKind.PACKAGE;
        }
    }
}
