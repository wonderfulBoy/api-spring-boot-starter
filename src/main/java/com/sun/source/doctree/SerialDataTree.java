package com.sun.source.doctree;

import java.util.List;

@jdk.Exported
public interface SerialDataTree extends BlockTagTree {
    List<? extends DocTree> getDescription();
}
