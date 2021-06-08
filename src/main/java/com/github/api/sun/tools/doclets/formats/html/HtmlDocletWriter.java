package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.*;
import com.github.api.sun.tools.doclets.formats.html.markup.*;
import com.github.api.sun.tools.doclets.internal.toolkit.*;
import com.github.api.sun.tools.doclets.internal.toolkit.taglets.DocRootTaglet;
import com.github.api.sun.tools.doclets.internal.toolkit.taglets.TagletWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.util.*;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class HtmlDocletWriter extends HtmlDocWriter {

    static final Set<String> blockTags = new HashSet<String>();

    static {
        for (HtmlTag t : HtmlTag.values()) {
            if (t.blockType == HtmlTag.BlockType.BLOCK)
                blockTags.add(t.value);
        }
    }

    public final DocPath pathToRoot;
    public final DocPath path;
    public final DocPath filename;
    public final ConfigurationImpl configuration;
    protected boolean printedAnnotationHeading = false;
    protected boolean printedAnnotationFieldHeading = false;
    private boolean isAnnotationDocumented = false;
    private boolean isContainerDocumented = false;

    public HtmlDocletWriter(ConfigurationImpl configuration, DocPath path)
            throws IOException {
        super(configuration, path);
        this.configuration = configuration;
        this.path = path;
        this.pathToRoot = path.parent().invert();
        this.filename = path.basename();
    }

    public static String removeNonInlineHtmlTags(String text) {
        final int len = text.length();
        int startPos = 0;
        int lessThanPos = text.indexOf('<');
        if (lessThanPos < 0) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        main:
        while (lessThanPos != -1) {
            int currPos = lessThanPos + 1;
            if (currPos == len)
                break;
            char ch = text.charAt(currPos);
            if (ch == '/') {
                if (++currPos == len)
                    break;
                ch = text.charAt(currPos);
            }
            int tagPos = currPos;
            while (isHtmlTagLetterOrDigit(ch)) {
                if (++currPos == len)
                    break main;
                ch = text.charAt(currPos);
            }
            if (ch == '>' && blockTags.contains(text.substring(tagPos, currPos).toLowerCase())) {
                result.append(text, startPos, lessThanPos);
                startPos = currPos + 1;
            }
            lessThanPos = text.indexOf('<', currPos);
        }
        result.append(text.substring(startPos));
        return result.toString();
    }

    private static boolean isHtmlTagLetterOrDigit(char ch) {
        return ('a' <= ch && ch <= 'z') ||
                ('A' <= ch && ch <= 'Z') ||
                ('1' <= ch && ch <= '6');
    }

    public String replaceDocRootDir(String htmlstr) {

        int index = htmlstr.indexOf("{@");
        if (index < 0) {
            return htmlstr;
        }
        String lowerHtml = htmlstr.toLowerCase();


        index = lowerHtml.indexOf("{@docroot}", index);
        if (index < 0) {
            return htmlstr;
        }
        StringBuilder buf = new StringBuilder();
        int previndex = 0;
        while (true) {
            final String docroot = "{@docroot}";

            index = lowerHtml.indexOf(docroot, previndex);

            if (index < 0) {
                buf.append(htmlstr.substring(previndex));
                break;
            }

            buf.append(htmlstr, previndex, index);
            previndex = index + docroot.length();
            if (configuration.docrootparent.length() > 0 && htmlstr.startsWith("/..", previndex)) {

                buf.append(configuration.docrootparent);
                previndex += 3;
            } else {

                buf.append(pathToRoot.isEmpty() ? "." : pathToRoot.getPath());
            }

            if (previndex < htmlstr.length() && htmlstr.charAt(previndex) != '/') {
                buf.append('/');
            }
        }
        return buf.toString();
    }

    public Content getAllClassesLinkScript(String id) {
        HtmlTree script = new HtmlTree(HtmlTag.SCRIPT);
        script.addAttr(HtmlAttr.TYPE, "text/javascript");
        String scriptCode = "<!--" + DocletConstants.NL +
                "  allClassesLink = document.getElementById(\"" + id + "\");" + DocletConstants.NL +
                "  if(window==top) {" + DocletConstants.NL +
                "    allClassesLink.style.display = \"block\";" + DocletConstants.NL +
                "  }" + DocletConstants.NL +
                "  else {" + DocletConstants.NL +
                "    allClassesLink.style.display = \"none\";" + DocletConstants.NL +
                "  }" + DocletConstants.NL +
                "  //-->" + DocletConstants.NL;
        Content scriptContent = new RawHtml(scriptCode);
        script.addContent(scriptContent);
        Content div = HtmlTree.DIV(script);
        return div;
    }

    private void addMethodInfo(MethodDoc method, Content dl) {
        ClassDoc[] intfacs = method.containingClass().interfaces();
        MethodDoc overriddenMethod = method.overriddenMethod();


        if ((intfacs.length > 0 &&
                new ImplementedMethods(method, this.configuration).build().length > 0) ||
                overriddenMethod != null) {
            MethodWriterImpl.addImplementsInfo(this, method, dl);
            if (overriddenMethod != null) {
                MethodWriterImpl.addOverridden(this,
                        method.overriddenType(), overriddenMethod, dl);
            }
        }
    }

    protected void addTagsInfo(Doc doc, Content htmltree) {
        if (configuration.nocomment) {
            return;
        }
        Content dl = new HtmlTree(HtmlTag.DL);
        if (doc instanceof MethodDoc) {
            addMethodInfo((MethodDoc) doc, dl);
        }
        Content output = new ContentBuilder();
        TagletWriter.genTagOuput(configuration.tagletManager, doc,
                configuration.tagletManager.getCustomTaglets(doc),
                getTagletWriterInstance(false), output);
        dl.addContent(output);
        htmltree.addContent(dl);
    }

    protected boolean hasSerializationOverviewTags(FieldDoc field) {
        Content output = new ContentBuilder();
        TagletWriter.genTagOuput(configuration.tagletManager, field,
                configuration.tagletManager.getCustomTaglets(field),
                getTagletWriterInstance(false), output);
        return !output.isEmpty();
    }

    public TagletWriter getTagletWriterInstance(boolean isFirstSentence) {
        return new TagletWriterImpl(this, isFirstSentence);
    }

    public Content getTargetPackageLink(PackageDoc pd, String target,
                                        Content label) {
        return getHyperLink(pathString(pd, DocPaths.PACKAGE_SUMMARY), label, "", target);
    }

    public Content getTargetProfilePackageLink(PackageDoc pd, String target,
                                               Content label, String profileName) {
        return getHyperLink(pathString(pd, DocPaths.profilePackageSummary(profileName)),
                label, "", target);
    }

    public Content getTargetProfileLink(String target, Content label,
                                        String profileName) {
        return getHyperLink(pathToRoot.resolve(
                DocPaths.profileSummary(profileName)), label, "", target);
    }

    public String getTypeNameForProfile(ClassDoc cd) {
        StringBuilder typeName =
                new StringBuilder((cd.containingPackage()).name().replace(".", "/"));
        typeName.append("/")
                .append(cd.name().replace(".", "$"));
        return typeName.toString();
    }

    public boolean isTypeInProfile(ClassDoc cd, int profileValue) {
        return (configuration.profiles.getProfile(getTypeNameForProfile(cd)) <= profileValue);
    }

    public void addClassesSummary(ClassDoc[] classes, String label,
                                  String tableSummary, String[] tableHeader, Content summaryContentTree,
                                  int profileValue) {
        if (classes.length > 0) {
            Arrays.sort(classes);
            Content caption = getTableCaption(new RawHtml(label));
            Content table = HtmlTree.TABLE(HtmlStyle.typeSummary, 0, 3, 0,
                    tableSummary, caption);
            table.addContent(getSummaryTableHeader(tableHeader, "col"));
            Content tbody = new HtmlTree(HtmlTag.TBODY);
            for (int i = 0; i < classes.length; i++) {
                if (!isTypeInProfile(classes[i], profileValue)) {
                    continue;
                }
                if (!Util.isCoreClass(classes[i]) ||
                        !configuration.isGeneratedDoc(classes[i])) {
                    continue;
                }
                Content classContent = getLink(new LinkInfoImpl(
                        configuration, LinkInfoImpl.Kind.PACKAGE, classes[i]));
                Content tdClass = HtmlTree.TD(HtmlStyle.colFirst, classContent);
                HtmlTree tr = HtmlTree.TR(tdClass);
                if (i % 2 == 0)
                    tr.addStyle(HtmlStyle.altColor);
                else
                    tr.addStyle(HtmlStyle.rowColor);
                HtmlTree tdClassDescription = new HtmlTree(HtmlTag.TD);
                tdClassDescription.addStyle(HtmlStyle.colLast);
                if (Util.isDeprecated(classes[i])) {
                    tdClassDescription.addContent(deprecatedLabel);
                    if (classes[i].tags("deprecated").length > 0) {
                        addSummaryDeprecatedComment(classes[i],
                                classes[i].tags("deprecated")[0], tdClassDescription);
                    }
                } else
                    addSummaryComment(classes[i], tdClassDescription);
                tr.addContent(tdClassDescription);
                tbody.addContent(tr);
            }
            table.addContent(tbody);
            summaryContentTree.addContent(table);
        }
    }

    public void printHtmlDocument(String[] metakeywords, boolean includeScript,
                                  Content body) throws IOException {
        Content htmlDocType = DocType.TRANSITIONAL;
        Content htmlComment = new Comment(configuration.getText("doclet.New_Page"));
        Content head = new HtmlTree(HtmlTag.HEAD);
        head.addContent(getGeneratedBy(!configuration.notimestamp));
        if (configuration.charset.length() > 0) {
            Content meta = HtmlTree.META("Content-Type", CONTENT_TYPE,
                    configuration.charset);
            head.addContent(meta);
        }
        head.addContent(getTitle());
        if (!configuration.notimestamp) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Content meta = HtmlTree.META("date", dateFormat.format(new Date()));
            head.addContent(meta);
        }
        if (metakeywords != null) {
            for (int i = 0; i < metakeywords.length; i++) {
                Content meta = HtmlTree.META("keywords", metakeywords[i]);
                head.addContent(meta);
            }
        }
        head.addContent(getStyleSheetProperties());
        head.addContent(getScriptProperties());
        Content htmlTree = HtmlTree.HTML(configuration.getLocale().getLanguage(),
                head, body);
        Content htmlDocument = new HtmlDocument(htmlDocType,
                htmlComment, htmlTree);
        write(htmlDocument);
    }

    public String getWindowTitle(String title) {
        if (configuration.windowtitle.length() > 0) {
            title += " (" + configuration.windowtitle + ")";
        }
        return title;
    }

    public Content getUserHeaderFooter(boolean header) {
        String content;
        if (header) {
            content = replaceDocRootDir(configuration.header);
        } else {
            if (configuration.footer.length() != 0) {
                content = replaceDocRootDir(configuration.footer);
            } else {
                content = replaceDocRootDir(configuration.header);
            }
        }
        Content rawContent = new RawHtml(content);
        return rawContent;
    }

    public void addTop(Content body) {
        Content top = new RawHtml(replaceDocRootDir(configuration.top));
        body.addContent(top);
    }

    public void addBottom(Content body) {
        Content bottom = new RawHtml(replaceDocRootDir(configuration.bottom));
        Content small = HtmlTree.SMALL(bottom);
        Content p = HtmlTree.P(HtmlStyle.legalCopy, small);
        body.addContent(p);
    }

    protected void addNavLinks(boolean header, Content body) {
        if (!configuration.nonavbar) {
            String allClassesId = "allclasses_";
            HtmlTree navDiv = new HtmlTree(HtmlTag.DIV);
            Content skipNavLinks = configuration.getResource("doclet.Skip_navigation_links");
            if (header) {
                body.addContent(HtmlConstants.START_OF_TOP_NAVBAR);
                navDiv.addStyle(HtmlStyle.topNav);
                allClassesId += "navbar_top";
                Content a = getMarkerAnchor(SectionName.NAVBAR_TOP);

                navDiv.addContent(a);
                Content skipLinkContent = HtmlTree.DIV(HtmlStyle.skipNav, getHyperLink(
                        getDocLink(SectionName.SKIP_NAVBAR_TOP), skipNavLinks,
                        skipNavLinks.toString(), ""));
                navDiv.addContent(skipLinkContent);
            } else {
                body.addContent(HtmlConstants.START_OF_BOTTOM_NAVBAR);
                navDiv.addStyle(HtmlStyle.bottomNav);
                allClassesId += "navbar_bottom";
                Content a = getMarkerAnchor(SectionName.NAVBAR_BOTTOM);
                navDiv.addContent(a);
                Content skipLinkContent = HtmlTree.DIV(HtmlStyle.skipNav, getHyperLink(
                        getDocLink(SectionName.SKIP_NAVBAR_BOTTOM), skipNavLinks,
                        skipNavLinks.toString(), ""));
                navDiv.addContent(skipLinkContent);
            }
            if (header) {
                navDiv.addContent(getMarkerAnchor(SectionName.NAVBAR_TOP_FIRSTROW));
            } else {
                navDiv.addContent(getMarkerAnchor(SectionName.NAVBAR_BOTTOM_FIRSTROW));
            }
            HtmlTree navList = new HtmlTree(HtmlTag.UL);
            navList.addStyle(HtmlStyle.navList);
            navList.addAttr(HtmlAttr.TITLE,
                    configuration.getText("doclet.Navigation"));
            if (configuration.createoverview) {
                navList.addContent(getNavLinkContents());
            }
            if (configuration.packages.length == 1) {
                navList.addContent(getNavLinkPackage(configuration.packages[0]));
            } else if (configuration.packages.length > 1) {
                navList.addContent(getNavLinkPackage());
            }
            navList.addContent(getNavLinkClass());
            if (configuration.classuse) {
                navList.addContent(getNavLinkClassUse());
            }
            if (configuration.createtree) {
                navList.addContent(getNavLinkTree());
            }
            if (!(configuration.nodeprecated ||
                    configuration.nodeprecatedlist)) {
                navList.addContent(getNavLinkDeprecated());
            }
            if (configuration.createindex) {
                navList.addContent(getNavLinkIndex());
            }
            if (!configuration.nohelp) {
                navList.addContent(getNavLinkHelp());
            }
            navDiv.addContent(navList);
            Content aboutDiv = HtmlTree.DIV(HtmlStyle.aboutLanguage, getUserHeaderFooter(header));
            navDiv.addContent(aboutDiv);
            body.addContent(navDiv);
            Content ulNav = HtmlTree.UL(HtmlStyle.navList, getNavLinkPrevious());
            ulNav.addContent(getNavLinkNext());
            Content subDiv = HtmlTree.DIV(HtmlStyle.subNav, ulNav);
            Content ulFrames = HtmlTree.UL(HtmlStyle.navList, getNavShowLists());
            ulFrames.addContent(getNavHideLists(filename));
            subDiv.addContent(ulFrames);
            HtmlTree ulAllClasses = HtmlTree.UL(HtmlStyle.navList, getNavLinkClassIndex());
            ulAllClasses.addAttr(HtmlAttr.ID, allClassesId);
            subDiv.addContent(ulAllClasses);
            subDiv.addContent(getAllClassesLinkScript(allClassesId));
            addSummaryDetailLinks(subDiv);
            if (header) {
                subDiv.addContent(getMarkerAnchor(SectionName.SKIP_NAVBAR_TOP));
                body.addContent(subDiv);
                body.addContent(HtmlConstants.END_OF_TOP_NAVBAR);
            } else {
                subDiv.addContent(getMarkerAnchor(SectionName.SKIP_NAVBAR_BOTTOM));
                body.addContent(subDiv);
                body.addContent(HtmlConstants.END_OF_BOTTOM_NAVBAR);
            }
        }
    }

    protected Content getNavLinkNext() {
        return getNavLinkNext(null);
    }

    protected Content getNavLinkPrevious() {
        return getNavLinkPrevious(null);
    }

    protected void addSummaryDetailLinks(Content navDiv) {
    }

    protected Content getNavLinkContents() {
        Content linkContent = getHyperLink(pathToRoot.resolve(DocPaths.OVERVIEW_SUMMARY),
                overviewLabel, "", "");
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    protected Content getNavLinkPackage(PackageDoc pkg) {
        Content linkContent = getPackageLink(pkg,
                packageLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    protected Content getNavLinkPackage() {
        Content li = HtmlTree.LI(packageLabel);
        return li;
    }

    protected Content getNavLinkClassUse() {
        Content li = HtmlTree.LI(useLabel);
        return li;
    }

    public Content getNavLinkPrevious(DocPath prev) {
        Content li;
        if (prev != null) {
            li = HtmlTree.LI(getHyperLink(prev, prevLabel, "", ""));
        } else
            li = HtmlTree.LI(prevLabel);
        return li;
    }

    public Content getNavLinkNext(DocPath next) {
        Content li;
        if (next != null) {
            li = HtmlTree.LI(getHyperLink(next, nextLabel, "", ""));
        } else
            li = HtmlTree.LI(nextLabel);
        return li;
    }

    protected Content getNavShowLists(DocPath link) {
        DocLink dl = new DocLink(link, path.getPath(), null);
        Content framesContent = getHyperLink(dl, framesLabel, "", "_top");
        Content li = HtmlTree.LI(framesContent);
        return li;
    }

    protected Content getNavShowLists() {
        return getNavShowLists(pathToRoot.resolve(DocPaths.INDEX));
    }

    protected Content getNavHideLists(DocPath link) {
        Content noFramesContent = getHyperLink(link, noframesLabel, "", "_top");
        Content li = HtmlTree.LI(noFramesContent);
        return li;
    }

    protected Content getNavLinkTree() {
        Content treeLinkContent;
        PackageDoc[] packages = configuration.root.specifiedPackages();
        if (packages.length == 1 && configuration.root.specifiedClasses().length == 0) {
            treeLinkContent = getHyperLink(pathString(packages[0],
                    DocPaths.PACKAGE_TREE), treeLabel,
                    "", "");
        } else {
            treeLinkContent = getHyperLink(pathToRoot.resolve(DocPaths.OVERVIEW_TREE),
                    treeLabel, "", "");
        }
        Content li = HtmlTree.LI(treeLinkContent);
        return li;
    }

    protected Content getNavLinkMainTree(String label) {
        Content mainTreeContent = getHyperLink(pathToRoot.resolve(DocPaths.OVERVIEW_TREE),
                new StringContent(label));
        Content li = HtmlTree.LI(mainTreeContent);
        return li;
    }

    protected Content getNavLinkClass() {
        Content li = HtmlTree.LI(classLabel);
        return li;
    }

    protected Content getNavLinkDeprecated() {
        Content linkContent = getHyperLink(pathToRoot.resolve(DocPaths.DEPRECATED_LIST),
                deprecatedLabel, "", "");
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    protected Content getNavLinkClassIndex() {
        Content allClassesContent = getHyperLink(pathToRoot.resolve(
                DocPaths.ALLCLASSES_NOFRAME),
                allclassesLabel, "", "");
        Content li = HtmlTree.LI(allClassesContent);
        return li;
    }

    protected Content getNavLinkIndex() {
        Content linkContent = getHyperLink(pathToRoot.resolve(
                (configuration.splitindex
                        ? DocPaths.INDEX_FILES.resolve(DocPaths.indexN(1))
                        : DocPaths.INDEX_ALL)),
                indexLabel, "", "");
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    protected Content getNavLinkHelp() {
        String helpfile = configuration.helpfile;
        DocPath helpfilenm;
        if (helpfile.isEmpty()) {
            helpfilenm = DocPaths.HELP_DOC;
        } else {
            DocFile file = DocFile.createFileForInput(configuration, helpfile);
            helpfilenm = DocPath.create(file.getName());
        }
        Content linkContent = getHyperLink(pathToRoot.resolve(helpfilenm),
                helpLabel, "", "");
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    public Content getSummaryTableHeader(String[] header, String scope) {
        Content tr = new HtmlTree(HtmlTag.TR);
        int size = header.length;
        Content tableHeader;
        if (size == 1) {
            tableHeader = new StringContent(header[0]);
            tr.addContent(HtmlTree.TH(HtmlStyle.colOne, scope, tableHeader));
            return tr;
        }
        for (int i = 0; i < size; i++) {
            tableHeader = new StringContent(header[i]);
            if (i == 0)
                tr.addContent(HtmlTree.TH(HtmlStyle.colFirst, scope, tableHeader));
            else if (i == (size - 1))
                tr.addContent(HtmlTree.TH(HtmlStyle.colLast, scope, tableHeader));
            else
                tr.addContent(HtmlTree.TH(scope, tableHeader));
        }
        return tr;
    }

    public Content getTableCaption(Content title) {
        Content captionSpan = HtmlTree.SPAN(title);
        Content space = getSpace();
        Content tabSpan = HtmlTree.SPAN(HtmlStyle.tabEnd, space);
        Content caption = HtmlTree.CAPTION(captionSpan);
        caption.addContent(tabSpan);
        return caption;
    }

    public Content getMarkerAnchor(String anchorName) {
        return getMarkerAnchor(getName(anchorName), null);
    }

    public Content getMarkerAnchor(SectionName sectionName) {
        return getMarkerAnchor(sectionName.getName(), null);
    }

    public Content getMarkerAnchor(SectionName sectionName, String anchorName) {
        return getMarkerAnchor(sectionName.getName() + getName(anchorName), null);
    }

    public Content getMarkerAnchor(String anchorName, Content anchorContent) {
        if (anchorContent == null)
            anchorContent = new Comment(" ");
        Content markerAnchor = HtmlTree.A_NAME(anchorName, anchorContent);
        return markerAnchor;
    }

    public Content getPackageName(PackageDoc packageDoc) {
        return packageDoc == null || packageDoc.name().length() == 0 ?
                defaultPackageLabel :
                getPackageLabel(packageDoc.name());
    }

    public Content getPackageLabel(String packageName) {
        return new StringContent(packageName);
    }

    protected void addPackageDeprecatedAPI(List<Doc> deprPkgs, String headingKey,
                                           String tableSummary, String[] tableHeader, Content contentTree) {
        if (deprPkgs.size() > 0) {
            Content table = HtmlTree.TABLE(HtmlStyle.deprecatedSummary, 0, 3, 0, tableSummary,
                    getTableCaption(configuration.getResource(headingKey)));
            table.addContent(getSummaryTableHeader(tableHeader, "col"));
            Content tbody = new HtmlTree(HtmlTag.TBODY);
            for (int i = 0; i < deprPkgs.size(); i++) {
                PackageDoc pkg = (PackageDoc) deprPkgs.get(i);
                HtmlTree td = HtmlTree.TD(HtmlStyle.colOne,
                        getPackageLink(pkg, getPackageName(pkg)));
                if (pkg.tags("deprecated").length > 0) {
                    addInlineDeprecatedComment(pkg, pkg.tags("deprecated")[0], td);
                }
                HtmlTree tr = HtmlTree.TR(td);
                if (i % 2 == 0) {
                    tr.addStyle(HtmlStyle.altColor);
                } else {
                    tr.addStyle(HtmlStyle.rowColor);
                }
                tbody.addContent(tr);
            }
            table.addContent(tbody);
            Content li = HtmlTree.LI(HtmlStyle.blockList, table);
            Content ul = HtmlTree.UL(HtmlStyle.blockList, li);
            contentTree.addContent(ul);
        }
    }

    protected DocPath pathString(ClassDoc cd, DocPath name) {
        return pathString(cd.containingPackage(), name);
    }

    protected DocPath pathString(PackageDoc pd, DocPath name) {
        return pathToRoot.resolve(DocPath.forPackage(pd).resolve(name));
    }

    public Content getPackageLink(PackageDoc pkg, String label) {
        return getPackageLink(pkg, new StringContent(label));
    }

    public Content getPackageLink(PackageDoc pkg, Content label) {
        boolean included = pkg != null && pkg.isIncluded();
        if (!included) {
            PackageDoc[] packages = configuration.packages;
            for (int i = 0; i < packages.length; i++) {
                if (packages[i].equals(pkg)) {
                    included = true;
                    break;
                }
            }
        }
        if (included || pkg == null) {
            return getHyperLink(pathString(pkg, DocPaths.PACKAGE_SUMMARY),
                    label);
        } else {
            DocLink crossPkgLink = getCrossPackageLink(Util.getPackageName(pkg));
            if (crossPkgLink != null) {
                return getHyperLink(crossPkgLink, label);
            } else {
                return label;
            }
        }
    }

    public Content italicsClassName(ClassDoc cd, boolean qual) {
        Content name = new StringContent((qual) ? cd.qualifiedName() : cd.name());
        return (cd.isInterface()) ? HtmlTree.SPAN(HtmlStyle.interfaceName, name) : name;
    }

    public void addSrcLink(ProgramElementDoc doc, Content label, Content htmltree) {
        if (doc == null) {
            return;
        }
        ClassDoc cd = doc.containingClass();
        if (cd == null) {

            cd = (ClassDoc) doc;
        }
        DocPath href = pathToRoot
                .resolve(DocPaths.SOURCE_OUTPUT)
                .resolve(DocPath.forClass(cd));
        Content linkContent = getHyperLink(href.fragment(SourceToHTMLConverter.getAnchorName(doc)), label, "", "");
        htmltree.addContent(linkContent);
    }

    public Content getLink(LinkInfoImpl linkInfo) {
        LinkFactoryImpl factory = new LinkFactoryImpl(this);
        return factory.getLink(linkInfo);
    }

    public Content getTypeParameterLinks(LinkInfoImpl linkInfo) {
        LinkFactoryImpl factory = new LinkFactoryImpl(this);
        return factory.getTypeParameterLinks(linkInfo, false);
    }

    public Content getCrossClassLink(String qualifiedClassName, String refMemName,
                                     Content label, boolean strong, String style,
                                     boolean code) {
        String className = "";
        String packageName = qualifiedClassName == null ? "" : qualifiedClassName;
        int periodIndex;
        while ((periodIndex = packageName.lastIndexOf('.')) != -1) {
            className = packageName.substring(periodIndex + 1) +
                    (className.length() > 0 ? "." + className : "");
            Content defaultLabel = new StringContent(className);
            if (code)
                defaultLabel = HtmlTree.CODE(defaultLabel);
            packageName = packageName.substring(0, periodIndex);
            if (getCrossPackageLink(packageName) != null) {


                DocLink link = configuration.extern.getExternalLink(packageName, pathToRoot,
                        className + ".html", refMemName);
                return getHyperLink(link,
                        (label == null) || label.isEmpty() ? defaultLabel : label,
                        strong, style,
                        configuration.getText("doclet.Href_Class_Or_Interface_Title", packageName),
                        "");
            }
        }
        return null;
    }

    public boolean isClassLinkable(ClassDoc cd) {
        if (cd.isIncluded()) {
            return configuration.isGeneratedDoc(cd);
        }
        return configuration.extern.isExternal(cd);
    }

    public DocLink getCrossPackageLink(String pkgName) {
        return configuration.extern.getExternalLink(pkgName, pathToRoot,
                DocPaths.PACKAGE_SUMMARY.getPath());
    }

    public Content getQualifiedClassLink(LinkInfoImpl.Kind context, ClassDoc cd) {
        return getLink(new LinkInfoImpl(configuration, context, cd)
                .label(configuration.getClassName(cd)));
    }

    public void addPreQualifiedClassLink(LinkInfoImpl.Kind context, ClassDoc cd, Content contentTree) {
        addPreQualifiedClassLink(context, cd, false, contentTree);
    }

    public Content getPreQualifiedClassLink(LinkInfoImpl.Kind context,
                                            ClassDoc cd, boolean isStrong) {
        ContentBuilder classlink = new ContentBuilder();
        PackageDoc pd = cd.containingPackage();
        if (pd != null && !configuration.shouldExcludeQualifier(pd.name())) {
            classlink.addContent(getPkgName(cd));
        }
        classlink.addContent(getLink(new LinkInfoImpl(configuration,
                context, cd).label(cd.name()).strong(isStrong)));
        return classlink;
    }

    public void addPreQualifiedClassLink(LinkInfoImpl.Kind context,
                                         ClassDoc cd, boolean isStrong, Content contentTree) {
        PackageDoc pd = cd.containingPackage();
        if (pd != null && !configuration.shouldExcludeQualifier(pd.name())) {
            contentTree.addContent(getPkgName(cd));
        }
        contentTree.addContent(getLink(new LinkInfoImpl(configuration,
                context, cd).label(cd.name()).strong(isStrong)));
    }

    public void addPreQualifiedStrongClassLink(LinkInfoImpl.Kind context, ClassDoc cd, Content contentTree) {
        addPreQualifiedClassLink(context, cd, true, contentTree);
    }

    public Content getDocLink(LinkInfoImpl.Kind context, MemberDoc doc, String label) {
        return getDocLink(context, doc.containingClass(), doc,
                new StringContent(label));
    }

    public Content getDocLink(LinkInfoImpl.Kind context, MemberDoc doc, String label,
                              boolean strong) {
        return getDocLink(context, doc.containingClass(), doc, label, strong);
    }

    public Content getDocLink(LinkInfoImpl.Kind context, ClassDoc classDoc, MemberDoc doc,
                              String label, boolean strong) {
        return getDocLink(context, classDoc, doc, label, strong, false);
    }

    public Content getDocLink(LinkInfoImpl.Kind context, ClassDoc classDoc, MemberDoc doc,
                              Content label, boolean strong) {
        return getDocLink(context, classDoc, doc, label, strong, false);
    }

    public Content getDocLink(LinkInfoImpl.Kind context, ClassDoc classDoc, MemberDoc doc,
                              String label, boolean strong, boolean isProperty) {
        return getDocLink(context, classDoc, doc, new StringContent(check(label)), strong, isProperty);
    }

    String check(String s) {
        if (s.matches(".*[&<>].*")) throw new IllegalArgumentException(s);
        return s;
    }

    public Content getDocLink(LinkInfoImpl.Kind context, ClassDoc classDoc, MemberDoc doc,
                              Content label, boolean strong, boolean isProperty) {
        if (!(doc.isIncluded() ||
                Util.isLinkable(classDoc, configuration))) {
            return label;
        } else if (doc instanceof ExecutableMemberDoc) {
            ExecutableMemberDoc emd = (ExecutableMemberDoc) doc;
            return getLink(new LinkInfoImpl(configuration, context, classDoc)
                    .label(label).where(getName(getAnchor(emd, isProperty))).strong(strong));
        } else if (doc instanceof MemberDoc) {
            return getLink(new LinkInfoImpl(configuration, context, classDoc)
                    .label(label).where(getName(doc.name())).strong(strong));
        } else {
            return label;
        }
    }

    public Content getDocLink(LinkInfoImpl.Kind context, ClassDoc classDoc, MemberDoc doc,
                              Content label) {
        if (!(doc.isIncluded() ||
                Util.isLinkable(classDoc, configuration))) {
            return label;
        } else if (doc instanceof ExecutableMemberDoc) {
            ExecutableMemberDoc emd = (ExecutableMemberDoc) doc;
            return getLink(new LinkInfoImpl(configuration, context, classDoc)
                    .label(label).where(getName(getAnchor(emd))));
        } else if (doc instanceof MemberDoc) {
            return getLink(new LinkInfoImpl(configuration, context, classDoc)
                    .label(label).where(getName(doc.name())));
        } else {
            return label;
        }
    }

    public String getAnchor(ExecutableMemberDoc emd) {
        return getAnchor(emd, false);
    }

    public String getAnchor(ExecutableMemberDoc emd, boolean isProperty) {
        if (isProperty) {
            return emd.name();
        }
        StringBuilder signature = new StringBuilder(emd.signature());
        StringBuilder signatureParsed = new StringBuilder();
        int counter = 0;
        for (int i = 0; i < signature.length(); i++) {
            char c = signature.charAt(i);
            if (c == '<') {
                counter++;
            } else if (c == '>') {
                counter--;
            } else if (counter == 0) {
                signatureParsed.append(c);
            }
        }
        return emd.name() + signatureParsed.toString();
    }

    public Content seeTagToContent(SeeTag see) {
        String tagName = see.name();
        if (!(tagName.startsWith("@link") || tagName.equals("@see"))) {
            return new ContentBuilder();
        }
        String seetext = replaceDocRootDir(Util.normalizeNewlines(see.text()));

        if (seetext.startsWith("<") || seetext.startsWith("\"")) {
            return new RawHtml(seetext);
        }
        boolean plain = tagName.equalsIgnoreCase("@linkplain");
        Content label = plainOrCode(plain, new RawHtml(see.label()));

        Content text = plainOrCode(plain, new RawHtml(seetext));
        ClassDoc refClass = see.referencedClass();
        String refClassName = see.referencedClassName();
        MemberDoc refMem = see.referencedMember();
        String refMemName = see.referencedMemberName();
        if (refClass == null) {

            PackageDoc refPackage = see.referencedPackage();
            if (refPackage != null && refPackage.isIncluded()) {

                if (label.isEmpty())
                    label = plainOrCode(plain, new StringContent(refPackage.name()));
                return getPackageLink(refPackage, label);
            } else {

                Content classCrossLink;
                DocLink packageCrossLink = getCrossPackageLink(refClassName);
                if (packageCrossLink != null) {

                    return getHyperLink(packageCrossLink,
                            (label.isEmpty() ? text : label));
                } else if ((classCrossLink = getCrossClassLink(refClassName,
                        refMemName, label, false, "", !plain)) != null) {

                    return classCrossLink;
                } else {

                    configuration.getDocletSpecificMsg().warning(see.position(), "doclet.see.class_or_package_not_found",
                            tagName, seetext);
                    return (label.isEmpty() ? text : label);
                }
            }
        } else if (refMemName == null) {

            if (label.isEmpty()) {
                label = plainOrCode(plain, new StringContent(refClass.name()));
            }
            return getLink(new LinkInfoImpl(configuration, LinkInfoImpl.Kind.DEFAULT, refClass)
                    .label(label));
        } else if (refMem == null) {


            return (label.isEmpty() ? text : label);
        } else {


            ClassDoc containing = refMem.containingClass();
            if (see.text().trim().startsWith("#") &&
                    !(containing.isPublic() ||
                            Util.isLinkable(containing, configuration))) {


                if (this instanceof ClassWriterImpl) {
                    containing = ((ClassWriterImpl) this).getClassDoc();
                } else if (!containing.isPublic()) {
                    configuration.getDocletSpecificMsg().warning(
                            see.position(), "doclet.see.class_or_package_not_accessible",
                            tagName, containing.qualifiedName());
                } else {
                    configuration.getDocletSpecificMsg().warning(
                            see.position(), "doclet.see.class_or_package_not_found",
                            tagName, seetext);
                }
            }
            if (configuration.currentcd != containing) {
                refMemName = containing.name() + "." + refMemName;
            }
            if (refMem instanceof ExecutableMemberDoc) {
                if (refMemName.indexOf('(') < 0) {
                    refMemName += ((ExecutableMemberDoc) refMem).signature();
                }
            }
            text = plainOrCode(plain, new StringContent(refMemName));
            return getDocLink(LinkInfoImpl.Kind.SEE_TAG, containing,
                    refMem, (label.isEmpty() ? text : label), false);
        }
    }

    private Content plainOrCode(boolean plain, Content body) {
        return (plain || body.isEmpty()) ? body : HtmlTree.CODE(body);
    }

    public void addInlineComment(Doc doc, Tag tag, Content htmltree) {
        addCommentTags(doc, tag, tag.inlineTags(), false, false, htmltree);
    }

    public void addInlineDeprecatedComment(Doc doc, Tag tag, Content htmltree) {
        addCommentTags(doc, tag.inlineTags(), true, false, htmltree);
    }

    public void addSummaryComment(Doc doc, Content htmltree) {
        addSummaryComment(doc, doc.firstSentenceTags(), htmltree);
    }

    public void addSummaryComment(Doc doc, Tag[] firstSentenceTags, Content htmltree) {
        addCommentTags(doc, firstSentenceTags, false, true, htmltree);
    }

    public void addSummaryDeprecatedComment(Doc doc, Tag tag, Content htmltree) {
        addCommentTags(doc, tag.firstSentenceTags(), true, true, htmltree);
    }

    public void addInlineComment(Doc doc, Content htmltree) {
        addCommentTags(doc, doc.inlineTags(), false, false, htmltree);
    }

    private void addCommentTags(Doc doc, Tag[] tags, boolean depr,
                                boolean first, Content htmltree) {
        addCommentTags(doc, null, tags, depr, first, htmltree);
    }

    private void addCommentTags(Doc doc, Tag holderTag, Tag[] tags, boolean depr,
                                boolean first, Content htmltree) {
        if (configuration.nocomment) {
            return;
        }
        Content div;
        Content result = commentTagsToContent(null, doc, tags, first);
        if (depr) {
            Content italic = HtmlTree.SPAN(HtmlStyle.deprecationComment, result);
            div = HtmlTree.DIV(HtmlStyle.block, italic);
            htmltree.addContent(div);
        } else {
            div = HtmlTree.DIV(HtmlStyle.block, result);
            htmltree.addContent(div);
        }
        if (tags.length == 0) {
            htmltree.addContent(getSpace());
        }
    }

    public Content commentTagsToContent(Tag holderTag, Doc doc, Tag[] tags,
                                        boolean isFirstSentence) {
        Content result = new ContentBuilder();
        boolean textTagChange = false;

        configuration.tagletManager.checkTags(doc, tags, true);
        for (int i = 0; i < tags.length; i++) {
            Tag tagelem = tags[i];
            String tagName = tagelem.name();
            if (tagelem instanceof SeeTag) {
                result.addContent(seeTagToContent((SeeTag) tagelem));
            } else if (!tagName.equals("Text")) {
                boolean wasEmpty = result.isEmpty();
                Content output;
                if (configuration.docrootparent.length() > 0
                        && tagelem.name().equals("@docRoot")
                        && ((tags[i + 1]).text()).startsWith("/..")) {


                    textTagChange = true;

                    output = new StringContent(configuration.docrootparent);
                } else {
                    output = TagletWriter.getInlineTagOuput(
                            configuration.tagletManager, holderTag,
                            tagelem, getTagletWriterInstance(isFirstSentence));
                }
                if (output != null)
                    result.addContent(output);
                if (wasEmpty && isFirstSentence && tagelem.name().equals("@inheritDoc") && !result.isEmpty()) {
                    break;
                } else {
                    continue;
                }
            } else {
                String text = tagelem.text();

                if (textTagChange) {
                    text = text.replaceFirst("/..", "");
                    textTagChange = false;
                }


                text = redirectRelativeLinks(tagelem.holder(), text);


                text = replaceDocRootDir(text);
                if (isFirstSentence) {
                    text = removeNonInlineHtmlTags(text);
                }
                text = Util.replaceTabs(configuration, text);
                text = Util.normalizeNewlines(text);
                result.addContent(new RawHtml(text));
            }
        }
        return result;
    }

    private boolean shouldNotRedirectRelativeLinks() {
        return this instanceof AnnotationTypeWriter ||
                this instanceof ClassWriter ||
                this instanceof PackageSummaryWriter;
    }

    private String redirectRelativeLinks(Doc doc, String text) {
        if (doc == null || shouldNotRedirectRelativeLinks()) {
            return text;
        }
        DocPath redirectPathFromRoot;
        if (doc instanceof ClassDoc) {
            redirectPathFromRoot = DocPath.forPackage(((ClassDoc) doc).containingPackage());
        } else if (doc instanceof MemberDoc) {
            redirectPathFromRoot = DocPath.forPackage(((MemberDoc) doc).containingPackage());
        } else if (doc instanceof PackageDoc) {
            redirectPathFromRoot = DocPath.forPackage((PackageDoc) doc);
        } else {
            return text;
        }

        int end, begin = text.toLowerCase().indexOf("<a");
        if (begin >= 0) {
            StringBuilder textBuff = new StringBuilder(text);
            while (begin >= 0) {
                if (textBuff.length() > begin + 2 && !Character.isWhitespace(textBuff.charAt(begin + 2))) {
                    begin = textBuff.toString().toLowerCase().indexOf("<a", begin + 1);
                    continue;
                }
                begin = textBuff.indexOf("=", begin) + 1;
                end = textBuff.indexOf(">", begin + 1);
                if (begin == 0) {

                    configuration.root.printWarning(
                            doc.position(),
                            configuration.getText("doclet.malformed_html_link_tag", text));
                    break;
                }
                if (end == -1) {


                    break;
                }
                if (textBuff.substring(begin, end).indexOf("\"") != -1) {
                    begin = textBuff.indexOf("\"", begin) + 1;
                    end = textBuff.indexOf("\"", begin + 1);
                    if (begin == 0 || end == -1) {

                        break;
                    }
                }
                String relativeLink = textBuff.substring(begin, end);
                if (!(relativeLink.toLowerCase().startsWith("mailto:") ||
                        relativeLink.toLowerCase().startsWith("http:") ||
                        relativeLink.toLowerCase().startsWith("https:") ||
                        relativeLink.toLowerCase().startsWith("file:"))) {
                    relativeLink = "{@" + (new DocRootTaglet()).getName() + "}/"
                            + redirectPathFromRoot.resolve(relativeLink).getPath();
                    textBuff.replace(begin, end, relativeLink);
                }
                begin = textBuff.toString().toLowerCase().indexOf("<a", begin + 1);
            }
            return textBuff.toString();
        }
        return text;
    }

    public HtmlTree getStyleSheetProperties() {
        String stylesheetfile = configuration.stylesheetfile;
        DocPath stylesheet;
        if (stylesheetfile.isEmpty()) {
            stylesheet = DocPaths.STYLESHEET;
        } else {
            DocFile file = DocFile.createFileForInput(configuration, stylesheetfile);
            stylesheet = DocPath.create(file.getName());
        }
        HtmlTree link = HtmlTree.LINK("stylesheet", "text/css",
                pathToRoot.resolve(stylesheet).getPath(),
                "Style");
        return link;
    }

    public HtmlTree getScriptProperties() {
        HtmlTree script = HtmlTree.SCRIPT("text/javascript",
                pathToRoot.resolve(DocPaths.JAVASCRIPT).getPath());
        return script;
    }

    public boolean isCoreClass(ClassDoc cd) {
        return cd.containingClass() == null || cd.isStatic();
    }

    public void addAnnotationInfo(PackageDoc packageDoc, Content htmltree) {
        addAnnotationInfo(packageDoc, packageDoc.annotations(), htmltree);
    }

    public void addReceiverAnnotationInfo(ExecutableMemberDoc method, AnnotationDesc[] descList,
                                          Content htmltree) {
        addAnnotationInfo(0, method, descList, false, htmltree);
    }

    public void addAnnotationInfo(ProgramElementDoc doc, Content htmltree) {
        addAnnotationInfo(doc, doc.annotations(), htmltree);
    }

    public boolean addAnnotationInfo(int indent, Doc doc, Parameter param,
                                     Content tree) {
        return addAnnotationInfo(indent, doc, param.annotations(), false, tree);
    }

    private void addAnnotationInfo(Doc doc, AnnotationDesc[] descList,
                                   Content htmltree) {
        addAnnotationInfo(0, doc, descList, true, htmltree);
    }

    private boolean addAnnotationInfo(int indent, Doc doc,
                                      AnnotationDesc[] descList, boolean lineBreak, Content htmltree) {
        List<Content> annotations = getAnnotations(indent, descList, lineBreak);
        String sep = "";
        if (annotations.isEmpty()) {
            return false;
        }
        for (Content annotation : annotations) {
            htmltree.addContent(sep);
            htmltree.addContent(annotation);
            sep = " ";
        }
        return true;
    }

    private List<Content> getAnnotations(int indent, AnnotationDesc[] descList, boolean linkBreak) {
        return getAnnotations(indent, descList, linkBreak, true);
    }

    public List<Content> getAnnotations(int indent, AnnotationDesc[] descList, boolean linkBreak,
                                        boolean isJava5DeclarationLocation) {
        List<Content> results = new ArrayList<Content>();
        ContentBuilder annotation;
        for (int i = 0; i < descList.length; i++) {
            AnnotationTypeDoc annotationDoc = descList[i].annotationType();


            if (!Util.isDocumentedAnnotation(annotationDoc) &&
                    (!isAnnotationDocumented && !isContainerDocumented)) {
                continue;
            }

            annotation = new ContentBuilder();
            isAnnotationDocumented = false;
            LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                    LinkInfoImpl.Kind.ANNOTATION, annotationDoc);
            AnnotationDesc.ElementValuePair[] pairs = descList[i].elementValues();

            if (descList[i].isSynthesized()) {
                for (int j = 0; j < pairs.length; j++) {
                    AnnotationValue annotationValue = pairs[j].value();
                    List<AnnotationValue> annotationTypeValues = new ArrayList<AnnotationValue>();
                    if (annotationValue.value() instanceof AnnotationValue[]) {
                        AnnotationValue[] annotationArray =
                                (AnnotationValue[]) annotationValue.value();
                        annotationTypeValues.addAll(Arrays.asList(annotationArray));
                    } else {
                        annotationTypeValues.add(annotationValue);
                    }
                    String sep = "";
                    for (AnnotationValue av : annotationTypeValues) {
                        annotation.addContent(sep);
                        annotation.addContent(annotationValueToContent(av));
                        sep = " ";
                    }
                }
            } else if (isAnnotationArray(pairs)) {


                if (pairs.length == 1 && isAnnotationDocumented) {
                    AnnotationValue[] annotationArray =
                            (AnnotationValue[]) (pairs[0].value()).value();
                    List<AnnotationValue> annotationTypeValues = new ArrayList<AnnotationValue>();
                    annotationTypeValues.addAll(Arrays.asList(annotationArray));
                    String sep = "";
                    for (AnnotationValue av : annotationTypeValues) {
                        annotation.addContent(sep);
                        annotation.addContent(annotationValueToContent(av));
                        sep = " ";
                    }
                } else {
                    addAnnotations(annotationDoc, linkInfo, annotation, pairs,
                            indent, false);
                }
            } else {
                addAnnotations(annotationDoc, linkInfo, annotation, pairs,
                        indent, linkBreak);
            }
            annotation.addContent(linkBreak ? DocletConstants.NL : "");
            results.add(annotation);
        }
        return results;
    }

    private void addAnnotations(AnnotationTypeDoc annotationDoc, LinkInfoImpl linkInfo,
                                ContentBuilder annotation, AnnotationDesc.ElementValuePair[] pairs,
                                int indent, boolean linkBreak) {
        linkInfo.label = new StringContent("@" + annotationDoc.name());
        annotation.addContent(getLink(linkInfo));
        if (pairs.length > 0) {
            annotation.addContent("(");
            for (int j = 0; j < pairs.length; j++) {
                if (j > 0) {
                    annotation.addContent(",");
                    if (linkBreak) {
                        annotation.addContent(DocletConstants.NL);
                        int spaces = annotationDoc.name().length() + 2;
                        for (int k = 0; k < (spaces + indent); k++) {
                            annotation.addContent(" ");
                        }
                    }
                }
                annotation.addContent(getDocLink(LinkInfoImpl.Kind.ANNOTATION,
                        pairs[j].element(), pairs[j].element().name(), false));
                annotation.addContent("=");
                AnnotationValue annotationValue = pairs[j].value();
                List<AnnotationValue> annotationTypeValues = new ArrayList<AnnotationValue>();
                if (annotationValue.value() instanceof AnnotationValue[]) {
                    AnnotationValue[] annotationArray =
                            (AnnotationValue[]) annotationValue.value();
                    annotationTypeValues.addAll(Arrays.asList(annotationArray));
                } else {
                    annotationTypeValues.add(annotationValue);
                }
                annotation.addContent(annotationTypeValues.size() == 1 ? "" : "{");
                String sep = "";
                for (AnnotationValue av : annotationTypeValues) {
                    annotation.addContent(sep);
                    annotation.addContent(annotationValueToContent(av));
                    sep = ",";
                }
                annotation.addContent(annotationTypeValues.size() == 1 ? "" : "}");
                isContainerDocumented = false;
            }
            annotation.addContent(")");
        }
    }

    private boolean isAnnotationArray(AnnotationDesc.ElementValuePair[] pairs) {
        AnnotationValue annotationValue;
        for (int j = 0; j < pairs.length; j++) {
            annotationValue = pairs[j].value();
            if (annotationValue.value() instanceof AnnotationValue[]) {
                AnnotationValue[] annotationArray =
                        (AnnotationValue[]) annotationValue.value();
                if (annotationArray.length > 1) {
                    if (annotationArray[0].value() instanceof AnnotationDesc) {
                        AnnotationTypeDoc annotationDoc =
                                ((AnnotationDesc) annotationArray[0].value()).annotationType();
                        isContainerDocumented = true;
                        if (Util.isDocumentedAnnotation(annotationDoc)) {
                            isAnnotationDocumented = true;
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Content annotationValueToContent(AnnotationValue annotationValue) {
        if (annotationValue.value() instanceof Type) {
            Type type = (Type) annotationValue.value();
            if (type.asClassDoc() != null) {
                LinkInfoImpl linkInfo = new LinkInfoImpl(configuration,
                        LinkInfoImpl.Kind.ANNOTATION, type);
                linkInfo.label = new StringContent((type.asClassDoc().isIncluded() ?
                        type.typeName() :
                        type.qualifiedTypeName()) + type.dimension() + ".class");
                return getLink(linkInfo);
            } else {
                return new StringContent(type.typeName() + type.dimension() + ".class");
            }
        } else if (annotationValue.value() instanceof AnnotationDesc) {
            List<Content> list = getAnnotations(0,
                    new AnnotationDesc[]{(AnnotationDesc) annotationValue.value()},
                    false);
            ContentBuilder buf = new ContentBuilder();
            for (Content c : list) {
                buf.addContent(c);
            }
            return buf;
        } else if (annotationValue.value() instanceof MemberDoc) {
            return getDocLink(LinkInfoImpl.Kind.ANNOTATION,
                    (MemberDoc) annotationValue.value(),
                    ((MemberDoc) annotationValue.value()).name(), false);
        } else {
            return new StringContent(annotationValue.toString());
        }
    }

    public Configuration configuration() {
        return configuration;
    }
}
