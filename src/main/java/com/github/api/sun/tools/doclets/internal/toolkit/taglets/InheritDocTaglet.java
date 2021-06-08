package com.github.api.sun.tools.doclets.internal.toolkit.taglets;

import com.github.api.sun.javadoc.ExecutableMemberDoc;
import com.github.api.sun.javadoc.ProgramElementDoc;
import com.github.api.sun.javadoc.Tag;
import com.github.api.sun.tools.doclets.internal.toolkit.Configuration;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocFinder;

public class InheritDocTaglet extends BaseInlineTaglet {

    public static final String INHERIT_DOC_INLINE_TAG = "{@inheritDoc}";

    public InheritDocTaglet() {
        name = "inheritDoc";
    }

    public boolean inField() {
        return false;
    }

    public boolean inConstructor() {
        return false;
    }

    public boolean inOverview() {
        return false;
    }

    public boolean inPackage() {
        return false;
    }

    public boolean inType() {
        return true;
    }

    private Content retrieveInheritedDocumentation(TagletWriter writer,
                                                   ProgramElementDoc ped, Tag holderTag, boolean isFirstSentence) {
        Content replacement = writer.getOutputInstance();
        Configuration configuration = writer.configuration();
        Taglet inheritableTaglet = holderTag == null ?
                null : configuration.tagletManager.getTaglet(holderTag.name());
        if (inheritableTaglet != null &&
                !(inheritableTaglet instanceof InheritableTaglet)) {
            String message = ped.name() +
                    ((ped instanceof ExecutableMemberDoc)
                            ? ((ExecutableMemberDoc) ped).flatSignature()
                            : "");

            configuration.message.warning(ped.position(),
                    "doclet.noInheritedDoc", message);
        }
        DocFinder.Output inheritedDoc =
                DocFinder.search(new DocFinder.Input(ped,
                        (InheritableTaglet) inheritableTaglet, holderTag,
                        isFirstSentence, true));
        if (inheritedDoc.isValidInheritDocTag) {
            if (inheritedDoc.inlineTags.length > 0) {
                replacement = writer.commentTagsToOutput(inheritedDoc.holderTag,
                        inheritedDoc.holder, inheritedDoc.inlineTags, isFirstSentence);
            }
        } else {
            String message = ped.name() +
                    ((ped instanceof ExecutableMemberDoc)
                            ? ((ExecutableMemberDoc) ped).flatSignature()
                            : "");
            configuration.message.warning(ped.position(),
                    "doclet.noInheritedDoc", message);
        }
        return replacement;
    }

    public Content getTagletOutput(Tag tag, TagletWriter tagletWriter) {
        if (!(tag.holder() instanceof ProgramElementDoc)) {
            return tagletWriter.getOutputInstance();
        }
        return tag.name().equals("@inheritDoc") ?
                retrieveInheritedDocumentation(tagletWriter, (ProgramElementDoc) tag.holder(), null, tagletWriter.isFirstSentence) :
                retrieveInheritedDocumentation(tagletWriter, (ProgramElementDoc) tag.holder(), tag, tagletWriter.isFirstSentence);
    }
}
