package com.sun.source.doctree;

import java.util.List;

@jdk.Exported
public interface SinceTree extends BlockTagTree {
    List<? extends DocTree> getBody();
}
