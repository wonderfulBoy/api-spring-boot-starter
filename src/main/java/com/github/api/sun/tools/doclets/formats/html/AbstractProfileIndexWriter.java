package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.tools.doclets.formats.html.markup.*;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.github.api.sun.tools.javac.sym.Profiles;

import java.io.IOException;

public abstract class AbstractProfileIndexWriter extends HtmlDocletWriter {

    protected Profiles profiles;

    public AbstractProfileIndexWriter(ConfigurationImpl configuration,
                                      DocPath filename) throws IOException {
        super(configuration, filename);
        profiles = configuration.profiles;
    }

    protected abstract void addNavigationBarHeader(Content body);

    protected abstract void addNavigationBarFooter(Content body);

    protected abstract void addOverviewHeader(Content body);

    protected abstract void addProfilesList(Profiles profiles, String text,
                                            String tableSummary, Content body);

    protected abstract void addProfilePackagesList(Profiles profiles, String text,
                                                   String tableSummary, Content body, String profileName);

    protected void buildProfileIndexFile(String title, boolean includeScript) throws IOException {
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

    protected void buildProfilePackagesIndexFile(String title,
                                                 boolean includeScript, String profileName) throws IOException {
        String windowOverview = configuration.getText(title);
        Content body = getBody(includeScript, getWindowTitle(windowOverview));
        addNavigationBarHeader(body);
        addOverviewHeader(body);
        addProfilePackagesIndex(body, profileName);
        addOverview(body);
        addNavigationBarFooter(body);
        printHtmlDocument(configuration.metakeywords.getOverviewMetaKeywords(title,
                configuration.doctitle), includeScript, body);
    }

    protected void addOverview(Content body) throws IOException {
    }

    protected void addIndex(Content body) {
        addIndexContents(profiles, "doclet.Profile_Summary",
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Profile_Summary"),
                        configuration.getText("doclet.profiles")), body);
    }

    protected void addProfilePackagesIndex(Content body, String profileName) {
        addProfilePackagesIndexContents(profiles, "doclet.Profile_Summary",
                configuration.getText("doclet.Member_Table_Summary",
                        configuration.getText("doclet.Profile_Summary"),
                        configuration.getText("doclet.profiles")), body, profileName);
    }

    protected void addIndexContents(Profiles profiles, String text,
                                    String tableSummary, Content body) {
        if (profiles.getProfileCount() > 0) {
            HtmlTree div = new HtmlTree(HtmlTag.DIV);
            div.addStyle(HtmlStyle.indexHeader);
            addAllClassesLink(div);
            addAllPackagesLink(div);
            body.addContent(div);
            addProfilesList(profiles, text, tableSummary, body);
        }
    }

    protected void addProfilePackagesIndexContents(Profiles profiles, String text,
                                                   String tableSummary, Content body, String profileName) {
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.indexHeader);
        addAllClassesLink(div);
        addAllPackagesLink(div);
        addAllProfilesLink(div);
        body.addContent(div);
        addProfilePackagesList(profiles, text, tableSummary, body, profileName);
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

    protected void addAllPackagesLink(Content div) {
    }

    protected void addAllProfilesLink(Content div) {
    }
}
