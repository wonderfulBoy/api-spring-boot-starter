package com.sun.tools.doclets.internal.toolkit.taglets;

import com.sun.javadoc.Doc;
import com.sun.javadoc.Tag;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocFinder;

public class SimpleTaglet extends BaseTaglet implements InheritableTaglet {

    public static final String EXCLUDED = "x";

    public static final String PACKAGE = "p";

    public static final String TYPE = "t";

    public static final String CONSTRUCTOR = "c";

    public static final String FIELD = "f";

    public static final String METHOD = "m";

    public static final String OVERVIEW = "o";

    public static final String ALL = "a";

    protected String tagName;

    protected String header;

    protected String locations;

    public SimpleTaglet(String tagName, String header, String locations) {
        this.tagName = tagName;
        this.header = header;
        locations = locations.toLowerCase();
        if (locations.indexOf(ALL) != -1 && locations.indexOf(EXCLUDED) == -1) {
            this.locations = PACKAGE + TYPE + FIELD + METHOD + CONSTRUCTOR + OVERVIEW;
        } else {
            this.locations = locations;
        }
    }

    public String getName() {
        return tagName;
    }

    public boolean inConstructor() {
        return locations.indexOf(CONSTRUCTOR) != -1 && locations.indexOf(EXCLUDED) == -1;
    }

    public boolean inField() {
        return locations.indexOf(FIELD) != -1 && locations.indexOf(EXCLUDED) == -1;
    }

    public boolean inMethod() {
        return locations.indexOf(METHOD) != -1 && locations.indexOf(EXCLUDED) == -1;
    }

    public boolean inOverview() {
        return locations.indexOf(OVERVIEW) != -1 && locations.indexOf(EXCLUDED) == -1;
    }

    public boolean inPackage() {
        return locations.indexOf(PACKAGE) != -1 && locations.indexOf(EXCLUDED) == -1;
    }

    public boolean inType() {
        return locations.indexOf(TYPE) != -1 && locations.indexOf(EXCLUDED) == -1;
    }

    public boolean isInlineTag() {
        return false;
    }

    @Override
    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        Tag[] tags = input.element.tags(tagName);
        if (tags.length > 0) {
            output.holder = input.element;
            output.holderTag = tags[0];
            output.inlineTags = input.isFirstSentence
                    ? tags[0].firstSentenceTags() : tags[0].inlineTags();
        }
    }

    public Content getTagletOutput(Tag tag, TagletWriter writer) {
        return header == null || tag == null ? null : writer.simpleTagOutput(tag, header);
    }

    public Content getTagletOutput(Doc holder, TagletWriter writer) {
        if (header == null || holder.tags(getName()).length == 0) {
            return null;
        }
        return writer.simpleTagOutput(holder.tags(getName()), header);
    }
}
