package com.github.api.sun.tools.doclets.internal.toolkit.util.links;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.ExecutableMemberDoc;
import com.github.api.sun.javadoc.Type;
import com.github.api.sun.tools.doclets.internal.toolkit.Configuration;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;

public abstract class LinkInfo {

    public ClassDoc classDoc;

    public ExecutableMemberDoc executableMemberDoc;

    public Type type;

    public boolean isVarArg = false;

    public boolean isTypeBound = false;

    public boolean isJava5DeclarationLocation = true;

    public Content label;

    public boolean isStrong = false;

    public boolean includeTypeInClassLinkLabel = true;

    public boolean includeTypeAsSepLink = false;

    public boolean excludeTypeBounds = false;

    public boolean excludeTypeParameterLinks = false;

    public boolean excludeTypeBoundsLinks = false;

    public boolean linkToSelf = true;

    protected abstract Content newContent();

    public abstract boolean isLinkable();

    public Content getClassLinkLabel(Configuration configuration) {
        if (label != null && !label.isEmpty()) {
            return label;
        } else if (isLinkable()) {
            Content label = newContent();
            label.addContent(classDoc.name());
            return label;
        } else {
            Content label = newContent();
            label.addContent(configuration.getClassName(classDoc));
            return label;
        }
    }
}
