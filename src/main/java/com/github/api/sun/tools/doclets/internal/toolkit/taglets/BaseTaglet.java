package com.github.api.sun.tools.doclets.internal.toolkit.taglets;

import com.github.api.sun.javadoc.Doc;
import com.github.api.sun.javadoc.Tag;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;

public abstract class BaseTaglet implements Taglet {
    protected String name = "Default";

    public boolean inConstructor() {
        return true;
    }

    public boolean inField() {
        return true;
    }

    public boolean inMethod() {
        return true;
    }

    public boolean inOverview() {
        return true;
    }

    public boolean inPackage() {
        return true;
    }

    public boolean inType() {
        return true;
    }

    public boolean isInlineTag() {
        return false;
    }

    public String getName() {
        return name;
    }

    public Content getTagletOutput(Tag tag, TagletWriter writer) {
        throw new IllegalArgumentException("Method not supported in taglet " + getName() + ".");
    }

    public Content getTagletOutput(Doc holder, TagletWriter writer) {
        throw new IllegalArgumentException("Method not supported in taglet " + getName() + ".");
    }
}
