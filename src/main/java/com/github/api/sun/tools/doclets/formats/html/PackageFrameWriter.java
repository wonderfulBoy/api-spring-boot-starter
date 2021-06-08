package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.PackageDoc;
import com.github.api.sun.tools.doclets.formats.html.markup.*;
import com.github.api.sun.tools.doclets.internal.toolkit.Configuration;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPaths;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocletAbortException;
import com.github.api.sun.tools.doclets.internal.toolkit.util.Util;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PackageFrameWriter extends HtmlDocletWriter {

    private PackageDoc packageDoc;

    private Set<ClassDoc> documentedClasses;

    public PackageFrameWriter(ConfigurationImpl configuration,
                              PackageDoc packageDoc)
            throws IOException {
        super(configuration, DocPath.forPackage(packageDoc).resolve(DocPaths.PACKAGE_FRAME));
        this.packageDoc = packageDoc;
        if (configuration.root.specifiedPackages().length == 0) {
            documentedClasses = new HashSet<ClassDoc>(Arrays.asList(configuration.root.classes()));
        }
    }

    public static void generate(ConfigurationImpl configuration,
                                PackageDoc packageDoc) {
        PackageFrameWriter packgen;
        try {
            packgen = new PackageFrameWriter(configuration, packageDoc);
            String pkgName = Util.getPackageName(packageDoc);
            Content body = packgen.getBody(false, packgen.getWindowTitle(pkgName));
            Content pkgNameContent = new StringContent(pkgName);
            Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, HtmlStyle.bar,
                    packgen.getTargetPackageLink(packageDoc, "classFrame", pkgNameContent));
            body.addContent(heading);
            HtmlTree div = new HtmlTree(HtmlTag.DIV);
            div.addStyle(HtmlStyle.indexContainer);
            packgen.addClassListing(div);
            body.addContent(div);
            packgen.printHtmlDocument(
                    configuration.metakeywords.getMetaKeywords(packageDoc), false, body);
            packgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), DocPaths.PACKAGE_FRAME.getPath());
            throw new DocletAbortException(exc);
        }
    }

    protected void addClassListing(Content contentTree) {
        Configuration config = configuration;
        if (packageDoc.isIncluded()) {
            addClassKindListing(packageDoc.interfaces(),
                    getResource("doclet.Interfaces"), contentTree);
            addClassKindListing(packageDoc.ordinaryClasses(),
                    getResource("doclet.Classes"), contentTree);
            addClassKindListing(packageDoc.enums(),
                    getResource("doclet.Enums"), contentTree);
            addClassKindListing(packageDoc.exceptions(),
                    getResource("doclet.Exceptions"), contentTree);
            addClassKindListing(packageDoc.errors(),
                    getResource("doclet.Errors"), contentTree);
            addClassKindListing(packageDoc.annotationTypes(),
                    getResource("doclet.AnnotationTypes"), contentTree);
        } else {
            String name = Util.getPackageName(packageDoc);
            addClassKindListing(config.classDocCatalog.interfaces(name),
                    getResource("doclet.Interfaces"), contentTree);
            addClassKindListing(config.classDocCatalog.ordinaryClasses(name),
                    getResource("doclet.Classes"), contentTree);
            addClassKindListing(config.classDocCatalog.enums(name),
                    getResource("doclet.Enums"), contentTree);
            addClassKindListing(config.classDocCatalog.exceptions(name),
                    getResource("doclet.Exceptions"), contentTree);
            addClassKindListing(config.classDocCatalog.errors(name),
                    getResource("doclet.Errors"), contentTree);
            addClassKindListing(config.classDocCatalog.annotationTypes(name),
                    getResource("doclet.AnnotationTypes"), contentTree);
        }
    }

    protected void addClassKindListing(ClassDoc[] arr, Content labelContent,
                                       Content contentTree) {
        arr = Util.filterOutPrivateClasses(arr, configuration.javafx);
        if (arr.length > 0) {
            Arrays.sort(arr);
            boolean printedHeader = false;
            HtmlTree ul = new HtmlTree(HtmlTag.UL);
            ul.setTitle(labelContent);
            for (int i = 0; i < arr.length; i++) {
                if (documentedClasses != null &&
                        !documentedClasses.contains(arr[i])) {
                    continue;
                }
                if (!Util.isCoreClass(arr[i]) || !
                        configuration.isGeneratedDoc(arr[i])) {
                    continue;
                }
                if (!printedHeader) {
                    Content heading = HtmlTree.HEADING(HtmlConstants.CONTENT_HEADING,
                            true, labelContent);
                    contentTree.addContent(heading);
                    printedHeader = true;
                }
                Content arr_i_name = new StringContent(arr[i].name());
                if (arr[i].isInterface()) arr_i_name = HtmlTree.SPAN(HtmlStyle.interfaceName, arr_i_name);
                Content link = getLink(new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.PACKAGE_FRAME, arr[i]).label(arr_i_name).target("classFrame"));
                Content li = HtmlTree.LI(link);
                ul.addContent(li);
            }
            contentTree.addContent(ul);
        }
    }
}
