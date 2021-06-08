package com.github.api.sun.source.tree;

import java.util.List;

@jdk.Exported
public interface ErroneousTree extends ExpressionTree {
    List<? extends Tree> getErrorTrees();
}
