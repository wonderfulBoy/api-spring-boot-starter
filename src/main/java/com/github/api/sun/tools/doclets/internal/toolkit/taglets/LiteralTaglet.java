package com.github.api.sun.tools.doclets.internal.toolkit.taglets;

import com.github.api.sun.javadoc.Tag;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;

import java.util.Map;

public class LiteralTaglet extends BaseInlineTaglet {
    private static final String NAME = "literal";

    public static void register(Map<String, Taglet> map) {
        map.remove(NAME);
        map.put(NAME, new LiteralTaglet());
    }

    public String getName() {
        return NAME;
    }

    public Content getTagletOutput(Tag tag, TagletWriter writer) {
        return writer.literalTagOutput(tag);
    }
}
