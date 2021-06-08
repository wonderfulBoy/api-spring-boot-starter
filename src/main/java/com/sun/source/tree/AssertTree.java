package com.sun.source.tree;

@jdk.Exported
public interface AssertTree extends StatementTree {
    ExpressionTree getCondition();

    ExpressionTree getDetail();
}
