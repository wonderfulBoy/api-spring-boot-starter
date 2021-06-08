package com.github.api.sun.source.tree;

@jdk.Exported
public interface IfTree extends StatementTree {
    ExpressionTree getCondition();

    StatementTree getThenStatement();

    StatementTree getElseStatement();
}
