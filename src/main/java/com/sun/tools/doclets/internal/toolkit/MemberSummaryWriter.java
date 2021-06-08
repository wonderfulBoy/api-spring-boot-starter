package com.sun.tools.doclets.internal.toolkit;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.Tag;

import java.io.IOException;
import java.util.List;

public interface MemberSummaryWriter {

    Content getMemberSummaryHeader(ClassDoc classDoc,
                                   Content memberSummaryTree);

    Content getSummaryTableTree(ClassDoc classDoc,
                                List<Content> tableContents);

    void addMemberSummary(ClassDoc classDoc, ProgramElementDoc member,
                          Tag[] firstSentenceTags, List<Content> tableContents, int counter);

    Content getInheritedSummaryHeader(ClassDoc classDoc);

    void addInheritedMemberSummary(ClassDoc classDoc,
                                   ProgramElementDoc member, boolean isFirst, boolean isLast,
                                   Content linksTree);

    Content getInheritedSummaryLinksTree();

    Content getMemberTree(Content memberTree);

    void close() throws IOException;
}
