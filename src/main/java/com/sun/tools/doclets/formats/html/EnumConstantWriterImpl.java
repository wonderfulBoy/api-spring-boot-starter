package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MemberDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.EnumConstantWriter;
import com.sun.tools.doclets.internal.toolkit.MemberSummaryWriter;
import com.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

import java.io.IOException;

public class EnumConstantWriterImpl extends AbstractMemberWriter
        implements EnumConstantWriter, MemberSummaryWriter {
    public EnumConstantWriterImpl(SubWriterHolderWriter writer,
                                  ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public EnumConstantWriterImpl(SubWriterHolderWriter writer) {
        super(writer);
    }

    public Content getMemberSummaryHeader(ClassDoc classDoc,
                                          Content memberSummaryTree) {
        memberSummaryTree.addContent(HtmlConstants.START_OF_ENUM_CONSTANT_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, classDoc, memberTree);
        return memberTree;
    }

    public Content getEnumConstantsDetailsTreeHeader(ClassDoc classDoc,
                                                     Content memberDetailsTree) {
        memberDetailsTree.addContent(HtmlConstants.START_OF_ENUM_CONSTANT_DETAILS);
        Content enumConstantsDetailsTree = writer.getMemberTreeHeader();
        enumConstantsDetailsTree.addContent(writer.getMarkerAnchor(
                SectionName.ENUM_CONSTANT_DETAIL));
        Content heading = HtmlTree.HEADING(HtmlConstants.DETAILS_HEADING,
                writer.enumConstantsDetailsLabel);
        enumConstantsDetailsTree.addContent(heading);
        return enumConstantsDetailsTree;
    }

    public Content getEnumConstantsTreeHeader(FieldDoc enumConstant,
                                              Content enumConstantsDetailsTree) {
        enumConstantsDetailsTree.addContent(
                writer.getMarkerAnchor(enumConstant.name()));
        Content enumConstantsTree = writer.getMemberTreeHeader();
        Content heading = new HtmlTree(HtmlConstants.MEMBER_HEADING);
        heading.addContent(enumConstant.name());
        enumConstantsTree.addContent(heading);
        return enumConstantsTree;
    }

    public Content getSignature(FieldDoc enumConstant) {
        Content pre = new HtmlTree(HtmlTag.PRE);
        writer.addAnnotationInfo(enumConstant, pre);
        addModifiers(enumConstant, pre);
        Content enumConstantLink = writer.getLink(new LinkInfoImpl(
                configuration, LinkInfoImpl.Kind.MEMBER, enumConstant.type()));
        pre.addContent(enumConstantLink);
        pre.addContent(" ");
        if (configuration.linksource) {
            Content enumConstantName = new StringContent(enumConstant.name());
            writer.addSrcLink(enumConstant, enumConstantName, pre);
        } else {
            addName(enumConstant.name(), pre);
        }
        return pre;
    }

    public void addDeprecated(FieldDoc enumConstant, Content enumConstantsTree) {
        addDeprecatedInfo(enumConstant, enumConstantsTree);
    }

    public void addComments(FieldDoc enumConstant, Content enumConstantsTree) {
        addComment(enumConstant, enumConstantsTree);
    }

    public void addTags(FieldDoc enumConstant, Content enumConstantsTree) {
        writer.addTagsInfo(enumConstant, enumConstantsTree);
    }

    public Content getEnumConstantsDetails(Content enumConstantsDetailsTree) {
        return getMemberTree(enumConstantsDetailsTree);
    }

    public Content getEnumConstants(Content enumConstantsTree,
                                    boolean isLastContent) {
        return getMemberTree(enumConstantsTree, isLastContent);
    }

    public void close() throws IOException {
        writer.close();
    }

    public int getMemberKind() {
        return VisibleMemberMap.ENUM_CONSTANTS;
    }

    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING,
                writer.getResource("doclet.Enum_Constant_Summary"));
        memberTree.addContent(label);
    }

    public String getTableSummary() {
        return configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Enum_Constant_Summary"),
                configuration.getText("doclet.enum_constants"));
    }

    public Content getCaption() {
        return configuration.getResource("doclet.Enum_Constants");
    }

    public String[] getSummaryTableHeader(ProgramElementDoc member) {
        String[] header = new String[]{
                configuration.getText("doclet.0_and_1",
                        configuration.getText("doclet.Enum_Constant"),
                        configuration.getText("doclet.Description"))
        };
        return header;
    }

    public void addSummaryAnchor(ClassDoc cd, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                SectionName.ENUM_CONSTANT_SUMMARY));
    }

    public void addInheritedSummaryAnchor(ClassDoc cd, Content inheritedTree) {
    }

    public void addInheritedSummaryLabel(ClassDoc cd, Content inheritedTree) {
    }

    protected void addSummaryLink(LinkInfoImpl.Kind context, ClassDoc cd, ProgramElementDoc member,
                                  Content tdSummary) {
        Content memberLink = HtmlTree.SPAN(HtmlStyle.memberNameLink,
                writer.getDocLink(context, (MemberDoc) member, member.name(), false));
        Content code = HtmlTree.CODE(memberLink);
        tdSummary.addContent(code);
    }

    @Override
    public void setSummaryColumnStyle(HtmlTree tdTree) {
        tdTree.addStyle(HtmlStyle.colOne);
    }

    protected void addInheritedSummaryLink(ClassDoc cd,
                                           ProgramElementDoc member, Content linksTree) {
    }

    protected void addSummaryType(ProgramElementDoc member, Content tdSummaryType) {

    }

    protected Content getDeprecatedLink(ProgramElementDoc member) {
        return writer.getDocLink(LinkInfoImpl.Kind.MEMBER,
                (MemberDoc) member, member.qualifiedName());
    }

    protected Content getNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            if (cd == null) {
                return writer.getHyperLink(SectionName.ENUM_CONSTANT_SUMMARY,
                        writer.getResource("doclet.navEnum"));
            } else {
                return writer.getHyperLink(
                        SectionName.ENUM_CONSTANTS_INHERITANCE,
                        configuration.getClassName(cd), writer.getResource("doclet.navEnum"));
            }
        } else {
            return writer.getResource("doclet.navEnum");
        }
    }

    protected void addNavDetailLink(boolean link, Content liNav) {
        if (link) {
            liNav.addContent(writer.getHyperLink(
                    SectionName.ENUM_CONSTANT_DETAIL,
                    writer.getResource("doclet.navEnum")));
        } else {
            liNav.addContent(writer.getResource("doclet.navEnum"));
        }
    }
}
