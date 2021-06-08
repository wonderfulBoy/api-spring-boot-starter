package com.sun.source.tree;
@jdk.Exported
public interface CatchTree extends Tree {
    VariableTree getParameter();
    BlockTree getBlock();
}
