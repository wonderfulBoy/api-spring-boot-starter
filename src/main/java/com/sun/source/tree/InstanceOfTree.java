package com.sun.source.tree;

@jdk.Exported
public interface InstanceOfTree extends ExpressionTree {
    ExpressionTree getExpression();

    Tree getType();
}
