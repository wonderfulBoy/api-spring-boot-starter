package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.Configuration;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.builders.SerializedFormBuilder;
import com.sun.tools.doclets.internal.toolkit.taglets.TagletWriter;
import com.sun.tools.doclets.internal.toolkit.util.*;

public class TagletWriterImpl extends TagletWriter {
    private final HtmlDocletWriter htmlWriter;
    private final ConfigurationImpl configuration;

    public TagletWriterImpl(HtmlDocletWriter htmlWriter, boolean isFirstSentence) {
        super(isFirstSentence);
        this.htmlWriter = htmlWriter;
        configuration = htmlWriter.configuration;
    }

    public Content getOutputInstance() {
        return new ContentBuilder();
    }

    protected Content codeTagOutput(Tag tag) {
        Content result = HtmlTree.CODE(new StringContent(Util.normalizeNewlines(tag.text())));
        return result;
    }

    public Content getDocRootOutput() {
        String path;
        if (htmlWriter.pathToRoot.isEmpty())
            path = ".";
        else
            path = htmlWriter.pathToRoot.getPath();
        return new StringContent(path);
    }

    public Content deprecatedTagOutput(Doc doc) {
        ContentBuilder result = new ContentBuilder();
        Tag[] deprs = doc.tags("deprecated");
        if (doc instanceof ClassDoc) {
            if (Util.isDeprecated(doc)) {
                result.addContent(HtmlTree.SPAN(HtmlStyle.deprecatedLabel,
                        new StringContent(configuration.getText("doclet.Deprecated"))));
                result.addContent(RawHtml.nbsp);
                if (deprs.length > 0) {
                    Tag[] commentTags = deprs[0].inlineTags();
                    if (commentTags.length > 0) {
                        result.addContent(commentTagsToOutput(null, doc,
                                deprs[0].inlineTags(), false)
                        );
                    }
                }
            }
        } else {
            MemberDoc member = (MemberDoc) doc;
            if (Util.isDeprecated(doc)) {
                result.addContent(HtmlTree.SPAN(HtmlStyle.deprecatedLabel,
                        new StringContent(configuration.getText("doclet.Deprecated"))));
                result.addContent(RawHtml.nbsp);
                if (deprs.length > 0) {
                    Content body = commentTagsToOutput(null, doc,
                            deprs[0].inlineTags(), false);
                    if (!body.isEmpty())
                        result.addContent(HtmlTree.SPAN(HtmlStyle.deprecationComment, body));
                }
            } else {
                if (Util.isDeprecated(member.containingClass())) {
                    result.addContent(HtmlTree.SPAN(HtmlStyle.deprecatedLabel,
                            new StringContent(configuration.getText("doclet.Deprecated"))));
                    result.addContent(RawHtml.nbsp);
                }
            }
        }
        return result;
    }

    protected Content literalTagOutput(Tag tag) {
        Content result = new StringContent(Util.normalizeNewlines(tag.text()));
        return result;
    }

    public MessageRetriever getMsgRetriever() {
        return configuration.message;
    }

    public Content getParamHeader(String header) {
        HtmlTree result = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.paramLabel,
                new StringContent(header)));
        return result;
    }

    public Content paramTagOutput(ParamTag paramTag, String paramName) {
        ContentBuilder body = new ContentBuilder();
        body.addContent(HtmlTree.CODE(new RawHtml(paramName)));
        body.addContent(" - ");
        body.addContent(htmlWriter.commentTagsToContent(paramTag, null, paramTag.inlineTags(), false));
        HtmlTree result = HtmlTree.DD(body);
        return result;
    }

    public Content propertyTagOutput(Tag tag, String prefix) {
        Content body = new ContentBuilder();
        body.addContent(new RawHtml(prefix));
        body.addContent(" ");
        body.addContent(HtmlTree.CODE(new RawHtml(tag.text())));
        body.addContent(".");
        Content result = HtmlTree.P(body);
        return result;
    }

    public Content returnTagOutput(Tag returnTag) {
        ContentBuilder result = new ContentBuilder();
        result.addContent(HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.returnLabel,
                new StringContent(configuration.getText("doclet.Returns")))));
        result.addContent(HtmlTree.DD(htmlWriter.commentTagsToContent(
                returnTag, null, returnTag.inlineTags(), false)));
        return result;
    }

    public Content seeTagOutput(Doc holder, SeeTag[] seeTags) {
        ContentBuilder body = new ContentBuilder();
        if (seeTags.length > 0) {
            for (int i = 0; i < seeTags.length; ++i) {
                appendSeparatorIfNotEmpty(body);
                body.addContent(htmlWriter.seeTagToContent(seeTags[i]));
            }
        }
        if (holder.isField() && ((FieldDoc) holder).constantValue() != null &&
                htmlWriter instanceof ClassWriterImpl) {

            appendSeparatorIfNotEmpty(body);
            DocPath constantsPath =
                    htmlWriter.pathToRoot.resolve(DocPaths.CONSTANT_VALUES);
            String whichConstant =
                    ((ClassWriterImpl) htmlWriter).getClassDoc().qualifiedName() + "." + holder.name();
            DocLink link = constantsPath.fragment(whichConstant);
            body.addContent(htmlWriter.getHyperLink(link,
                    new StringContent(configuration.getText("doclet.Constants_Summary"))));
        }
        if (holder.isClass() && ((ClassDoc) holder).isSerializable()) {

            if ((SerializedFormBuilder.serialInclude(holder) &&
                    SerializedFormBuilder.serialInclude(((ClassDoc) holder).containingPackage()))) {
                appendSeparatorIfNotEmpty(body);
                DocPath serialPath = htmlWriter.pathToRoot.resolve(DocPaths.SERIALIZED_FORM);
                DocLink link = serialPath.fragment(((ClassDoc) holder).qualifiedName());
                body.addContent(htmlWriter.getHyperLink(link,
                        new StringContent(configuration.getText("doclet.Serialized_Form"))));
            }
        }
        if (body.isEmpty())
            return body;
        ContentBuilder result = new ContentBuilder();
        result.addContent(HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.seeLabel,
                new StringContent(configuration.getText("doclet.See_Also")))));
        result.addContent(HtmlTree.DD(body));
        return result;
    }

    private void appendSeparatorIfNotEmpty(ContentBuilder body) {
        if (!body.isEmpty()) {
            body.addContent(", ");
            body.addContent(DocletConstants.NL);
        }
    }

    public Content simpleTagOutput(Tag[] simpleTags, String header) {
        ContentBuilder result = new ContentBuilder();
        result.addContent(HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.simpleTagLabel, new RawHtml(header))));
        ContentBuilder body = new ContentBuilder();
        for (int i = 0; i < simpleTags.length; i++) {
            if (i > 0) {
                body.addContent(", ");
            }
            body.addContent(htmlWriter.commentTagsToContent(
                    simpleTags[i], null, simpleTags[i].inlineTags(), false));
        }
        result.addContent(HtmlTree.DD(body));
        return result;
    }

    public Content simpleTagOutput(Tag simpleTag, String header) {
        ContentBuilder result = new ContentBuilder();
        result.addContent(HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.simpleTagLabel, new RawHtml(header))));
        Content body = htmlWriter.commentTagsToContent(
                simpleTag, null, simpleTag.inlineTags(), false);
        result.addContent(HtmlTree.DD(body));
        return result;
    }

    public Content getThrowsHeader() {
        HtmlTree result = HtmlTree.DT(HtmlTree.SPAN(HtmlStyle.throwsLabel,
                new StringContent(configuration.getText("doclet.Throws"))));
        return result;
    }

    public Content throwsTagOutput(ThrowsTag throwsTag) {
        ContentBuilder body = new ContentBuilder();
        Content excName = (throwsTag.exceptionType() == null) ?
                new RawHtml(throwsTag.exceptionName()) :
                htmlWriter.getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.MEMBER,
                        throwsTag.exceptionType()));
        body.addContent(HtmlTree.CODE(excName));
        Content desc = htmlWriter.commentTagsToContent(throwsTag, null,
                throwsTag.inlineTags(), false);
        if (desc != null && !desc.isEmpty()) {
            body.addContent(" - ");
            body.addContent(desc);
        }
        HtmlTree result = HtmlTree.DD(body);
        return result;
    }

    public Content throwsTagOutput(Type throwsType) {
        HtmlTree result = HtmlTree.DD(HtmlTree.CODE(htmlWriter.getLink(
                new LinkInfoImpl(configuration, LinkInfoImpl.Kind.MEMBER, throwsType))));
        return result;
    }

    public Content valueTagOutput(FieldDoc field, String constantVal,
                                  boolean includeLink) {
        return includeLink ?
                htmlWriter.getDocLink(LinkInfoImpl.Kind.VALUE_TAG, field,
                        constantVal, false) : new RawHtml(constantVal);
    }

    public Content commentTagsToOutput(Tag holderTag, Tag[] tags) {
        return commentTagsToOutput(holderTag, null, tags, false);
    }

    public Content commentTagsToOutput(Doc holderDoc, Tag[] tags) {
        return commentTagsToOutput(null, holderDoc, tags, false);
    }

    public Content commentTagsToOutput(Tag holderTag,
                                       Doc holderDoc, Tag[] tags, boolean isFirstSentence) {
        return htmlWriter.commentTagsToContent(
                holderTag, holderDoc, tags, isFirstSentence);
    }

    public Configuration configuration() {
        return configuration;
    }
}
