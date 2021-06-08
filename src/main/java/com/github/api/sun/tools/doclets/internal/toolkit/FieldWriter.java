package com.github.api.sun.tools.doclets.internal.toolkit;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.FieldDoc;

import java.io.IOException;

public interface FieldWriter {

    Content getFieldDetailsTreeHeader(ClassDoc classDoc,
                                      Content memberDetailsTree);

    Content getFieldDocTreeHeader(FieldDoc field,
                                  Content fieldDetailsTree);

    Content getSignature(FieldDoc field);

    void addDeprecated(FieldDoc field, Content fieldDocTree);

    void addComments(FieldDoc field, Content fieldDocTree);

    void addTags(FieldDoc field, Content fieldDocTree);

    Content getFieldDetails(Content memberDetailsTree);

    Content getFieldDoc(Content fieldDocTree, boolean isLastContent);

    void close() throws IOException;
}
