package com.github.api.sun.source.doctree;

import java.util.List;

@jdk.Exported
public interface LinkTree extends InlineTagTree {
    ReferenceTree getReference();

    List<? extends DocTree> getLabel();
}
