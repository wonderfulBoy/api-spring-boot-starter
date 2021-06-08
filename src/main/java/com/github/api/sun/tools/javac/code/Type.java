package com.github.api.sun.tools.javac.code;

import com.github.api.sun.tools.javac.code.Symbol.ClassSymbol;
import com.github.api.sun.tools.javac.code.Symbol.TypeSymbol;
import com.github.api.sun.tools.javac.code.Symbol.TypeVariableSymbol;
import com.github.api.sun.tools.javac.util.List;
import com.github.api.sun.tools.javac.util.*;

import javax.lang.model.type.*;
import java.lang.annotation.Annotation;
import java.util.*;

import static com.github.api.sun.tools.javac.code.BoundKind.*;
import static com.github.api.sun.tools.javac.code.Flags.*;
import static com.github.api.sun.tools.javac.code.Kinds.ERR;
import static com.github.api.sun.tools.javac.code.Kinds.TYP;
import static com.github.api.sun.tools.javac.code.TypeTag.*;

public abstract class Type extends AnnoConstruct implements TypeMirror {

    public static final JCNoType noType = new JCNoType();

    public static final JCNoType recoveryType = new JCNoType();

    public static final JCNoType stuckType = new JCNoType();

    public static boolean moreInfo = false;

    public TypeSymbol tsym;

    public Type(TypeSymbol tsym) {
        this.tsym = tsym;
    }

    public static List<Type> getModelTypes(List<Type> ts) {
        ListBuffer<Type> lb = new ListBuffer<>();
        for (Type t : ts)
            lb.append(t.getModelType());
        return lb.toList();
    }

    public static List<Type> map(List<Type> ts, Mapping f) {
        if (ts.nonEmpty()) {
            List<Type> tail1 = map(ts.tail, f);
            Type t = f.apply(ts.head);
            if (tail1 != ts.tail || t != ts.head)
                return tail1.prepend(t);
        }
        return ts;
    }

    public static List<Type> baseTypes(List<Type> ts) {
        if (ts.nonEmpty()) {
            Type t = ts.head.baseType();
            List<Type> baseTypes = baseTypes(ts.tail);
            if (t != ts.head || baseTypes != ts.tail)
                return baseTypes.prepend(t);
        }
        return ts;
    }

    public static String toString(List<Type> ts) {
        if (ts.isEmpty()) {
            return "";
        } else {
            StringBuilder buf = new StringBuilder();
            buf.append(ts.head.toString());
            for (List<Type> l = ts.tail; l.nonEmpty(); l = l.tail)
                buf.append(",").append(l.head.toString());
            return buf.toString();
        }
    }

    public static boolean isErroneous(List<Type> ts) {
        for (List<Type> l = ts; l.nonEmpty(); l = l.tail)
            if (l.head.isErroneous()) return true;
        return false;
    }

    public static boolean contains(List<Type> ts, Type t) {
        for (List<Type> l = ts;
             l.tail != null;
             l = l.tail)
            if (l.head.contains(t)) return true;
        return false;
    }

    public static boolean containsAny(List<Type> ts1, List<Type> ts2) {
        for (Type t : ts1)
            if (t.containsAny(ts2)) return true;
        return false;
    }

    public static List<Type> filter(List<Type> ts, Filter<Type> tf) {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (Type t : ts) {
            if (tf.accepts(t)) {
                buf.append(t);
            }
        }
        return buf.toList();
    }

    public boolean hasTag(TypeTag tag) {
        return tag == getTag();
    }

    public abstract TypeTag getTag();

    public boolean isNumeric() {
        return false;
    }

    public boolean isPrimitive() {
        return false;
    }

    public boolean isPrimitiveOrVoid() {
        return false;
    }

    public boolean isReference() {
        return false;
    }

    public boolean isNullOrReference() {
        return false;
    }

    public boolean isPartial() {
        return false;
    }

    public Object constValue() {
        return null;
    }

    public boolean isFalse() {
        return false;
    }

    public boolean isTrue() {
        return false;
    }

    public Type getModelType() {
        return this;
    }

    public Type getOriginalType() {
        return this;
    }

    public <R, S> R accept(Visitor<R, S> v, S s) {
        return v.visitType(this, s);
    }

    public Type map(Mapping f) {
        return this;
    }

    public Type constType(Object constValue) {
        throw new AssertionError();
    }

    public Type baseType() {
        return this;
    }

    public Type annotatedType(List<Attribute.TypeCompound> annos) {
        return new AnnotatedType(annos, this);
    }

    public boolean isAnnotated() {
        return false;
    }

    public Type unannotatedType() {
        return this;
    }

    @Override
    public List<Attribute.TypeCompound> getAnnotationMirrors() {
        return List.nil();
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return null;
    }

    @Override
    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
        @SuppressWarnings("unchecked")
        A[] tmp = (A[]) java.lang.reflect.Array.newInstance(annotationType, 0);
        return tmp;
    }

    public String toString() {
        String s = (tsym == null || tsym.name == null)
                ? "<none>"
                : tsym.name.toString();
        if (moreInfo && hasTag(TYPEVAR)) {
            s = s + hashCode();
        }
        return s;
    }

    public String stringValue() {
        Object cv = Assert.checkNonNull(constValue());
        return cv.toString();
    }

    @Override
    public boolean equals(Object t) {
        return super.equals(t);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public String argtypes(boolean varargs) {
        List<Type> args = getParameterTypes();
        if (!varargs) return args.toString();
        StringBuilder buf = new StringBuilder();
        while (args.tail.nonEmpty()) {
            buf.append(args.head);
            args = args.tail;
            buf.append(',');
        }
        if (args.head.unannotatedType().hasTag(ARRAY)) {
            buf.append(((ArrayType) args.head.unannotatedType()).elemtype);
            if (args.head.getAnnotationMirrors().nonEmpty()) {
                buf.append(args.head.getAnnotationMirrors());
            }
            buf.append("...");
        } else {
            buf.append(args.head);
        }
        return buf.toString();
    }

    public List<Type> getTypeArguments() {
        return List.nil();
    }

    public Type getEnclosingType() {
        return null;
    }

    public List<Type> getParameterTypes() {
        return List.nil();
    }

    public Type getReturnType() {
        return null;
    }

    public Type getReceiverType() {
        return null;
    }

    public List<Type> getThrownTypes() {
        return List.nil();
    }

    public Type getUpperBound() {
        return null;
    }

    public Type getLowerBound() {
        return null;
    }

    public List<Type> allparams() {
        return List.nil();
    }

    public boolean isErroneous() {
        return false;
    }

    public boolean isParameterized() {
        return false;
    }

    public boolean isRaw() {
        return false;
    }

    public boolean isCompound() {
        return tsym.completer == null


                && (tsym.flags() & COMPOUND) != 0;
    }

    public boolean isInterface() {
        return (tsym.flags() & INTERFACE) != 0;
    }

    public boolean isFinal() {
        return (tsym.flags() & FINAL) != 0;
    }

    public boolean contains(Type t) {
        return t == this;
    }

    public boolean containsAny(List<Type> ts) {
        for (Type t : ts)
            if (this.contains(t)) return true;
        return false;
    }

    public boolean isSuperBound() {
        return false;
    }

    public boolean isExtendsBound() {
        return false;
    }

    public boolean isUnbound() {
        return false;
    }

    public Type withTypeVar(Type t) {
        return this;
    }

    public MethodType asMethodType() {
        throw new AssertionError();
    }

    public void complete() {
    }

    public TypeSymbol asElement() {
        return tsym;
    }

    @Override
    public TypeKind getKind() {
        return TypeKind.OTHER;
    }

    @Override
    public <R, P> R accept(TypeVisitor<R, P> v, P p) {
        throw new AssertionError();
    }

    public interface Visitor<R, S> {
        R visitClassType(ClassType t, S s);

        R visitWildcardType(WildcardType t, S s);

        R visitArrayType(ArrayType t, S s);

        R visitMethodType(MethodType t, S s);

        R visitPackageType(PackageType t, S s);

        R visitTypeVar(TypeVar t, S s);

        R visitCapturedType(CapturedType t, S s);

        R visitForAll(ForAll t, S s);

        R visitUndetVar(UndetVar t, S s);

        R visitErrorType(ErrorType t, S s);

        R visitAnnotatedType(AnnotatedType t, S s);

        R visitType(Type t, S s);
    }

    public static abstract class Mapping {
        private String name;

        public Mapping(String name) {
            this.name = name;
        }

        public abstract Type apply(Type t);

        public String toString() {
            return name;
        }
    }

    public static class JCPrimitiveType extends Type
            implements PrimitiveType {
        TypeTag tag;

        public JCPrimitiveType(TypeTag tag, TypeSymbol tsym) {
            super(tsym);
            this.tag = tag;
            Assert.check(tag.isPrimitive);
        }

        @Override
        public boolean isNumeric() {
            return tag != BOOLEAN;
        }

        @Override
        public boolean isPrimitive() {
            return true;
        }

        @Override
        public TypeTag getTag() {
            return tag;
        }

        @Override
        public boolean isPrimitiveOrVoid() {
            return true;
        }

        @Override
        public Type constType(Object constValue) {
            final Object value = constValue;
            return new JCPrimitiveType(tag, tsym) {
                @Override
                public Object constValue() {
                    return value;
                }

                @Override
                public Type baseType() {
                    return tsym.type;
                }
            };
        }

        @Override
        public String stringValue() {
            Object cv = Assert.checkNonNull(constValue());
            if (tag == BOOLEAN) {
                return ((Integer) cv).intValue() == 0 ? "false" : "true";
            } else if (tag == CHAR) {
                return String.valueOf((char) ((Integer) cv).intValue());
            } else {
                return cv.toString();
            }
        }

        @Override
        public boolean isFalse() {
            return
                    tag == BOOLEAN &&
                            constValue() != null &&
                            ((Integer) constValue()).intValue() == 0;
        }

        @Override
        public boolean isTrue() {
            return
                    tag == BOOLEAN &&
                            constValue() != null &&
                            ((Integer) constValue()).intValue() != 0;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitPrimitive(this, p);
        }

        @Override
        public TypeKind getKind() {
            switch (tag) {
                case BYTE:
                    return TypeKind.BYTE;
                case CHAR:
                    return TypeKind.CHAR;
                case SHORT:
                    return TypeKind.SHORT;
                case INT:
                    return TypeKind.INT;
                case LONG:
                    return TypeKind.LONG;
                case FLOAT:
                    return TypeKind.FLOAT;
                case DOUBLE:
                    return TypeKind.DOUBLE;
                case BOOLEAN:
                    return TypeKind.BOOLEAN;
            }
            throw new AssertionError();
        }
    }

    public static class WildcardType extends Type
            implements javax.lang.model.type.WildcardType {
        public Type type;
        public BoundKind kind;
        public TypeVar bound;
        boolean isPrintingBound = false;

        public WildcardType(Type type, BoundKind kind, TypeSymbol tsym) {
            super(tsym);
            this.type = Assert.checkNonNull(type);
            this.kind = kind;
        }

        public WildcardType(WildcardType t, TypeVar bound) {
            this(t.type, t.kind, t.tsym, bound);
        }

        public WildcardType(Type type, BoundKind kind, TypeSymbol tsym, TypeVar bound) {
            this(type, kind, tsym);
            this.bound = bound;
        }

        @Override
        public <R, S> R accept(Visitor<R, S> v, S s) {
            return v.visitWildcardType(this, s);
        }

        @Override
        public TypeTag getTag() {
            return WILDCARD;
        }

        @Override
        public boolean contains(Type t) {
            return kind != UNBOUND && type.contains(t);
        }

        public boolean isSuperBound() {
            return kind == SUPER ||
                    kind == UNBOUND;
        }

        public boolean isExtendsBound() {
            return kind == EXTENDS ||
                    kind == UNBOUND;
        }

        public boolean isUnbound() {
            return kind == UNBOUND;
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean isNullOrReference() {
            return true;
        }

        @Override
        public Type withTypeVar(Type t) {

            if (bound == t)
                return this;
            bound = (TypeVar) t;
            return this;
        }

        public String toString() {
            StringBuilder s = new StringBuilder();
            s.append(kind.toString());
            if (kind != UNBOUND)
                s.append(type);
            if (moreInfo && bound != null && !isPrintingBound)
                try {
                    isPrintingBound = true;
                    s.append("{:").append(bound.bound).append(":}");
                } finally {
                    isPrintingBound = false;
                }
            return s.toString();
        }

        public Type map(Mapping f) {

            Type t = type;
            if (t != null)
                t = f.apply(t);
            if (t == type)
                return this;
            else
                return new WildcardType(t, kind, tsym, bound);
        }

        public Type getExtendsBound() {
            if (kind == EXTENDS)
                return type;
            else
                return null;
        }

        public Type getSuperBound() {
            if (kind == SUPER)
                return type;
            else
                return null;
        }

        public TypeKind getKind() {
            return TypeKind.WILDCARD;
        }

        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitWildcard(this, p);
        }
    }

    public static class ClassType extends Type implements DeclaredType {

        public List<Type> typarams_field;
        public List<Type> allparams_field;
        public Type supertype_field;
        public List<Type> interfaces_field;
        public List<Type> all_interfaces_field;
        int rank_field = -1;
        private Type outer_field;

        public ClassType(Type outer, List<Type> typarams, TypeSymbol tsym) {
            super(tsym);
            this.outer_field = outer;
            this.typarams_field = typarams;
            this.allparams_field = null;
            this.supertype_field = null;
            this.interfaces_field = null;

        }

        @Override
        public TypeTag getTag() {
            return CLASS;
        }

        @Override
        public <R, S> R accept(Visitor<R, S> v, S s) {
            return v.visitClassType(this, s);
        }

        public Type constType(Object constValue) {
            final Object value = constValue;
            return new ClassType(getEnclosingType(), typarams_field, tsym) {
                @Override
                public Object constValue() {
                    return value;
                }

                @Override
                public Type baseType() {
                    return tsym.type;
                }
            };
        }

        public String toString() {
            StringBuilder buf = new StringBuilder();
            if (getEnclosingType().hasTag(CLASS) && tsym.owner.kind == TYP) {
                buf.append(getEnclosingType().toString());
                buf.append(".");
                buf.append(className(tsym, false));
            } else {
                buf.append(className(tsym, true));
            }
            if (getTypeArguments().nonEmpty()) {
                buf.append('<');
                buf.append(getTypeArguments().toString());
                buf.append(">");
            }
            return buf.toString();
        }

        private String className(Symbol sym, boolean longform) {
            if (sym.name.isEmpty() && (sym.flags() & COMPOUND) != 0) {
                StringBuilder s = new StringBuilder(supertype_field.toString());
                for (List<Type> is = interfaces_field; is.nonEmpty(); is = is.tail) {
                    s.append("&");
                    s.append(is.head.toString());
                }
                return s.toString();
            } else if (sym.name.isEmpty()) {
                String s;
                ClassType norm = (ClassType) tsym.type.unannotatedType();
                if (norm == null) {
                    s = Log.getLocalizedString("anonymous.class", (Object) null);
                } else if (norm.interfaces_field != null && norm.interfaces_field.nonEmpty()) {
                    s = Log.getLocalizedString("anonymous.class",
                            norm.interfaces_field.head);
                } else {
                    s = Log.getLocalizedString("anonymous.class",
                            norm.supertype_field);
                }
                if (moreInfo)
                    s += String.valueOf(sym.hashCode());
                return s;
            } else if (longform) {
                return sym.getQualifiedName().toString();
            } else {
                return sym.name.toString();
            }
        }

        public List<Type> getTypeArguments() {
            if (typarams_field == null) {
                complete();
                if (typarams_field == null)
                    typarams_field = List.nil();
            }
            return typarams_field;
        }

        public boolean hasErasedSupertypes() {
            return isRaw();
        }

        public Type getEnclosingType() {
            return outer_field;
        }

        public void setEnclosingType(Type outer) {
            outer_field = outer;
        }

        public List<Type> allparams() {
            if (allparams_field == null) {
                allparams_field = getTypeArguments().prependList(getEnclosingType().allparams());
            }
            return allparams_field;
        }

        public boolean isErroneous() {
            return
                    getEnclosingType().isErroneous() ||
                            isErroneous(getTypeArguments()) ||
                            this != tsym.type.unannotatedType() && tsym.type.isErroneous();
        }

        public boolean isParameterized() {
            return allparams().tail != null;

        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean isNullOrReference() {
            return true;
        }

        public boolean isRaw() {
            return
                    this != tsym.type &&
                            tsym.type.allparams().nonEmpty() &&
                            allparams().isEmpty();
        }

        public Type map(Mapping f) {
            Type outer = getEnclosingType();
            Type outer1 = f.apply(outer);
            List<Type> typarams = getTypeArguments();
            List<Type> typarams1 = map(typarams, f);
            if (outer1 == outer && typarams1 == typarams) return this;
            else return new ClassType(outer1, typarams1, tsym);
        }

        public boolean contains(Type elem) {
            return
                    elem == this
                            || (isParameterized()
                            && (getEnclosingType().contains(elem) || contains(getTypeArguments(), elem)))
                            || (isCompound()
                            && (supertype_field.contains(elem) || contains(interfaces_field, elem)));
        }

        public void complete() {
            if (tsym.completer != null) tsym.complete();
        }

        public TypeKind getKind() {
            return TypeKind.DECLARED;
        }

        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitDeclared(this, p);
        }
    }

    public static class ErasedClassType extends ClassType {
        public ErasedClassType(Type outer, TypeSymbol tsym) {
            super(outer, List.nil(), tsym);
        }

        @Override
        public boolean hasErasedSupertypes() {
            return true;
        }
    }

    public static class UnionClassType extends ClassType implements UnionType {
        final List<? extends Type> alternatives_field;

        public UnionClassType(ClassType ct, List<? extends Type> alternatives) {
            super(ct.outer_field, ct.typarams_field, ct.tsym);
            allparams_field = ct.allparams_field;
            supertype_field = ct.supertype_field;
            interfaces_field = ct.interfaces_field;
            all_interfaces_field = ct.interfaces_field;
            alternatives_field = alternatives;
        }

        public Type getLub() {
            return tsym.type;
        }

        public java.util.List<? extends TypeMirror> getAlternatives() {
            return Collections.unmodifiableList(alternatives_field);
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.UNION;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitUnion(this, p);
        }
    }

    public static class IntersectionClassType extends ClassType implements IntersectionType {
        public boolean allInterfaces;

        public IntersectionClassType(List<Type> bounds, ClassSymbol csym, boolean allInterfaces) {
            super(Type.noType, List.nil(), csym);
            this.allInterfaces = allInterfaces;
            Assert.check((csym.flags() & COMPOUND) != 0);
            supertype_field = bounds.head;
            interfaces_field = bounds.tail;
            Assert.check(supertype_field.tsym.completer != null ||
                    !supertype_field.isInterface(), supertype_field);
        }

        public java.util.List<? extends TypeMirror> getBounds() {
            return Collections.unmodifiableList(getExplicitComponents());
        }

        public List<Type> getComponents() {
            return interfaces_field.prepend(supertype_field);
        }

        public List<Type> getExplicitComponents() {
            return allInterfaces ?
                    interfaces_field :
                    getComponents();
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.INTERSECTION;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitIntersection(this, p);
        }
    }

    public static class ArrayType extends Type
            implements javax.lang.model.type.ArrayType {
        public Type elemtype;

        public ArrayType(Type elemtype, TypeSymbol arrayClass) {
            super(arrayClass);
            this.elemtype = elemtype;
        }

        @Override
        public TypeTag getTag() {
            return ARRAY;
        }

        public <R, S> R accept(Visitor<R, S> v, S s) {
            return v.visitArrayType(this, s);
        }

        public String toString() {
            return elemtype + "[]";
        }

        public boolean equals(Object obj) {
            return
                    this == obj ||
                            (obj instanceof ArrayType &&
                                    this.elemtype.equals(((ArrayType) obj).elemtype));
        }

        public int hashCode() {
            return (ARRAY.ordinal() << 5) + elemtype.hashCode();
        }

        public boolean isVarargs() {
            return false;
        }

        public List<Type> allparams() {
            return elemtype.allparams();
        }

        public boolean isErroneous() {
            return elemtype.isErroneous();
        }

        public boolean isParameterized() {
            return elemtype.isParameterized();
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean isNullOrReference() {
            return true;
        }

        public boolean isRaw() {
            return elemtype.isRaw();
        }

        public ArrayType makeVarargs() {
            return new ArrayType(elemtype, tsym) {
                @Override
                public boolean isVarargs() {
                    return true;
                }
            };
        }

        public Type map(Mapping f) {
            Type elemtype1 = f.apply(elemtype);
            if (elemtype1 == elemtype) return this;
            else return new ArrayType(elemtype1, tsym);
        }

        public boolean contains(Type elem) {
            return elem == this || elemtype.contains(elem);
        }

        public void complete() {
            elemtype.complete();
        }

        public Type getComponentType() {
            return elemtype;
        }

        public TypeKind getKind() {
            return TypeKind.ARRAY;
        }

        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitArray(this, p);
        }
    }

    public static class MethodType extends Type implements ExecutableType {
        public List<Type> argtypes;
        public Type restype;
        public List<Type> thrown;

        public Type recvtype;

        public MethodType(List<Type> argtypes,
                          Type restype,
                          List<Type> thrown,
                          TypeSymbol methodClass) {
            super(methodClass);
            this.argtypes = argtypes;
            this.restype = restype;
            this.thrown = thrown;
        }

        @Override
        public TypeTag getTag() {
            return METHOD;
        }

        public <R, S> R accept(Visitor<R, S> v, S s) {
            return v.visitMethodType(this, s);
        }

        public String toString() {
            return "(" + argtypes + ")" + restype;
        }

        public List<Type> getParameterTypes() {
            return argtypes;
        }

        public Type getReturnType() {
            return restype;
        }

        public Type getReceiverType() {
            return recvtype;
        }

        public List<Type> getThrownTypes() {
            return thrown;
        }

        public boolean isErroneous() {
            return
                    isErroneous(argtypes) ||
                            restype != null && restype.isErroneous();
        }

        public Type map(Mapping f) {
            List<Type> argtypes1 = map(argtypes, f);
            Type restype1 = f.apply(restype);
            List<Type> thrown1 = map(thrown, f);
            if (argtypes1 == argtypes &&
                    restype1 == restype &&
                    thrown1 == thrown) return this;
            else return new MethodType(argtypes1, restype1, thrown1, tsym);
        }

        public boolean contains(Type elem) {
            return elem == this || contains(argtypes, elem) || restype.contains(elem) || contains(thrown, elem);
        }

        public MethodType asMethodType() {
            return this;
        }

        public void complete() {
            for (List<Type> l = argtypes; l.nonEmpty(); l = l.tail)
                l.head.complete();
            restype.complete();
            recvtype.complete();
            for (List<Type> l = thrown; l.nonEmpty(); l = l.tail)
                l.head.complete();
        }

        public List<TypeVar> getTypeVariables() {
            return List.nil();
        }

        public TypeSymbol asElement() {
            return null;
        }

        public TypeKind getKind() {
            return TypeKind.EXECUTABLE;
        }

        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitExecutable(this, p);
        }
    }

    public static class PackageType extends Type implements NoType {
        PackageType(TypeSymbol tsym) {
            super(tsym);
        }

        @Override
        public TypeTag getTag() {
            return PACKAGE;
        }

        @Override
        public <R, S> R accept(Visitor<R, S> v, S s) {
            return v.visitPackageType(this, s);
        }

        public String toString() {
            return tsym.getQualifiedName().toString();
        }

        public TypeKind getKind() {
            return TypeKind.PACKAGE;
        }

        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitNoType(this, p);
        }
    }

    public static class TypeVar extends Type implements TypeVariable {

        public Type bound = null;

        public Type lower;
        int rank_field = -1;

        public TypeVar(Name name, Symbol owner, Type lower) {
            super(null);
            tsym = new TypeVariableSymbol(0, name, this, owner);
            this.lower = lower;
        }

        public TypeVar(TypeSymbol tsym, Type bound, Type lower) {
            super(tsym);
            this.bound = bound;
            this.lower = lower;
        }

        @Override
        public TypeTag getTag() {
            return TYPEVAR;
        }

        @Override
        public <R, S> R accept(Visitor<R, S> v, S s) {
            return v.visitTypeVar(this, s);
        }

        @Override
        public Type getUpperBound() {
            if ((bound == null || bound.hasTag(NONE)) && this != tsym.type) {
                bound = tsym.type.getUpperBound();
            }
            return bound;
        }

        @Override
        public Type getLowerBound() {
            return lower;
        }

        public TypeKind getKind() {
            return TypeKind.TYPEVAR;
        }

        public boolean isCaptured() {
            return false;
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean isNullOrReference() {
            return true;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitTypeVariable(this, p);
        }
    }

    public static class CapturedType extends TypeVar {
        public WildcardType wildcard;

        public CapturedType(Name name,
                            Symbol owner,
                            Type upper,
                            Type lower,
                            WildcardType wildcard) {
            super(name, owner, lower);
            this.lower = Assert.checkNonNull(lower);
            this.bound = upper;
            this.wildcard = wildcard;
        }

        @Override
        public <R, S> R accept(Visitor<R, S> v, S s) {
            return v.visitCapturedType(this, s);
        }

        @Override
        public boolean isCaptured() {
            return true;
        }

        @Override
        public String toString() {
            return "capture#"
                    + (hashCode() & 0xFFFFFFFFL) % Printer.PRIME
                    + " of "
                    + wildcard;
        }
    }

    public static abstract class DelegatedType extends Type {
        public Type qtype;
        public TypeTag tag;

        public DelegatedType(TypeTag tag, Type qtype) {
            super(qtype.tsym);
            this.tag = tag;
            this.qtype = qtype;
        }

        public TypeTag getTag() {
            return tag;
        }

        public String toString() {
            return qtype.toString();
        }

        public List<Type> getTypeArguments() {
            return qtype.getTypeArguments();
        }

        public Type getEnclosingType() {
            return qtype.getEnclosingType();
        }

        public List<Type> getParameterTypes() {
            return qtype.getParameterTypes();
        }

        public Type getReturnType() {
            return qtype.getReturnType();
        }

        public Type getReceiverType() {
            return qtype.getReceiverType();
        }

        public List<Type> getThrownTypes() {
            return qtype.getThrownTypes();
        }

        public List<Type> allparams() {
            return qtype.allparams();
        }

        public Type getUpperBound() {
            return qtype.getUpperBound();
        }

        public boolean isErroneous() {
            return qtype.isErroneous();
        }
    }

    public static class ForAll extends DelegatedType implements ExecutableType {
        public List<Type> tvars;

        public ForAll(List<Type> tvars, Type qtype) {
            super(FORALL, qtype);
            this.tvars = tvars;
        }

        @Override
        public <R, S> R accept(Visitor<R, S> v, S s) {
            return v.visitForAll(this, s);
        }

        public String toString() {
            return "<" + tvars + ">" + qtype;
        }

        public List<Type> getTypeArguments() {
            return tvars;
        }

        public boolean isErroneous() {
            return qtype.isErroneous();
        }

        public Type map(Mapping f) {
            return f.apply(qtype);
        }

        public boolean contains(Type elem) {
            return qtype.contains(elem);
        }

        public MethodType asMethodType() {
            return (MethodType) qtype;
        }

        public void complete() {
            for (List<Type> l = tvars; l.nonEmpty(); l = l.tail) {
                ((TypeVar) l.head).bound.complete();
            }
            qtype.complete();
        }

        public List<TypeVar> getTypeVariables() {
            return List.convert(TypeVar.class, getTypeArguments());
        }

        public TypeKind getKind() {
            return TypeKind.EXECUTABLE;
        }

        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitExecutable(this, p);
        }
    }

    public static class UndetVar extends DelegatedType {

        public Type inst = null;
        public int declaredCount;
        public UndetVarListener listener = null;
        protected Map<InferenceBound, List<Type>> bounds;
        Mapping toTypeVarMap = new Mapping("toTypeVarMap") {
            @Override
            public Type apply(Type t) {
                if (t.hasTag(UNDETVAR)) {
                    UndetVar uv = (UndetVar) t;
                    return uv.inst != null ? uv.inst : uv.qtype;
                } else {
                    return t.map(this);
                }
            }
        };

        public UndetVar(TypeVar origin, Types types) {
            super(UNDETVAR, origin);
            bounds = new EnumMap<InferenceBound, List<Type>>(InferenceBound.class);
            List<Type> declaredBounds = types.getBounds(origin);
            declaredCount = declaredBounds.length();
            bounds.put(InferenceBound.UPPER, declaredBounds);
            bounds.put(InferenceBound.LOWER, List.nil());
            bounds.put(InferenceBound.EQ, List.nil());
        }

        @Override
        public <R, S> R accept(Visitor<R, S> v, S s) {
            return v.visitUndetVar(this, s);
        }

        public String toString() {
            if (inst != null) return inst.toString();
            else return qtype + "?";
        }

        @Override
        public boolean isPartial() {
            return true;
        }

        @Override
        public Type baseType() {
            if (inst != null) return inst.baseType();
            else return this;
        }

        public List<Type> getBounds(InferenceBound... ibs) {
            ListBuffer<Type> buf = new ListBuffer<>();
            for (InferenceBound ib : ibs) {
                buf.appendList(bounds.get(ib));
            }
            return buf.toList();
        }

        public List<Type> getDeclaredBounds() {
            ListBuffer<Type> buf = new ListBuffer<>();
            int count = 0;
            for (Type b : getBounds(InferenceBound.UPPER)) {
                if (count++ == declaredCount) break;
                buf.append(b);
            }
            return buf.toList();
        }

        public void setBounds(InferenceBound ib, List<Type> newBounds) {
            bounds.put(ib, newBounds);
        }

        public final void addBound(InferenceBound ib, Type bound, Types types) {
            addBound(ib, bound, types, false);
        }

        protected void addBound(InferenceBound ib, Type bound, Types types, boolean update) {
            Type bound2 = toTypeVarMap.apply(bound).baseType();
            List<Type> prevBounds = bounds.get(ib);
            for (Type b : prevBounds) {


                if (types.isSameType(b, bound2, true) || bound == qtype) return;
            }
            bounds.put(ib, prevBounds.prepend(bound2));
            notifyChange(EnumSet.of(ib));
        }

        public void substBounds(List<Type> from, List<Type> to, Types types) {
            List<Type> instVars = from.diff(to);

            if (instVars.isEmpty()) return;
            final EnumSet<InferenceBound> boundsChanged = EnumSet.noneOf(InferenceBound.class);
            UndetVarListener prevListener = listener;
            try {

                listener = new UndetVarListener() {
                    public void varChanged(UndetVar uv, Set<InferenceBound> ibs) {
                        boundsChanged.addAll(ibs);
                    }
                };
                for (Map.Entry<InferenceBound, List<Type>> _entry : bounds.entrySet()) {
                    InferenceBound ib = _entry.getKey();
                    List<Type> prevBounds = _entry.getValue();
                    ListBuffer<Type> newBounds = new ListBuffer<>();
                    ListBuffer<Type> deps = new ListBuffer<>();

                    for (Type t : prevBounds) {
                        if (!t.containsAny(instVars)) {
                            newBounds.append(t);
                        } else {
                            deps.append(t);
                        }
                    }

                    bounds.put(ib, newBounds.toList());

                    for (Type dep : deps) {
                        addBound(ib, types.subst(dep, from, to), types, true);
                    }
                }
            } finally {
                listener = prevListener;
                if (!boundsChanged.isEmpty()) {
                    notifyChange(boundsChanged);
                }
            }
        }

        private void notifyChange(EnumSet<InferenceBound> ibs) {
            if (listener != null) {
                listener.varChanged(this, ibs);
            }
        }

        public boolean isCaptured() {
            return false;
        }

        public enum InferenceBound {

            UPPER,

            LOWER,

            EQ
        }

        public interface UndetVarListener {

            void varChanged(UndetVar uv, Set<InferenceBound> ibs);
        }
    }

    public static class CapturedUndetVar extends UndetVar {
        public CapturedUndetVar(CapturedType origin, Types types) {
            super(origin, types);
            if (!origin.lower.hasTag(BOT)) {
                bounds.put(InferenceBound.LOWER, List.of(origin.lower));
            }
        }

        @Override
        public void addBound(InferenceBound ib, Type bound, Types types, boolean update) {
            if (update) {

                super.addBound(ib, bound, types, update);
            }
        }

        @Override
        public boolean isCaptured() {
            return true;
        }
    }

    public static class JCNoType extends Type implements NoType {
        public JCNoType() {
            super(null);
        }

        @Override
        public TypeTag getTag() {
            return NONE;
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.NONE;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitNoType(this, p);
        }

        @Override
        public boolean isCompound() {
            return false;
        }
    }

    public static class JCVoidType extends Type implements NoType {
        public JCVoidType() {
            super(null);
        }

        @Override
        public TypeTag getTag() {
            return VOID;
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.VOID;
        }

        @Override
        public boolean isCompound() {
            return false;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitNoType(this, p);
        }

        @Override
        public boolean isPrimitiveOrVoid() {
            return true;
        }
    }

    static class BottomType extends Type implements NullType {
        public BottomType() {
            super(null);
        }

        @Override
        public TypeTag getTag() {
            return BOT;
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.NULL;
        }

        @Override
        public boolean isCompound() {
            return false;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitNull(this, p);
        }

        @Override
        public Type constType(Object value) {
            return this;
        }

        @Override
        public String stringValue() {
            return "null";
        }

        @Override
        public boolean isNullOrReference() {
            return true;
        }
    }

    public static class ErrorType extends ClassType
            implements javax.lang.model.type.ErrorType {
        private Type originalType = null;

        public ErrorType(Type originalType, TypeSymbol tsym) {
            super(noType, List.nil(), null);
            this.tsym = tsym;
            this.originalType = (originalType == null ? noType : originalType);
        }

        public ErrorType(ClassSymbol c, Type originalType) {
            this(originalType, c);
            c.type = this;
            c.kind = ERR;
            c.members_field = new Scope.ErrorScope(c);
        }

        public ErrorType(Name name, TypeSymbol container, Type originalType) {
            this(new ClassSymbol(PUBLIC | STATIC | ACYCLIC, name, null, container), originalType);
        }

        @Override
        public TypeTag getTag() {
            return ERROR;
        }

        @Override
        public boolean isPartial() {
            return true;
        }

        @Override
        public boolean isReference() {
            return true;
        }

        @Override
        public boolean isNullOrReference() {
            return true;
        }

        @Override
        public <R, S> R accept(Visitor<R, S> v, S s) {
            return v.visitErrorType(this, s);
        }

        public Type constType(Object constValue) {
            return this;
        }

        public Type getEnclosingType() {
            return this;
        }

        public Type getReturnType() {
            return this;
        }

        public Type asSub(Symbol sym) {
            return this;
        }

        public Type map(Mapping f) {
            return this;
        }

        public boolean isGenType(Type t) {
            return true;
        }

        public boolean isErroneous() {
            return true;
        }

        public boolean isCompound() {
            return false;
        }

        public boolean isInterface() {
            return false;
        }

        public List<Type> allparams() {
            return List.nil();
        }

        public List<Type> getTypeArguments() {
            return List.nil();
        }

        public TypeKind getKind() {
            return TypeKind.ERROR;
        }

        public Type getOriginalType() {
            return originalType;
        }

        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitError(this, p);
        }
    }

    public static class AnnotatedType extends Type
            implements
            javax.lang.model.type.ArrayType,
            DeclaredType,
            PrimitiveType,
            TypeVariable,
            javax.lang.model.type.WildcardType {

        private List<Attribute.TypeCompound> typeAnnotations;

        private Type underlyingType;

        protected AnnotatedType(List<Attribute.TypeCompound> typeAnnotations,
                                Type underlyingType) {
            super(underlyingType.tsym);
            this.typeAnnotations = typeAnnotations;
            this.underlyingType = underlyingType;
            Assert.check(typeAnnotations != null && typeAnnotations.nonEmpty(),
                    "Can't create AnnotatedType without annotations: " + underlyingType);
            Assert.check(!underlyingType.isAnnotated(),
                    "Can't annotate already annotated type: " + underlyingType +
                            "; adding: " + typeAnnotations);
        }

        @Override
        public TypeTag getTag() {
            return underlyingType.getTag();
        }

        @Override
        public boolean isAnnotated() {
            return true;
        }

        @Override
        public List<Attribute.TypeCompound> getAnnotationMirrors() {
            return typeAnnotations;
        }

        @Override
        public TypeKind getKind() {
            return underlyingType.getKind();
        }

        @Override
        public Type unannotatedType() {
            return underlyingType;
        }

        @Override
        public <R, S> R accept(Visitor<R, S> v, S s) {
            return v.visitAnnotatedType(this, s);
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return underlyingType.accept(v, p);
        }

        @Override
        public Type map(Mapping f) {
            underlyingType.map(f);
            return this;
        }

        @Override
        public Type constType(Object constValue) {
            return underlyingType.constType(constValue);
        }

        @Override
        public Type getEnclosingType() {
            return underlyingType.getEnclosingType();
        }

        @Override
        public Type getReturnType() {
            return underlyingType.getReturnType();
        }

        @Override
        public List<Type> getTypeArguments() {
            return underlyingType.getTypeArguments();
        }

        @Override
        public List<Type> getParameterTypes() {
            return underlyingType.getParameterTypes();
        }

        @Override
        public Type getReceiverType() {
            return underlyingType.getReceiverType();
        }

        @Override
        public List<Type> getThrownTypes() {
            return underlyingType.getThrownTypes();
        }

        @Override
        public Type getUpperBound() {
            return underlyingType.getUpperBound();
        }

        @Override
        public Type getLowerBound() {
            return underlyingType.getLowerBound();
        }

        @Override
        public boolean isErroneous() {
            return underlyingType.isErroneous();
        }

        @Override
        public boolean isCompound() {
            return underlyingType.isCompound();
        }

        @Override
        public boolean isInterface() {
            return underlyingType.isInterface();
        }

        @Override
        public List<Type> allparams() {
            return underlyingType.allparams();
        }

        @Override
        public boolean isPrimitive() {
            return underlyingType.isPrimitive();
        }

        @Override
        public boolean isPrimitiveOrVoid() {
            return underlyingType.isPrimitiveOrVoid();
        }

        @Override
        public boolean isNumeric() {
            return underlyingType.isNumeric();
        }

        @Override
        public boolean isReference() {
            return underlyingType.isReference();
        }

        @Override
        public boolean isNullOrReference() {
            return underlyingType.isNullOrReference();
        }

        @Override
        public boolean isPartial() {
            return underlyingType.isPartial();
        }

        @Override
        public boolean isParameterized() {
            return underlyingType.isParameterized();
        }

        @Override
        public boolean isRaw() {
            return underlyingType.isRaw();
        }

        @Override
        public boolean isFinal() {
            return underlyingType.isFinal();
        }

        @Override
        public boolean isSuperBound() {
            return underlyingType.isSuperBound();
        }

        @Override
        public boolean isExtendsBound() {
            return underlyingType.isExtendsBound();
        }

        @Override
        public boolean isUnbound() {
            return underlyingType.isUnbound();
        }

        @Override
        public String toString() {


            if (typeAnnotations != null &&
                    !typeAnnotations.isEmpty()) {
                return "(" + typeAnnotations.toString() + " :: " + underlyingType.toString() + ")";
            } else {
                return "({} :: " + underlyingType.toString() + ")";
            }
        }

        @Override
        public boolean contains(Type t) {
            return underlyingType.contains(t);
        }

        @Override
        public Type withTypeVar(Type t) {


            underlyingType = underlyingType.withTypeVar(t);
            return this;
        }

        @Override
        public TypeSymbol asElement() {
            return underlyingType.asElement();
        }

        @Override
        public MethodType asMethodType() {
            return underlyingType.asMethodType();
        }

        @Override
        public void complete() {
            underlyingType.complete();
        }

        @Override
        public TypeMirror getComponentType() {
            return ((ArrayType) underlyingType).getComponentType();
        }

        public Type makeVarargs() {
            return ((ArrayType) underlyingType).makeVarargs().annotatedType(typeAnnotations);
        }

        @Override
        public TypeMirror getExtendsBound() {
            return ((WildcardType) underlyingType).getExtendsBound();
        }

        @Override
        public TypeMirror getSuperBound() {
            return ((WildcardType) underlyingType).getSuperBound();
        }
    }

    public static class UnknownType extends Type {
        public UnknownType() {
            super(null);
        }

        @Override
        public TypeTag getTag() {
            return UNKNOWN;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitUnknown(this, p);
        }

        @Override
        public boolean isPartial() {
            return true;
        }
    }
}
