package com.github.api.sun.source.tree;

@jdk.Exported
public interface SynchronizedTree extends StatementTree {
    ExpressionTree getExpression();

    BlockTree getBlock();
}
