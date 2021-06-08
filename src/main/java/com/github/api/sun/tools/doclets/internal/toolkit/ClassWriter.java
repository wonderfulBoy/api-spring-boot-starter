package com.github.api.sun.tools.doclets.internal.toolkit;

import com.github.api.sun.javadoc.ClassDoc;

import java.io.IOException;

public interface ClassWriter {

    Content getHeader(String header);

    Content getClassContentHeader();

    void addClassTree(Content classContentTree);

    Content getClassInfoTreeHeader();

    void addTypeParamInfo(Content classInfoTree);

    void addSuperInterfacesInfo(Content classInfoTree);

    void addImplementedInterfacesInfo(Content classInfoTree);

    void addSubClassInfo(Content classInfoTree);

    void addSubInterfacesInfo(Content classInfoTree);

    void addInterfaceUsageInfo(Content classInfoTree);

    void addFunctionalInterfaceInfo(Content classInfoTree);

    void addNestedClassInfo(Content classInfoTree);

    Content getClassInfo(Content classInfoTree);

    void addClassDeprecationInfo(Content classInfoTree);

    void addClassSignature(String modifiers, Content classInfoTree);

    void addClassDescription(Content classInfoTree);

    void addClassTagInfo(Content classInfoTree);

    Content getMemberTreeHeader();

    void addFooter(Content contentTree);

    void printDocument(Content contentTree) throws IOException;

    void close() throws IOException;

    ClassDoc getClassDoc();

    Content getMemberSummaryTree(Content memberTree);

    Content getMemberDetailsTree(Content memberTree);
}
