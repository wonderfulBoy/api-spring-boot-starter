package com.github.api.sun.tools.doclets.internal.toolkit.builders;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.PackageDoc;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.ProfilePackageSummaryWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.util.Util;
import com.github.api.sun.tools.javac.jvm.Profile;

import java.io.IOException;

public class ProfilePackageSummaryBuilder extends AbstractBuilder {

    public static final String ROOT = "PackageDoc";

    private final PackageDoc packageDoc;

    private final String profileName;

    private final int profileValue;

    private final ProfilePackageSummaryWriter profilePackageWriter;

    private Content contentTree;

    private ProfilePackageSummaryBuilder(Context context,
                                         PackageDoc pkg, ProfilePackageSummaryWriter profilePackageWriter,
                                         Profile profile) {
        super(context);
        this.packageDoc = pkg;
        this.profilePackageWriter = profilePackageWriter;
        this.profileName = profile.name;
        this.profileValue = profile.value;
    }

    public static ProfilePackageSummaryBuilder getInstance(Context context,
                                                           PackageDoc pkg, ProfilePackageSummaryWriter profilePackageWriter,
                                                           Profile profile) {
        return new ProfilePackageSummaryBuilder(context, pkg, profilePackageWriter,
                profile);
    }

    public void build() throws IOException {
        if (profilePackageWriter == null) {

            return;
        }
        build(layoutParser.parseXML(ROOT), contentTree);
    }

    public String getName() {
        return ROOT;
    }

    public void buildPackageDoc(XMLNode node, Content contentTree) throws Exception {
        contentTree = profilePackageWriter.getPackageHeader(
                Util.getPackageName(packageDoc));
        buildChildren(node, contentTree);
        profilePackageWriter.addPackageFooter(contentTree);
        profilePackageWriter.printDocument(contentTree);
        profilePackageWriter.close();
        Util.copyDocFiles(configuration, packageDoc);
    }

    public void buildContent(XMLNode node, Content contentTree) {
        Content packageContentTree = profilePackageWriter.getContentHeader();
        buildChildren(node, packageContentTree);
        contentTree.addContent(packageContentTree);
    }

    public void buildSummary(XMLNode node, Content packageContentTree) {
        Content summaryContentTree = profilePackageWriter.getSummaryHeader();
        buildChildren(node, summaryContentTree);
        packageContentTree.addContent(summaryContentTree);
    }

    public void buildInterfaceSummary(XMLNode node, Content summaryContentTree) {
        String interfaceTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Interface_Summary"),
                        configuration.getText("doclet.interfaces"));
        String[] interfaceTableHeader = new String[]{
                configuration.getText("doclet.Interface"),
                configuration.getText("doclet.Description")
        };
        ClassDoc[] interfaces =
                packageDoc.isIncluded()
                        ? packageDoc.interfaces()
                        : configuration.classDocCatalog.interfaces(
                        Util.getPackageName(packageDoc));
        if (interfaces.length > 0) {
            profilePackageWriter.addClassesSummary(
                    interfaces,
                    configuration.getText("doclet.Interface_Summary"),
                    interfaceTableSummary, interfaceTableHeader, summaryContentTree);
        }
    }

    public void buildClassSummary(XMLNode node, Content summaryContentTree) {
        String classTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Class_Summary"),
                        configuration.getText("doclet.classes"));
        String[] classTableHeader = new String[]{
                configuration.getText("doclet.Class"),
                configuration.getText("doclet.Description")
        };
        ClassDoc[] classes =
                packageDoc.isIncluded()
                        ? packageDoc.ordinaryClasses()
                        : configuration.classDocCatalog.ordinaryClasses(
                        Util.getPackageName(packageDoc));
        if (classes.length > 0) {
            profilePackageWriter.addClassesSummary(
                    classes,
                    configuration.getText("doclet.Class_Summary"),
                    classTableSummary, classTableHeader, summaryContentTree);
        }
    }

    public void buildEnumSummary(XMLNode node, Content summaryContentTree) {
        String enumTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Enum_Summary"),
                        configuration.getText("doclet.enums"));
        String[] enumTableHeader = new String[]{
                configuration.getText("doclet.Enum"),
                configuration.getText("doclet.Description")
        };
        ClassDoc[] enums =
                packageDoc.isIncluded()
                        ? packageDoc.enums()
                        : configuration.classDocCatalog.enums(
                        Util.getPackageName(packageDoc));
        if (enums.length > 0) {
            profilePackageWriter.addClassesSummary(
                    enums,
                    configuration.getText("doclet.Enum_Summary"),
                    enumTableSummary, enumTableHeader, summaryContentTree);
        }
    }

    public void buildExceptionSummary(XMLNode node, Content summaryContentTree) {
        String exceptionTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Exception_Summary"),
                        configuration.getText("doclet.exceptions"));
        String[] exceptionTableHeader = new String[]{
                configuration.getText("doclet.Exception"),
                configuration.getText("doclet.Description")
        };
        ClassDoc[] exceptions =
                packageDoc.isIncluded()
                        ? packageDoc.exceptions()
                        : configuration.classDocCatalog.exceptions(
                        Util.getPackageName(packageDoc));
        if (exceptions.length > 0) {
            profilePackageWriter.addClassesSummary(
                    exceptions,
                    configuration.getText("doclet.Exception_Summary"),
                    exceptionTableSummary, exceptionTableHeader, summaryContentTree);
        }
    }

    public void buildErrorSummary(XMLNode node, Content summaryContentTree) {
        String errorTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Error_Summary"),
                        configuration.getText("doclet.errors"));
        String[] errorTableHeader = new String[]{
                configuration.getText("doclet.Error"),
                configuration.getText("doclet.Description")
        };
        ClassDoc[] errors =
                packageDoc.isIncluded()
                        ? packageDoc.errors()
                        : configuration.classDocCatalog.errors(
                        Util.getPackageName(packageDoc));
        if (errors.length > 0) {
            profilePackageWriter.addClassesSummary(
                    errors,
                    configuration.getText("doclet.Error_Summary"),
                    errorTableSummary, errorTableHeader, summaryContentTree);
        }
    }

    public void buildAnnotationTypeSummary(XMLNode node, Content summaryContentTree) {
        String annotationtypeTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Annotation_Types_Summary"),
                        configuration.getText("doclet.annotationtypes"));
        String[] annotationtypeTableHeader = new String[]{
                configuration.getText("doclet.AnnotationType"),
                configuration.getText("doclet.Description")
        };
        ClassDoc[] annotationTypes =
                packageDoc.isIncluded()
                        ? packageDoc.annotationTypes()
                        : configuration.classDocCatalog.annotationTypes(
                        Util.getPackageName(packageDoc));
        if (annotationTypes.length > 0) {
            profilePackageWriter.addClassesSummary(
                    annotationTypes,
                    configuration.getText("doclet.Annotation_Types_Summary"),
                    annotationtypeTableSummary, annotationtypeTableHeader,
                    summaryContentTree);
        }
    }

    public void buildPackageDescription(XMLNode node, Content packageContentTree) {
        if (configuration.nocomment) {
            return;
        }
        profilePackageWriter.addPackageDescription(packageContentTree);
    }

    public void buildPackageTags(XMLNode node, Content packageContentTree) {
        if (configuration.nocomment) {
            return;
        }
        profilePackageWriter.addPackageTags(packageContentTree);
    }
}
