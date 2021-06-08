package com.sun.source.tree;

import javax.lang.model.element.Name;
import java.util.List;

@jdk.Exported
public interface MemberReferenceTree extends ExpressionTree {
    ReferenceMode getMode();

    ExpressionTree getQualifierExpression();

    Name getName();

    List<? extends ExpressionTree> getTypeArguments();

    @jdk.Exported
    enum ReferenceMode {
        INVOKE,
        NEW
    }
}
