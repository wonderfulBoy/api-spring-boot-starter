package com.sun.source.tree;

@jdk.Exported
public interface ImportTree extends Tree {
    boolean isStatic();

    Tree getQualifiedIdentifier();
}
