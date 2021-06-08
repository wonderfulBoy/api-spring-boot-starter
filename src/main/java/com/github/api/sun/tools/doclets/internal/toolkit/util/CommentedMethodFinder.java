package com.github.api.sun.tools.doclets.internal.toolkit.util;

import com.github.api.sun.javadoc.MethodDoc;

public class CommentedMethodFinder extends MethodFinder {
    public boolean isCorrectMethod(MethodDoc method) {
        return method.inlineTags().length > 0;
    }
}
