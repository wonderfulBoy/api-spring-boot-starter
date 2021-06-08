package com.sun.tools.doclets;

import com.sun.javadoc.Tag;

public interface Taglet {

    boolean inField();

    boolean inConstructor();

    boolean inMethod();

    boolean inOverview();

    boolean inPackage();

    boolean inType();

    boolean isInlineTag();

    String getName();

    String toString(Tag tag);

    String toString(Tag[] tags);
}
