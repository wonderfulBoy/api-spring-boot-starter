package com.sun.tools.javac.model;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ArrayType;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;
import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.annotation.EnumConstantNotPresentExceptionProxy;
import sun.reflect.annotation.ExceptionProxy;

import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationTypeMismatchException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public class AnnotationProxyMaker {
    private final Attribute.Compound anno;
    private final Class<? extends Annotation> annoType;

    private AnnotationProxyMaker(Attribute.Compound anno,
                                 Class<? extends Annotation> annoType) {
        this.anno = anno;
        this.annoType = annoType;
    }

    public static <A extends Annotation> A generateAnnotation(
            Attribute.Compound anno, Class<A> annoType) {
        AnnotationProxyMaker apm = new AnnotationProxyMaker(anno, annoType);
        return annoType.cast(apm.generateAnnotation());
    }

    private Annotation generateAnnotation() {
        return AnnotationParser.annotationForMap(annoType,
                getAllReflectedValues());
    }

    private Map<String, Object> getAllReflectedValues() {
        Map<String, Object> res = new LinkedHashMap<String, Object>();
        for (Map.Entry<MethodSymbol, Attribute> entry :
                getAllValues().entrySet()) {
            MethodSymbol meth = entry.getKey();
            Object value = generateValue(meth, entry.getValue());
            if (value != null) {
                res.put(meth.name.toString(), value);
            } else {


            }
        }
        return res;
    }

    private Map<MethodSymbol, Attribute> getAllValues() {
        Map<MethodSymbol, Attribute> res =
                new LinkedHashMap<MethodSymbol, Attribute>();

        ClassSymbol sym = (ClassSymbol) anno.type.tsym;
        for (Scope.Entry e = sym.members().elems; e != null; e = e.sibling) {
            if (e.sym.kind == Kinds.MTH) {
                MethodSymbol m = (MethodSymbol) e.sym;
                Attribute def = m.getDefaultValue();
                if (def != null)
                    res.put(m, def);
            }
        }

        for (Pair<MethodSymbol, Attribute> p : anno.values)
            res.put(p.fst, p.snd);
        return res;
    }

    private Object generateValue(MethodSymbol meth, Attribute attr) {
        ValueVisitor vv = new ValueVisitor(meth);
        return vv.getValue(attr);
    }

    private static final class MirroredTypeExceptionProxy extends ExceptionProxy {
        static final long serialVersionUID = 269;
        private final String typeString;
        private transient TypeMirror type;

        MirroredTypeExceptionProxy(TypeMirror t) {
            type = t;
            typeString = t.toString();
        }

        public String toString() {
            return typeString;
        }

        public int hashCode() {
            return (type != null ? type : typeString).hashCode();
        }

        public boolean equals(Object obj) {
            return type != null &&
                    obj instanceof MirroredTypeExceptionProxy &&
                    type.equals(((MirroredTypeExceptionProxy) obj).type);
        }

        protected RuntimeException generateException() {
            return new MirroredTypeException(type);
        }

        private void readObject(ObjectInputStream s)
                throws IOException, ClassNotFoundException {
            s.defaultReadObject();
            type = null;
        }
    }

    private static final class MirroredTypesExceptionProxy extends ExceptionProxy {
        static final long serialVersionUID = 269;
        private final String typeStrings;
        private transient List<TypeMirror> types;

        MirroredTypesExceptionProxy(List<TypeMirror> ts) {
            types = ts;
            typeStrings = ts.toString();
        }

        public String toString() {
            return typeStrings;
        }

        public int hashCode() {
            return (types != null ? types : typeStrings).hashCode();
        }

        public boolean equals(Object obj) {
            return types != null &&
                    obj instanceof MirroredTypesExceptionProxy &&
                    types.equals(
                            ((MirroredTypesExceptionProxy) obj).types);
        }

        protected RuntimeException generateException() {
            return new MirroredTypesException(types);
        }

        private void readObject(ObjectInputStream s)
                throws IOException, ClassNotFoundException {
            s.defaultReadObject();
            types = null;
        }
    }

    private class ValueVisitor implements Attribute.Visitor {
        private MethodSymbol meth;
        private Class<?> returnClass;
        private Object value;

        ValueVisitor(MethodSymbol meth) {
            this.meth = meth;
        }

        Object getValue(Attribute attr) {
            Method method;
            try {
                method = annoType.getMethod(meth.name.toString());
            } catch (NoSuchMethodException e) {
                return null;
            }
            returnClass = method.getReturnType();
            attr.accept(this);
            if (!(value instanceof ExceptionProxy) &&
                    !AnnotationType.invocationHandlerReturnType(returnClass)
                            .isInstance(value)) {
                typeMismatch(method, attr);
            }
            return value;
        }

        public void visitConstant(Attribute.Constant c) {
            value = c.getValue();
        }

        public void visitClass(Attribute.Class c) {
            value = new MirroredTypeExceptionProxy(c.classType);
        }

        public void visitArray(Attribute.Array a) {
            Name elemName = ((ArrayType) a.type).elemtype.tsym.getQualifiedName();
            if (elemName.equals(elemName.table.names.java_lang_Class)) {

                ListBuffer<TypeMirror> elems = new ListBuffer<TypeMirror>();
                for (Attribute value : a.values) {
                    Type elem = ((Attribute.Class) value).classType;
                    elems.append(elem);
                }
                value = new MirroredTypesExceptionProxy(elems.toList());
            } else {
                int len = a.values.length;
                Class<?> returnClassSaved = returnClass;
                returnClass = returnClass.getComponentType();
                try {
                    Object res = Array.newInstance(returnClass, len);
                    for (int i = 0; i < len; i++) {
                        a.values[i].accept(this);
                        if (value == null || value instanceof ExceptionProxy) {
                            return;
                        }
                        try {
                            Array.set(res, i, value);
                        } catch (IllegalArgumentException e) {
                            value = null;
                            return;
                        }
                    }
                    value = res;
                } finally {
                    returnClass = returnClassSaved;
                }
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void visitEnum(Attribute.Enum e) {
            if (returnClass.isEnum()) {
                String constName = e.value.toString();
                try {
                    value = Enum.valueOf((Class) returnClass, constName);
                } catch (IllegalArgumentException ex) {
                    value = new EnumConstantNotPresentExceptionProxy(
                            (Class<Enum<?>>) returnClass, constName);
                }
            } else {
                value = null;
            }
        }

        public void visitCompound(Attribute.Compound c) {
            try {
                Class<? extends Annotation> nested =
                        returnClass.asSubclass(Annotation.class);
                value = generateAnnotation(c, nested);
            } catch (ClassCastException ex) {
                value = null;
            }
        }

        public void visitError(Attribute.Error e) {
            if (e instanceof Attribute.UnresolvedClass)
                value = new MirroredTypeExceptionProxy(((Attribute.UnresolvedClass) e).classType);
            else
                value = null;
        }

        private void typeMismatch(Method method, final Attribute attr) {
            class AnnotationTypeMismatchExceptionProxy extends ExceptionProxy {
                static final long serialVersionUID = 269;
                transient final Method method;

                AnnotationTypeMismatchExceptionProxy(Method method) {
                    this.method = method;
                }

                public String toString() {
                    return "<error>";
                }

                protected RuntimeException generateException() {
                    return new AnnotationTypeMismatchException(method,
                            attr.type.toString());
                }
            }
            value = new AnnotationTypeMismatchExceptionProxy(method);
        }
    }
}
