package com.github.api.sun.tools.javac.code;

import com.github.api.sun.tools.javac.model.AnnotationProxyMaker;
import com.github.api.sun.tools.javac.util.List;
import com.github.api.sun.tools.javac.util.ListBuffer;

import javax.lang.model.AnnotatedConstruct;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public abstract class AnnoConstruct implements AnnotatedConstruct {
    private static final Class<? extends Annotation> REPEATABLE_CLASS = initRepeatable();
    private static final Method VALUE_ELEMENT_METHOD = initValueElementMethod();

    private static Class<? extends Annotation> initRepeatable() {
        try {
            return Class.forName("java.lang.annotation.Repeatable").asSubclass(Annotation.class);
        } catch (ClassNotFoundException | SecurityException e) {
            return null;
        }
    }

    private static Method initValueElementMethod() {
        if (REPEATABLE_CLASS == null)
            return null;
        Method m = null;
        try {
            m = REPEATABLE_CLASS.getMethod("value");
            if (m != null)
                m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Class<? extends Annotation> getContainer(Class<? extends Annotation> annoType) {
        if (REPEATABLE_CLASS != null &&
                VALUE_ELEMENT_METHOD != null) {
            Annotation repeatable = annoType.getAnnotation(REPEATABLE_CLASS);
            if (repeatable != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Class<? extends Annotation> containerType = (Class) VALUE_ELEMENT_METHOD.invoke(repeatable);
                    return containerType;
                } catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Attribute[] unpackAttributes(Attribute.Compound container) {
        return ((Attribute.Array) container.member(container.type.tsym.name.table.names.value)).values;
    }

    @Override
    public abstract List<? extends Attribute.Compound> getAnnotationMirrors();

    protected <A extends Annotation> Attribute.Compound getAttribute(Class<A> annoType) {
        String name = annoType.getName();
        for (Attribute.Compound anno : getAnnotationMirrors()) {
            if (name.equals(anno.type.tsym.flatName().toString()))
                return anno;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    protected <A extends Annotation> A[] getInheritedAnnotations(Class<A> annoType) {
        return (A[]) java.lang.reflect.Array.newInstance(annoType, 0);
    }

    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annoType) {
        if (!annoType.isAnnotation())
            throw new IllegalArgumentException("Not an annotation type: "
                    + annoType);
        Class<? extends Annotation> containerType = getContainer(annoType);
        if (containerType == null) {
            A res = getAnnotation(annoType);
            int size = res == null ? 0 : 1;
            @SuppressWarnings("unchecked")
            A[] arr = (A[]) java.lang.reflect.Array.newInstance(annoType, size);
            if (res != null)
                arr[0] = res;
            return arr;
        }
        String annoTypeName = annoType.getName();
        String containerTypeName = containerType.getName();
        int directIndex = -1, containerIndex = -1;
        Attribute.Compound direct = null, container = null;
        int index = -1;
        for (Attribute.Compound attribute : getAnnotationMirrors()) {
            index++;
            if (attribute.type.tsym.flatName().contentEquals(annoTypeName)) {
                directIndex = index;
                direct = attribute;
            } else if (containerTypeName != null &&
                    attribute.type.tsym.flatName().contentEquals(containerTypeName)) {
                containerIndex = index;
                container = attribute;
            }
        }
        if (direct == null && container == null &&
                annoType.isAnnotationPresent(Inherited.class))
            return getInheritedAnnotations(annoType);
        Attribute.Compound[] contained = unpackContained(container);
        if (direct == null && contained.length == 0 &&
                annoType.isAnnotationPresent(Inherited.class))
            return getInheritedAnnotations(annoType);
        int size = (direct == null ? 0 : 1) + contained.length;
        @SuppressWarnings("unchecked")
        A[] arr = (A[]) java.lang.reflect.Array.newInstance(annoType, size);
        int insert = -1;
        int length = arr.length;
        if (directIndex >= 0 && containerIndex >= 0) {
            if (directIndex < containerIndex) {
                arr[0] = AnnotationProxyMaker.generateAnnotation(direct, annoType);
                insert = 1;
            } else {
                arr[arr.length - 1] = AnnotationProxyMaker.generateAnnotation(direct, annoType);
                insert = 0;
                length--;
            }
        } else if (directIndex >= 0) {
            arr[0] = AnnotationProxyMaker.generateAnnotation(direct, annoType);
            return arr;
        } else {
            insert = 0;
        }
        for (int i = 0; i + insert < length; i++)
            arr[insert + i] = AnnotationProxyMaker.generateAnnotation(contained[i], annoType);
        return arr;
    }

    private Attribute.Compound[] unpackContained(Attribute.Compound container) {
        Attribute[] contained0 = null;
        if (container != null)
            contained0 = unpackAttributes(container);
        ListBuffer<Attribute.Compound> compounds = new ListBuffer<>();
        if (contained0 != null) {
            for (Attribute a : contained0)
                if (a instanceof Attribute.Compound)
                    compounds = compounds.append((Attribute.Compound) a);
        }
        return compounds.toArray(new Attribute.Compound[compounds.size()]);
    }

    public <A extends Annotation> A getAnnotation(Class<A> annoType) {
        if (!annoType.isAnnotation())
            throw new IllegalArgumentException("Not an annotation type: " + annoType);
        Attribute.Compound c = getAttribute(annoType);
        return c == null ? null : AnnotationProxyMaker.generateAnnotation(c, annoType);
    }
}
