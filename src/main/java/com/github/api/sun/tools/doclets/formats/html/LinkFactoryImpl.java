package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.*;
import com.github.api.sun.tools.doclets.formats.html.markup.ContentBuilder;
import com.github.api.sun.tools.doclets.internal.toolkit.Configuration;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.github.api.sun.tools.doclets.internal.toolkit.util.Util;
import com.github.api.sun.tools.doclets.internal.toolkit.util.links.LinkFactory;
import com.github.api.sun.tools.doclets.internal.toolkit.util.links.LinkInfo;

import java.util.List;

public class LinkFactoryImpl extends LinkFactory {
    private HtmlDocletWriter m_writer;

    public LinkFactoryImpl(HtmlDocletWriter writer) {
        m_writer = writer;
    }

    protected Content newContent() {
        return new ContentBuilder();
    }

    protected Content getClassLink(LinkInfo linkInfo) {
        LinkInfoImpl classLinkInfo = (LinkInfoImpl) linkInfo;
        boolean noLabel = linkInfo.label == null || linkInfo.label.isEmpty();
        ClassDoc classDoc = classLinkInfo.classDoc;


        String title =
                (classLinkInfo.where == null || classLinkInfo.where.length() == 0) ?
                        getClassToolTip(classDoc,
                                classLinkInfo.type != null &&
                                        !classDoc.qualifiedTypeName().equals(classLinkInfo.type.qualifiedTypeName())) :
                        "";
        Content label = classLinkInfo.getClassLinkLabel(m_writer.configuration);
        Configuration configuration = m_writer.configuration;
        Content link = new ContentBuilder();
        if (classDoc.isIncluded()) {
            if (configuration.isGeneratedDoc(classDoc)) {
                DocPath filename = getPath(classLinkInfo);
                if (linkInfo.linkToSelf ||
                        !(DocPath.forName(classDoc)).equals(m_writer.filename)) {
                    link.addContent(m_writer.getHyperLink(
                            filename.fragment(classLinkInfo.where),
                            label,
                            classLinkInfo.isStrong, classLinkInfo.styleName,
                            title, classLinkInfo.target));
                    if (noLabel && !classLinkInfo.excludeTypeParameterLinks) {
                        link.addContent(getTypeParameterLinks(linkInfo));
                    }
                    return link;
                }
            }
        } else {
            Content crossLink = m_writer.getCrossClassLink(
                    classDoc.qualifiedName(), classLinkInfo.where,
                    label, classLinkInfo.isStrong, classLinkInfo.styleName,
                    true);
            if (crossLink != null) {
                link.addContent(crossLink);
                if (noLabel && !classLinkInfo.excludeTypeParameterLinks) {
                    link.addContent(getTypeParameterLinks(linkInfo));
                }
                return link;
            }
        }

        link.addContent(label);
        if (noLabel && !classLinkInfo.excludeTypeParameterLinks) {
            link.addContent(getTypeParameterLinks(linkInfo));
        }
        return link;
    }

    protected Content getTypeParameterLink(LinkInfo linkInfo,
                                           Type typeParam) {
        LinkInfoImpl typeLinkInfo = new LinkInfoImpl(m_writer.configuration,
                ((LinkInfoImpl) linkInfo).getContext(), typeParam);
        typeLinkInfo.excludeTypeBounds = linkInfo.excludeTypeBounds;
        typeLinkInfo.excludeTypeParameterLinks = linkInfo.excludeTypeParameterLinks;
        typeLinkInfo.linkToSelf = linkInfo.linkToSelf;
        typeLinkInfo.isJava5DeclarationLocation = false;
        return getLink(typeLinkInfo);
    }

    protected Content getTypeAnnotationLink(LinkInfo linkInfo,
                                            AnnotationDesc annotation) {
        throw new RuntimeException("Not implemented yet!");
    }

    public Content getTypeAnnotationLinks(LinkInfo linkInfo) {
        ContentBuilder links = new ContentBuilder();
        AnnotationDesc[] annotations;
        if (linkInfo.type instanceof AnnotatedType) {
            annotations = linkInfo.type.asAnnotatedType().annotations();
        } else if (linkInfo.type instanceof TypeVariable) {
            annotations = linkInfo.type.asTypeVariable().annotations();
        } else {
            return links;
        }
        if (annotations.length == 0)
            return links;
        List<Content> annos = m_writer.getAnnotations(0, annotations, false, linkInfo.isJava5DeclarationLocation);
        boolean isFirst = true;
        for (Content anno : annos) {
            if (!isFirst) {
                links.addContent(" ");
            }
            links.addContent(anno);
            isFirst = false;
        }
        if (!annos.isEmpty()) {
            links.addContent(" ");
        }
        return links;
    }

    private String getClassToolTip(ClassDoc classDoc, boolean isTypeLink) {
        Configuration configuration = m_writer.configuration;
        if (isTypeLink) {
            return configuration.getText("doclet.Href_Type_Param_Title",
                    classDoc.name());
        } else if (classDoc.isInterface()) {
            return configuration.getText("doclet.Href_Interface_Title",
                    Util.getPackageName(classDoc.containingPackage()));
        } else if (classDoc.isAnnotationType()) {
            return configuration.getText("doclet.Href_Annotation_Title",
                    Util.getPackageName(classDoc.containingPackage()));
        } else if (classDoc.isEnum()) {
            return configuration.getText("doclet.Href_Enum_Title",
                    Util.getPackageName(classDoc.containingPackage()));
        } else {
            return configuration.getText("doclet.Href_Class_Title",
                    Util.getPackageName(classDoc.containingPackage()));
        }
    }

    private DocPath getPath(LinkInfoImpl linkInfo) {
        if (linkInfo.context == LinkInfoImpl.Kind.PACKAGE_FRAME) {


            return DocPath.forName(linkInfo.classDoc);
        }
        return m_writer.pathToRoot.resolve(DocPath.forClass(linkInfo.classDoc));
    }
}
