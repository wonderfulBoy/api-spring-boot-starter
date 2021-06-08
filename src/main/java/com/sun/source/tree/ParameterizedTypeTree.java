package com.sun.source.tree;

import java.util.List;

@jdk.Exported
public interface ParameterizedTypeTree extends Tree {
    Tree getType();

    List<? extends Tree> getTypeArguments();
}
