package com.sun.source.doctree;

import java.util.List;
@jdk.Exported
public interface SerialFieldTree extends BlockTagTree {
    IdentifierTree getName();
    ReferenceTree getType();
    List<? extends DocTree> getDescription();
}
