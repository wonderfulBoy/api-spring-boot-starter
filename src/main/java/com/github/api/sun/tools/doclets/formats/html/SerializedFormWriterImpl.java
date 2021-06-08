package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.tools.doclets.formats.html.markup.*;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.SerializedFormWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPaths;

import java.io.IOException;

public class SerializedFormWriterImpl extends SubWriterHolderWriter
        implements SerializedFormWriter {

    public SerializedFormWriterImpl(ConfigurationImpl configuration)
            throws IOException {
        super(configuration, DocPaths.SERIALIZED_FORM);
    }

    public Content getHeader(String header) {
        Content bodyTree = getBody(true, getWindowTitle(header));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        Content h1Content = new StringContent(header);
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, h1Content);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        bodyTree.addContent(div);
        return bodyTree;
    }

    public Content getSerializedSummariesHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        return ul;
    }

    public Content getPackageSerializedHeader() {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        li.addStyle(HtmlStyle.blockList);
        return li;
    }

    public Content getPackageHeader(String packageName) {
        Content heading = HtmlTree.HEADING(HtmlConstants.PACKAGE_HEADING, true,
                packageLabel);
        heading.addContent(getSpace());
        heading.addContent(packageName);
        return heading;
    }

    public Content getClassSerializedHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        return ul;
    }

    public Content getClassHeader(ClassDoc classDoc) {
        Content classLink = (classDoc.isPublic() || classDoc.isProtected()) ?
                getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.DEFAULT, classDoc)
                        .label(configuration.getClassName(classDoc))) :
                new StringContent(classDoc.qualifiedName());
        Content li = HtmlTree.LI(HtmlStyle.blockList, getMarkerAnchor(
                classDoc.qualifiedName()));
        Content superClassLink =
                classDoc.superclassType() != null ?
                        getLink(new LinkInfoImpl(configuration,
                                LinkInfoImpl.Kind.SERIALIZED_FORM,
                                classDoc.superclassType())) :
                        null;

        Content className = superClassLink == null ?
                configuration.getResource(
                        "doclet.Class_0_implements_serializable", classLink) :
                configuration.getResource(
                        "doclet.Class_0_extends_implements_serializable", classLink,
                        superClassLink);
        li.addContent(HtmlTree.HEADING(HtmlConstants.SERIALIZED_MEMBER_HEADING,
                className));
        return li;
    }

    public Content getSerialUIDInfoHeader() {
        HtmlTree dl = new HtmlTree(HtmlTag.DL);
        dl.addStyle(HtmlStyle.nameValue);
        return dl;
    }

    public void addSerialUIDInfo(String header, String serialUID,
                                 Content serialUidTree) {
        Content headerContent = new StringContent(header);
        serialUidTree.addContent(HtmlTree.DT(headerContent));
        Content serialContent = new StringContent(serialUID);
        serialUidTree.addContent(HtmlTree.DD(serialContent));
    }

    public Content getClassContentHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        return ul;
    }

    public Content getSerializedContent(Content serializedTreeContent) {
        Content divContent = HtmlTree.DIV(HtmlStyle.serializedFormContainer,
                serializedTreeContent);
        return divContent;
    }

    public void addFooter(Content serializedTree) {
        addNavLinks(false, serializedTree);
        addBottom(serializedTree);
    }

    public void printDocument(Content serializedTree) throws IOException {
        printHtmlDocument(null, true, serializedTree);
    }

    public SerialFieldWriter getSerialFieldWriter(ClassDoc classDoc) {
        return new HtmlSerialFieldWriter(this, classDoc);
    }

    public SerialMethodWriter getSerialMethodWriter(ClassDoc classDoc) {
        return new HtmlSerialMethodWriter(this, classDoc);
    }
}
