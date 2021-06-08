package com.sun.tools.doclets.internal.toolkit;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.PackageDoc;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public interface ConstantsSummaryWriter {

    void close() throws IOException;

    Content getHeader();

    Content getContentsHeader();

    void addLinkToPackageContent(PackageDoc pkg, String parsedPackageName,
                                 Set<String> WriteedPackageHeaders, Content contentListTree);

    Content getContentsList(Content contentListTree);

    Content getConstantSummaries();

    void addPackageName(PackageDoc pkg,
                        String parsedPackageName, Content summariesTree);

    Content getClassConstantHeader();

    void addConstantMembers(ClassDoc cd, List<FieldDoc> fields,
                            Content classConstantTree);

    void addFooter(Content contentTree);

    void printDocument(Content contentTree) throws IOException;
}
