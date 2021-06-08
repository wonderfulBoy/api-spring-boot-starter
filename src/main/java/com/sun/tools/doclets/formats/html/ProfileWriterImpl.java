package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.Tag;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.ProfileSummaryWriter;
import com.sun.tools.doclets.internal.toolkit.util.DocPaths;
import com.sun.tools.doclets.internal.toolkit.util.Util;
import com.sun.tools.javac.jvm.Profile;

import java.io.IOException;

public class ProfileWriterImpl extends HtmlDocletWriter
        implements ProfileSummaryWriter {

    protected Profile prevProfile;

    protected Profile nextProfile;

    protected Profile profile;

    public ProfileWriterImpl(ConfigurationImpl configuration,
                             Profile profile, Profile prevProfile, Profile nextProfile)
            throws IOException {
        super(configuration, DocPaths.profileSummary(profile.name));
        this.prevProfile = prevProfile;
        this.nextProfile = nextProfile;
        this.profile = profile;
    }

    public Content getProfileHeader(String heading) {
        String profileName = profile.name;
        Content bodyTree = getBody(true, getWindowTitle(profileName));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.header);
        Content tHeading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, profileLabel);
        tHeading.addContent(getSpace());
        Content profileHead = new RawHtml(heading);
        tHeading.addContent(profileHead);
        div.addContent(tHeading);
        bodyTree.addContent(div);
        return bodyTree;
    }

    public Content getContentHeader() {
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.contentContainer);
        return div;
    }

    public Content getSummaryHeader() {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        li.addStyle(HtmlStyle.blockList);
        return li;
    }

    public Content getSummaryTree(Content summaryContentTree) {
        HtmlTree ul = HtmlTree.UL(HtmlStyle.blockList, summaryContentTree);
        HtmlTree div = HtmlTree.DIV(HtmlStyle.summary, ul);
        return div;
    }

    public Content getPackageSummaryHeader(PackageDoc pkg) {
        Content pkgName = getTargetProfilePackageLink(pkg,
                "classFrame", new StringContent(pkg.name()), profile.name);
        Content heading = HtmlTree.HEADING(HtmlTag.H3, pkgName);
        HtmlTree li = HtmlTree.LI(HtmlStyle.blockList, heading);
        addPackageDeprecationInfo(li, pkg);
        return li;
    }

    public Content getPackageSummaryTree(Content packageSummaryContentTree) {
        HtmlTree ul = HtmlTree.UL(HtmlStyle.blockList, packageSummaryContentTree);
        return ul;
    }

    public void addClassesSummary(ClassDoc[] classes, String label,
                                  String tableSummary, String[] tableHeader, Content packageSummaryContentTree) {
        addClassesSummary(classes, label, tableSummary, tableHeader,
                packageSummaryContentTree, profile.value);
    }

    public void addProfileFooter(Content contentTree) {
        addNavLinks(false, contentTree);
        addBottom(contentTree);
    }

    public void printDocument(Content contentTree) throws IOException {
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(profile),
                true, contentTree);
    }

    public void addPackageDeprecationInfo(Content li, PackageDoc pkg) {
        Tag[] deprs;
        if (Util.isDeprecated(pkg)) {
            deprs = pkg.tags("deprecated");
            HtmlTree deprDiv = new HtmlTree(HtmlTag.DIV);
            deprDiv.addStyle(HtmlStyle.deprecatedContent);
            Content deprPhrase = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
            deprDiv.addContent(deprPhrase);
            if (deprs.length > 0) {
                Tag[] commentTags = deprs[0].inlineTags();
                if (commentTags.length > 0) {
                    addInlineDeprecatedComment(pkg, deprs[0], deprDiv);
                }
            }
            li.addContent(deprDiv);
        }
    }

    public Content getNavLinkPrevious() {
        Content li;
        if (prevProfile == null) {
            li = HtmlTree.LI(prevprofileLabel);
        } else {
            li = HtmlTree.LI(getHyperLink(pathToRoot.resolve(DocPaths.profileSummary(
                    prevProfile.name)), prevprofileLabel, "", ""));
        }
        return li;
    }

    public Content getNavLinkNext() {
        Content li;
        if (nextProfile == null) {
            li = HtmlTree.LI(nextprofileLabel);
        } else {
            li = HtmlTree.LI(getHyperLink(pathToRoot.resolve(DocPaths.profileSummary(
                    nextProfile.name)), nextprofileLabel, "", ""));
        }
        return li;
    }
}
