package com.sun.source.tree;

@jdk.Exported
public interface ExpressionStatementTree extends StatementTree {
    ExpressionTree getExpression();
}
