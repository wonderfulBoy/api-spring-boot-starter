package com.sun.tools.doclets.internal.toolkit.builders;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.ProfileSummaryWriter;
import com.sun.tools.doclets.internal.toolkit.util.DocPaths;
import com.sun.tools.doclets.internal.toolkit.util.Util;
import com.sun.tools.javac.jvm.Profile;

import java.io.IOException;

public class ProfileSummaryBuilder extends AbstractBuilder {

    public static final String ROOT = "ProfileDoc";

    private final Profile profile;

    private final ProfileSummaryWriter profileWriter;

    private Content contentTree;

    private PackageDoc pkg;

    private ProfileSummaryBuilder(Context context,
                                  Profile profile, ProfileSummaryWriter profileWriter) {
        super(context);
        this.profile = profile;
        this.profileWriter = profileWriter;
    }

    public static ProfileSummaryBuilder getInstance(Context context,
                                                    Profile profile, ProfileSummaryWriter profileWriter) {
        return new ProfileSummaryBuilder(context, profile, profileWriter);
    }

    public void build() throws IOException {
        if (profileWriter == null) {

            return;
        }
        build(layoutParser.parseXML(ROOT), contentTree);
    }

    public String getName() {
        return ROOT;
    }

    public void buildProfileDoc(XMLNode node, Content contentTree) throws Exception {
        contentTree = profileWriter.getProfileHeader(profile.name);
        buildChildren(node, contentTree);
        profileWriter.addProfileFooter(contentTree);
        profileWriter.printDocument(contentTree);
        profileWriter.close();
        Util.copyDocFiles(configuration, DocPaths.profileSummary(profile.name));
    }

    public void buildContent(XMLNode node, Content contentTree) {
        Content profileContentTree = profileWriter.getContentHeader();
        buildChildren(node, profileContentTree);
        contentTree.addContent(profileContentTree);
    }

    public void buildSummary(XMLNode node, Content profileContentTree) {
        Content summaryContentTree = profileWriter.getSummaryHeader();
        buildChildren(node, summaryContentTree);
        profileContentTree.addContent(profileWriter.getSummaryTree(summaryContentTree));
    }

    public void buildPackageSummary(XMLNode node, Content summaryContentTree) {
        PackageDoc[] packages = configuration.profilePackages.get(profile.name);
        for (int i = 0; i < packages.length; i++) {
            this.pkg = packages[i];
            Content packageSummaryContentTree = profileWriter.getPackageSummaryHeader(this.pkg);
            buildChildren(node, packageSummaryContentTree);
            summaryContentTree.addContent(profileWriter.getPackageSummaryTree(
                    packageSummaryContentTree));
        }
    }

    public void buildInterfaceSummary(XMLNode node, Content packageSummaryContentTree) {
        String interfaceTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Interface_Summary"),
                        configuration.getText("doclet.interfaces"));
        String[] interfaceTableHeader = new String[]{
                configuration.getText("doclet.Interface"),
                configuration.getText("doclet.Description")
        };
        ClassDoc[] interfaces = pkg.interfaces();
        if (interfaces.length > 0) {
            profileWriter.addClassesSummary(
                    interfaces,
                    configuration.getText("doclet.Interface_Summary"),
                    interfaceTableSummary, interfaceTableHeader, packageSummaryContentTree);
        }
    }

    public void buildClassSummary(XMLNode node, Content packageSummaryContentTree) {
        String classTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Class_Summary"),
                        configuration.getText("doclet.classes"));
        String[] classTableHeader = new String[]{
                configuration.getText("doclet.Class"),
                configuration.getText("doclet.Description")
        };
        ClassDoc[] classes = pkg.ordinaryClasses();
        if (classes.length > 0) {
            profileWriter.addClassesSummary(
                    classes,
                    configuration.getText("doclet.Class_Summary"),
                    classTableSummary, classTableHeader, packageSummaryContentTree);
        }
    }

    public void buildEnumSummary(XMLNode node, Content packageSummaryContentTree) {
        String enumTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Enum_Summary"),
                        configuration.getText("doclet.enums"));
        String[] enumTableHeader = new String[]{
                configuration.getText("doclet.Enum"),
                configuration.getText("doclet.Description")
        };
        ClassDoc[] enums = pkg.enums();
        if (enums.length > 0) {
            profileWriter.addClassesSummary(
                    enums,
                    configuration.getText("doclet.Enum_Summary"),
                    enumTableSummary, enumTableHeader, packageSummaryContentTree);
        }
    }

    public void buildExceptionSummary(XMLNode node, Content packageSummaryContentTree) {
        String exceptionTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Exception_Summary"),
                        configuration.getText("doclet.exceptions"));
        String[] exceptionTableHeader = new String[]{
                configuration.getText("doclet.Exception"),
                configuration.getText("doclet.Description")
        };
        ClassDoc[] exceptions = pkg.exceptions();
        if (exceptions.length > 0) {
            profileWriter.addClassesSummary(
                    exceptions,
                    configuration.getText("doclet.Exception_Summary"),
                    exceptionTableSummary, exceptionTableHeader, packageSummaryContentTree);
        }
    }

    public void buildErrorSummary(XMLNode node, Content packageSummaryContentTree) {
        String errorTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Error_Summary"),
                        configuration.getText("doclet.errors"));
        String[] errorTableHeader = new String[]{
                configuration.getText("doclet.Error"),
                configuration.getText("doclet.Description")
        };
        ClassDoc[] errors = pkg.errors();
        if (errors.length > 0) {
            profileWriter.addClassesSummary(
                    errors,
                    configuration.getText("doclet.Error_Summary"),
                    errorTableSummary, errorTableHeader, packageSummaryContentTree);
        }
    }

    public void buildAnnotationTypeSummary(XMLNode node, Content packageSummaryContentTree) {
        String annotationtypeTableSummary =
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Annotation_Types_Summary"),
                        configuration.getText("doclet.annotationtypes"));
        String[] annotationtypeTableHeader = new String[]{
                configuration.getText("doclet.AnnotationType"),
                configuration.getText("doclet.Description")
        };
        ClassDoc[] annotationTypes = pkg.annotationTypes();
        if (annotationTypes.length > 0) {
            profileWriter.addClassesSummary(
                    annotationTypes,
                    configuration.getText("doclet.Annotation_Types_Summary"),
                    annotationtypeTableSummary, annotationtypeTableHeader,
                    packageSummaryContentTree);
        }
    }
}
