package com.sun.source.tree;

import java.util.List;
@jdk.Exported
public interface AnnotatedTypeTree extends ExpressionTree {
    List<? extends AnnotationTree> getAnnotations();
    ExpressionTree getUnderlyingType();
}
