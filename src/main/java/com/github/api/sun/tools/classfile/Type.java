package com.github.api.sun.tools.classfile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class Type {
    protected Type() {
    }

    protected static void append(StringBuilder sb, String prefix, List<? extends Type> types, String suffix) {
        sb.append(prefix);
        String sep = "";
        for (Type t : types) {
            sb.append(sep);
            sb.append(t);
            sep = ", ";
        }
        sb.append(suffix);
    }

    protected static void appendIfNotEmpty(StringBuilder sb, String prefix, List<? extends Type> types, String suffix) {
        if (types != null && types.size() > 0)
            append(sb, prefix, types, suffix);
    }

    public boolean isObject() {
        return false;
    }

    public abstract <R, D> R accept(Visitor<R, D> visitor, D data);

    public interface Visitor<R, P> {
        R visitSimpleType(SimpleType type, P p);

        R visitArrayType(ArrayType type, P p);

        R visitMethodType(MethodType type, P p);

        R visitClassSigType(ClassSigType type, P p);

        R visitClassType(ClassType type, P p);

        R visitTypeParamType(TypeParamType type, P p);

        R visitWildcardType(WildcardType type, P p);
    }

    public static class SimpleType extends Type {
        private static final Set<String> primitiveTypes = new HashSet<String>(Arrays.asList(
                "boolean", "byte", "char", "double", "float", "int", "long", "short", "void"));
        public final String name;

        public SimpleType(String name) {
            this.name = name;
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitSimpleType(this, data);
        }

        public boolean isPrimitiveType() {
            return primitiveTypes.contains(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class ArrayType extends Type {
        public final Type elemType;

        public ArrayType(Type elemType) {
            this.elemType = elemType;
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitArrayType(this, data);
        }

        @Override
        public String toString() {
            return elemType + "[]";
        }
    }

    public static class MethodType extends Type {
        public final List<? extends TypeParamType> typeParamTypes;
        public final List<? extends Type> paramTypes;
        public final Type returnType;
        public final List<? extends Type> throwsTypes;

        public MethodType(List<? extends Type> paramTypes, Type resultType) {
            this(null, paramTypes, resultType, null);
        }
        public MethodType(List<? extends TypeParamType> typeParamTypes,
                          List<? extends Type> paramTypes,
                          Type returnType,
                          List<? extends Type> throwsTypes) {
            this.typeParamTypes = typeParamTypes;
            this.paramTypes = paramTypes;
            this.returnType = returnType;
            this.throwsTypes = throwsTypes;
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitMethodType(this, data);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            appendIfNotEmpty(sb, "<", typeParamTypes, "> ");
            sb.append(returnType);
            append(sb, " (", paramTypes, ")");
            appendIfNotEmpty(sb, " throws ", throwsTypes, "");
            return sb.toString();
        }
    }

    public static class ClassSigType extends Type {
        public final List<TypeParamType> typeParamTypes;
        public final Type superclassType;
        public final List<Type> superinterfaceTypes;

        public ClassSigType(List<TypeParamType> typeParamTypes, Type superclassType,
                            List<Type> superinterfaceTypes) {
            this.typeParamTypes = typeParamTypes;
            this.superclassType = superclassType;
            this.superinterfaceTypes = superinterfaceTypes;
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitClassSigType(this, data);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            appendIfNotEmpty(sb, "<", typeParamTypes, ">");
            if (superclassType != null) {
                sb.append(" extends ");
                sb.append(superclassType);
            }
            appendIfNotEmpty(sb, " implements ", superinterfaceTypes, "");
            return sb.toString();
        }
    }

    public static class ClassType extends Type {
        public final ClassType outerType;
        public final String name;
        public final List<Type> typeArgs;

        public ClassType(ClassType outerType, String name, List<Type> typeArgs) {
            this.outerType = outerType;
            this.name = name;
            this.typeArgs = typeArgs;
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitClassType(this, data);
        }

        public String getBinaryName() {
            if (outerType == null)
                return name;
            else
                return (outerType.getBinaryName() + "$" + name);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (outerType != null) {
                sb.append(outerType);
                sb.append(".");
            }
            sb.append(name);
            appendIfNotEmpty(sb, "<", typeArgs, ">");
            return sb.toString();
        }

        @Override
        public boolean isObject() {
            return (outerType == null)
                    && name.equals("java/lang/Object")
                    && (typeArgs == null || typeArgs.isEmpty());
        }
    }

    public static class TypeParamType extends Type {
        public final String name;
        public final Type classBound;
        public final List<Type> interfaceBounds;

        public TypeParamType(String name, Type classBound, List<Type> interfaceBounds) {
            this.name = name;
            this.classBound = classBound;
            this.interfaceBounds = interfaceBounds;
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitTypeParamType(this, data);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name);
            String sep = " extends ";
            if (classBound != null) {
                sb.append(sep);
                sb.append(classBound);
                sep = " & ";
            }
            if (interfaceBounds != null) {
                for (Type bound : interfaceBounds) {
                    sb.append(sep);
                    sb.append(bound);
                    sep = " & ";
                }
            }
            return sb.toString();
        }
    }

    public static class WildcardType extends Type {
        public final Kind kind;

        public final Type boundType;

        public WildcardType() {
            this(Kind.UNBOUNDED, null);
        }

        public WildcardType(Kind kind, Type boundType) {
            this.kind = kind;
            this.boundType = boundType;
        }

        public <R, D> R accept(Visitor<R, D> visitor, D data) {
            return visitor.visitWildcardType(this, data);
        }

        @Override
        public String toString() {
            switch (kind) {
                case UNBOUNDED:
                    return "?";
                case EXTENDS:
                    return "? extends " + boundType;
                case SUPER:
                    return "? super " + boundType;
                default:
                    throw new AssertionError();
            }
        }
        public enum Kind {UNBOUNDED, EXTENDS, SUPER}
    }
}
