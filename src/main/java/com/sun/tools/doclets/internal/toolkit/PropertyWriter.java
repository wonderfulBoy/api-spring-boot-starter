package com.sun.tools.doclets.internal.toolkit;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;

import java.io.IOException;

public interface PropertyWriter {

    Content getPropertyDetailsTreeHeader(ClassDoc classDoc,
                                         Content memberDetailsTree);

    Content getPropertyDocTreeHeader(MethodDoc property,
                                     Content propertyDetailsTree);

    Content getSignature(MethodDoc property);

    void addDeprecated(MethodDoc property, Content propertyDocTree);

    void addComments(MethodDoc property, Content propertyDocTree);

    void addTags(MethodDoc property, Content propertyDocTree);

    Content getPropertyDetails(Content memberDetailsTree);

    Content getPropertyDoc(Content propertyDocTree, boolean isLastContent);

    void close() throws IOException;
}
