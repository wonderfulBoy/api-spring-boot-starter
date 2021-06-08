package com.sun.source.tree;

import java.util.List;
@jdk.Exported
public interface CaseTree extends Tree {
    ExpressionTree getExpression();
    List<? extends StatementTree> getStatements();
}
