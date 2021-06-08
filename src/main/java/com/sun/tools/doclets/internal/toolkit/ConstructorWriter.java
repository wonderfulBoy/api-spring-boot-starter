package com.sun.tools.doclets.internal.toolkit;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;

import java.io.IOException;

public interface ConstructorWriter {

    Content getConstructorDetailsTreeHeader(ClassDoc classDoc,
                                            Content memberDetailsTree);

    Content getConstructorDocTreeHeader(ConstructorDoc constructor,
                                        Content constructorDetailsTree);

    Content getSignature(ConstructorDoc constructor);

    void addDeprecated(ConstructorDoc constructor, Content constructorDocTree);

    void addComments(ConstructorDoc constructor, Content constructorDocTree);

    void addTags(ConstructorDoc constructor, Content constructorDocTree);

    Content getConstructorDetails(Content memberDetailsTree);

    Content getConstructorDoc(Content constructorDocTree, boolean isLastContent);

    void setFoundNonPubConstructor(boolean foundNonPubConstructor);

    void close() throws IOException;
}
