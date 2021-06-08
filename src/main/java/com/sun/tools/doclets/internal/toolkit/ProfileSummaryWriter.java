package com.sun.tools.doclets.internal.toolkit;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;

import java.io.IOException;

public interface ProfileSummaryWriter {

    Content getProfileHeader(String heading);

    Content getContentHeader();

    Content getSummaryHeader();

    Content getSummaryTree(Content summaryContentTree);

    Content getPackageSummaryHeader(PackageDoc pkg);

    Content getPackageSummaryTree(Content packageSummaryContentTree);

    void addClassesSummary(ClassDoc[] classes, String label,
                           String tableSummary, String[] tableHeader, Content packageSummaryContentTree);

    void addProfileFooter(Content contentTree);

    void printDocument(Content contentTree) throws IOException;

    void close() throws IOException;
}
