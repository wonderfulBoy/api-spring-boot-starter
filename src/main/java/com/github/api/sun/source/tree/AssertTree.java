package com.github.api.sun.source.tree;

@jdk.Exported
public interface AssertTree extends StatementTree {
    ExpressionTree getCondition();

    ExpressionTree getDetail();
}
