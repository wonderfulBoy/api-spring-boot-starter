package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.MethodDoc;
import com.github.api.sun.tools.doclets.formats.html.markup.*;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.SerializedFormWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.taglets.TagletManager;
import com.github.api.sun.tools.doclets.internal.toolkit.taglets.TagletWriter;

public class HtmlSerialMethodWriter extends MethodWriterImpl implements
        SerializedFormWriter.SerialMethodWriter {
    public HtmlSerialMethodWriter(SubWriterHolderWriter writer,
                                  ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public Content getSerializableMethodsHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        return ul;
    }

    public Content getMethodsContentHeader(boolean isLastContent) {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        if (isLastContent)
            li.addStyle(HtmlStyle.blockListLast);
        else
            li.addStyle(HtmlStyle.blockList);
        return li;
    }

    public Content getSerializableMethods(String heading, Content serializableMethodContent) {
        Content headingContent = new StringContent(heading);
        Content serialHeading = HtmlTree.HEADING(HtmlConstants.SERIALIZED_MEMBER_HEADING,
                headingContent);
        Content li = HtmlTree.LI(HtmlStyle.blockList, serialHeading);
        li.addContent(serializableMethodContent);
        return li;
    }

    public Content getNoCustomizationMsg(String msg) {
        Content noCustomizationMsg = new StringContent(msg);
        return noCustomizationMsg;
    }

    public void addMemberHeader(MethodDoc member, Content methodsContentTree) {
        methodsContentTree.addContent(getHead(member));
        methodsContentTree.addContent(getSignature(member));
    }

    public void addDeprecatedMemberInfo(MethodDoc member, Content methodsContentTree) {
        addDeprecatedInfo(member, methodsContentTree);
    }

    public void addMemberDescription(MethodDoc member, Content methodsContentTree) {
        addComment(member, methodsContentTree);
    }

    public void addMemberTags(MethodDoc member, Content methodsContentTree) {
        Content tagContent = new ContentBuilder();
        TagletManager tagletManager =
                configuration.tagletManager;
        TagletWriter.genTagOuput(tagletManager, member,
                tagletManager.getSerializedFormTaglets(),
                writer.getTagletWriterInstance(false), tagContent);
        Content dlTags = new HtmlTree(HtmlTag.DL);
        dlTags.addContent(tagContent);
        methodsContentTree.addContent(dlTags);
        MethodDoc method = member;
        if (method.name().compareTo("writeExternal") == 0
                && method.tags("serialData").length == 0) {
            serialWarning(member.position(), "doclet.MissingSerialDataTag",
                    method.containingClass().qualifiedName(), method.name());
        }
    }
}
