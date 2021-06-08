package com.github.api.sun.tools.doclets.internal.toolkit;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.LanguageVersion;
import com.github.api.sun.javadoc.PackageDoc;
import com.github.api.sun.javadoc.RootDoc;
import com.github.api.sun.tools.doclets.internal.toolkit.builders.AbstractBuilder;
import com.github.api.sun.tools.doclets.internal.toolkit.builders.BuilderFactory;
import com.github.api.sun.tools.doclets.internal.toolkit.util.*;

public abstract class AbstractDoclet {

    private static final String TOOLKIT_DOCLET_NAME =
            com.github.api.sun.tools.doclets.formats.html.HtmlDoclet.class.getName();
    public Configuration configuration;

    public static LanguageVersion languageVersion() {
        return LanguageVersion.JAVA_1_5;
    }

    private boolean isValidDoclet(AbstractDoclet doclet) {
        if (!doclet.getClass().getName().equals(TOOLKIT_DOCLET_NAME)) {
            configuration.message.error("doclet.Toolkit_Usage_Violation",
                    TOOLKIT_DOCLET_NAME);
            return false;
        }
        return true;
    }

    public boolean start(AbstractDoclet doclet, RootDoc root) {
        configuration = configuration();
        configuration.root = root;
        if (!isValidDoclet(doclet)) {
            return false;
        }
        try {
            doclet.startGeneration(root);
        } catch (Configuration.Fault f) {
            root.printError(f.getMessage());
            return false;
        } catch (DocletAbortException e) {
            Throwable cause = e.getCause();
            if (cause != null) {
                if (cause.getLocalizedMessage() != null) {
                    root.printError(cause.getLocalizedMessage());
                } else {
                    root.printError(cause.toString());
                }
            }
            return false;
        } catch (Exception exc) {
            exc.printStackTrace();
            return false;
        }
        return true;
    }

    public abstract Configuration configuration();

    private void startGeneration(RootDoc root) throws Exception {
        if (root.classes().length == 0) {
            configuration.message.
                    error("doclet.No_Public_Classes_To_Document");
            return;
        }
        configuration.setOptions();
        configuration.getDocletSpecificMsg().notice("doclet.build_version",
                configuration.getDocletSpecificBuildDate());
        ClassTree classtree = new ClassTree(configuration, configuration.nodeprecated);
        generateClassFiles(root, classtree);
        Util.copyDocFiles(configuration, DocPaths.DOC_FILES);
        PackageListWriter.generate(configuration);
        generatePackageFiles(classtree);
        generateProfileFiles();
        generateOtherFiles(root, classtree);
        configuration.tagletManager.printReport();
    }

    protected void generateOtherFiles(RootDoc root, ClassTree classtree) throws Exception {
        BuilderFactory builderFactory = configuration.getBuilderFactory();
        AbstractBuilder constantsSummaryBuilder = builderFactory.getConstantsSummaryBuider();
        constantsSummaryBuilder.build();
        AbstractBuilder serializedFormBuilder = builderFactory.getSerializedFormBuilder();
        serializedFormBuilder.build();
    }

    protected abstract void generateProfileFiles() throws Exception;

    protected abstract void generatePackageFiles(ClassTree classtree) throws Exception;

    protected abstract void generateClassFiles(ClassDoc[] arr, ClassTree classtree);

    protected void generateClassFiles(RootDoc root, ClassTree classtree) {
        generateClassFiles(classtree);
        PackageDoc[] packages = root.specifiedPackages();
        for (int i = 0; i < packages.length; i++) {
            generateClassFiles(packages[i].allClasses(), classtree);
        }
    }

    private void generateClassFiles(ClassTree classtree) {
        String[] packageNames = configuration.classDocCatalog.packageNames();
        for (int packageNameIndex = 0; packageNameIndex < packageNames.length;
             packageNameIndex++) {
            generateClassFiles(configuration.classDocCatalog.allClasses(
                    packageNames[packageNameIndex]), classtree);
        }
    }
}
