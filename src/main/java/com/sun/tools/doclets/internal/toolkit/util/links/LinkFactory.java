package com.sun.tools.doclets.internal.toolkit.util.links;

import com.sun.javadoc.*;
import com.sun.tools.doclets.internal.toolkit.Content;

public abstract class LinkFactory {

    protected abstract Content newContent();

    public Content getLink(LinkInfo linkInfo) {
        if (linkInfo.type != null) {
            Type type = linkInfo.type;
            Content link = newContent();
            if (type.isPrimitive()) {

                link.addContent(type.typeName());
            } else if (type.asAnnotatedType() != null && type.dimension().length() == 0) {
                link.addContent(getTypeAnnotationLinks(linkInfo));
                linkInfo.type = type.asAnnotatedType().underlyingType();
                link.addContent(getLink(linkInfo));
                return link;
            } else if (type.asWildcardType() != null) {

                linkInfo.isTypeBound = true;
                link.addContent("?");
                WildcardType wildcardType = type.asWildcardType();
                Type[] extendsBounds = wildcardType.extendsBounds();
                for (int i = 0; i < extendsBounds.length; i++) {
                    link.addContent(i > 0 ? ", " : " extends ");
                    setBoundsLinkInfo(linkInfo, extendsBounds[i]);
                    link.addContent(getLink(linkInfo));
                }
                Type[] superBounds = wildcardType.superBounds();
                for (int i = 0; i < superBounds.length; i++) {
                    link.addContent(i > 0 ? ", " : " super ");
                    setBoundsLinkInfo(linkInfo, superBounds[i]);
                    link.addContent(getLink(linkInfo));
                }
            } else if (type.asTypeVariable() != null) {
                link.addContent(getTypeAnnotationLinks(linkInfo));
                linkInfo.isTypeBound = true;

                Doc owner = type.asTypeVariable().owner();
                if ((!linkInfo.excludeTypeParameterLinks) &&
                        owner instanceof ClassDoc) {
                    linkInfo.classDoc = (ClassDoc) owner;
                    Content label = newContent();
                    label.addContent(type.typeName());
                    linkInfo.label = label;
                    link.addContent(getClassLink(linkInfo));
                } else {

                    link.addContent(type.typeName());
                }
                Type[] bounds = type.asTypeVariable().bounds();
                if (!linkInfo.excludeTypeBounds) {
                    linkInfo.excludeTypeBounds = true;
                    for (int i = 0; i < bounds.length; i++) {
                        link.addContent(i > 0 ? " & " : " extends ");
                        setBoundsLinkInfo(linkInfo, bounds[i]);
                        link.addContent(getLink(linkInfo));
                    }
                }
            } else if (type.asClassDoc() != null) {

                if (linkInfo.isTypeBound &&
                        linkInfo.excludeTypeBoundsLinks) {


                    link.addContent(type.typeName());
                    link.addContent(getTypeParameterLinks(linkInfo));
                    return link;
                } else {
                    linkInfo.classDoc = type.asClassDoc();
                    link = newContent();
                    link.addContent(getClassLink(linkInfo));
                    if (linkInfo.includeTypeAsSepLink) {
                        link.addContent(getTypeParameterLinks(linkInfo, false));
                    }
                }
            }
            if (linkInfo.isVarArg) {
                if (type.dimension().length() > 2) {


                    link.addContent(type.dimension().substring(2));
                }
                link.addContent("...");
            } else {
                while (type != null && type.dimension().length() > 0) {
                    if (type.asAnnotatedType() != null) {
                        linkInfo.type = type;
                        link.addContent(" ");
                        link.addContent(getTypeAnnotationLinks(linkInfo));
                        link.addContent("[]");
                        type = type.asAnnotatedType().underlyingType().getElementType();
                    } else {
                        link.addContent("[]");
                        type = type.getElementType();
                    }
                }
                linkInfo.type = type;
                Content newLink = newContent();
                newLink.addContent(getTypeAnnotationLinks(linkInfo));
                newLink.addContent(link);
                link = newLink;
            }
            return link;
        } else if (linkInfo.classDoc != null) {

            Content link = newContent();
            link.addContent(getClassLink(linkInfo));
            if (linkInfo.includeTypeAsSepLink) {
                link.addContent(getTypeParameterLinks(linkInfo, false));
            }
            return link;
        } else {
            return null;
        }
    }

    private void setBoundsLinkInfo(LinkInfo linkInfo, Type bound) {
        linkInfo.classDoc = null;
        linkInfo.label = null;
        linkInfo.type = bound;
    }

    protected abstract Content getClassLink(LinkInfo linkInfo);

    protected abstract Content getTypeParameterLink(LinkInfo linkInfo,
                                                    Type typeParam);

    protected abstract Content getTypeAnnotationLink(LinkInfo linkInfo,
                                                     AnnotationDesc annotation);

    public Content getTypeParameterLinks(LinkInfo linkInfo) {
        return getTypeParameterLinks(linkInfo, true);
    }

    public Content getTypeParameterLinks(LinkInfo linkInfo, boolean isClassLabel) {
        Content links = newContent();
        Type[] vars;
        if (linkInfo.executableMemberDoc != null) {
            vars = linkInfo.executableMemberDoc.typeParameters();
        } else if (linkInfo.type != null &&
                linkInfo.type.asParameterizedType() != null) {
            vars = linkInfo.type.asParameterizedType().typeArguments();
        } else if (linkInfo.classDoc != null) {
            vars = linkInfo.classDoc.typeParameters();
        } else {

            return links;
        }
        if (((linkInfo.includeTypeInClassLinkLabel && isClassLabel) ||
                (linkInfo.includeTypeAsSepLink && !isClassLabel)
        )
                && vars.length > 0) {
            links.addContent("<");
            for (int i = 0; i < vars.length; i++) {
                if (i > 0) {
                    links.addContent(",");
                }
                links.addContent(getTypeParameterLink(linkInfo, vars[i]));
            }
            links.addContent(">");
        }
        return links;
    }

    public Content getTypeAnnotationLinks(LinkInfo linkInfo) {
        Content links = newContent();
        if (linkInfo.type.asAnnotatedType() == null)
            return links;
        AnnotationDesc[] annotations = linkInfo.type.asAnnotatedType().annotations();
        for (int i = 0; i < annotations.length; i++) {
            if (i > 0) {
                links.addContent(" ");
            }
            links.addContent(getTypeAnnotationLink(linkInfo, annotations[i]));
        }
        links.addContent(" ");
        return links;
    }
}
