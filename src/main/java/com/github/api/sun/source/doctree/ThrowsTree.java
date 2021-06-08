package com.github.api.sun.source.doctree;

import java.util.List;

@jdk.Exported
public interface ThrowsTree extends BlockTagTree {
    ReferenceTree getExceptionName();

    List<? extends DocTree> getDescription();
}
