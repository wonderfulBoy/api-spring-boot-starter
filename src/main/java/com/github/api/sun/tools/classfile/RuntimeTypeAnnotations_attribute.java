package com.github.api.sun.tools.classfile;

import java.io.IOException;

public abstract class RuntimeTypeAnnotations_attribute extends Attribute {
    public final TypeAnnotation[] annotations;

    protected RuntimeTypeAnnotations_attribute(ClassReader cr, int name_index, int length)
            throws IOException, Annotation.InvalidAnnotation {
        super(name_index, length);
        int num_annotations = cr.readUnsignedShort();
        annotations = new TypeAnnotation[num_annotations];
        for (int i = 0; i < annotations.length; i++)
            annotations[i] = new TypeAnnotation(cr);
    }

    protected RuntimeTypeAnnotations_attribute(int name_index, TypeAnnotation[] annotations) {
        super(name_index, length(annotations));
        this.annotations = annotations;
    }

    private static int length(TypeAnnotation[] annos) {
        int n = 2;
        for (TypeAnnotation anno : annos)
            n += anno.length();
        return n;
    }
}
