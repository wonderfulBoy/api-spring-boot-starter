package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MemberDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.FieldWriter;
import com.sun.tools.doclets.internal.toolkit.MemberSummaryWriter;
import com.sun.tools.doclets.internal.toolkit.util.Util;
import com.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

import java.io.IOException;

public class FieldWriterImpl extends AbstractMemberWriter
        implements FieldWriter, MemberSummaryWriter {
    public FieldWriterImpl(SubWriterHolderWriter writer, ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public FieldWriterImpl(SubWriterHolderWriter writer) {
        super(writer);
    }

    public Content getMemberSummaryHeader(ClassDoc classDoc,
                                          Content memberSummaryTree) {
        memberSummaryTree.addContent(HtmlConstants.START_OF_FIELD_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, classDoc, memberTree);
        return memberTree;
    }

    public Content getFieldDetailsTreeHeader(ClassDoc classDoc,
                                             Content memberDetailsTree) {
        memberDetailsTree.addContent(HtmlConstants.START_OF_FIELD_DETAILS);
        Content fieldDetailsTree = writer.getMemberTreeHeader();
        fieldDetailsTree.addContent(writer.getMarkerAnchor(
                SectionName.FIELD_DETAIL));
        Content heading = HtmlTree.HEADING(HtmlConstants.DETAILS_HEADING,
                writer.fieldDetailsLabel);
        fieldDetailsTree.addContent(heading);
        return fieldDetailsTree;
    }

    public Content getFieldDocTreeHeader(FieldDoc field,
                                         Content fieldDetailsTree) {
        fieldDetailsTree.addContent(
                writer.getMarkerAnchor(field.name()));
        Content fieldDocTree = writer.getMemberTreeHeader();
        Content heading = new HtmlTree(HtmlConstants.MEMBER_HEADING);
        heading.addContent(field.name());
        fieldDocTree.addContent(heading);
        return fieldDocTree;
    }

    public Content getSignature(FieldDoc field) {
        Content pre = new HtmlTree(HtmlTag.PRE);
        writer.addAnnotationInfo(field, pre);
        addModifiers(field, pre);
        Content fieldlink = writer.getLink(new LinkInfoImpl(
                configuration, LinkInfoImpl.Kind.MEMBER, field.type()));
        pre.addContent(fieldlink);
        pre.addContent(" ");
        if (configuration.linksource) {
            Content fieldName = new StringContent(field.name());
            writer.addSrcLink(field, fieldName, pre);
        } else {
            addName(field.name(), pre);
        }
        return pre;
    }

    public void addDeprecated(FieldDoc field, Content fieldDocTree) {
        addDeprecatedInfo(field, fieldDocTree);
    }

    public void addComments(FieldDoc field, Content fieldDocTree) {
        ClassDoc holder = field.containingClass();
        if (field.inlineTags().length > 0) {
            if (holder.equals(classdoc) ||
                    (!(holder.isPublic() || Util.isLinkable(holder, configuration)))) {
                writer.addInlineComment(field, fieldDocTree);
            } else {
                Content link =
                        writer.getDocLink(LinkInfoImpl.Kind.FIELD_DOC_COPY,
                                holder, field,
                                holder.isIncluded() ?
                                        holder.typeName() : holder.qualifiedTypeName(),
                                false);
                Content codeLink = HtmlTree.CODE(link);
                Content descfrmLabel = HtmlTree.SPAN(HtmlStyle.descfrmTypeLabel, holder.isClass() ?
                        writer.descfrmClassLabel : writer.descfrmInterfaceLabel);
                descfrmLabel.addContent(writer.getSpace());
                descfrmLabel.addContent(codeLink);
                fieldDocTree.addContent(HtmlTree.DIV(HtmlStyle.block, descfrmLabel));
                writer.addInlineComment(field, fieldDocTree);
            }
        }
    }

    public void addTags(FieldDoc field, Content fieldDocTree) {
        writer.addTagsInfo(field, fieldDocTree);
    }

    public Content getFieldDetails(Content fieldDetailsTree) {
        return getMemberTree(fieldDetailsTree);
    }

    public Content getFieldDoc(Content fieldDocTree,
                               boolean isLastContent) {
        return getMemberTree(fieldDocTree, isLastContent);
    }

    public void close() throws IOException {
        writer.close();
    }

    public int getMemberKind() {
        return VisibleMemberMap.FIELDS;
    }

    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING,
                writer.getResource("doclet.Field_Summary"));
        memberTree.addContent(label);
    }

    public String getTableSummary() {
        return configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Field_Summary"),
                configuration.getText("doclet.fields"));
    }

    public Content getCaption() {
        return configuration.getResource("doclet.Fields");
    }

    public String[] getSummaryTableHeader(ProgramElementDoc member) {
        String[] header = new String[]{
                writer.getModifierTypeHeader(),
                configuration.getText("doclet.0_and_1",
                        configuration.getText("doclet.Field"),
                        configuration.getText("doclet.Description"))
        };
        return header;
    }

    public void addSummaryAnchor(ClassDoc cd, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                SectionName.FIELD_SUMMARY));
    }

    public void addInheritedSummaryAnchor(ClassDoc cd, Content inheritedTree) {
        inheritedTree.addContent(writer.getMarkerAnchor(
                SectionName.FIELDS_INHERITANCE, configuration.getClassName(cd)));
    }

    public void addInheritedSummaryLabel(ClassDoc cd, Content inheritedTree) {
        Content classLink = writer.getPreQualifiedClassLink(
                LinkInfoImpl.Kind.MEMBER, cd, false);
        Content label = new StringContent(cd.isClass() ?
                configuration.getText("doclet.Fields_Inherited_From_Class") :
                configuration.getText("doclet.Fields_Inherited_From_Interface"));
        Content labelHeading = HtmlTree.HEADING(HtmlConstants.INHERITED_SUMMARY_HEADING,
                label);
        labelHeading.addContent(writer.getSpace());
        labelHeading.addContent(classLink);
        inheritedTree.addContent(labelHeading);
    }

    protected void addSummaryLink(LinkInfoImpl.Kind context, ClassDoc cd, ProgramElementDoc member,
                                  Content tdSummary) {
        Content memberLink = HtmlTree.SPAN(HtmlStyle.memberNameLink,
                writer.getDocLink(context, cd, (MemberDoc) member, member.name(), false));
        Content code = HtmlTree.CODE(memberLink);
        tdSummary.addContent(code);
    }

    protected void addInheritedSummaryLink(ClassDoc cd,
                                           ProgramElementDoc member, Content linksTree) {
        linksTree.addContent(
                writer.getDocLink(LinkInfoImpl.Kind.MEMBER, cd, (MemberDoc) member,
                        member.name(), false));
    }

    protected void addSummaryType(ProgramElementDoc member, Content tdSummaryType) {
        FieldDoc field = (FieldDoc) member;
        addModifierAndType(field, field.type(), tdSummaryType);
    }

    protected Content getDeprecatedLink(ProgramElementDoc member) {
        return writer.getDocLink(LinkInfoImpl.Kind.MEMBER,
                (MemberDoc) member, member.qualifiedName());
    }

    protected Content getNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            if (cd == null) {
                return writer.getHyperLink(
                        SectionName.FIELD_SUMMARY,
                        writer.getResource("doclet.navField"));
            } else {
                return writer.getHyperLink(
                        SectionName.FIELDS_INHERITANCE,
                        configuration.getClassName(cd), writer.getResource("doclet.navField"));
            }
        } else {
            return writer.getResource("doclet.navField");
        }
    }

    protected void addNavDetailLink(boolean link, Content liNav) {
        if (link) {
            liNav.addContent(writer.getHyperLink(
                    SectionName.FIELD_DETAIL,
                    writer.getResource("doclet.navField")));
        } else {
            liNav.addContent(writer.getResource("doclet.navField"));
        }
    }
}
