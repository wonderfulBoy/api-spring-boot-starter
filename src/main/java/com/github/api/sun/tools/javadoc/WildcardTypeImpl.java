package com.github.api.sun.tools.javadoc;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.WildcardType;
import com.github.api.sun.tools.javac.code.Symbol.ClassSymbol;
import com.github.api.sun.tools.javac.code.Type;
import com.github.api.sun.tools.javac.util.List;

public class WildcardTypeImpl extends AbstractTypeImpl implements WildcardType {
    WildcardTypeImpl(DocEnv env, Type.WildcardType type) {
        super(env, type);
    }

    static String wildcardTypeToString(DocEnv env,
                                       Type.WildcardType wildThing, boolean full) {
        if (env.legacyDoclet) {
            return TypeMaker.getTypeName(env.types.erasure(wildThing), full);
        }
        StringBuilder s = new StringBuilder("?");
        List<Type> bounds = getExtendsBounds(wildThing);
        if (bounds.nonEmpty()) {
            s.append(" extends ");
        } else {
            bounds = getSuperBounds(wildThing);
            if (bounds.nonEmpty()) {
                s.append(" super ");
            }
        }
        boolean first = true;
        for (Type b : bounds) {
            if (!first) {
                s.append(" & ");
            }
            s.append(TypeMaker.getTypeString(env, b, full));
            first = false;
        }
        return s.toString();
    }

    private static List<Type> getExtendsBounds(Type.WildcardType wild) {
        return wild.isSuperBound()
                ? List.nil()
                : List.of(wild.type);
    }

    private static List<Type> getSuperBounds(Type.WildcardType wild) {
        return wild.isExtendsBound()
                ? List.nil()
                : List.of(wild.type);
    }

    public com.github.api.sun.javadoc.Type[] extendsBounds() {
        return TypeMaker.getTypes(env, getExtendsBounds((Type.WildcardType) type));
    }

    public com.github.api.sun.javadoc.Type[] superBounds() {
        return TypeMaker.getTypes(env, getSuperBounds((Type.WildcardType) type));
    }

    @Override
    public ClassDoc asClassDoc() {
        return env.getClassDoc((ClassSymbol) env.types.erasure(type).tsym);
    }

    @Override
    public WildcardType asWildcardType() {
        return this;
    }

    @Override
    public String typeName() {
        return "?";
    }

    @Override
    public String qualifiedTypeName() {
        return "?";
    }

    @Override
    public String simpleTypeName() {
        return "?";
    }

    @Override
    public String toString() {
        return wildcardTypeToString(env, (Type.WildcardType) type, true);
    }
}
