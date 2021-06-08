package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.*;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.AnnotationTypeFieldWriter;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.MemberSummaryWriter;

import java.io.IOException;

public class AnnotationTypeFieldWriterImpl extends AbstractMemberWriter
        implements AnnotationTypeFieldWriter, MemberSummaryWriter {

    public AnnotationTypeFieldWriterImpl(SubWriterHolderWriter writer,
                                         AnnotationTypeDoc annotationType) {
        super(writer, annotationType);
    }

    public Content getMemberSummaryHeader(ClassDoc classDoc,
                                          Content memberSummaryTree) {
        memberSummaryTree.addContent(
                HtmlConstants.START_OF_ANNOTATION_TYPE_FIELD_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, classDoc, memberTree);
        return memberTree;
    }

    public Content getMemberTreeHeader() {
        return writer.getMemberTreeHeader();
    }

    public void addAnnotationFieldDetailsMarker(Content memberDetails) {
        memberDetails.addContent(HtmlConstants.START_OF_ANNOTATION_TYPE_FIELD_DETAILS);
    }

    public void addAnnotationDetailsTreeHeader(ClassDoc classDoc,
                                               Content memberDetailsTree) {
        if (!writer.printedAnnotationFieldHeading) {
            memberDetailsTree.addContent(writer.getMarkerAnchor(
                    SectionName.ANNOTATION_TYPE_FIELD_DETAIL));
            Content heading = HtmlTree.HEADING(HtmlConstants.DETAILS_HEADING,
                    writer.fieldDetailsLabel);
            memberDetailsTree.addContent(heading);
            writer.printedAnnotationFieldHeading = true;
        }
    }

    public Content getAnnotationDocTreeHeader(MemberDoc member,
                                              Content annotationDetailsTree) {
        annotationDetailsTree.addContent(
                writer.getMarkerAnchor(member.name()));
        Content annotationDocTree = writer.getMemberTreeHeader();
        Content heading = new HtmlTree(HtmlConstants.MEMBER_HEADING);
        heading.addContent(member.name());
        annotationDocTree.addContent(heading);
        return annotationDocTree;
    }

    public Content getSignature(MemberDoc member) {
        Content pre = new HtmlTree(HtmlTag.PRE);
        writer.addAnnotationInfo(member, pre);
        addModifiers(member, pre);
        Content link =
                writer.getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.MEMBER, getType(member)));
        pre.addContent(link);
        pre.addContent(writer.getSpace());
        if (configuration.linksource) {
            Content memberName = new StringContent(member.name());
            writer.addSrcLink(member, memberName, pre);
        } else {
            addName(member.name(), pre);
        }
        return pre;
    }

    public void addDeprecated(MemberDoc member, Content annotationDocTree) {
        addDeprecatedInfo(member, annotationDocTree);
    }

    public void addComments(MemberDoc member, Content annotationDocTree) {
        addComment(member, annotationDocTree);
    }

    public void addTags(MemberDoc member, Content annotationDocTree) {
        writer.addTagsInfo(member, annotationDocTree);
    }

    public Content getAnnotationDetails(Content annotationDetailsTree) {
        return getMemberTree(annotationDetailsTree);
    }

    public Content getAnnotationDoc(Content annotationDocTree,
                                    boolean isLastContent) {
        return getMemberTree(annotationDocTree, isLastContent);
    }

    public void close() throws IOException {
        writer.close();
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
                        configuration.getText("doclet.Fields"),
                        configuration.getText("doclet.Description"))
        };
        return header;
    }

    public void addSummaryAnchor(ClassDoc cd, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                SectionName.ANNOTATION_TYPE_FIELD_SUMMARY));
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

    protected void addInheritedSummaryLink(ClassDoc cd,
                                           ProgramElementDoc member, Content linksTree) {

    }

    protected void addSummaryType(ProgramElementDoc member, Content tdSummaryType) {
        MemberDoc m = (MemberDoc) member;
        addModifierAndType(m, getType(m), tdSummaryType);
    }

    protected Content getDeprecatedLink(ProgramElementDoc member) {
        return writer.getDocLink(LinkInfoImpl.Kind.MEMBER,
                (MemberDoc) member, member.qualifiedName());
    }

    protected Content getNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            return writer.getHyperLink(
                    SectionName.ANNOTATION_TYPE_FIELD_SUMMARY,
                    writer.getResource("doclet.navField"));
        } else {
            return writer.getResource("doclet.navField");
        }
    }

    protected void addNavDetailLink(boolean link, Content liNav) {
        if (link) {
            liNav.addContent(writer.getHyperLink(
                    SectionName.ANNOTATION_TYPE_FIELD_DETAIL,
                    writer.getResource("doclet.navField")));
        } else {
            liNav.addContent(writer.getResource("doclet.navField"));
        }
    }

    private Type getType(MemberDoc member) {
        if (member instanceof FieldDoc) {
            return ((FieldDoc) member).type();
        } else {
            return ((MethodDoc) member).returnType();
        }
    }
}
