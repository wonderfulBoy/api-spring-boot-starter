package com.sun.source.tree;

import javax.lang.model.element.Name;
import java.util.List;

@jdk.Exported
public interface TypeParameterTree extends Tree {
    Name getName();

    List<? extends Tree> getBounds();

    List<? extends AnnotationTree> getAnnotations();
}
