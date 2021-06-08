package com.github.api.sun.source.tree;

import javax.lang.model.element.Name;

@jdk.Exported
public interface VariableTree extends StatementTree {
    ModifiersTree getModifiers();

    Name getName();

    ExpressionTree getNameExpression();

    Tree getType();

    ExpressionTree getInitializer();
}
