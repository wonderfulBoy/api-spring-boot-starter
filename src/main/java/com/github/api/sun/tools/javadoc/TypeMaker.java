package com.github.api.sun.tools.javadoc;

import com.github.api.sun.javadoc.*;
import com.github.api.sun.tools.javac.code.Symbol;
import com.github.api.sun.tools.javac.code.Symbol.ClassSymbol;
import com.github.api.sun.tools.javac.code.Type;
import com.github.api.sun.tools.javac.code.Type.ArrayType;
import com.github.api.sun.tools.javac.code.Type.ClassType;
import com.github.api.sun.tools.javac.code.Type.TypeVar;
import com.github.api.sun.tools.javac.util.List;

import static com.github.api.sun.tools.javac.code.TypeTag.ARRAY;

public class TypeMaker {
    public static com.github.api.sun.javadoc.Type getType(DocEnv env, Type t) {
        return getType(env, t, true);
    }

    public static com.github.api.sun.javadoc.Type getType(DocEnv env, Type t,
                                               boolean errorToClassDoc) {
        return getType(env, t, errorToClassDoc, true);
    }

    @SuppressWarnings("fallthrough")
    public static com.github.api.sun.javadoc.Type getType(DocEnv env, Type t,
                                               boolean errToClassDoc, boolean considerAnnotations) {
        if (env.legacyDoclet) {
            t = env.types.erasure(t);
        }
        if (considerAnnotations && t.isAnnotated()) {
            return new AnnotatedTypeImpl(env, t);
        }
        switch (t.getTag()) {
            case CLASS:
                if (ClassDocImpl.isGeneric((ClassSymbol) t.tsym)) {
                    return env.getParameterizedType((ClassType) t);
                } else {
                    return env.getClassDoc((ClassSymbol) t.tsym);
                }
            case WILDCARD:
                Type.WildcardType a = (Type.WildcardType) t;
                return new WildcardTypeImpl(env, a);
            case TYPEVAR:
                return new TypeVariableImpl(env, (TypeVar) t);
            case ARRAY:
                return new ArrayTypeImpl(env, t);
            case BYTE:
                return PrimitiveType.byteType;
            case CHAR:
                return PrimitiveType.charType;
            case SHORT:
                return PrimitiveType.shortType;
            case INT:
                return PrimitiveType.intType;
            case LONG:
                return PrimitiveType.longType;
            case FLOAT:
                return PrimitiveType.floatType;
            case DOUBLE:
                return PrimitiveType.doubleType;
            case BOOLEAN:
                return PrimitiveType.booleanType;
            case VOID:
                return PrimitiveType.voidType;
            case ERROR:
                if (errToClassDoc)
                    return env.getClassDoc((ClassSymbol) t.tsym);
            default:
                return new PrimitiveType(t.tsym.getQualifiedName().toString());
        }
    }

    public static com.github.api.sun.javadoc.Type[] getTypes(DocEnv env, List<Type> ts) {
        return getTypes(env, ts, new com.github.api.sun.javadoc.Type[ts.length()]);
    }

    public static com.github.api.sun.javadoc.Type[] getTypes(DocEnv env, List<Type> ts,
                                                  com.github.api.sun.javadoc.Type[] res) {
        int i = 0;
        for (Type t : ts) {
            res[i++] = getType(env, t);
        }
        return res;
    }

    public static String getTypeName(Type t, boolean full) {
        switch (t.getTag()) {
            case ARRAY:
                StringBuilder s = new StringBuilder();
                while (t.hasTag(ARRAY)) {
                    s.append("[]");
                    t = ((ArrayType) t).elemtype;
                }
                s.insert(0, getTypeName(t, full));
                return s.toString();
            case CLASS:
                return ClassDocImpl.getClassName((ClassSymbol) t.tsym, full);
            default:
                return t.tsym.getQualifiedName().toString();
        }
    }

    static String getTypeString(DocEnv env, Type t, boolean full) {
        if (t.isAnnotated()) {
            t = t.unannotatedType();
        }
        switch (t.getTag()) {
            case ARRAY:
                StringBuilder s = new StringBuilder();
                while (t.hasTag(ARRAY)) {
                    s.append("[]");
                    t = env.types.elemtype(t);
                }
                s.insert(0, getTypeString(env, t, full));
                return s.toString();
            case CLASS:
                return ParameterizedTypeImpl.
                        parameterizedTypeToString(env, (ClassType) t, full);
            case WILDCARD:
                Type.WildcardType a = (Type.WildcardType) t;
                return WildcardTypeImpl.wildcardTypeToString(env, a, full);
            default:
                return t.tsym.getQualifiedName().toString();
        }
    }

    static String typeParametersString(DocEnv env, Symbol sym, boolean full) {
        if (env.legacyDoclet || sym.type.getTypeArguments().isEmpty()) {
            return "";
        }
        StringBuilder s = new StringBuilder();
        for (Type t : sym.type.getTypeArguments()) {
            s.append(s.length() == 0 ? "<" : ", ");
            s.append(TypeVariableImpl.typeVarToString(env, (TypeVar) t, full));
        }
        s.append(">");
        return s.toString();
    }

    static String typeArgumentsString(DocEnv env, ClassType cl, boolean full) {
        if (env.legacyDoclet || cl.getTypeArguments().isEmpty()) {
            return "";
        }
        StringBuilder s = new StringBuilder();
        for (Type t : cl.getTypeArguments()) {
            s.append(s.length() == 0 ? "<" : ", ");
            s.append(getTypeString(env, t, full));
        }
        s.append(">");
        return s.toString();
    }

    private static class ArrayTypeImpl implements com.github.api.sun.javadoc.Type {
        Type arrayType;
        DocEnv env;
        private com.github.api.sun.javadoc.Type skipArraysCache = null;

        ArrayTypeImpl(DocEnv env, Type arrayType) {
            this.env = env;
            this.arrayType = arrayType;
        }

        public com.github.api.sun.javadoc.Type getElementType() {
            return TypeMaker.getType(env, env.types.elemtype(arrayType));
        }

        private com.github.api.sun.javadoc.Type skipArrays() {
            if (skipArraysCache == null) {
                Type t;
                for (t = arrayType; t.hasTag(ARRAY); t = env.types.elemtype(t)) {
                }
                skipArraysCache = TypeMaker.getType(env, t);
            }
            return skipArraysCache;
        }

        public String dimension() {
            StringBuilder dimension = new StringBuilder();
            for (Type t = arrayType; t.hasTag(ARRAY); t = env.types.elemtype(t)) {
                dimension.append("[]");
            }
            return dimension.toString();
        }

        public String typeName() {
            return skipArrays().typeName();
        }

        public String qualifiedTypeName() {
            return skipArrays().qualifiedTypeName();
        }

        public String simpleTypeName() {
            return skipArrays().simpleTypeName();
        }

        public ClassDoc asClassDoc() {
            return skipArrays().asClassDoc();
        }

        public ParameterizedType asParameterizedType() {
            return skipArrays().asParameterizedType();
        }

        public TypeVariable asTypeVariable() {
            return skipArrays().asTypeVariable();
        }

        public WildcardType asWildcardType() {
            return null;
        }

        public AnnotatedType asAnnotatedType() {
            return null;
        }

        public AnnotationTypeDoc asAnnotationTypeDoc() {
            return skipArrays().asAnnotationTypeDoc();
        }

        public boolean isPrimitive() {
            return skipArrays().isPrimitive();
        }

        @Override
        public String toString() {
            return qualifiedTypeName() + dimension();
        }
    }
}
