package com.github.api.sun.tools.doclets.internal.toolkit.taglets;

import com.github.api.sun.javadoc.Doc;
import com.github.api.sun.javadoc.Tag;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;

public interface Taglet {

    boolean inField();

    boolean inConstructor();

    boolean inMethod();

    boolean inOverview();

    boolean inPackage();

    boolean inType();

    boolean isInlineTag();

    String getName();

    Content getTagletOutput(Tag tag, TagletWriter writer) throws IllegalArgumentException;

    Content getTagletOutput(Doc holder, TagletWriter writer) throws IllegalArgumentException;

    @Override
    String toString();
}
