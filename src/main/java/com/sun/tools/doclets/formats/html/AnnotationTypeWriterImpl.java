package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.AnnotationTypeDoc;
import com.sun.javadoc.Tag;
import com.sun.javadoc.Type;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.AnnotationTypeWriter;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.builders.MemberSummaryBuilder;
import com.sun.tools.doclets.internal.toolkit.util.*;

import java.io.IOException;

public class AnnotationTypeWriterImpl extends SubWriterHolderWriter
        implements AnnotationTypeWriter {
    protected AnnotationTypeDoc annotationType;
    protected Type prev;
    protected Type next;

    public AnnotationTypeWriterImpl(ConfigurationImpl configuration,
                                    AnnotationTypeDoc annotationType, Type prevType, Type nextType)
            throws Exception {
        super(configuration, DocPath.forClass(annotationType));
        this.annotationType = annotationType;
        configuration.currentcd = annotationType.asClassDoc();
        this.prev = prevType;
        this.next = nextType;
    }

    protected Content getNavLinkPackage() {
        Content linkContent = getHyperLink(DocPaths.PACKAGE_SUMMARY,
                packageLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    protected Content getNavLinkClass() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, classLabel);
        return li;
    }

    protected Content getNavLinkClassUse() {
        Content linkContent = getHyperLink(DocPaths.CLASS_USE.resolve(filename), useLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    public Content getNavLinkPrevious() {
        Content li;
        if (prev != null) {
            Content prevLink = getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS, prev.asClassDoc())
                    .label(prevclassLabel).strong(true));
            li = HtmlTree.LI(prevLink);
        } else
            li = HtmlTree.LI(prevclassLabel);
        return li;
    }

    public Content getNavLinkNext() {
        Content li;
        if (next != null) {
            Content nextLink = getLink(new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.CLASS, next.asClassDoc())
                    .label(nextclassLabel).strong(true));
            li = HtmlTree.LI(nextLink);
        } else
            li = HtmlTree.LI(nextclassLabel);
        return li;
    }

    public Content getHeader(String header) {
        String pkgname = (annotationType.containingPackage() != null) ?
                annotationType.containingPackage().name() : "";
        String clname = annotationType.name();
        Content bodyTree = getBody(true, getWindowTitle(clname));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        bodyTree.addContent(HtmlConstants.START_OF_CLASS_DATA);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.header);
        if (pkgname.length() > 0) {
            Content pkgNameContent = new StringContent(pkgname);
            Content pkgNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, pkgNameContent);
            div.addContent(pkgNameDiv);
        }
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_HEADER, annotationType);
        Content headerContent = new StringContent(header);
        Content heading = HtmlTree.HEADING(HtmlConstants.CLASS_PAGE_HEADING, true,
                HtmlStyle.title, headerContent);
        heading.addContent(getTypeParameterLinks(linkInfo));
        div.addContent(heading);
        bodyTree.addContent(div);
        return bodyTree;
    }

    public Content getAnnotationContentHeader() {
        return getContentHeader();
    }

    public void addFooter(Content contentTree) {
        contentTree.addContent(HtmlConstants.END_OF_CLASS_DATA);
        addNavLinks(false, contentTree);
        addBottom(contentTree);
    }

    public void printDocument(Content contentTree) throws IOException {
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(annotationType),
                true, contentTree);
    }

    public Content getAnnotationInfoTreeHeader() {
        return getMemberTreeHeader();
    }

    public Content getAnnotationInfo(Content annotationInfoTree) {
        return getMemberTree(HtmlStyle.description, annotationInfoTree);
    }

    public void addAnnotationTypeSignature(String modifiers, Content annotationInfoTree) {
        annotationInfoTree.addContent(new HtmlTree(HtmlTag.BR));
        Content pre = new HtmlTree(HtmlTag.PRE);
        addAnnotationInfo(annotationType, pre);
        pre.addContent(modifiers);
        LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                LinkInfoImpl.Kind.CLASS_SIGNATURE, annotationType);
        Content annotationName = new StringContent(annotationType.name());
        Content parameterLinks = getTypeParameterLinks(linkInfo);
        if (configuration.linksource) {
            addSrcLink(annotationType, annotationName, pre);
            pre.addContent(parameterLinks);
        } else {
            Content span = HtmlTree.SPAN(HtmlStyle.memberNameLabel, annotationName);
            span.addContent(parameterLinks);
            pre.addContent(span);
        }
        annotationInfoTree.addContent(pre);
    }

    public void addAnnotationTypeDescription(Content annotationInfoTree) {
        if (!configuration.nocomment) {
            if (annotationType.inlineTags().length > 0) {
                addInlineComment(annotationType, annotationInfoTree);
            }
        }
    }

    public void addAnnotationTypeTagInfo(Content annotationInfoTree) {
        if (!configuration.nocomment) {
            addTagsInfo(annotationType, annotationInfoTree);
        }
    }

    public void addAnnotationTypeDeprecationInfo(Content annotationInfoTree) {
        Content hr = new HtmlTree(HtmlTag.HR);
        annotationInfoTree.addContent(hr);
        Tag[] deprs = annotationType.tags("deprecated");
        if (Util.isDeprecated(annotationType)) {
            Content deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
            Content div = HtmlTree.DIV(HtmlStyle.block, deprLabel);
            if (deprs.length > 0) {
                Tag[] commentTags = deprs[0].inlineTags();
                if (commentTags.length > 0) {
                    div.addContent(getSpace());
                    addInlineDeprecatedComment(annotationType, deprs[0], div);
                }
            }
            annotationInfoTree.addContent(div);
        }
    }

    protected Content getNavLinkTree() {
        Content treeLinkContent = getHyperLink(DocPaths.PACKAGE_TREE,
                treeLabel, "", "");
        Content li = HtmlTree.LI(treeLinkContent);
        return li;
    }

    protected void addSummaryDetailLinks(Content subDiv) {
        try {
            Content div = HtmlTree.DIV(getNavSummaryLinks());
            div.addContent(getNavDetailLinks());
            subDiv.addContent(div);
        } catch (Exception e) {
            e.printStackTrace();
            throw new DocletAbortException(e);
        }
    }

    protected Content getNavSummaryLinks() throws Exception {
        Content li = HtmlTree.LI(summaryLabel);
        li.addContent(getSpace());
        Content ulNav = HtmlTree.UL(HtmlStyle.subNavList, li);
        MemberSummaryBuilder memberSummaryBuilder = (MemberSummaryBuilder)
                configuration.getBuilderFactory().getMemberSummaryBuilder(this);
        Content liNavField = new HtmlTree(HtmlTag.LI);
        addNavSummaryLink(memberSummaryBuilder,
                "doclet.navField",
                VisibleMemberMap.ANNOTATION_TYPE_FIELDS, liNavField);
        addNavGap(liNavField);
        ulNav.addContent(liNavField);
        Content liNavReq = new HtmlTree(HtmlTag.LI);
        addNavSummaryLink(memberSummaryBuilder,
                "doclet.navAnnotationTypeRequiredMember",
                VisibleMemberMap.ANNOTATION_TYPE_MEMBER_REQUIRED, liNavReq);
        addNavGap(liNavReq);
        ulNav.addContent(liNavReq);
        Content liNavOpt = new HtmlTree(HtmlTag.LI);
        addNavSummaryLink(memberSummaryBuilder,
                "doclet.navAnnotationTypeOptionalMember",
                VisibleMemberMap.ANNOTATION_TYPE_MEMBER_OPTIONAL, liNavOpt);
        ulNav.addContent(liNavOpt);
        return ulNav;
    }

    protected void addNavSummaryLink(MemberSummaryBuilder builder,
                                     String label, int type, Content liNav) {
        AbstractMemberWriter writer = ((AbstractMemberWriter) builder.
                getMemberSummaryWriter(type));
        if (writer == null) {
            liNav.addContent(getResource(label));
        } else {
            liNav.addContent(writer.getNavSummaryLink(null,
                    !builder.getVisibleMemberMap(type).noVisibleMembers()));
        }
    }

    protected Content getNavDetailLinks() throws Exception {
        Content li = HtmlTree.LI(detailLabel);
        li.addContent(getSpace());
        Content ulNav = HtmlTree.UL(HtmlStyle.subNavList, li);
        MemberSummaryBuilder memberSummaryBuilder = (MemberSummaryBuilder)
                configuration.getBuilderFactory().getMemberSummaryBuilder(this);
        AbstractMemberWriter writerField =
                ((AbstractMemberWriter) memberSummaryBuilder.
                        getMemberSummaryWriter(VisibleMemberMap.ANNOTATION_TYPE_FIELDS));
        AbstractMemberWriter writerOptional =
                ((AbstractMemberWriter) memberSummaryBuilder.
                        getMemberSummaryWriter(VisibleMemberMap.ANNOTATION_TYPE_MEMBER_OPTIONAL));
        AbstractMemberWriter writerRequired =
                ((AbstractMemberWriter) memberSummaryBuilder.
                        getMemberSummaryWriter(VisibleMemberMap.ANNOTATION_TYPE_MEMBER_REQUIRED));
        Content liNavField = new HtmlTree(HtmlTag.LI);
        if (writerField != null) {
            writerField.addNavDetailLink(annotationType.fields().length > 0, liNavField);
        } else {
            liNavField.addContent(getResource("doclet.navField"));
        }
        addNavGap(liNavField);
        ulNav.addContent(liNavField);
        if (writerOptional != null) {
            Content liNavOpt = new HtmlTree(HtmlTag.LI);
            writerOptional.addNavDetailLink(annotationType.elements().length > 0, liNavOpt);
            ulNav.addContent(liNavOpt);
        } else if (writerRequired != null) {
            Content liNavReq = new HtmlTree(HtmlTag.LI);
            writerRequired.addNavDetailLink(annotationType.elements().length > 0, liNavReq);
            ulNav.addContent(liNavReq);
        } else {
            Content liNav = HtmlTree.LI(getResource("doclet.navAnnotationTypeMember"));
            ulNav.addContent(liNav);
        }
        return ulNav;
    }

    protected void addNavGap(Content liNav) {
        liNav.addContent(getSpace());
        liNav.addContent("|");
        liNav.addContent(getSpace());
    }

    public AnnotationTypeDoc getAnnotationTypeDoc() {
        return annotationType;
    }
}
