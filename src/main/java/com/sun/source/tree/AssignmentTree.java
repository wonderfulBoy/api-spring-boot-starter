package com.sun.source.tree;
@jdk.Exported
public interface AssignmentTree extends ExpressionTree {
    ExpressionTree getVariable();
    ExpressionTree getExpression();
}
