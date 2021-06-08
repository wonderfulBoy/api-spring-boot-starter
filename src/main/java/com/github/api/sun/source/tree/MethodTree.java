package com.github.api.sun.source.tree;

import javax.lang.model.element.Name;
import java.util.List;

@jdk.Exported
public interface MethodTree extends Tree {
    ModifiersTree getModifiers();

    Name getName();

    Tree getReturnType();

    List<? extends TypeParameterTree> getTypeParameters();

    List<? extends VariableTree> getParameters();

    VariableTree getReceiverParameter();

    List<? extends ExpressionTree> getThrows();

    BlockTree getBody();

    Tree getDefaultValue();
}
