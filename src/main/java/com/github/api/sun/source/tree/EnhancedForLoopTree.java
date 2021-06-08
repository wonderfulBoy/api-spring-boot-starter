package com.github.api.sun.source.tree;

@jdk.Exported
public interface EnhancedForLoopTree extends StatementTree {
    VariableTree getVariable();

    ExpressionTree getExpression();

    StatementTree getStatement();
}
