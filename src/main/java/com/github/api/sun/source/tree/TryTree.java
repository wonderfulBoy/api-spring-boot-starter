package com.github.api.sun.source.tree;

import java.util.List;

@jdk.Exported
public interface TryTree extends StatementTree {
    BlockTree getBlock();

    List<? extends CatchTree> getCatches();

    BlockTree getFinallyBlock();

    List<? extends Tree> getResources();
}
