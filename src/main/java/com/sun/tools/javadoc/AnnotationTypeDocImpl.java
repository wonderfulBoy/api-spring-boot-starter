package com.sun.tools.javadoc;

import com.sun.javadoc.*;

import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.util.List;

public class AnnotationTypeDocImpl
        extends ClassDocImpl implements AnnotationTypeDoc {

    public AnnotationTypeDocImpl(DocEnv env, ClassSymbol sym) {
        this(env, sym, null);
    }

    public AnnotationTypeDocImpl(DocEnv env, ClassSymbol sym, TreePath treePath) {
        super(env, sym, treePath);
    }

    public boolean isAnnotationType() {
        return !isInterface();
    }

    public boolean isInterface() {
        return env.legacyDoclet;
    }

    public MethodDoc[] methods(boolean filter) {
        return env.legacyDoclet ? elements() : new MethodDoc[0];
    }

    public AnnotationTypeElementDoc[] elements() {
        List<AnnotationTypeElementDoc> elements = List.nil();
        for (Scope.Entry e = tsym.members().elems; e != null; e = e.sibling) {
            if (e.sym != null && e.sym.kind == Kinds.MTH) {
                MethodSymbol s = (MethodSymbol)e.sym;
                elements = elements.prepend(env.getAnnotationTypeElementDoc(s));
            }
        }
        return elements.toArray(new AnnotationTypeElementDoc[elements.length()]);
    }
}
