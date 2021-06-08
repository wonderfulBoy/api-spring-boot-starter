package com.sun.tools.doclets.internal.toolkit.taglets;

import com.sun.javadoc.Tag;
import com.sun.tools.doclets.internal.toolkit.Content;

import java.util.Map;

public class CodeTaglet extends BaseInlineTaglet {
    private static final String NAME = "code";

    public static void register(Map<String, Taglet> map) {
        map.remove(NAME);
        map.put(NAME, new CodeTaglet());
    }

    public String getName() {
        return NAME;
    }

    public Content getTagletOutput(Tag tag, TagletWriter writer) {
        return writer.codeTagOutput(tag);
    }
}
