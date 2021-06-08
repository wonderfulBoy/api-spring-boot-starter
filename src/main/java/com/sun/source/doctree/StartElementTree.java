package com.sun.source.doctree;

import javax.lang.model.element.Name;
import java.util.List;

@jdk.Exported
public interface StartElementTree extends DocTree {
    Name getName();

    List<? extends DocTree> getAttributes();

    boolean isSelfClosing();
}
