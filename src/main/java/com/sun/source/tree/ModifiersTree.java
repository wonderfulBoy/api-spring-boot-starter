package com.sun.source.tree;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Set;

@jdk.Exported
public interface ModifiersTree extends Tree {
    Set<Modifier> getFlags();

    List<? extends AnnotationTree> getAnnotations();
}
