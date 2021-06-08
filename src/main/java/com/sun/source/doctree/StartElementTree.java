package com.sun.source.doctree;

import java.util.List;
import javax.lang.model.element.Name;
@jdk.Exported
public interface StartElementTree extends DocTree {
    Name getName();
    List<? extends DocTree> getAttributes();
    boolean isSelfClosing();
}
