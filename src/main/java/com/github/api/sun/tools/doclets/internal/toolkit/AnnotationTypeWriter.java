package com.github.api.sun.tools.doclets.internal.toolkit;

import com.github.api.sun.javadoc.AnnotationTypeDoc;

import java.io.IOException;

public interface AnnotationTypeWriter {

    Content getHeader(String header);

    Content getAnnotationContentHeader();

    Content getAnnotationInfoTreeHeader();

    Content getAnnotationInfo(Content annotationInfoTree);

    void addAnnotationTypeSignature(String modifiers, Content annotationInfoTree);

    void addAnnotationTypeDescription(Content annotationInfoTree);

    void addAnnotationTypeTagInfo(Content annotationInfoTree);

    void addAnnotationTypeDeprecationInfo(Content annotationInfoTree);

    Content getMemberTreeHeader();

    Content getMemberTree(Content memberTree);

    Content getMemberSummaryTree(Content memberTree);

    Content getMemberDetailsTree(Content memberTree);

    void addFooter(Content contentTree);

    void printDocument(Content contentTree) throws IOException;

    void close() throws IOException;

    AnnotationTypeDoc getAnnotationTypeDoc();
}
