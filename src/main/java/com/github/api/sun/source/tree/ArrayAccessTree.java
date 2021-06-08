package com.github.api.sun.source.tree;

@jdk.Exported
public interface ArrayAccessTree extends ExpressionTree {
    ExpressionTree getExpression();

    ExpressionTree getIndex();
}
