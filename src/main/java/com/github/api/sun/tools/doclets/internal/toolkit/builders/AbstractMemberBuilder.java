package com.github.api.sun.tools.doclets.internal.toolkit.builders;

import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocletAbortException;

public abstract class AbstractMemberBuilder extends AbstractBuilder {

    public AbstractMemberBuilder(Context context) {
        super(context);
    }

    public void build() throws DocletAbortException {

        throw new DocletAbortException("not supported");
    }

    @Override
    public void build(XMLNode node, Content contentTree) {
        if (hasMembersToDocument()) {
            super.build(node, contentTree);
        }
    }

    public abstract boolean hasMembersToDocument();
}
