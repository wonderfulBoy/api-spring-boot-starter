package com.sun.tools.doclets.internal.toolkit.taglets;

import com.sun.javadoc.Doc;
import com.sun.tools.doclets.internal.toolkit.Content;

public class DeprecatedTaglet extends BaseTaglet {
    public DeprecatedTaglet() {
        name = "deprecated";
    }

    public Content getTagletOutput(Doc holder, TagletWriter writer) {
        return writer.deprecatedTagOutput(holder);
    }
}
