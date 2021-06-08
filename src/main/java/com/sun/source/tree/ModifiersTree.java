package com.sun.source.tree;

import java.util.List;
import java.util.Set;
import javax.lang.model.element.Modifier;

@jdk.Exported
public interface ModifiersTree extends Tree {
    Set<Modifier> getFlags();
    List<? extends AnnotationTree> getAnnotations();
}
