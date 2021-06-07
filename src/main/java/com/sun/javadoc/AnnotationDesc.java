package com.sun.javadoc;

public interface AnnotationDesc {

    AnnotationTypeDoc annotationType();

    ElementValuePair[] elementValues();

    boolean isSynthesized();

    interface ElementValuePair {

        AnnotationTypeElementDoc element();

        AnnotationValue value();
    }

}
