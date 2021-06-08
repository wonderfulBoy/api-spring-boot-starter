package com.sun.source.tree;
@jdk.Exported
public interface WhileLoopTree extends StatementTree {
    ExpressionTree getCondition();
    StatementTree getStatement();
}
