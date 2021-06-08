package com.github.api.sun.source.doctree;

import java.util.List;

@jdk.Exported
public interface ParamTree extends BlockTagTree {
    boolean isTypeParameter();

    IdentifierTree getName();

    List<? extends DocTree> getDescription();
}
