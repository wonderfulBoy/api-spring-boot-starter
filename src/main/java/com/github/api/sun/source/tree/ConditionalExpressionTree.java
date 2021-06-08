package com.github.api.sun.source.tree;

@jdk.Exported
public interface ConditionalExpressionTree extends ExpressionTree {
    ExpressionTree getCondition();

    ExpressionTree getTrueExpression();

    ExpressionTree getFalseExpression();
}
