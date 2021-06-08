package com.sun.tools.doclets.internal.toolkit.builders;

import com.sun.javadoc.AnnotationTypeDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.tools.doclets.internal.toolkit.AnnotationTypeWriter;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.Util;

import java.io.IOException;
import java.util.Arrays;

public class AnnotationTypeBuilder extends AbstractBuilder {

    public static final String ROOT = "AnnotationTypeDoc";

    private final AnnotationTypeDoc annotationTypeDoc;

    private final AnnotationTypeWriter writer;

    private Content contentTree;

    private AnnotationTypeBuilder(Context context,
                                  AnnotationTypeDoc annotationTypeDoc,
                                  AnnotationTypeWriter writer) {
        super(context);
        this.annotationTypeDoc = annotationTypeDoc;
        this.writer = writer;
    }

    public static AnnotationTypeBuilder getInstance(Context context,
                                                    AnnotationTypeDoc annotationTypeDoc,
                                                    AnnotationTypeWriter writer)
            throws Exception {
        return new AnnotationTypeBuilder(context, annotationTypeDoc, writer);
    }

    public void build() throws IOException {
        build(layoutParser.parseXML(ROOT), contentTree);
    }

    public String getName() {
        return ROOT;
    }

    public void buildAnnotationTypeDoc(XMLNode node, Content contentTree) throws Exception {
        contentTree = writer.getHeader(configuration.getText("doclet.AnnotationType") +
                " " + annotationTypeDoc.name());
        Content annotationContentTree = writer.getAnnotationContentHeader();
        buildChildren(node, annotationContentTree);
        contentTree.addContent(annotationContentTree);
        writer.addFooter(contentTree);
        writer.printDocument(contentTree);
        writer.close();
        copyDocFiles();
    }

    private void copyDocFiles() {
        PackageDoc containingPackage = annotationTypeDoc.containingPackage();
        if ((configuration.packages == null ||
                Arrays.binarySearch(configuration.packages,
                        containingPackage) < 0) &&
                !containingPackagesSeen.contains(containingPackage.name())) {


            Util.copyDocFiles(configuration, containingPackage);
            containingPackagesSeen.add(containingPackage.name());
        }
    }

    public void buildAnnotationTypeInfo(XMLNode node, Content annotationContentTree) {
        Content annotationInfoTree = writer.getAnnotationInfoTreeHeader();
        buildChildren(node, annotationInfoTree);
        annotationContentTree.addContent(writer.getAnnotationInfo(annotationInfoTree));
    }

    public void buildDeprecationInfo(XMLNode node, Content annotationInfoTree) {
        writer.addAnnotationTypeDeprecationInfo(annotationInfoTree);
    }

    public void buildAnnotationTypeSignature(XMLNode node, Content annotationInfoTree) {
        StringBuilder modifiers = new StringBuilder(
                annotationTypeDoc.modifiers() + " ");
        writer.addAnnotationTypeSignature(Util.replaceText(
                modifiers.toString(), "interface", "@interface"), annotationInfoTree);
    }

    public void buildAnnotationTypeDescription(XMLNode node, Content annotationInfoTree) {
        writer.addAnnotationTypeDescription(annotationInfoTree);
    }

    public void buildAnnotationTypeTagInfo(XMLNode node, Content annotationInfoTree) {
        writer.addAnnotationTypeTagInfo(annotationInfoTree);
    }

    public void buildMemberSummary(XMLNode node, Content annotationContentTree)
            throws Exception {
        Content memberSummaryTree = writer.getMemberTreeHeader();
        configuration.getBuilderFactory().
                getMemberSummaryBuilder(writer).buildChildren(node, memberSummaryTree);
        annotationContentTree.addContent(writer.getMemberSummaryTree(memberSummaryTree));
    }

    public void buildAnnotationTypeMemberDetails(XMLNode node, Content annotationContentTree) {
        Content memberDetailsTree = writer.getMemberTreeHeader();
        buildChildren(node, memberDetailsTree);
        if (memberDetailsTree.isValid()) {
            annotationContentTree.addContent(writer.getMemberDetailsTree(memberDetailsTree));
        }
    }

    public void buildAnnotationTypeFieldDetails(XMLNode node, Content memberDetailsTree)
            throws Exception {
        configuration.getBuilderFactory().
                getAnnotationTypeFieldsBuilder(writer).buildChildren(node, memberDetailsTree);
    }

    public void buildAnnotationTypeOptionalMemberDetails(XMLNode node, Content memberDetailsTree)
            throws Exception {
        configuration.getBuilderFactory().
                getAnnotationTypeOptionalMemberBuilder(writer).buildChildren(node, memberDetailsTree);
    }

    public void buildAnnotationTypeRequiredMemberDetails(XMLNode node, Content memberDetailsTree)
            throws Exception {
        configuration.getBuilderFactory().
                getAnnotationTypeRequiredMemberBuilder(writer).buildChildren(node, memberDetailsTree);
    }
}
