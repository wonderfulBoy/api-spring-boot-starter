package com.github.api.sun.tools.doclets.internal.toolkit.taglets;

import com.github.api.sun.javadoc.Tag;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;

public class DocRootTaglet extends BaseInlineTaglet {

    public DocRootTaglet() {
        name = "docRoot";
    }

    public Content getTagletOutput(Tag tag, TagletWriter writer) {
        return writer.getDocRootOutput();
    }
}
