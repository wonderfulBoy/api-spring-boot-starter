package com.github.api.sun.tools.classfile;

import java.io.IOException;

public class RuntimeVisibleTypeAnnotations_attribute extends RuntimeTypeAnnotations_attribute {
    RuntimeVisibleTypeAnnotations_attribute(ClassReader cr, int name_index, int length)
            throws IOException, Annotation.InvalidAnnotation {
        super(cr, name_index, length);
    }

    public RuntimeVisibleTypeAnnotations_attribute(ConstantPool cp, TypeAnnotation[] annotations)
            throws ConstantPoolException {
        this(cp.getUTF8Index(Attribute.RuntimeVisibleTypeAnnotations), annotations);
    }

    public RuntimeVisibleTypeAnnotations_attribute(int name_index, TypeAnnotation[] annotations) {
        super(name_index, annotations);
    }

    public <R, P> R accept(Visitor<R, P> visitor, P p) {
        return visitor.visitRuntimeVisibleTypeAnnotations(this, p);
    }
}
