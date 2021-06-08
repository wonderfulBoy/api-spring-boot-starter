package com.sun.tools.doclets.internal.toolkit.taglets;

import com.sun.javadoc.Doc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Tag;
import com.sun.javadoc.Type;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocFinder;

public class ReturnTaglet extends BaseExecutableMemberTaglet
        implements InheritableTaglet {
    public ReturnTaglet() {
        name = "return";
    }

    public void inherit(DocFinder.Input input, DocFinder.Output output) {
        Tag[] tags = input.element.tags("return");
        if (tags.length > 0) {
            output.holder = input.element;
            output.holderTag = tags[0];
            output.inlineTags = input.isFirstSentence ?
                    tags[0].firstSentenceTags() : tags[0].inlineTags();
        }
    }

    public boolean inConstructor() {
        return false;
    }

    public Content getTagletOutput(Doc holder, TagletWriter writer) {
        Type returnType = ((MethodDoc) holder).returnType();
        Tag[] tags = holder.tags(name);

        if (returnType.isPrimitive() && returnType.typeName().equals("void")) {
            if (tags.length > 0) {
                writer.getMsgRetriever().warning(holder.position(),
                        "doclet.Return_tag_on_void_method");
            }
            return null;
        }

        if (tags.length == 0) {
            DocFinder.Output inheritedDoc =
                    DocFinder.search(new DocFinder.Input((MethodDoc) holder, this));
            tags = inheritedDoc.holderTag == null ? tags : new Tag[]{inheritedDoc.holderTag};
        }
        return tags.length > 0 ? writer.returnTagOutput(tags[0]) : null;
    }
}
