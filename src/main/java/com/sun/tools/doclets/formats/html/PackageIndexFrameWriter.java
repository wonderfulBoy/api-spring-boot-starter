package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.PackageDoc;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.sun.tools.doclets.internal.toolkit.util.DocPaths;
import com.sun.tools.doclets.internal.toolkit.util.DocletAbortException;
import com.sun.tools.doclets.internal.toolkit.util.Util;

import java.io.IOException;

public class PackageIndexFrameWriter extends AbstractPackageIndexWriter {

    public PackageIndexFrameWriter(ConfigurationImpl configuration,
                                   DocPath filename) throws IOException {
        super(configuration, filename);
    }

    public static void generate(ConfigurationImpl configuration) {
        PackageIndexFrameWriter packgen;
        DocPath filename = DocPaths.OVERVIEW_FRAME;
        try {
            packgen = new PackageIndexFrameWriter(configuration, filename);
            packgen.buildPackageIndexFile("doclet.Window_Overview", false);
            packgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }

    protected void addPackagesList(PackageDoc[] packages, String text,
                                   String tableSummary, Content body) {
        Content heading = HtmlTree.HEADING(HtmlConstants.PACKAGE_HEADING, true,
                packagesLabel);
        Content div = HtmlTree.DIV(HtmlStyle.indexContainer, heading);
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.setTitle(packagesLabel);
        for (int i = 0; i < packages.length; i++) {


            if (packages[i] != null &&
                    (!(configuration.nodeprecated && Util.isDeprecated(packages[i])))) {
                ul.addContent(getPackage(packages[i]));
            }
        }
        div.addContent(ul);
        body.addContent(div);
    }

    protected Content getPackage(PackageDoc pd) {
        Content packageLinkContent;
        Content packageLabel;
        if (pd.name().length() > 0) {
            packageLabel = getPackageLabel(pd.name());
            packageLinkContent = getHyperLink(pathString(pd,
                    DocPaths.PACKAGE_FRAME), packageLabel, "",
                    "packageFrame");
        } else {
            packageLabel = new StringContent("<unnamed package>");
            packageLinkContent = getHyperLink(DocPaths.PACKAGE_FRAME,
                    packageLabel, "", "packageFrame");
        }
        Content li = HtmlTree.LI(packageLinkContent);
        return li;
    }

    protected void addNavigationBarHeader(Content body) {
        Content headerContent;
        if (configuration.packagesheader.length() > 0) {
            headerContent = new RawHtml(replaceDocRootDir(configuration.packagesheader));
        } else {
            headerContent = new RawHtml(replaceDocRootDir(configuration.header));
        }
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.bar, headerContent);
        body.addContent(heading);
    }

    protected void addOverviewHeader(Content body) {
    }

    protected void addAllClassesLink(Content div) {
        Content linkContent = getHyperLink(DocPaths.ALLCLASSES_FRAME,
                allclassesLabel, "", "packageFrame");
        Content span = HtmlTree.SPAN(linkContent);
        div.addContent(span);
    }

    protected void addAllProfilesLink(Content div) {
        Content linkContent = getHyperLink(DocPaths.PROFILE_OVERVIEW_FRAME,
                allprofilesLabel, "", "packageListFrame");
        Content span = HtmlTree.SPAN(linkContent);
        div.addContent(span);
    }

    protected void addNavigationBarFooter(Content body) {
        Content p = HtmlTree.P(getSpace());
        body.addContent(p);
    }
}
