package com.sun.source.doctree;

import java.util.List;
@jdk.Exported
public interface UnknownInlineTagTree extends InlineTagTree {
    List<? extends DocTree> getContent();
}
