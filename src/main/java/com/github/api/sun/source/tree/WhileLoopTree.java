package com.github.api.sun.source.tree;

@jdk.Exported
public interface WhileLoopTree extends StatementTree {
    ExpressionTree getCondition();

    StatementTree getStatement();
}
