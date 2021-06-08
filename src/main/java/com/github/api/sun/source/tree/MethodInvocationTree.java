package com.github.api.sun.source.tree;

import java.util.List;

@jdk.Exported
public interface MethodInvocationTree extends ExpressionTree {
    List<? extends Tree> getTypeArguments();

    ExpressionTree getMethodSelect();

    List<? extends ExpressionTree> getArguments();
}
