package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.*;
import com.github.api.sun.tools.doclets.internal.toolkit.AbstractDoclet;
import com.github.api.sun.tools.doclets.internal.toolkit.Configuration;
import com.github.api.sun.tools.doclets.internal.toolkit.builders.AbstractBuilder;
import com.github.api.sun.tools.doclets.internal.toolkit.util.*;
import com.github.api.sun.tools.javac.jvm.Profile;

import java.io.IOException;
import java.util.Arrays;

public class HtmlDoclet extends AbstractDoclet {

    public static final ConfigurationImpl sharedInstanceForOptions =
            new ConfigurationImpl();
    private static HtmlDoclet docletToStart = null;
    public final ConfigurationImpl configuration;

    public HtmlDoclet() {
        configuration = new ConfigurationImpl();
    }

    public static boolean start(RootDoc root) {


        HtmlDoclet doclet;
        if (docletToStart != null) {
            doclet = docletToStart;
            docletToStart = null;
        } else {
            doclet = new HtmlDoclet();
        }
        return doclet.start(doclet, root);
    }

    public static int optionLength(String option) {

        return sharedInstanceForOptions.optionLength(option);
    }

    public static boolean validOptions(String[][] options,
                                       DocErrorReporter reporter) {
        docletToStart = new HtmlDoclet();
        return docletToStart.configuration.validOptions(options, reporter);
    }

    public Configuration configuration() {
        return configuration;
    }

    protected void generateOtherFiles(RootDoc root, ClassTree classtree)
            throws Exception {
        super.generateOtherFiles(root, classtree);
        if (configuration.linksource) {
            SourceToHTMLConverter.convertRoot(configuration,
                    root, DocPaths.SOURCE_OUTPUT);
        }
        if (configuration.topFile.isEmpty()) {
            configuration.standardmessage.
                    error("doclet.No_Non_Deprecated_Classes_To_Document");
            return;
        }
        boolean nodeprecated = configuration.nodeprecated;
        performCopy(configuration.helpfile);
        performCopy(configuration.stylesheetfile);

        if (configuration.classuse) {
            ClassUseWriter.generate(configuration, classtree);
        }
        IndexBuilder indexbuilder = new IndexBuilder(configuration, nodeprecated);
        if (configuration.createtree) {
            TreeWriter.generate(configuration, classtree);
        }
        if (configuration.createindex) {
            if (configuration.splitindex) {
                SplitIndexWriter.generate(configuration, indexbuilder);
            } else {
                SingleIndexWriter.generate(configuration, indexbuilder);
            }
        }
        if (!(configuration.nodeprecatedlist || nodeprecated)) {
            DeprecatedListWriter.generate(configuration);
        }
        AllClassesFrameWriter.generate(configuration,
                new IndexBuilder(configuration, nodeprecated, true));
        FrameOutputWriter.generate(configuration);
        if (configuration.createoverview) {
            PackageIndexWriter.generate(configuration);
        }
        if (configuration.helpfile.length() == 0 &&
                !configuration.nohelp) {
            HelpWriter.generate(configuration);
        }


        DocFile f;
        if (configuration.stylesheetfile.length() == 0) {
            f = DocFile.createFileForOutput(configuration, DocPaths.STYLESHEET);
            f.copyResource(DocPaths.RESOURCES.resolve(DocPaths.STYLESHEET), false, true);
        }
        f = DocFile.createFileForOutput(configuration, DocPaths.JAVASCRIPT);
        f.copyResource(DocPaths.RESOURCES.resolve(DocPaths.JAVASCRIPT), true, true);
    }

    protected void generateClassFiles(ClassDoc[] arr, ClassTree classtree) {
        Arrays.sort(arr);
        for (int i = 0; i < arr.length; i++) {
            if (!(configuration.isGeneratedDoc(arr[i]) && arr[i].isIncluded())) {
                continue;
            }
            ClassDoc prev = (i == 0) ?
                    null :
                    arr[i - 1];
            ClassDoc curr = arr[i];
            ClassDoc next = (i + 1 == arr.length) ?
                    null :
                    arr[i + 1];
            try {
                if (curr.isAnnotationType()) {
                    AbstractBuilder annotationTypeBuilder =
                            configuration.getBuilderFactory()
                                    .getAnnotationTypeBuilder((AnnotationTypeDoc) curr,
                                            prev, next);
                    annotationTypeBuilder.build();
                } else {
                    AbstractBuilder classBuilder =
                            configuration.getBuilderFactory()
                                    .getClassBuilder(curr, prev, next, classtree);
                    classBuilder.build();
                }
            } catch (IOException e) {
                throw new DocletAbortException(e);
            } catch (DocletAbortException de) {
                throw de;
            } catch (Exception e) {
                e.printStackTrace();
                throw new DocletAbortException(e);
            }
        }
    }

    protected void generateProfileFiles() throws Exception {
        if (configuration.showProfiles && configuration.profilePackages.size() > 0) {
            ProfileIndexFrameWriter.generate(configuration);
            Profile prevProfile = null, nextProfile;
            String profileName;
            for (int i = 1; i < configuration.profiles.getProfileCount(); i++) {
                profileName = Profile.lookup(i).name;


                if (!configuration.shouldDocumentProfile(profileName))
                    continue;
                ProfilePackageIndexFrameWriter.generate(configuration, profileName);
                PackageDoc[] packages = configuration.profilePackages.get(
                        profileName);
                PackageDoc prev = null, next;
                for (int j = 0; j < packages.length; j++) {


                    if (!(configuration.nodeprecated && Util.isDeprecated(packages[j]))) {
                        ProfilePackageFrameWriter.generate(configuration, packages[j], i);
                        next = (j + 1 < packages.length
                                && packages[j + 1].name().length() > 0) ? packages[j + 1] : null;
                        AbstractBuilder profilePackageSummaryBuilder =
                                configuration.getBuilderFactory().getProfilePackageSummaryBuilder(
                                        packages[j], prev, next, Profile.lookup(i));
                        profilePackageSummaryBuilder.build();
                        prev = packages[j];
                    }
                }
                nextProfile = (i + 1 < configuration.profiles.getProfileCount()) ?
                        Profile.lookup(i + 1) : null;
                AbstractBuilder profileSummaryBuilder =
                        configuration.getBuilderFactory().getProfileSummaryBuilder(
                                Profile.lookup(i), prevProfile, nextProfile);
                profileSummaryBuilder.build();
                prevProfile = Profile.lookup(i);
            }
        }
    }

    protected void generatePackageFiles(ClassTree classtree) throws Exception {
        PackageDoc[] packages = configuration.packages;
        if (packages.length > 1) {
            PackageIndexFrameWriter.generate(configuration);
        }
        PackageDoc prev = null, next;
        for (int i = 0; i < packages.length; i++) {


            if (!(configuration.nodeprecated && Util.isDeprecated(packages[i]))) {
                PackageFrameWriter.generate(configuration, packages[i]);
                next = (i + 1 < packages.length &&
                        packages[i + 1].name().length() > 0) ? packages[i + 1] : null;

                next = (i + 2 < packages.length && next == null) ? packages[i + 2] : next;
                AbstractBuilder packageSummaryBuilder =
                        configuration.getBuilderFactory().getPackageSummaryBuilder(
                                packages[i], prev, next);
                packageSummaryBuilder.build();
                if (configuration.createtree) {
                    PackageTreeWriter.generate(configuration,
                            packages[i], prev, next,
                            configuration.nodeprecated);
                }
                prev = packages[i];
            }
        }
    }

    private void performCopy(String filename) {
        if (filename.isEmpty())
            return;
        try {
            DocFile fromfile = DocFile.createFileForInput(configuration, filename);
            DocPath path = DocPath.create(fromfile.getName());
            DocFile toFile = DocFile.createFileForOutput(configuration, path);
            if (toFile.isSameFile(fromfile))
                return;
            configuration.message.notice((SourcePosition) null,
                    "doclet.Copying_File_0_To_File_1",
                    fromfile.toString(), path.getPath());
            toFile.copyFile(fromfile);
        } catch (IOException exc) {
            configuration.message.error((SourcePosition) null,
                    "doclet.perform_copy_exception_encountered",
                    exc.toString());
            throw new DocletAbortException(exc);
        }
    }
}
