package com.sun.source.tree;

import java.util.List;
@jdk.Exported
public interface BlockTree extends StatementTree {
    boolean isStatic();
    List<? extends StatementTree> getStatements();
}
