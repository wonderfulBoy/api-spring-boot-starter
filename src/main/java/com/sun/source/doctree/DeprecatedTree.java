package com.sun.source.doctree;

import java.util.List;

@jdk.Exported
public interface DeprecatedTree extends BlockTagTree {
    List<? extends DocTree> getBody();
}
