package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.ProgramElementDoc;
import com.github.api.sun.tools.doclets.formats.html.markup.HtmlConstants;
import com.github.api.sun.tools.doclets.formats.html.markup.HtmlStyle;
import com.github.api.sun.tools.doclets.formats.html.markup.HtmlTree;
import com.github.api.sun.tools.doclets.formats.html.markup.StringContent;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.MemberSummaryWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

import java.io.IOException;

public class NestedClassWriterImpl extends AbstractMemberWriter
        implements MemberSummaryWriter {
    public NestedClassWriterImpl(SubWriterHolderWriter writer,
                                 ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public NestedClassWriterImpl(SubWriterHolderWriter writer) {
        super(writer);
    }

    public Content getMemberSummaryHeader(ClassDoc classDoc,
                                          Content memberSummaryTree) {
        memberSummaryTree.addContent(HtmlConstants.START_OF_NESTED_CLASS_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, classDoc, memberTree);
        return memberTree;
    }

    public void close() throws IOException {
        writer.close();
    }

    public int getMemberKind() {
        return VisibleMemberMap.INNERCLASSES;
    }

    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING,
                writer.getResource("doclet.Nested_Class_Summary"));
        memberTree.addContent(label);
    }

    public String getTableSummary() {
        return configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Nested_Class_Summary"),
                configuration.getText("doclet.nested_classes"));
    }

    public Content getCaption() {
        return configuration.getResource("doclet.Nested_Classes");
    }

    public String[] getSummaryTableHeader(ProgramElementDoc member) {
        String[] header;
        if (member.isInterface()) {
            header = new String[]{
                    writer.getModifierTypeHeader(),
                    configuration.getText("doclet.0_and_1",
                            configuration.getText("doclet.Interface"),
                            configuration.getText("doclet.Description"))
            };
        } else {
            header = new String[]{
                    writer.getModifierTypeHeader(),
                    configuration.getText("doclet.0_and_1",
                            configuration.getText("doclet.Class"),
                            configuration.getText("doclet.Description"))
            };
        }
        return header;
    }

    public void addSummaryAnchor(ClassDoc cd, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                SectionName.NESTED_CLASS_SUMMARY));
    }

    public void addInheritedSummaryAnchor(ClassDoc cd, Content inheritedTree) {
        inheritedTree.addContent(writer.getMarkerAnchor(
                SectionName.NESTED_CLASSES_INHERITANCE,
                cd.qualifiedName()));
    }

    public void addInheritedSummaryLabel(ClassDoc cd, Content inheritedTree) {
        Content classLink = writer.getPreQualifiedClassLink(
                LinkInfoImpl.Kind.MEMBER, cd, false);
        Content label = new StringContent(cd.isInterface() ?
                configuration.getText("doclet.Nested_Classes_Interface_Inherited_From_Interface") :
                configuration.getText("doclet.Nested_Classes_Interfaces_Inherited_From_Class"));
        Content labelHeading = HtmlTree.HEADING(HtmlConstants.INHERITED_SUMMARY_HEADING,
                label);
        labelHeading.addContent(writer.getSpace());
        labelHeading.addContent(classLink);
        inheritedTree.addContent(labelHeading);
    }

    protected void addSummaryLink(LinkInfoImpl.Kind context, ClassDoc cd, ProgramElementDoc member,
                                  Content tdSummary) {
        Content memberLink = HtmlTree.SPAN(HtmlStyle.memberNameLink,
                writer.getLink(new LinkInfoImpl(configuration, context, (ClassDoc) member)));
        Content code = HtmlTree.CODE(memberLink);
        tdSummary.addContent(code);
    }

    protected void addInheritedSummaryLink(ClassDoc cd,
                                           ProgramElementDoc member, Content linksTree) {
        linksTree.addContent(
                writer.getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.MEMBER,
                        (ClassDoc) member)));
    }

    protected void addSummaryType(ProgramElementDoc member,
                                  Content tdSummaryType) {
        ClassDoc cd = (ClassDoc) member;
        addModifierAndType(cd, null, tdSummaryType);
    }

    protected Content getDeprecatedLink(ProgramElementDoc member) {
        return writer.getQualifiedClassLink(LinkInfoImpl.Kind.MEMBER,
                (ClassDoc) member);
    }

    protected Content getNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            if (cd == null) {
                return writer.getHyperLink(
                        SectionName.NESTED_CLASS_SUMMARY,
                        writer.getResource("doclet.navNested"));
            } else {
                return writer.getHyperLink(
                        SectionName.NESTED_CLASSES_INHERITANCE,
                        cd.qualifiedName(), writer.getResource("doclet.navNested"));
            }
        } else {
            return writer.getResource("doclet.navNested");
        }
    }

    protected void addNavDetailLink(boolean link, Content liNav) {
    }
}
