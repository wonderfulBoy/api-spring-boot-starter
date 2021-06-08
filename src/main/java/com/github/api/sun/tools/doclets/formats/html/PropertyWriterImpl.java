package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.MemberDoc;
import com.github.api.sun.javadoc.MethodDoc;
import com.github.api.sun.javadoc.ProgramElementDoc;
import com.github.api.sun.tools.doclets.formats.html.markup.*;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.MemberSummaryWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.PropertyWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.util.Util;
import com.github.api.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

import java.io.IOException;

public class PropertyWriterImpl extends AbstractMemberWriter
        implements PropertyWriter, MemberSummaryWriter {
    public PropertyWriterImpl(SubWriterHolderWriter writer, ClassDoc classdoc) {
        super(writer, classdoc);
    }

    public Content getMemberSummaryHeader(ClassDoc classDoc,
                                          Content memberSummaryTree) {
        memberSummaryTree.addContent(HtmlConstants.START_OF_PROPERTY_SUMMARY);
        Content memberTree = writer.getMemberTreeHeader();
        writer.addSummaryHeader(this, classDoc, memberTree);
        return memberTree;
    }

    public Content getPropertyDetailsTreeHeader(ClassDoc classDoc,
                                                Content memberDetailsTree) {
        memberDetailsTree.addContent(HtmlConstants.START_OF_PROPERTY_DETAILS);
        Content propertyDetailsTree = writer.getMemberTreeHeader();
        propertyDetailsTree.addContent(writer.getMarkerAnchor(
                SectionName.PROPERTY_DETAIL));
        Content heading = HtmlTree.HEADING(HtmlConstants.DETAILS_HEADING,
                writer.propertyDetailsLabel);
        propertyDetailsTree.addContent(heading);
        return propertyDetailsTree;
    }

    public Content getPropertyDocTreeHeader(MethodDoc property,
                                            Content propertyDetailsTree) {
        propertyDetailsTree.addContent(
                writer.getMarkerAnchor(property.name()));
        Content propertyDocTree = writer.getMemberTreeHeader();
        Content heading = new HtmlTree(HtmlConstants.MEMBER_HEADING);
        heading.addContent(property.name().substring(0, property.name().lastIndexOf("Property")));
        propertyDocTree.addContent(heading);
        return propertyDocTree;
    }

    public Content getSignature(MethodDoc property) {
        Content pre = new HtmlTree(HtmlTag.PRE);
        writer.addAnnotationInfo(property, pre);
        addModifiers(property, pre);
        Content propertylink = writer.getLink(new LinkInfoImpl(
                configuration, LinkInfoImpl.Kind.MEMBER,
                property.returnType()));
        pre.addContent(propertylink);
        pre.addContent(" ");
        if (configuration.linksource) {
            Content propertyName = new StringContent(property.name());
            writer.addSrcLink(property, propertyName, pre);
        } else {
            addName(property.name(), pre);
        }
        return pre;
    }

    public void addDeprecated(MethodDoc property, Content propertyDocTree) {
    }

    public void addComments(MethodDoc property, Content propertyDocTree) {
        ClassDoc holder = property.containingClass();
        if (property.inlineTags().length > 0) {
            if (holder.equals(classdoc) ||
                    (!(holder.isPublic() || Util.isLinkable(holder, configuration)))) {
                writer.addInlineComment(property, propertyDocTree);
            } else {
                Content link =
                        writer.getDocLink(LinkInfoImpl.Kind.PROPERTY_DOC_COPY,
                                holder, property,
                                holder.isIncluded() ?
                                        holder.typeName() : holder.qualifiedTypeName(),
                                false);
                Content codeLink = HtmlTree.CODE(link);
                Content descfrmLabel = HtmlTree.SPAN(HtmlStyle.descfrmTypeLabel, holder.isClass() ?
                        writer.descfrmClassLabel : writer.descfrmInterfaceLabel);
                descfrmLabel.addContent(writer.getSpace());
                descfrmLabel.addContent(codeLink);
                propertyDocTree.addContent(HtmlTree.DIV(HtmlStyle.block, descfrmLabel));
                writer.addInlineComment(property, propertyDocTree);
            }
        }
    }

    public void addTags(MethodDoc property, Content propertyDocTree) {
        writer.addTagsInfo(property, propertyDocTree);
    }

    public Content getPropertyDetails(Content propertyDetailsTree) {
        return getMemberTree(propertyDetailsTree);
    }

    public Content getPropertyDoc(Content propertyDocTree,
                                  boolean isLastContent) {
        return getMemberTree(propertyDocTree, isLastContent);
    }

    public void close() throws IOException {
        writer.close();
    }

    public int getMemberKind() {
        return VisibleMemberMap.PROPERTIES;
    }

    public void addSummaryLabel(Content memberTree) {
        Content label = HtmlTree.HEADING(HtmlConstants.SUMMARY_HEADING,
                writer.getResource("doclet.Property_Summary"));
        memberTree.addContent(label);
    }

    public String getTableSummary() {
        return configuration.getText("doclet.Member_Table_Summary",
                configuration.getText("doclet.Property_Summary"),
                configuration.getText("doclet.properties"));
    }

    public Content getCaption() {
        return configuration.getResource("doclet.Properties");
    }

    public String[] getSummaryTableHeader(ProgramElementDoc member) {
        String[] header = new String[]{
                configuration.getText("doclet.Type"),
                configuration.getText("doclet.0_and_1",
                        configuration.getText("doclet.Property"),
                        configuration.getText("doclet.Description"))
        };
        return header;
    }

    public void addSummaryAnchor(ClassDoc cd, Content memberTree) {
        memberTree.addContent(writer.getMarkerAnchor(
                SectionName.PROPERTY_SUMMARY));
    }

    public void addInheritedSummaryAnchor(ClassDoc cd, Content inheritedTree) {
        inheritedTree.addContent(writer.getMarkerAnchor(
                SectionName.PROPERTIES_INHERITANCE,
                configuration.getClassName(cd)));
    }

    public void addInheritedSummaryLabel(ClassDoc cd, Content inheritedTree) {
        Content classLink = writer.getPreQualifiedClassLink(
                LinkInfoImpl.Kind.MEMBER, cd, false);
        Content label = new StringContent(cd.isClass() ?
                configuration.getText("doclet.Properties_Inherited_From_Class") :
                configuration.getText("doclet.Properties_Inherited_From_Interface"));
        Content labelHeading = HtmlTree.HEADING(HtmlConstants.INHERITED_SUMMARY_HEADING,
                label);
        labelHeading.addContent(writer.getSpace());
        labelHeading.addContent(classLink);
        inheritedTree.addContent(labelHeading);
    }

    protected void addSummaryLink(LinkInfoImpl.Kind context, ClassDoc cd, ProgramElementDoc member,
                                  Content tdSummary) {
        Content memberLink = HtmlTree.SPAN(HtmlStyle.memberNameLink,
                writer.getDocLink(context, cd,
                        (MemberDoc) member,
                        member.name().substring(0, member.name().lastIndexOf("Property")),
                        false,
                        true));
        Content code = HtmlTree.CODE(memberLink);
        tdSummary.addContent(code);
    }

    protected void addInheritedSummaryLink(ClassDoc cd,
                                           ProgramElementDoc member, Content linksTree) {
        linksTree.addContent(
                writer.getDocLink(LinkInfoImpl.Kind.MEMBER, cd, (MemberDoc) member,
                        ((member.name().lastIndexOf("Property") != -1) && configuration.javafx)
                                ? member.name().substring(0, member.name().length() - "Property".length())
                                : member.name(),
                        false, true));
    }

    protected void addSummaryType(ProgramElementDoc member, Content tdSummaryType) {
        MethodDoc property = (MethodDoc) member;
        addModifierAndType(property, property.returnType(), tdSummaryType);
    }

    protected Content getDeprecatedLink(ProgramElementDoc member) {
        return writer.getDocLink(LinkInfoImpl.Kind.MEMBER,
                (MemberDoc) member, member.qualifiedName());
    }

    protected Content getNavSummaryLink(ClassDoc cd, boolean link) {
        if (link) {
            if (cd == null) {
                return writer.getHyperLink(
                        SectionName.PROPERTY_SUMMARY,
                        writer.getResource("doclet.navProperty"));
            } else {
                return writer.getHyperLink(
                        SectionName.PROPERTIES_INHERITANCE,
                        configuration.getClassName(cd), writer.getResource("doclet.navProperty"));
            }
        } else {
            return writer.getResource("doclet.navProperty");
        }
    }

    protected void addNavDetailLink(boolean link, Content liNav) {
        if (link) {
            liNav.addContent(writer.getHyperLink(
                    SectionName.PROPERTY_DETAIL,
                    writer.getResource("doclet.navProperty")));
        } else {
            liNav.addContent(writer.getResource("doclet.navProperty"));
        }
    }
}
