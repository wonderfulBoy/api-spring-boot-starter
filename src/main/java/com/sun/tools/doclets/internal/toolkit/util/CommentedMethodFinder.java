package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.javadoc.MethodDoc;

public class CommentedMethodFinder extends MethodFinder {
    public boolean isCorrectMethod(MethodDoc method) {
        return method.inlineTags().length > 0;
    }
}
