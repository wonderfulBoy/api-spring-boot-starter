package com.sun.tools.doclets.internal.toolkit;

import com.sun.javadoc.MemberDoc;

public interface AnnotationTypeOptionalMemberWriter extends
        AnnotationTypeRequiredMemberWriter {

    void addDefaultValueInfo(MemberDoc member, Content annotationDocTree);
}
