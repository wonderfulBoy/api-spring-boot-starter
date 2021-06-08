package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.sun.tools.doclets.internal.toolkit.util.DocPaths;
import com.sun.tools.doclets.internal.toolkit.util.DocletAbortException;
import com.sun.tools.doclets.internal.toolkit.util.Util;
import com.sun.tools.javac.jvm.Profile;

import java.io.IOException;
import java.util.Arrays;

public class ProfilePackageFrameWriter extends HtmlDocletWriter {

    private PackageDoc packageDoc;

    public ProfilePackageFrameWriter(ConfigurationImpl configuration,
                                     PackageDoc packageDoc, String profileName)
            throws IOException {
        super(configuration, DocPath.forPackage(packageDoc).resolve(
                DocPaths.profilePackageFrame(profileName)));
        this.packageDoc = packageDoc;
    }

    public static void generate(ConfigurationImpl configuration,
                                PackageDoc packageDoc, int profileValue) {
        ProfilePackageFrameWriter profpackgen;
        try {
            String profileName = Profile.lookup(profileValue).name;
            profpackgen = new ProfilePackageFrameWriter(configuration, packageDoc,
                    profileName);
            StringBuilder winTitle = new StringBuilder(profileName);
            String sep = " - ";
            winTitle.append(sep);
            String pkgName = Util.getPackageName(packageDoc);
            winTitle.append(pkgName);
            Content body = profpackgen.getBody(false,
                    profpackgen.getWindowTitle(winTitle.toString()));
            Content profName = new StringContent(profileName);
            Content sepContent = new StringContent(sep);
            Content pkgNameContent = new RawHtml(pkgName);
            Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, HtmlStyle.bar,
                    profpackgen.getTargetProfileLink("classFrame", profName, profileName));
            heading.addContent(sepContent);
            heading.addContent(profpackgen.getTargetProfilePackageLink(packageDoc,
                    "classFrame", pkgNameContent, profileName));
            body.addContent(heading);
            HtmlTree div = new HtmlTree(HtmlTag.DIV);
            div.addStyle(HtmlStyle.indexContainer);
            profpackgen.addClassListing(div, profileValue);
            body.addContent(div);
            profpackgen.printHtmlDocument(
                    configuration.metakeywords.getMetaKeywords(packageDoc), false, body);
            profpackgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), DocPaths.PACKAGE_FRAME.getPath());
            throw new DocletAbortException(exc);
        }
    }

    protected void addClassListing(Content contentTree, int profileValue) {
        if (packageDoc.isIncluded()) {
            addClassKindListing(packageDoc.interfaces(),
                    getResource("doclet.Interfaces"), contentTree, profileValue);
            addClassKindListing(packageDoc.ordinaryClasses(),
                    getResource("doclet.Classes"), contentTree, profileValue);
            addClassKindListing(packageDoc.enums(),
                    getResource("doclet.Enums"), contentTree, profileValue);
            addClassKindListing(packageDoc.exceptions(),
                    getResource("doclet.Exceptions"), contentTree, profileValue);
            addClassKindListing(packageDoc.errors(),
                    getResource("doclet.Errors"), contentTree, profileValue);
            addClassKindListing(packageDoc.annotationTypes(),
                    getResource("doclet.AnnotationTypes"), contentTree, profileValue);
        }
    }

    protected void addClassKindListing(ClassDoc[] arr, Content labelContent,
                                       Content contentTree, int profileValue) {
        if (arr.length > 0) {
            Arrays.sort(arr);
            boolean printedHeader = false;
            HtmlTree ul = new HtmlTree(HtmlTag.UL);
            ul.setTitle(labelContent);
            for (int i = 0; i < arr.length; i++) {
                if (!isTypeInProfile(arr[i], profileValue)) {
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
