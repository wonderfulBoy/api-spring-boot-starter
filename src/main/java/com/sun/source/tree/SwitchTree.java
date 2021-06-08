package com.sun.source.tree;

import java.util.List;
@jdk.Exported
public interface SwitchTree extends StatementTree {
    ExpressionTree getExpression();
    List<? extends CaseTree> getCases();
}
