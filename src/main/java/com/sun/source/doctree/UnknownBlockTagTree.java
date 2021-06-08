package com.sun.source.doctree;

import java.util.List;

@jdk.Exported
public interface UnknownBlockTagTree extends BlockTagTree {
    List<? extends DocTree> getContent();
}
