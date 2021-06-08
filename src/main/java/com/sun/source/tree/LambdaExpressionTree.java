package com.sun.source.tree;

import java.util.List;

@jdk.Exported
public interface LambdaExpressionTree extends ExpressionTree {

    List<? extends VariableTree> getParameters();
    Tree getBody();
    BodyKind getBodyKind();

    @jdk.Exported
    enum BodyKind {
        EXPRESSION,
        STATEMENT;
    }
}
