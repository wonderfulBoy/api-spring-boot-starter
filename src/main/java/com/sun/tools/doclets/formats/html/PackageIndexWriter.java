package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.RootDoc;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.sun.tools.doclets.internal.toolkit.util.DocPaths;
import com.sun.tools.doclets.internal.toolkit.util.DocletAbortException;
import com.sun.tools.doclets.internal.toolkit.util.Util;
import com.sun.tools.javac.jvm.Profile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class PackageIndexWriter extends AbstractPackageIndexWriter {

    private RootDoc root;

    private Map<String, List<PackageDoc>> groupPackageMap;

    private List<String> groupList;

    public PackageIndexWriter(ConfigurationImpl configuration,
                              DocPath filename)
            throws IOException {
        super(configuration, filename);
        this.root = configuration.root;
        groupPackageMap = configuration.group.groupPackages(packages);
        groupList = configuration.group.getGroupList();
    }

    public static void generate(ConfigurationImpl configuration) {
        PackageIndexWriter packgen;
        DocPath filename = DocPaths.OVERVIEW_SUMMARY;
        try {
            packgen = new PackageIndexWriter(configuration, filename);
            packgen.buildPackageIndexFile("doclet.Window_Overview_Summary", true);
            packgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }

    protected void addIndex(Content body) {
        for (int i = 0; i < groupList.size(); i++) {
            String groupname = groupList.get(i);
            List<PackageDoc> list = groupPackageMap.get(groupname);
            if (list != null && list.size() > 0) {
                addIndexContents(list.toArray(new PackageDoc[list.size()]),
                        groupname, configuration.getText("doclet.Member_Table_Summary",
                                groupname, configuration.getText("doclet.packages")), body);
            }
        }
    }

    protected void addProfilesList(Content profileSummary, Content body) {
        Content h2 = HtmlTree.HEADING(HtmlTag.H2, profileSummary);
        Content profilesDiv = HtmlTree.DIV(h2);
        Content ul = new HtmlTree(HtmlTag.UL);
        String profileName;
        for (int i = 1; i < configuration.profiles.getProfileCount(); i++) {
            profileName = Profile.lookup(i).name;


            if (configuration.shouldDocumentProfile(profileName)) {
                Content profileLinkContent = getTargetProfileLink("classFrame",
                        new StringContent(profileName), profileName);
                Content li = HtmlTree.LI(profileLinkContent);
                ul.addContent(li);
            }
        }
        profilesDiv.addContent(ul);
        Content div = HtmlTree.DIV(HtmlStyle.contentContainer, profilesDiv);
        body.addContent(div);
    }

    protected void addPackagesList(PackageDoc[] packages, String text,
                                   String tableSummary, Content body) {
        Content table = HtmlTree.TABLE(HtmlStyle.overviewSummary, 0, 3, 0, tableSummary,
                getTableCaption(new RawHtml(text)));
        table.addContent(getSummaryTableHeader(packageTableHeader, "col"));
        Content tbody = new HtmlTree(HtmlTag.TBODY);
        addPackagesList(packages, tbody);
        table.addContent(tbody);
        Content div = HtmlTree.DIV(HtmlStyle.contentContainer, table);
        body.addContent(div);
    }

    protected void addPackagesList(PackageDoc[] packages, Content tbody) {
        for (int i = 0; i < packages.length; i++) {
            if (packages[i] != null && packages[i].name().length() > 0) {
                if (configuration.nodeprecated && Util.isDeprecated(packages[i]))
                    continue;
                Content packageLinkContent = getPackageLink(packages[i],
                        getPackageName(packages[i]));
                Content tdPackage = HtmlTree.TD(HtmlStyle.colFirst, packageLinkContent);
                HtmlTree tdSummary = new HtmlTree(HtmlTag.TD);
                tdSummary.addStyle(HtmlStyle.colLast);
                addSummaryComment(packages[i], tdSummary);
                HtmlTree tr = HtmlTree.TR(tdPackage);
                tr.addContent(tdSummary);
                if (i % 2 == 0)
                    tr.addStyle(HtmlStyle.altColor);
                else
                    tr.addStyle(HtmlStyle.rowColor);
                tbody.addContent(tr);
            }
        }
    }

    protected void addOverviewHeader(Content body) {
        if (root.inlineTags().length > 0) {
            HtmlTree subTitleDiv = new HtmlTree(HtmlTag.DIV);
            subTitleDiv.addStyle(HtmlStyle.subTitle);
            addSummaryComment(root, subTitleDiv);
            Content div = HtmlTree.DIV(HtmlStyle.header, subTitleDiv);
            Content see = seeLabel;
            see.addContent(" ");
            Content descPara = HtmlTree.P(see);
            Content descLink = getHyperLink(getDocLink(
                    SectionName.OVERVIEW_DESCRIPTION),
                    descriptionLabel, "", "");
            descPara.addContent(descLink);
            div.addContent(descPara);
            body.addContent(div);
        }
    }

    protected void addOverviewComment(Content htmltree) {
        if (root.inlineTags().length > 0) {
            htmltree.addContent(
                    getMarkerAnchor(SectionName.OVERVIEW_DESCRIPTION));
            addInlineComment(root, htmltree);
        }
    }

    protected void addOverview(Content body) throws IOException {
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.contentContainer);
        addOverviewComment(div);
        addTagsInfo(root, div);
        body.addContent(div);
    }

    protected void addNavigationBarHeader(Content body) {
        addTop(body);
        addNavLinks(true, body);
        addConfigurationTitle(body);
    }

    protected void addNavigationBarFooter(Content body) {
        addNavLinks(false, body);
        addBottom(body);
    }
}
