package com.sun.source.doctree;

import java.util.List;

@jdk.Exported
public interface VersionTree extends BlockTagTree {
    List<? extends DocTree> getBody();
}
