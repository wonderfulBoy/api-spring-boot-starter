package com.github.api.sun.tools.javadoc;

import com.github.api.sun.javadoc.AnnotationTypeElementDoc;
import com.github.api.sun.javadoc.AnnotationValue;
import com.github.api.sun.source.util.TreePath;
import com.github.api.sun.tools.javac.code.Symbol.MethodSymbol;

public class AnnotationTypeElementDocImpl
        extends MethodDocImpl implements AnnotationTypeElementDoc {
    public AnnotationTypeElementDocImpl(DocEnv env,
                                        MethodSymbol sym) {
        super(env, sym);
    }

    public AnnotationTypeElementDocImpl(DocEnv env,
                                        MethodSymbol sym,
                                        TreePath treePath) {
        super(env, sym, treePath);
    }

    public boolean isAnnotationTypeElement() {
        return !isMethod();
    }

    public boolean isMethod() {
        return env.legacyDoclet;
    }

    public boolean isAbstract() {
        return false;
    }

    public AnnotationValue defaultValue() {
        return (sym.defaultValue == null) ?
                null : new AnnotationValueImpl(env, sym.defaultValue);
    }
}
