package com.sun.tools.doclets.internal.toolkit;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;

import java.io.IOException;

public interface EnumConstantWriter {

    Content getEnumConstantsDetailsTreeHeader(ClassDoc classDoc,
                                              Content memberDetailsTree);

    Content getEnumConstantsTreeHeader(FieldDoc enumConstant,
                                       Content enumConstantsDetailsTree);

    Content getSignature(FieldDoc enumConstant);

    void addDeprecated(FieldDoc enumConstant, Content enumConstantsTree);

    void addComments(FieldDoc enumConstant, Content enumConstantsTree);

    void addTags(FieldDoc enumConstant, Content enumConstantsTree);

    Content getEnumConstantsDetails(Content memberDetailsTree);

    Content getEnumConstants(Content enumConstantsTree, boolean isLastContent);

    void close() throws IOException;
}
