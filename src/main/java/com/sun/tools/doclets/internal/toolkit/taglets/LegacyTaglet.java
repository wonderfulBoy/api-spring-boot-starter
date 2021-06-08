package com.sun.tools.doclets.internal.toolkit.taglets;

import com.sun.javadoc.Doc;
import com.sun.javadoc.Tag;
import com.sun.tools.doclets.formats.html.markup.RawHtml;
import com.sun.tools.doclets.internal.toolkit.Content;

public class LegacyTaglet implements Taglet {
    private com.sun.tools.doclets.Taglet legacyTaglet;

    public LegacyTaglet(com.sun.tools.doclets.Taglet t) {
        legacyTaglet = t;
    }

    public boolean inField() {
        return legacyTaglet.isInlineTag() || legacyTaglet.inField();
    }

    public boolean inConstructor() {
        return legacyTaglet.isInlineTag() || legacyTaglet.inConstructor();
    }

    public boolean inMethod() {
        return legacyTaglet.isInlineTag() || legacyTaglet.inMethod();
    }

    public boolean inOverview() {
        return legacyTaglet.isInlineTag() || legacyTaglet.inOverview();
    }

    public boolean inPackage() {
        return legacyTaglet.isInlineTag() || legacyTaglet.inPackage();
    }

    public boolean inType() {
        return legacyTaglet.isInlineTag() || legacyTaglet.inType();
    }

    public boolean isInlineTag() {
        return legacyTaglet.isInlineTag();
    }

    public String getName() {
        return legacyTaglet.getName();
    }

    public Content getTagletOutput(Tag tag, TagletWriter writer)
            throws IllegalArgumentException {
        Content output = writer.getOutputInstance();
        output.addContent(new RawHtml(legacyTaglet.toString(tag)));
        return output;
    }

    public Content getTagletOutput(Doc holder, TagletWriter writer)
            throws IllegalArgumentException {
        Content output = writer.getOutputInstance();
        Tag[] tags = holder.tags(getName());
        if (tags.length > 0) {
            String tagString = legacyTaglet.toString(tags);
            if (tagString != null) {
                output.addContent(new RawHtml(tagString));
            }
        }
        return output;
    }
}
