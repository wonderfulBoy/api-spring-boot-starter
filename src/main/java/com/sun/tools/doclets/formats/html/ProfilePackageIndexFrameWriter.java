package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.PackageDoc;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.sun.tools.doclets.internal.toolkit.util.DocPaths;
import com.sun.tools.doclets.internal.toolkit.util.DocletAbortException;
import com.sun.tools.doclets.internal.toolkit.util.Util;
import com.sun.tools.javac.sym.Profiles;

import java.io.IOException;

public class ProfilePackageIndexFrameWriter extends AbstractProfileIndexWriter {

    public ProfilePackageIndexFrameWriter(ConfigurationImpl configuration,
                                          DocPath filename) throws IOException {
        super(configuration, filename);
    }

    public static void generate(ConfigurationImpl configuration, String profileName) {
        ProfilePackageIndexFrameWriter profpackgen;
        DocPath filename = DocPaths.profileFrame(profileName);
        try {
            profpackgen = new ProfilePackageIndexFrameWriter(configuration, filename);
            profpackgen.buildProfilePackagesIndexFile("doclet.Window_Overview", false, profileName);
            profpackgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }

    protected void addProfilePackagesList(Profiles profiles, String text,
                                          String tableSummary, Content body, String profileName) {
        Content profNameContent = new StringContent(profileName);
        Content heading = HtmlTree.HEADING(HtmlConstants.PACKAGE_HEADING, true,
                getTargetProfileLink("classFrame", profNameContent, profileName));
        heading.addContent(getSpace());
        heading.addContent(packagesLabel);
        Content div = HtmlTree.DIV(HtmlStyle.indexContainer, heading);
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.setTitle(packagesLabel);
        PackageDoc[] packages = configuration.profilePackages.get(profileName);
        for (int i = 0; i < packages.length; i++) {
            if ((!(configuration.nodeprecated && Util.isDeprecated(packages[i])))) {
                ul.addContent(getPackage(packages[i], profileName));
            }
        }
        div.addContent(ul);
        body.addContent(div);
    }

    protected Content getPackage(PackageDoc pd, String profileName) {
        Content packageLinkContent;
        Content pkgLabel;
        if (pd.name().length() > 0) {
            pkgLabel = getPackageLabel(pd.name());
            packageLinkContent = getHyperLink(pathString(pd,
                    DocPaths.profilePackageFrame(profileName)), pkgLabel, "",
                    "packageFrame");
        } else {
            pkgLabel = new StringContent("<unnamed package>");
            packageLinkContent = getHyperLink(DocPaths.PACKAGE_FRAME,
                    pkgLabel, "", "packageFrame");
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

    protected void addProfilesList(Profiles profiles, String text,
                                   String tableSummary, Content body) {
    }

    protected void addAllClassesLink(Content div) {
        Content linkContent = getHyperLink(DocPaths.ALLCLASSES_FRAME,
                allclassesLabel, "", "packageFrame");
        Content span = HtmlTree.SPAN(linkContent);
        div.addContent(span);
    }

    protected void addAllPackagesLink(Content div) {
        Content linkContent = getHyperLink(DocPaths.OVERVIEW_FRAME,
                allpackagesLabel, "", "packageListFrame");
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
