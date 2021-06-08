package com.github.api.sun.tools.doclets.internal.toolkit.taglets;

import com.github.api.sun.javadoc.*;
import com.github.api.sun.tools.doclets.internal.toolkit.Configuration;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.MessageRetriever;

public abstract class TagletWriter {

    protected final boolean isFirstSentence;

    protected TagletWriter(boolean isFirstSentence) {
        this.isFirstSentence = isFirstSentence;
    }

    public static void genTagOuput(TagletManager tagletManager, Doc doc,
                                   Taglet[] taglets, TagletWriter writer, Content output) {
        tagletManager.checkTags(doc, doc.tags(), false);
        tagletManager.checkTags(doc, doc.inlineTags(), true);
        Content currentOutput = null;
        for (int i = 0; i < taglets.length; i++) {
            currentOutput = null;
            if (doc instanceof ClassDoc && taglets[i] instanceof ParamTaglet) {


                continue;
            }
            if (taglets[i] instanceof DeprecatedTaglet) {


                continue;
            }
            try {
                currentOutput = taglets[i].getTagletOutput(doc, writer);
            } catch (IllegalArgumentException e) {


                Tag[] tags = doc.tags(taglets[i].getName());
                if (tags.length > 0) {
                    currentOutput = taglets[i].getTagletOutput(tags[0], writer);
                }
            }
            if (currentOutput != null) {
                tagletManager.seenCustomTag(taglets[i].getName());
                output.addContent(currentOutput);
            }
        }
    }

    public static Content getInlineTagOuput(TagletManager tagletManager,
                                            Tag holderTag, Tag inlineTag, TagletWriter tagletWriter) {
        Taglet[] definedTags = tagletManager.getInlineCustomTaglets();

        for (int j = 0; j < definedTags.length; j++) {
            if (("@" + definedTags[j].getName()).equals(inlineTag.name())) {


                tagletManager.seenCustomTag(definedTags[j].getName());
                Content output = definedTags[j].getTagletOutput(
                        holderTag != null &&
                                definedTags[j].getName().equals("inheritDoc") ?
                                holderTag : inlineTag, tagletWriter);
                return output;
            }
        }
        return null;
    }

    public abstract Content getOutputInstance();

    protected abstract Content codeTagOutput(Tag tag);

    protected abstract Content getDocRootOutput();

    protected abstract Content deprecatedTagOutput(Doc doc);

    protected abstract Content literalTagOutput(Tag tag);

    protected abstract MessageRetriever getMsgRetriever();

    protected abstract Content getParamHeader(String header);

    protected abstract Content paramTagOutput(ParamTag paramTag,
                                              String paramName);

    protected abstract Content propertyTagOutput(Tag propertyTag, String prefix);

    protected abstract Content returnTagOutput(Tag returnTag);

    protected abstract Content seeTagOutput(Doc holder, SeeTag[] seeTags);

    protected abstract Content simpleTagOutput(Tag[] simpleTags,
                                               String header);

    protected abstract Content simpleTagOutput(Tag simpleTag, String header);

    protected abstract Content getThrowsHeader();

    protected abstract Content throwsTagOutput(ThrowsTag throwsTag);

    protected abstract Content throwsTagOutput(Type throwsType);

    protected abstract Content valueTagOutput(FieldDoc field,
                                              String constantVal, boolean includeLink);

    public abstract Content commentTagsToOutput(Tag holderTag, Tag[] tags);

    public abstract Content commentTagsToOutput(Doc holderDoc, Tag[] tags);

    public abstract Content commentTagsToOutput(Tag holderTag,
                                                Doc holderDoc, Tag[] tags, boolean isFirstSentence);

    public abstract Configuration configuration();
}
