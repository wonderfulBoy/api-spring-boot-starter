package com.sun.source.tree;

import java.util.List;

@jdk.Exported
public interface AnnotationTree extends ExpressionTree {
    Tree getAnnotationType();

    List<? extends ExpressionTree> getArguments();
}
