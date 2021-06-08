package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.*;
import com.github.api.sun.tools.doclets.formats.html.markup.HtmlConstants;
import com.github.api.sun.tools.doclets.formats.html.markup.HtmlTree;
import com.github.api.sun.tools.doclets.formats.html.markup.StringContent;
import com.github.api.sun.tools.doclets.internal.toolkit.AnnotationTypeOptionalMemberWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.MemberSummaryWriter;

import java.io.IOException;

public class AnnotationTypeOptionalMemberWriterImpl extends
        AnnotationTypeRequiredMemberWriterImpl
        implements AnnotationTypeOptionalMemberWriter, MemberSummaryWriter {

    public AnnotationTypeOptionalMemberWriterImpl(SubWriterHolderWriter writer,
                                                  AnnotationTypeDoc annotationType) {
        super(writer, annotationType);
    }

    public Content getMemberSummaryHeader(ClassDoc classDoc,
                                          Content memberSummaryTree) {
        memberSummaryTree.addContent(
                HtmlConstants.START_OF_ANNOTATION_TYPE_OPTIONAL_MEMBER_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, classDoc, memberTree);
        return memberTree;
    }

    public void addDefaultValueInfo(MemberDoc member, Content annotationDocTree) {
        if (((AnnotationTypeElementDoc) member).defaultValue() != null) {
            Content dt = HtmlTree.DT(writer.getResource("doclet.Default"));
            Content dl = HtmlTree.DL(dt);
            Content dd = HtmlTree.DD(new StringContent(
                    ((AnnotationTypeElementDoc) member).defaultValue().toString()));
            dl.addContent(dd);
            annotationDocTree.addContent(dl);
        }
    }

    public void close() throws IOException {
        writer.close();
    }

    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING,
                writer.getResource("doclet.Annotation_Type_Optional_Member_Summary"));
        memberTree.addContent(label);
    }

    public String getTableSummary() {
        return configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Annotation_Type_Optional_Member_Summary"),
                configuration.getText("doclet.annotation_type_optional_members"));
    }

    public Content getCaption() {
        return configuration.getResource("doclet.Annotation_Type_Optional_Members");
    }

    public String[] getSummaryTableHeader(ProgramElementDoc member) {
        String[] header = new String[]{
                writer.getModifierTypeHeader(),
                configuration.getText("doclet.0_and_1",
                        configuration.getText("doclet.Annotation_Type_Optional_Member"),
                        configuration.getText("doclet.Description"))
        };
        return header;
    }

    public void addSummaryAnchor(ClassDoc cd, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                SectionName.ANNOTATION_TYPE_OPTIONAL_ELEMENT_SUMMARY));
    }

    protected Content getNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            return writer.getHyperLink(
                    SectionName.ANNOTATION_TYPE_OPTIONAL_ELEMENT_SUMMARY,
                    writer.getResource("doclet.navAnnotationTypeOptionalMember"));
        } else {
            return writer.getResource("doclet.navAnnotationTypeOptionalMember");
        }
    }
}
