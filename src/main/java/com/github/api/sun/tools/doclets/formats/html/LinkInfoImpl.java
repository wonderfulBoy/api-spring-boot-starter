package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.ExecutableMemberDoc;
import com.github.api.sun.javadoc.Type;
import com.github.api.sun.tools.doclets.formats.html.markup.ContentBuilder;
import com.github.api.sun.tools.doclets.formats.html.markup.StringContent;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.Util;
import com.github.api.sun.tools.doclets.internal.toolkit.util.links.LinkInfo;

public class LinkInfoImpl extends LinkInfo {
    public final ConfigurationImpl configuration;
    public Kind context = Kind.DEFAULT;
    public String where = "";
    public String styleName = "";
    public String target = "";

    public LinkInfoImpl(ConfigurationImpl configuration,
                        Kind context, ExecutableMemberDoc executableMemberDoc) {
        this.configuration = configuration;
        this.executableMemberDoc = executableMemberDoc;
        setContext(context);
    }

    public LinkInfoImpl(ConfigurationImpl configuration,
                        Kind context, ClassDoc classDoc) {
        this.configuration = configuration;
        this.classDoc = classDoc;
        setContext(context);
    }

    public LinkInfoImpl(ConfigurationImpl configuration,
                        Kind context, Type type) {
        this.configuration = configuration;
        this.type = type;
        setContext(context);
    }

    protected Content newContent() {
        return new ContentBuilder();
    }

    public LinkInfoImpl label(String label) {
        this.label = new StringContent(label);
        return this;
    }

    public LinkInfoImpl label(Content label) {
        this.label = label;
        return this;
    }

    public LinkInfoImpl strong(boolean strong) {
        this.isStrong = strong;
        return this;
    }

    public LinkInfoImpl styleName(String styleName) {
        this.styleName = styleName;
        return this;
    }

    public LinkInfoImpl target(String target) {
        this.target = target;
        return this;
    }

    public LinkInfoImpl varargs(boolean varargs) {
        this.isVarArg = varargs;
        return this;
    }

    public LinkInfoImpl where(String where) {
        this.where = where;
        return this;
    }

    public Kind getContext() {
        return context;
    }

    public final void setContext(Kind c) {

        switch (c) {
            case ALL_CLASSES_FRAME:
            case PACKAGE_FRAME:
            case IMPLEMENTED_CLASSES:
            case SUBCLASSES:
            case METHOD_DOC_COPY:
            case FIELD_DOC_COPY:
            case PROPERTY_DOC_COPY:
            case CLASS_USE_HEADER:
                includeTypeInClassLinkLabel = false;
                break;
            case ANNOTATION:
                excludeTypeParameterLinks = true;
                excludeTypeBounds = true;
                break;
            case IMPLEMENTED_INTERFACES:
            case SUPER_INTERFACES:
            case SUBINTERFACES:
            case CLASS_TREE_PARENT:
            case TREE:
            case CLASS_SIGNATURE_PARENT_NAME:
                excludeTypeParameterLinks = true;
                excludeTypeBounds = true;
                includeTypeInClassLinkLabel = false;
                includeTypeAsSepLink = true;
                break;
            case PACKAGE:
            case CLASS_USE:
            case CLASS_HEADER:
            case CLASS_SIGNATURE:
                excludeTypeParameterLinks = true;
                includeTypeAsSepLink = true;
                includeTypeInClassLinkLabel = false;
                break;
            case MEMBER_TYPE_PARAMS:
                includeTypeAsSepLink = true;
                includeTypeInClassLinkLabel = false;
                break;
            case RETURN_TYPE:
            case SUMMARY_RETURN_TYPE:
                excludeTypeBounds = true;
                break;
            case EXECUTABLE_MEMBER_PARAM:
                excludeTypeBounds = true;
                break;
        }
        context = c;
        if (type != null &&
                type.asTypeVariable() != null &&
                type.asTypeVariable().owner() instanceof ExecutableMemberDoc) {
            excludeTypeParameterLinks = true;
        }
    }

    public boolean isLinkable() {
        return Util.isLinkable(classDoc, configuration);
    }

    public enum Kind {
        DEFAULT,

        ALL_CLASSES_FRAME,

        CLASS,

        MEMBER,

        CLASS_USE,

        INDEX,

        CONSTANT_SUMMARY,

        SERIALIZED_FORM,

        SERIAL_MEMBER,

        PACKAGE,

        SEE_TAG,

        VALUE_TAG,

        TREE,

        PACKAGE_FRAME,

        CLASS_HEADER,

        CLASS_SIGNATURE,

        RETURN_TYPE,

        SUMMARY_RETURN_TYPE,

        EXECUTABLE_MEMBER_PARAM,

        SUPER_INTERFACES,

        IMPLEMENTED_INTERFACES,

        IMPLEMENTED_CLASSES,

        SUBINTERFACES,

        SUBCLASSES,

        CLASS_SIGNATURE_PARENT_NAME,

        METHOD_DOC_COPY,

        METHOD_SPECIFIED_BY,

        METHOD_OVERRIDES,

        ANNOTATION,

        FIELD_DOC_COPY,

        CLASS_TREE_PARENT,

        MEMBER_TYPE_PARAMS,

        CLASS_USE_HEADER,

        PROPERTY_DOC_COPY
    }
}
