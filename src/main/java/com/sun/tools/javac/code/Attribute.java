package com.sun.tools.javac.code;

import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.util.*;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.type.DeclaredType;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class Attribute implements AnnotationValue {

    public Type type;

    public Attribute(Type type) {
        this.type = type;
    }

    public abstract void accept(Visitor v);

    public Object getValue() {
        throw new UnsupportedOperationException();
    }

    public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
        throw new UnsupportedOperationException();
    }

    public boolean isSynthesized() {
        return false;
    }

    public TypeAnnotationPosition getPosition() {
        return null;
    }

    ;

    public static enum RetentionPolicy {
        SOURCE,
        CLASS,
        RUNTIME
    }

    public static interface Visitor {
        void visitConstant(Constant value);

        void visitClass(Class clazz);

        void visitCompound(Compound compound);

        void visitArray(Array array);

        void visitEnum(Enum e);

        void visitError(Error e);
    }

    public static class Constant extends Attribute {
        public final Object value;

        public Constant(Type type, Object value) {
            super(type);
            this.value = value;
        }

        public void accept(Visitor v) {
            v.visitConstant(this);
        }

        public String toString() {
            return Constants.format(value, type);
        }

        public Object getValue() {
            return Constants.decode(value, type);
        }

        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            if (value instanceof String)
                return v.visitString((String) value, p);
            if (value instanceof Integer) {
                int i = (Integer) value;
                switch (type.getTag()) {
                    case BOOLEAN:
                        return v.visitBoolean(i != 0, p);
                    case CHAR:
                        return v.visitChar((char) i, p);
                    case BYTE:
                        return v.visitByte((byte) i, p);
                    case SHORT:
                        return v.visitShort((short) i, p);
                    case INT:
                        return v.visitInt(i, p);
                }
            }
            switch (type.getTag()) {
                case LONG:
                    return v.visitLong((Long) value, p);
                case FLOAT:
                    return v.visitFloat((Float) value, p);
                case DOUBLE:
                    return v.visitDouble((Double) value, p);
            }
            throw new AssertionError("Bad annotation element value: " + value);
        }
    }

    public static class Class extends Attribute {
        public final Type classType;

        public Class(Types types, Type type) {
            super(makeClassType(types, type));
            this.classType = type;
        }

        static Type makeClassType(Types types, Type type) {
            Type arg = type.isPrimitive()
                    ? types.boxedClass(type).type
                    : types.erasure(type);
            return new Type.ClassType(types.syms.classType.getEnclosingType(),
                    List.of(arg),
                    types.syms.classType.tsym);
        }

        public void accept(Visitor v) {
            v.visitClass(this);
        }

        public String toString() {
            return classType + ".class";
        }

        public Type getValue() {
            return classType;
        }

        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitType(classType, p);
        }
    }

    public static class Compound extends Attribute implements AnnotationMirror {

        public final List<Pair<MethodSymbol, Attribute>> values;

        private boolean synthesized = false;

        public Compound(Type type,
                        List<Pair<MethodSymbol, Attribute>> values) {
            super(type);
            this.values = values;
        }

        @Override
        public boolean isSynthesized() {
            return synthesized;
        }

        public void setSynthesized(boolean synthesized) {
            this.synthesized = synthesized;
        }

        public void accept(Visitor v) {
            v.visitCompound(this);
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("@");
            buf.append(type);
            int len = values.length();
            if (len > 0) {
                buf.append('(');
                boolean first = true;
                for (Pair<MethodSymbol, Attribute> value : values) {
                    if (!first) buf.append(", ");
                    first = false;

                    Name name = value.fst.name;
                    if (len > 1 || name != name.table.names.value) {
                        buf.append(name);
                        buf.append('=');
                    }
                    buf.append(value.snd);
                }
                buf.append(')');
            }
            return buf.toString();
        }

        public Attribute member(Name member) {
            Pair<MethodSymbol, Attribute> res = getElemPair(member);
            return res == null ? null : res.snd;
        }

        private Pair<MethodSymbol, Attribute> getElemPair(Name member) {
            for (Pair<MethodSymbol, Attribute> pair : values)
                if (pair.fst.name == member) return pair;
            return null;
        }

        public Compound getValue() {
            return this;
        }

        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitAnnotation(this, p);
        }

        public DeclaredType getAnnotationType() {
            return (DeclaredType) type;
        }

        @Override
        public TypeAnnotationPosition getPosition() {
            if (values.size() != 0) {
                Name valueName = values.head.fst.name.table.names.value;
                Pair<MethodSymbol, Attribute> res = getElemPair(valueName);
                return res == null ? null : res.snd.getPosition();
            }
            return null;
        }

        public Map<MethodSymbol, Attribute> getElementValues() {
            Map<MethodSymbol, Attribute> valmap =
                    new LinkedHashMap<MethodSymbol, Attribute>();
            for (Pair<MethodSymbol, Attribute> value : values)
                valmap.put(value.fst, value.snd);
            return valmap;
        }
    }

    public static class TypeCompound extends Compound {
        public TypeAnnotationPosition position;

        public TypeCompound(Compound compound,
                            TypeAnnotationPosition position) {
            this(compound.type, compound.values, position);
        }

        public TypeCompound(Type type,
                            List<Pair<MethodSymbol, Attribute>> values,
                            TypeAnnotationPosition position) {
            super(type, values);
            this.position = position;
        }

        @Override
        public TypeAnnotationPosition getPosition() {
            if (hasUnknownPosition()) {
                position = super.getPosition();
            }
            return position;
        }

        public boolean hasUnknownPosition() {
            return position.type == TargetType.UNKNOWN;
        }

        public boolean isContainerTypeCompound() {
            if (isSynthesized() && values.size() == 1)
                return getFirstEmbeddedTC() != null;
            return false;
        }

        private TypeCompound getFirstEmbeddedTC() {
            if (values.size() == 1) {
                Pair<MethodSymbol, Attribute> val = values.get(0);
                if (val.fst.getSimpleName().contentEquals("value")
                        && val.snd instanceof Array) {
                    Array arr = (Array) val.snd;
                    if (arr.values.length != 0
                            && arr.values[0] instanceof TypeCompound)
                        return (TypeCompound) arr.values[0];
                }
            }
            return null;
        }

        public boolean tryFixPosition() {
            if (!isContainerTypeCompound())
                return false;

            TypeCompound from = getFirstEmbeddedTC();
            if (from != null && from.position != null &&
                    from.position.type != TargetType.UNKNOWN) {
                position = from.position;
                return true;
            }
            return false;
        }
    }

    public static class Array extends Attribute {
        public final Attribute[] values;

        public Array(Type type, Attribute[] values) {
            super(type);
            this.values = values;
        }

        public Array(Type type, List<Attribute> values) {
            super(type);
            this.values = values.toArray(new Attribute[values.size()]);
        }

        public void accept(Visitor v) {
            v.visitArray(this);
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append('{');
            boolean first = true;
            for (Attribute value : values) {
                if (!first)
                    buf.append(", ");
                first = false;
                buf.append(value);
            }
            buf.append('}');
            return buf.toString();
        }

        public List<Attribute> getValue() {
            return List.from(values);
        }

        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitArray(getValue(), p);
        }

        @Override
        public TypeAnnotationPosition getPosition() {
            if (values.length != 0)
                return values[0].getPosition();
            else
                return null;
        }
    }

    public static class Enum extends Attribute {
        public VarSymbol value;

        public Enum(Type type, VarSymbol value) {
            super(type);
            this.value = Assert.checkNonNull(value);
        }

        public void accept(Visitor v) {
            v.visitEnum(this);
        }

        public String toString() {
            return value.enclClass() + "." + value;     // qualified name
        }

        public VarSymbol getValue() {
            return value;
        }

        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitEnumConstant(value, p);
        }
    }

    public static class Error extends Attribute {
        public Error(Type type) {
            super(type);
        }

        public void accept(Visitor v) {
            v.visitError(this);
        }

        public String toString() {
            return "<error>";
        }

        public String getValue() {
            return toString();
        }

        public <R, P> R accept(AnnotationValueVisitor<R, P> v, P p) {
            return v.visitString(toString(), p);
        }
    }

    public static class UnresolvedClass extends Error {
        public Type classType;

        public UnresolvedClass(Type type, Type classType) {
            super(type);
            this.classType = classType;
        }
    }
}
