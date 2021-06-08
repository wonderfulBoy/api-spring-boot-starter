package com.github.api.sun.javadoc;

public interface AnnotatedType extends Type {
    AnnotationDesc[] annotations();

    Type underlyingType();
}
