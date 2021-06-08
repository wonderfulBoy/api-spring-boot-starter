package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.tools.doclets.formats.html.markup.*;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPaths;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocletAbortException;
import com.github.api.sun.tools.javac.jvm.Profile;
import com.github.api.sun.tools.javac.sym.Profiles;

import java.io.IOException;

public class ProfileIndexFrameWriter extends AbstractProfileIndexWriter {

    public ProfileIndexFrameWriter(ConfigurationImpl configuration,
                                   DocPath filename) throws IOException {
        super(configuration, filename);
    }

    public static void generate(ConfigurationImpl configuration) {
        ProfileIndexFrameWriter profilegen;
        DocPath filename = DocPaths.PROFILE_OVERVIEW_FRAME;
        try {
            profilegen = new ProfileIndexFrameWriter(configuration, filename);
            profilegen.buildProfileIndexFile("doclet.Window_Overview", false);
            profilegen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }

    protected void addProfilesList(Profiles profiles, String text,
                                   String tableSummary, Content body) {
        Content heading = HtmlTree.HEADING(HtmlConstants.PROFILE_HEADING, true,
                profilesLabel);
        Content div = HtmlTree.DIV(HtmlStyle.indexContainer, heading);
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.setTitle(profilesLabel);
        String profileName;
        for (int i = 1; i < profiles.getProfileCount(); i++) {
            profileName = (Profile.lookup(i)).name;


            if (configuration.shouldDocumentProfile(profileName))
                ul.addContent(getProfile(profileName));
        }
        div.addContent(ul);
        body.addContent(div);
    }

    protected Content getProfile(String profileName) {
        Content profileLinkContent;
        Content profileLabel;
        profileLabel = new StringContent(profileName);
        profileLinkContent = getHyperLink(DocPaths.profileFrame(profileName), profileLabel, "",
                "packageListFrame");
        Content li = HtmlTree.LI(profileLinkContent);
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

    protected void addAllPackagesLink(Content div) {
        Content linkContent = getHyperLink(DocPaths.OVERVIEW_FRAME,
                allpackagesLabel, "", "packageListFrame");
        Content span = HtmlTree.SPAN(linkContent);
        div.addContent(span);
    }

    protected void addNavigationBarFooter(Content body) {
        Content p = HtmlTree.P(getSpace());
        body.addContent(p);
    }

    protected void addProfilePackagesList(Profiles profiles, String text,
                                          String tableSummary, Content body, String profileName) {
    }
}
