package com.github.api.sun.tools.javadoc;

import com.github.api.sun.javadoc.ConstructorDoc;
import com.github.api.sun.source.util.TreePath;
import com.github.api.sun.tools.javac.code.Symbol.ClassSymbol;
import com.github.api.sun.tools.javac.code.Symbol.MethodSymbol;

public class ConstructorDocImpl extends ExecutableMemberDocImpl implements ConstructorDoc {
    public ConstructorDocImpl(DocEnv env, MethodSymbol sym) {
        super(env, sym);
    }

    public ConstructorDocImpl(DocEnv env, MethodSymbol sym, TreePath treePath) {
        super(env, sym, treePath);
    }

    public boolean isConstructor() {
        return true;
    }

    public String name() {
        ClassSymbol c = sym.enclClass();
        return c.name.toString();
    }

    public String qualifiedName() {
        return sym.enclClass().getQualifiedName().toString();
    }

    public String toString() {
        return typeParametersString() + qualifiedName() + signature();
    }
}
