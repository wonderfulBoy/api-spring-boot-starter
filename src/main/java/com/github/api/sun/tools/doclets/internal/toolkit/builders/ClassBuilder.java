package com.github.api.sun.tools.doclets.internal.toolkit.builders;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.PackageDoc;
import com.github.api.sun.tools.doclets.internal.toolkit.ClassWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.Util;

import java.io.IOException;
import java.util.Arrays;

public class ClassBuilder extends AbstractBuilder {

    public static final String ROOT = "ClassDoc";

    private final ClassDoc classDoc;

    private final ClassWriter writer;

    private final boolean isInterface;

    private final boolean isEnum;

    private Content contentTree;

    private ClassBuilder(Context context,
                         ClassDoc classDoc, ClassWriter writer) {
        super(context);
        this.classDoc = classDoc;
        this.writer = writer;
        if (classDoc.isInterface()) {
            isInterface = true;
            isEnum = false;
        } else if (classDoc.isEnum()) {
            isInterface = false;
            isEnum = true;
            Util.setEnumDocumentation(configuration, classDoc);
        } else {
            isInterface = false;
            isEnum = false;
        }
    }

    public static ClassBuilder getInstance(Context context,
                                           ClassDoc classDoc, ClassWriter writer) {
        return new ClassBuilder(context, classDoc, writer);
    }

    public void build() throws IOException {
        build(layoutParser.parseXML(ROOT), contentTree);
    }

    public String getName() {
        return ROOT;
    }

    public void buildClassDoc(XMLNode node, Content contentTree) throws Exception {
        String key;
        if (isInterface) {
            key = "doclet.Interface";
        } else if (isEnum) {
            key = "doclet.Enum";
        } else {
            key = "doclet.Class";
        }
        contentTree = writer.getHeader(configuration.getText(key) + " " +
                classDoc.name());
        Content classContentTree = writer.getClassContentHeader();
        buildChildren(node, classContentTree);
        contentTree.addContent(classContentTree);
        writer.addFooter(contentTree);
        writer.printDocument(contentTree);
        writer.close();
        copyDocFiles();
    }

    public void buildClassTree(XMLNode node, Content classContentTree) {
        writer.addClassTree(classContentTree);
    }

    public void buildClassInfo(XMLNode node, Content classContentTree) {
        Content classInfoTree = writer.getClassInfoTreeHeader();
        buildChildren(node, classInfoTree);
        classContentTree.addContent(writer.getClassInfo(classInfoTree));
    }

    public void buildTypeParamInfo(XMLNode node, Content classInfoTree) {
        writer.addTypeParamInfo(classInfoTree);
    }

    public void buildSuperInterfacesInfo(XMLNode node, Content classInfoTree) {
        writer.addSuperInterfacesInfo(classInfoTree);
    }

    public void buildImplementedInterfacesInfo(XMLNode node, Content classInfoTree) {
        writer.addImplementedInterfacesInfo(classInfoTree);
    }

    public void buildSubClassInfo(XMLNode node, Content classInfoTree) {
        writer.addSubClassInfo(classInfoTree);
    }

    public void buildSubInterfacesInfo(XMLNode node, Content classInfoTree) {
        writer.addSubInterfacesInfo(classInfoTree);
    }

    public void buildInterfaceUsageInfo(XMLNode node, Content classInfoTree) {
        writer.addInterfaceUsageInfo(classInfoTree);
    }

    public void buildFunctionalInterfaceInfo(XMLNode node, Content classInfoTree) {
        writer.addFunctionalInterfaceInfo(classInfoTree);
    }

    public void buildDeprecationInfo(XMLNode node, Content classInfoTree) {
        writer.addClassDeprecationInfo(classInfoTree);
    }

    public void buildNestedClassInfo(XMLNode node, Content classInfoTree) {
        writer.addNestedClassInfo(classInfoTree);
    }

    private void copyDocFiles() {
        PackageDoc containingPackage = classDoc.containingPackage();
        if ((configuration.packages == null ||
                Arrays.binarySearch(configuration.packages,
                        containingPackage) < 0) &&
                !containingPackagesSeen.contains(containingPackage.name())) {


            Util.copyDocFiles(configuration, containingPackage);
            containingPackagesSeen.add(containingPackage.name());
        }
    }

    public void buildClassSignature(XMLNode node, Content classInfoTree) {
        StringBuilder modifiers = new StringBuilder(classDoc.modifiers());
        modifiers.append(modifiers.length() == 0 ? "" : " ");
        if (isEnum) {
            modifiers.append("enum ");
            int index;
            if ((index = modifiers.indexOf("abstract")) >= 0) {
                modifiers.delete(index, index + "abstract".length());
                modifiers = new StringBuilder(
                        Util.replaceText(modifiers.toString(), "  ", " "));
            }
            if ((index = modifiers.indexOf("final")) >= 0) {
                modifiers.delete(index, index + "final".length());
                modifiers = new StringBuilder(
                        Util.replaceText(modifiers.toString(), "  ", " "));
            }


        } else if (!isInterface) {
            modifiers.append("class ");
        }
        writer.addClassSignature(modifiers.toString(), classInfoTree);
    }

    public void buildClassDescription(XMLNode node, Content classInfoTree) {
        writer.addClassDescription(classInfoTree);
    }

    public void buildClassTagInfo(XMLNode node, Content classInfoTree) {
        writer.addClassTagInfo(classInfoTree);
    }

    public void buildMemberSummary(XMLNode node, Content classContentTree) throws Exception {
        Content memberSummaryTree = writer.getMemberTreeHeader();
        configuration.getBuilderFactory().
                getMemberSummaryBuilder(writer).buildChildren(node, memberSummaryTree);
        classContentTree.addContent(writer.getMemberSummaryTree(memberSummaryTree));
    }

    public void buildMemberDetails(XMLNode node, Content classContentTree) {
        Content memberDetailsTree = writer.getMemberTreeHeader();
        buildChildren(node, memberDetailsTree);
        classContentTree.addContent(writer.getMemberDetailsTree(memberDetailsTree));
    }

    public void buildEnumConstantsDetails(XMLNode node,
                                          Content memberDetailsTree) throws Exception {
        configuration.getBuilderFactory().
                getEnumConstantsBuilder(writer).buildChildren(node, memberDetailsTree);
    }

    public void buildFieldDetails(XMLNode node,
                                  Content memberDetailsTree) throws Exception {
        configuration.getBuilderFactory().
                getFieldBuilder(writer).buildChildren(node, memberDetailsTree);
    }

    public void buildPropertyDetails(XMLNode node,
                                     Content memberDetailsTree) throws Exception {
        configuration.getBuilderFactory().
                getPropertyBuilder(writer).buildChildren(node, memberDetailsTree);
    }

    public void buildConstructorDetails(XMLNode node,
                                        Content memberDetailsTree) throws Exception {
        configuration.getBuilderFactory().
                getConstructorBuilder(writer).buildChildren(node, memberDetailsTree);
    }

    public void buildMethodDetails(XMLNode node,
                                   Content memberDetailsTree) throws Exception {
        configuration.getBuilderFactory().
                getMethodBuilder(writer).buildChildren(node, memberDetailsTree);
    }
}
