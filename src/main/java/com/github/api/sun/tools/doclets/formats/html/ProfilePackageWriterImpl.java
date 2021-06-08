package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.PackageDoc;
import com.github.api.sun.javadoc.Tag;
import com.github.api.sun.tools.doclets.formats.html.markup.*;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.ProfilePackageSummaryWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPaths;
import com.github.api.sun.tools.doclets.internal.toolkit.util.Util;
import com.github.api.sun.tools.javac.jvm.Profile;

import java.io.IOException;

public class ProfilePackageWriterImpl extends HtmlDocletWriter
        implements ProfilePackageSummaryWriter {

    protected PackageDoc prev;

    protected PackageDoc next;

    protected PackageDoc packageDoc;

    protected String profileName;

    protected int profileValue;

    public ProfilePackageWriterImpl(ConfigurationImpl configuration,
                                    PackageDoc packageDoc, PackageDoc prev, PackageDoc next,
                                    Profile profile) throws IOException {
        super(configuration, DocPath.forPackage(packageDoc).resolve(
                DocPaths.profilePackageSummary(profile.name)));
        this.prev = prev;
        this.next = next;
        this.packageDoc = packageDoc;
        this.profileName = profile.name;
        this.profileValue = profile.value;
    }

    public Content getPackageHeader(String heading) {
        String pkgName = packageDoc.name();
        Content bodyTree = getBody(true, getWindowTitle(pkgName));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.header);
        Content profileContent = new StringContent(profileName);
        Content profileNameDiv = HtmlTree.DIV(HtmlStyle.subTitle, profileContent);
        div.addContent(profileNameDiv);
        Content annotationContent = new HtmlTree(HtmlTag.P);
        addAnnotationInfo(packageDoc, annotationContent);
        div.addContent(annotationContent);
        Content tHeading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, packageLabel);
        tHeading.addContent(getSpace());
        Content packageHead = new RawHtml(heading);
        tHeading.addContent(packageHead);
        div.addContent(tHeading);
        addDeprecationInfo(div);
        if (packageDoc.inlineTags().length > 0 && !configuration.nocomment) {
            HtmlTree docSummaryDiv = new HtmlTree(HtmlTag.DIV);
            docSummaryDiv.addStyle(HtmlStyle.docSummary);
            addSummaryComment(packageDoc, docSummaryDiv);
            div.addContent(docSummaryDiv);
            Content space = getSpace();
            Content descLink = getHyperLink(getDocLink(
                    SectionName.PACKAGE_DESCRIPTION),
                    descriptionLabel, "", "");
            Content descPara = new HtmlTree(HtmlTag.P, seeLabel, space, descLink);
            div.addContent(descPara);
        }
        bodyTree.addContent(div);
        return bodyTree;
    }

    public Content getContentHeader() {
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.contentContainer);
        return div;
    }

    public void addDeprecationInfo(Content div) {
        Tag[] deprs = packageDoc.tags("deprecated");
        if (Util.isDeprecated(packageDoc)) {
            HtmlTree deprDiv = new HtmlTree(HtmlTag.DIV);
            deprDiv.addStyle(HtmlStyle.deprecatedContent);
            Content deprPhrase = HtmlTree.SPAN(HtmlStyle.deprecatedLabel, deprecatedPhrase);
            deprDiv.addContent(deprPhrase);
            if (deprs.length > 0) {
                Tag[] commentTags = deprs[0].inlineTags();
                if (commentTags.length > 0) {
                    addInlineDeprecatedComment(packageDoc, deprs[0], deprDiv);
                }
            }
            div.addContent(deprDiv);
        }
    }

    public void addClassesSummary(ClassDoc[] classes, String label,
                                  String tableSummary, String[] tableHeader, Content packageSummaryContentTree) {
        HtmlTree li = new HtmlTree(HtmlTag.LI);
        li.addStyle(HtmlStyle.blockList);
        addClassesSummary(classes, label, tableSummary, tableHeader,
                li, profileValue);
        packageSummaryContentTree.addContent(li);
    }

    public Content getSummaryHeader() {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        return ul;
    }

    public void addPackageDescription(Content packageContentTree) {
        if (packageDoc.inlineTags().length > 0) {
            packageContentTree.addContent(
                    getMarkerAnchor(SectionName.PACKAGE_DESCRIPTION));
            Content h2Content = new StringContent(
                    configuration.getText("doclet.Package_Description",
                            packageDoc.name()));
            packageContentTree.addContent(HtmlTree.HEADING(HtmlConstants.PACKAGE_HEADING,
                    true, h2Content));
            addInlineComment(packageDoc, packageContentTree);
        }
    }

    public void addPackageTags(Content packageContentTree) {
        addTagsInfo(packageDoc, packageContentTree);
    }

    public void addPackageFooter(Content contentTree) {
        addNavLinks(false, contentTree);
        addBottom(contentTree);
    }

    public void printDocument(Content contentTree) throws IOException {
        printHtmlDocument(configuration.metakeywords.getMetaKeywords(packageDoc),
                true, contentTree);
    }

    protected Content getNavLinkClassUse() {
        Content useLink = getHyperLink(DocPaths.PACKAGE_USE,
                useLabel, "", "");
        Content li = HtmlTree.LI(useLink);
        return li;
    }

    public Content getNavLinkPrevious() {
        Content li;
        if (prev == null) {
            li = HtmlTree.LI(prevpackageLabel);
        } else {
            DocPath path = DocPath.relativePath(packageDoc, prev);
            li = HtmlTree.LI(getHyperLink(path.resolve(DocPaths.profilePackageSummary(profileName)),
                    prevpackageLabel, "", ""));
        }
        return li;
    }

    public Content getNavLinkNext() {
        Content li;
        if (next == null) {
            li = HtmlTree.LI(nextpackageLabel);
        } else {
            DocPath path = DocPath.relativePath(packageDoc, next);
            li = HtmlTree.LI(getHyperLink(path.resolve(DocPaths.profilePackageSummary(profileName)),
                    nextpackageLabel, "", ""));
        }
        return li;
    }

    protected Content getNavLinkTree() {
        Content useLink = getHyperLink(DocPaths.PACKAGE_TREE,
                treeLabel, "", "");
        Content li = HtmlTree.LI(useLink);
        return li;
    }

    protected Content getNavLinkPackage() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, packageLabel);
        return li;
    }
}
