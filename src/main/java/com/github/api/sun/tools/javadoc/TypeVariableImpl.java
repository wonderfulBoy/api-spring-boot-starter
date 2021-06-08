package com.github.api.sun.tools.javadoc;

import com.github.api.sun.javadoc.AnnotationDesc;
import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.ProgramElementDoc;
import com.github.api.sun.javadoc.TypeVariable;
import com.github.api.sun.tools.javac.code.Attribute;
import com.github.api.sun.tools.javac.code.Attribute.TypeCompound;
import com.github.api.sun.tools.javac.code.Kinds;
import com.github.api.sun.tools.javac.code.Symbol;
import com.github.api.sun.tools.javac.code.Symbol.ClassSymbol;
import com.github.api.sun.tools.javac.code.Symbol.MethodSymbol;
import com.github.api.sun.tools.javac.code.Type;
import com.github.api.sun.tools.javac.code.Type.TypeVar;
import com.github.api.sun.tools.javac.util.List;
import com.github.api.sun.tools.javac.util.Name;
import com.github.api.sun.tools.javac.util.Names;

public class TypeVariableImpl extends AbstractTypeImpl implements TypeVariable {
    TypeVariableImpl(DocEnv env, TypeVar type) {
        super(env, type);
    }

    static String typeVarToString(DocEnv env, TypeVar v, boolean full) {
        StringBuilder s = new StringBuilder(v.toString());
        List<Type> bounds = getBounds(v, env);
        if (bounds.nonEmpty()) {
            boolean first = true;
            for (Type b : bounds) {
                s.append(first ? " extends " : " & ");
                s.append(TypeMaker.getTypeString(env, b, full));
                first = false;
            }
        }
        return s.toString();
    }

    private static List<Type> getBounds(TypeVar v, DocEnv env) {
        final Type upperBound = v.getUpperBound();
        Name boundname = upperBound.tsym.getQualifiedName();
        if (boundname == boundname.table.names.java_lang_Object
                && !upperBound.isAnnotated()) {
            return List.nil();
        } else {
            return env.types.getBounds(v);
        }
    }

    public com.github.api.sun.javadoc.Type[] bounds() {
        return TypeMaker.getTypes(env, getBounds((TypeVar) type, env));
    }

    public ProgramElementDoc owner() {
        Symbol osym = type.tsym.owner;
        if ((osym.kind & Kinds.TYP) != 0) {
            return env.getClassDoc((ClassSymbol) osym);
        }
        Names names = osym.name.table.names;
        if (osym.name == names.init) {
            return env.getConstructorDoc((MethodSymbol) osym);
        } else {
            return env.getMethodDoc((MethodSymbol) osym);
        }
    }

    @Override
    public ClassDoc asClassDoc() {
        return env.getClassDoc((ClassSymbol) env.types.erasure(type).tsym);
    }

    @Override
    public TypeVariable asTypeVariable() {
        return this;
    }

    @Override
    public String toString() {
        return typeVarToString(env, (TypeVar) type, true);
    }

    public AnnotationDesc[] annotations() {
        if (!type.isAnnotated()) {
            return new AnnotationDesc[0];
        }
        List<? extends TypeCompound> tas = type.getAnnotationMirrors();
        AnnotationDesc[] res = new AnnotationDesc[tas.length()];
        int i = 0;
        for (Attribute.Compound a : tas) {
            res[i++] = new AnnotationDescImpl(env, a);
        }
        return res;
    }
}
