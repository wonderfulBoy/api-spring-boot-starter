package com.github.api.sun.source.doctree;

import javax.lang.model.element.Name;
import java.util.List;

@jdk.Exported
public interface AttributeTree extends DocTree {
    Name getName();

    ValueKind getValueKind();

    List<? extends DocTree> getValue();

    @jdk.Exported
    enum ValueKind {EMPTY, UNQUOTED, SINGLE, DOUBLE}
}
