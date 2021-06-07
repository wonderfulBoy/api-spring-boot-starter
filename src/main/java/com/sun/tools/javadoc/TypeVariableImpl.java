package com.sun.tools.javadoc;

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.TypeVariable;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Attribute.TypeCompound;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

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

    public com.sun.javadoc.Type[] bounds() {
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
        AnnotationDesc res[] = new AnnotationDesc[tas.length()];
        int i = 0;
        for (Attribute.Compound a : tas) {
            res[i++] = new AnnotationDescImpl(env, a);
        }
        return res;
    }
}
