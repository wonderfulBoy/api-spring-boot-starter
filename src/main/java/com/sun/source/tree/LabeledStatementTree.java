package com.sun.source.tree;

import javax.lang.model.element.Name;

@jdk.Exported
public interface LabeledStatementTree extends StatementTree {
    Name getLabel();

    StatementTree getStatement();
}
