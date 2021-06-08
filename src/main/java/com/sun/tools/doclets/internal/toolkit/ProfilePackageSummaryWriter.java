package com.sun.tools.doclets.internal.toolkit;

import com.sun.javadoc.ClassDoc;

import java.io.IOException;

public interface ProfilePackageSummaryWriter {

    Content getPackageHeader(String heading);

    Content getContentHeader();

    Content getSummaryHeader();

    void addClassesSummary(ClassDoc[] classes, String label,
                           String tableSummary, String[] tableHeader, Content summaryContentTree);

    void addPackageDescription(Content packageContentTree);

    void addPackageTags(Content packageContentTree);

    void addPackageFooter(Content contentTree);

    void printDocument(Content contentTree) throws IOException;

    void close() throws IOException;
}
