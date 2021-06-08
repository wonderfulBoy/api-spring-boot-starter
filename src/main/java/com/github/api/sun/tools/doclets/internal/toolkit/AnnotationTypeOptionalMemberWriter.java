package com.github.api.sun.tools.doclets.internal.toolkit;

import com.github.api.sun.javadoc.MemberDoc;

public interface AnnotationTypeOptionalMemberWriter extends
        AnnotationTypeRequiredMemberWriter {

    void addDefaultValueInfo(MemberDoc member, Content annotationDocTree);
}
