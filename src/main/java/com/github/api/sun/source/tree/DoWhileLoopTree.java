package com.github.api.sun.source.tree;

@jdk.Exported
public interface DoWhileLoopTree extends StatementTree {
    ExpressionTree getCondition();

    StatementTree getStatement();
}
