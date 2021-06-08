package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.PackageDoc;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocPath;

import java.io.IOException;
import java.util.Arrays;

public abstract class AbstractPackageIndexWriter extends HtmlDocletWriter {

    protected PackageDoc[] packages;

    public AbstractPackageIndexWriter(ConfigurationImpl configuration,
                                      DocPath filename) throws IOException {
        super(configuration, filename);
        packages = configuration.packages;
    }

    protected abstract void addNavigationBarHeader(Content body);

    protected abstract void addNavigationBarFooter(Content body);

    protected abstract void addOverviewHeader(Content body);

    protected abstract void addPackagesList(PackageDoc[] packages, String text,
                                            String tableSummary, Content body);

    protected void buildPackageIndexFile(String title, boolean includeScript) throws IOException {
        String windowOverview = configuration.getText(title);
        Content body = getBody(includeScript, getWindowTitle(windowOverview));
        addNavigationBarHeader(body);
        addOverviewHeader(body);
        addIndex(body);
        addOverview(body);
        addNavigationBarFooter(body);
        printHtmlDocument(configuration.metakeywords.getOverviewMetaKeywords(title,
                configuration.doctitle), includeScript, body);
    }

    protected void addOverview(Content body) throws IOException {
    }

    protected void addIndex(Content body) {
        addIndexContents(packages, "doclet.Package_Summary",
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Package_Summary"),
                        configuration.getText("doclet.packages")), body);
    }

    protected void addIndexContents(PackageDoc[] packages, String text,
                                    String tableSummary, Content body) {
        if (packages.length > 0) {
            Arrays.sort(packages);
            HtmlTree div = new HtmlTree(HtmlTag.DIV);
            div.addStyle(HtmlStyle.indexHeader);
            addAllClassesLink(div);
            if (configuration.showProfiles) {
                addAllProfilesLink(div);
            }
            body.addContent(div);
            if (configuration.showProfiles && configuration.profilePackages.size() > 0) {
                Content profileSummary = configuration.getResource("doclet.Profiles");
                addProfilesList(profileSummary, body);
            }
            addPackagesList(packages, text, tableSummary, body);
        }
    }

    protected void addConfigurationTitle(Content body) {
        if (configuration.doctitle.length() > 0) {
            Content title = new RawHtml(configuration.doctitle);
            Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING,
                    HtmlStyle.title, title);
            Content div = HtmlTree.DIV(HtmlStyle.header, heading);
            body.addContent(div);
        }
    }

    protected Content getNavLinkContents() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, overviewLabel);
        return li;
    }

    protected void addAllClassesLink(Content div) {
    }

    protected void addAllProfilesLink(Content div) {
    }

    protected void addProfilesList(Content profileSummary, Content body) {
    }
}
