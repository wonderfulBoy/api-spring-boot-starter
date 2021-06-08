package com.sun.tools.doclets.internal.toolkit.taglets;

import com.sun.javadoc.Doc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.SeeTag;
import com.sun.javadoc.Tag;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocFinder;

public class SeeTaglet extends BaseTaglet implements InheritableTaglet {
    public SeeTaglet() {
        name = "see";
    }

    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        Tag[] tags = input.element.seeTags();
        if (tags.length > 0) {
            output.holder = input.element;
            output.holderTag = tags[0];
            output.inlineTags = input.isFirstSentence ?
                    tags[0].firstSentenceTags() : tags[0].inlineTags();
        }
    }

    public Content getTagletOutput(Doc holder, TagletWriter writer) {
        SeeTag[] tags = holder.seeTags();
        if (tags.length == 0 && holder instanceof MethodDoc) {
            DocFinder.Output inheritedDoc =
                    DocFinder.search(new DocFinder.Input((MethodDoc) holder, this));
            if (inheritedDoc.holder != null) {
                tags = inheritedDoc.holder.seeTags();
            }
        }
        return writer.seeTagOutput(holder, tags);
    }
}
