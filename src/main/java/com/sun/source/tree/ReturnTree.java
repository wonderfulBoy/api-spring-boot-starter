package com.sun.source.tree;

@jdk.Exported
public interface ReturnTree extends StatementTree {
    ExpressionTree getExpression();
}
