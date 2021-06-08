package com.github.api.sun.source.doctree;

import javax.lang.model.element.Name;

@jdk.Exported
public interface IdentifierTree extends DocTree {
    Name getName();
}
