package com.github.api.sun.source.tree;

@jdk.Exported
public interface BinaryTree extends ExpressionTree {
    ExpressionTree getLeftOperand();

    ExpressionTree getRightOperand();
}
