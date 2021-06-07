package com.sun.tools.javadoc;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;

import java.lang.reflect.Modifier;

import static com.sun.tools.javac.code.TypeTag.CLASS;

public class MethodDocImpl
        extends ExecutableMemberDocImpl implements MethodDoc {

    private String name;
    private String qualifiedName;

    public MethodDocImpl(DocEnv env, MethodSymbol sym) {
        super(env, sym);
    }

    public MethodDocImpl(DocEnv env, MethodSymbol sym, TreePath treePath) {
        super(env, sym, treePath);
    }

    public boolean isMethod() {
        return true;
    }

    public boolean isDefault() {
        return (sym.flags() & Flags.DEFAULT) != 0;
    }

    public boolean isAbstract() {
        return (Modifier.isAbstract(getModifiers()) && !isDefault());
    }

    public com.sun.javadoc.Type returnType() {
        return TypeMaker.getType(env, sym.type.getReturnType(), false);
    }

    public ClassDoc overriddenClass() {
        com.sun.javadoc.Type t = overriddenType();
        return (t != null) ? t.asClassDoc() : null;
    }

    public com.sun.javadoc.Type overriddenType() {

        if ((sym.flags() & Flags.STATIC) != 0) {
            return null;
        }

        ClassSymbol origin = (ClassSymbol) sym.owner;
        for (Type t = env.types.supertype(origin.type);
             t.hasTag(CLASS);
             t = env.types.supertype(t)) {
            ClassSymbol c = (ClassSymbol) t.tsym;
            for (Scope.Entry e = c.members().lookup(sym.name); e.scope != null; e = e.next()) {
                if (sym.overrides(e.sym, origin, env.types, true)) {
                    return TypeMaker.getType(env, t);
                }
            }
        }
        return null;
    }

    public MethodDoc overriddenMethod() {

        if ((sym.flags() & Flags.STATIC) != 0) {
            return null;
        }

        ClassSymbol origin = (ClassSymbol) sym.owner;
        for (Type t = env.types.supertype(origin.type);
             t.hasTag(CLASS);
             t = env.types.supertype(t)) {
            ClassSymbol c = (ClassSymbol) t.tsym;
            for (Scope.Entry e = c.members().lookup(sym.name); e.scope != null; e = e.next()) {
                if (sym.overrides(e.sym, origin, env.types, true)) {
                    return env.getMethodDoc((MethodSymbol) e.sym);
                }
            }
        }
        return null;
    }

    public boolean overrides(MethodDoc meth) {
        MethodSymbol overridee = ((MethodDocImpl) meth).sym;
        ClassSymbol origin = (ClassSymbol) sym.owner;

        return sym.name == overridee.name && sym != overridee && !sym.isStatic() &&
                env.types.asSuper(origin.type, overridee.owner) != null &&
                sym.overrides(overridee, origin, env.types, false);
    }

    public String name() {
        if (name == null) {
            name = sym.name.toString();
        }
        return name;
    }

    public String qualifiedName() {
        if (qualifiedName == null) {
            qualifiedName = sym.enclClass().getQualifiedName() + "." + sym.name;
        }
        return qualifiedName;
    }

    public String toString() {
        return sym.enclClass().getQualifiedName() +
                "." + typeParametersString() + name() + signature();
    }
}
