package com.sun.source.tree;

@jdk.Exported
public interface ParenthesizedTree extends ExpressionTree {
    ExpressionTree getExpression();
}
