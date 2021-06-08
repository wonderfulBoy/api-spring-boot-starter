package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.Doc;
import com.github.api.sun.javadoc.ProgramElementDoc;
import com.github.api.sun.javadoc.Tag;
import com.github.api.sun.tools.doclets.formats.html.markup.HtmlStyle;
import com.github.api.sun.tools.doclets.formats.html.markup.HtmlTag;
import com.github.api.sun.tools.doclets.formats.html.markup.HtmlTree;
import com.github.api.sun.tools.doclets.formats.html.markup.StringContent;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.github.api.sun.tools.doclets.internal.toolkit.util.MethodTypes;
import com.github.api.sun.tools.doclets.internal.toolkit.util.Util;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public abstract class SubWriterHolderWriter extends HtmlDocletWriter {
    public SubWriterHolderWriter(ConfigurationImpl configuration, DocPath filename)
            throws IOException {
        super(configuration, filename);
    }

    public void addSummaryHeader(AbstractMemberWriter mw, ClassDoc cd,
                                 Content memberTree) {
        mw.addSummaryAnchor(cd, memberTree);
        mw.addSummaryLabel(memberTree);
    }

    public Content getSummaryTableTree(AbstractMemberWriter mw, ClassDoc cd,
                                       List<Content> tableContents, boolean showTabs) {
        Content caption;
        if (showTabs) {
            caption = getTableCaption(mw.methodTypes);
            generateMethodTypesScript(mw.typeMap, mw.methodTypes);
        } else {
            caption = getTableCaption(mw.getCaption());
        }
        Content table = HtmlTree.TABLE(HtmlStyle.memberSummary, 0, 3, 0,
                mw.getTableSummary(), caption);
        table.addContent(getSummaryTableHeader(mw.getSummaryTableHeader(cd), "col"));
        for (int i = 0; i < tableContents.size(); i++) {
            table.addContent(tableContents.get(i));
        }
        return table;
    }

    public Content getTableCaption(Set<MethodTypes> methodTypes) {
        Content tabbedCaption = new HtmlTree(HtmlTag.CAPTION);
        for (MethodTypes type : methodTypes) {
            Content captionSpan;
            Content span;
            if (type.isDefaultTab()) {
                captionSpan = HtmlTree.SPAN(new StringContent(type.text()));
                span = HtmlTree.SPAN(type.tabId(),
                        HtmlStyle.activeTableTab, captionSpan);
            } else {
                captionSpan = HtmlTree.SPAN(getMethodTypeLinks(type));
                span = HtmlTree.SPAN(type.tabId(),
                        HtmlStyle.tableTab, captionSpan);
            }
            Content tabSpan = HtmlTree.SPAN(HtmlStyle.tabEnd, getSpace());
            span.addContent(tabSpan);
            tabbedCaption.addContent(span);
        }
        return tabbedCaption;
    }

    public Content getMethodTypeLinks(MethodTypes methodType) {
        String jsShow = "javascript:show(" + methodType.value() + ");";
        HtmlTree link = HtmlTree.A(jsShow, new StringContent(methodType.text()));
        return link;
    }

    public void addInheritedSummaryHeader(AbstractMemberWriter mw, ClassDoc cd,
                                          Content inheritedTree) {
        mw.addInheritedSummaryAnchor(cd, inheritedTree);
        mw.addInheritedSummaryLabel(cd, inheritedTree);
    }

    protected void addIndexComment(Doc member, Content contentTree) {
        addIndexComment(member, member.firstSentenceTags(), contentTree);
    }

    protected void addIndexComment(Doc member, Tag[] firstSentenceTags,
                                   Content tdSummary) {
        Tag[] deprs = member.tags("deprecated");
        Content div;
        if (Util.isDeprecated(member)) {
            Content deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
            div = HtmlTree.DIV(HtmlStyle.block, deprLabel);
            div.addContent(getSpace());
            if (deprs.length > 0) {
                addInlineDeprecatedComment(member, deprs[0], div);
            }
            tdSummary.addContent(div);
            return;
        } else {
            ClassDoc cd = ((ProgramElementDoc) member).containingClass();
            if (cd != null && Util.isDeprecated(cd)) {
                Content deprLabel = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
                div = HtmlTree.DIV(HtmlStyle.block, deprLabel);
                div.addContent(getSpace());
                tdSummary.addContent(div);
            }
        }
        addSummaryComment(member, firstSentenceTags, tdSummary);
    }

    public void addSummaryType(AbstractMemberWriter mw, ProgramElementDoc member,
                               Content tdSummaryType) {
        mw.addSummaryType(member, tdSummaryType);
    }

    public void addSummaryLinkComment(AbstractMemberWriter mw,
                                      ProgramElementDoc member, Content contentTree) {
        addSummaryLinkComment(mw, member, member.firstSentenceTags(), contentTree);
    }

    public void addSummaryLinkComment(AbstractMemberWriter mw,
                                      ProgramElementDoc member, Tag[] firstSentenceTags, Content tdSummary) {
        addIndexComment(member, firstSentenceTags, tdSummary);
    }

    public void addInheritedMemberSummary(AbstractMemberWriter mw, ClassDoc cd,
                                          ProgramElementDoc member, boolean isFirst, Content linksTree) {
        if (!isFirst) {
            linksTree.addContent(", ");
        }
        mw.addInheritedSummaryLink(cd, member, linksTree);
    }

    public Content getContentHeader() {
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.contentContainer);
        return div;
    }

    public Content getMemberTreeHeader() {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        li.addStyle(HtmlStyle.blockList);
        return li;
    }

    public Content getMemberTree(Content contentTree) {
        Content ul = HtmlTree.UL(HtmlStyle.blockList, contentTree);
        return ul;
    }

    public Content getMemberSummaryTree(Content contentTree) {
        return getMemberTree(HtmlStyle.summary, contentTree);
    }

    public Content getMemberDetailsTree(Content contentTree) {
        return getMemberTree(HtmlStyle.details, contentTree);
    }

    public Content getMemberTree(HtmlStyle style, Content contentTree) {
        Content div = HtmlTree.DIV(style, getMemberTree(contentTree));
        return div;
    }
}
