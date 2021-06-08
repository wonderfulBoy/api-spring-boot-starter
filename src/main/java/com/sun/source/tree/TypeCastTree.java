package com.sun.source.tree;
@jdk.Exported
public interface TypeCastTree extends ExpressionTree {
    Tree getType();
    ExpressionTree getExpression();
}
