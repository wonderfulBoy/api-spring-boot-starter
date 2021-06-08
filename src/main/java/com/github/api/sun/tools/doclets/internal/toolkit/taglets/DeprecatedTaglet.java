package com.github.api.sun.tools.doclets.internal.toolkit.taglets;

import com.github.api.sun.javadoc.Doc;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;

public class DeprecatedTaglet extends BaseTaglet {
    public DeprecatedTaglet() {
        name = "deprecated";
    }

    public Content getTagletOutput(Doc holder, TagletWriter writer) {
        return writer.deprecatedTagOutput(holder);
    }
}
