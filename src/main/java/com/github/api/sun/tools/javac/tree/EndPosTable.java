package com.github.api.sun.tools.javac.tree;

public interface EndPosTable {

    int getEndPos(JCTree tree);

    void storeEnd(JCTree tree, int endpos);

    int replaceTree(JCTree oldtree, JCTree newtree);
}
