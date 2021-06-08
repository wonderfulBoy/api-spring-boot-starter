package com.sun.source.tree;

import javax.lang.model.type.TypeKind;
@jdk.Exported
public interface PrimitiveTypeTree extends Tree {
    TypeKind getPrimitiveTypeKind();
}
