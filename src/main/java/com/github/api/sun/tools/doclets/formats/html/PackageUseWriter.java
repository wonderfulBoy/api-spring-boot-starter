package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.PackageDoc;
import com.github.api.sun.tools.doclets.formats.html.markup.*;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.*;

import java.io.IOException;
import java.util.*;

public class PackageUseWriter extends SubWriterHolderWriter {
    final PackageDoc pkgdoc;
    final SortedMap<String, Set<ClassDoc>> usingPackageToUsedClasses = new TreeMap<String, Set<ClassDoc>>();

    public PackageUseWriter(ConfigurationImpl configuration,
                            ClassUseMapper mapper, DocPath filename,
                            PackageDoc pkgdoc) throws IOException {
        super(configuration, DocPath.forPackage(pkgdoc).resolve(filename));
        this.pkgdoc = pkgdoc;


        ClassDoc[] content = pkgdoc.allClasses();
        for (int i = 0; i < content.length; ++i) {
            ClassDoc usedClass = content[i];
            Set<ClassDoc> usingClasses = mapper.classToClass.get(usedClass.qualifiedName());
            if (usingClasses != null) {
                for (Iterator<ClassDoc> it = usingClasses.iterator(); it.hasNext(); ) {
                    ClassDoc usingClass = it.next();
                    PackageDoc usingPackage = usingClass.containingPackage();
                    Set<ClassDoc> usedClasses = usingPackageToUsedClasses
                            .get(usingPackage.name());
                    if (usedClasses == null) {
                        usedClasses = new TreeSet<ClassDoc>();
                        usingPackageToUsedClasses.put(Util.getPackageName(usingPackage),
                                usedClasses);
                    }
                    usedClasses.add(usedClass);
                }
            }
        }
    }

    public static void generate(ConfigurationImpl configuration,
                                ClassUseMapper mapper, PackageDoc pkgdoc) {
        PackageUseWriter pkgusegen;
        DocPath filename = DocPaths.PACKAGE_USE;
        try {
            pkgusegen = new PackageUseWriter(configuration,
                    mapper, filename, pkgdoc);
            pkgusegen.generatePackageUseFile();
            pkgusegen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }

    protected void generatePackageUseFile() throws IOException {
        Content body = getPackageUseHeader();
        HtmlTree div = new HtmlTree(HtmlTag.DIV);
        div.addStyle(HtmlStyle.contentContainer);
        if (usingPackageToUsedClasses.isEmpty()) {
            div.addContent(getResource(
                    "doclet.ClassUse_No.usage.of.0", pkgdoc.name()));
        } else {
            addPackageUse(div);
        }
        body.addContent(div);
        addNavLinks(false, body);
        addBottom(body);
        printHtmlDocument(null, true, body);
    }

    protected void addPackageUse(Content contentTree) throws IOException {
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.blockList);
        if (configuration.packages.length > 1) {
            addPackageList(ul);
        }
        addClassList(ul);
        contentTree.addContent(ul);
    }

    protected void addPackageList(Content contentTree) throws IOException {
        Content table = HtmlTree.TABLE(HtmlStyle.useSummary, 0, 3, 0, useTableSummary,
                getTableCaption(configuration.getResource(
                        "doclet.ClassUse_Packages.that.use.0",
                        getPackageLink(pkgdoc, Util.getPackageName(pkgdoc)))));
        table.addContent(getSummaryTableHeader(packageTableHeader, "col"));
        Content tbody = new HtmlTree(HtmlTag.TBODY);
        Iterator<String> it = usingPackageToUsedClasses.keySet().iterator();
        for (int i = 0; it.hasNext(); i++) {
            PackageDoc pkg = configuration.root.packageNamed(it.next());
            HtmlTree tr = new HtmlTree(HtmlTag.TR);
            if (i % 2 == 0) {
                tr.addStyle(HtmlStyle.altColor);
            } else {
                tr.addStyle(HtmlStyle.rowColor);
            }
            addPackageUse(pkg, tr);
            tbody.addContent(tr);
        }
        table.addContent(tbody);
        Content li = HtmlTree.LI(HtmlStyle.blockList, table);
        contentTree.addContent(li);
    }

    protected void addClassList(Content contentTree) throws IOException {
        String[] classTableHeader = new String[]{
                configuration.getText("doclet.0_and_1",
                        configuration.getText("doclet.Class"),
                        configuration.getText("doclet.Description"))
        };
        Iterator<String> itp = usingPackageToUsedClasses.keySet().iterator();
        while (itp.hasNext()) {
            String packageName = itp.next();
            PackageDoc usingPackage = configuration.root.packageNamed(packageName);
            HtmlTree li = new HtmlTree(HtmlTag.LI);
            li.addStyle(HtmlStyle.blockList);
            if (usingPackage != null) {
                li.addContent(getMarkerAnchor(usingPackage.name()));
            }
            String tableSummary = configuration.getText("doclet.Use_Table_Summary",
                    configuration.getText("doclet.classes"));
            Content table = HtmlTree.TABLE(HtmlStyle.useSummary, 0, 3, 0, tableSummary,
                    getTableCaption(configuration.getResource(
                            "doclet.ClassUse_Classes.in.0.used.by.1",
                            getPackageLink(pkgdoc, Util.getPackageName(pkgdoc)),
                            getPackageLink(usingPackage, Util.getPackageName(usingPackage)))));
            table.addContent(getSummaryTableHeader(classTableHeader, "col"));
            Content tbody = new HtmlTree(HtmlTag.TBODY);
            Iterator<ClassDoc> itc =
                    usingPackageToUsedClasses.get(packageName).iterator();
            for (int i = 0; itc.hasNext(); i++) {
                HtmlTree tr = new HtmlTree(HtmlTag.TR);
                if (i % 2 == 0) {
                    tr.addStyle(HtmlStyle.altColor);
                } else {
                    tr.addStyle(HtmlStyle.rowColor);
                }
                addClassRow(itc.next(), packageName, tr);
                tbody.addContent(tr);
            }
            table.addContent(tbody);
            li.addContent(table);
            contentTree.addContent(li);
        }
    }

    protected void addClassRow(ClassDoc usedClass, String packageName,
                               Content contentTree) {
        DocPath dp = pathString(usedClass,
                DocPaths.CLASS_USE.resolve(DocPath.forName(usedClass)));
        Content td = HtmlTree.TD(HtmlStyle.colOne,
                getHyperLink(dp.fragment(packageName), new StringContent(usedClass.name())));
        addIndexComment(usedClass, td);
        contentTree.addContent(td);
    }

    protected void addPackageUse(PackageDoc pkg, Content contentTree) throws IOException {
        Content tdFirst = HtmlTree.TD(HtmlStyle.colFirst,
                getHyperLink(Util.getPackageName(pkg),
                        new StringContent(Util.getPackageName(pkg))));
        contentTree.addContent(tdFirst);
        HtmlTree tdLast = new HtmlTree(HtmlTag.TD);
        tdLast.addStyle(HtmlStyle.colLast);
        if (pkg != null && pkg.name().length() != 0) {
            addSummaryComment(pkg, tdLast);
        } else {
            tdLast.addContent(getSpace());
        }
        contentTree.addContent(tdLast);
    }

    protected Content getPackageUseHeader() {
        String packageText = configuration.getText("doclet.Package");
        String name = pkgdoc.name();
        String title = configuration.getText("doclet.Window_ClassUse_Header",
                packageText, name);
        Content bodyTree = getBody(true, getWindowTitle(title));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        ContentBuilder headContent = new ContentBuilder();
        headContent.addContent(getResource("doclet.ClassUse_Title", packageText));
        headContent.addContent(new HtmlTree(HtmlTag.BR));
        headContent.addContent(name);
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, true,
                HtmlStyle.title, headContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        bodyTree.addContent(div);
        return bodyTree;
    }

    protected Content getNavLinkPackage() {
        Content linkContent = getHyperLink(DocPaths.PACKAGE_SUMMARY,
                packageLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }

    protected Content getNavLinkClassUse() {
        Content li = HtmlTree.LI(HtmlStyle.navBarCell1Rev, useLabel);
        return li;
    }

    protected Content getNavLinkTree() {
        Content linkContent = getHyperLink(DocPaths.PACKAGE_TREE,
                treeLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }
}
