package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.PackageDoc;
import com.sun.tools.doclets.formats.html.markup.HtmlConstants;
import com.sun.tools.doclets.formats.html.markup.HtmlStyle;
import com.sun.tools.doclets.formats.html.markup.HtmlTag;
import com.sun.tools.doclets.formats.html.markup.HtmlTree;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.*;

import java.io.IOException;

public class PackageTreeWriter extends AbstractTreeWriter {

    protected PackageDoc packagedoc;

    protected PackageDoc prev;

    protected PackageDoc next;

    public PackageTreeWriter(ConfigurationImpl configuration,
                             DocPath path,
                             PackageDoc packagedoc,
                             PackageDoc prev, PackageDoc next)
            throws IOException {
        super(configuration, path,
                new ClassTree(
                        configuration.classDocCatalog.allClasses(packagedoc),
                        configuration));
        this.packagedoc = packagedoc;
        this.prev = prev;
        this.next = next;
    }

    public static void generate(ConfigurationImpl configuration,
                                PackageDoc pkg, PackageDoc prev,
                                PackageDoc next, boolean noDeprecated) {
        PackageTreeWriter packgen;
        DocPath path = DocPath.forPackage(pkg).resolve(DocPaths.PACKAGE_TREE);
        try {
            packgen = new PackageTreeWriter(configuration, path, pkg,
                    prev, next);
            packgen.generatePackageTreeFile();
            packgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), path.getPath());
            throw new DocletAbortException(exc);
        }
    }

    protected void generatePackageTreeFile() throws IOException {
        Content body = getPackageTreeHeader();
        Content headContent = getResource("doclet.Hierarchy_For_Package",
                Util.getPackageName(packagedoc));
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, false,
                HtmlStyle.title, headContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        if (configuration.packages.length > 1) {
            addLinkToMainTree(div);
        }
        body.addContent(div);
        HtmlTree divTree = new HtmlTree(HtmlTag.DIV);
        divTree.addStyle(HtmlStyle.contentContainer);
        addTree(classtree.baseclasses(), "doclet.Class_Hierarchy", divTree);
        addTree(classtree.baseinterfaces(), "doclet.Interface_Hierarchy", divTree);
        addTree(classtree.baseAnnotationTypes(), "doclet.Annotation_Type_Hierarchy", divTree);
        addTree(classtree.baseEnums(), "doclet.Enum_Hierarchy", divTree);
        body.addContent(divTree);
        addNavLinks(false, body);
        addBottom(body);
        printHtmlDocument(null, true, body);
    }

    protected Content getPackageTreeHeader() {
        String title = packagedoc.name() + " " +
                configuration.getText("doclet.Window_Class_Hierarchy");
        Content bodyTree = getBody(true, getWindowTitle(title));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        return bodyTree;
    }

    protected void addLinkToMainTree(Content div) {
        Content span = HtmlTree.SPAN(HtmlStyle.packageHierarchyLabel,
                getResource("doclet.Package_Hierarchies"));
        div.addContent(span);
        HtmlTree ul = new HtmlTree(HtmlTag.UL);
        ul.addStyle(HtmlStyle.horizontal);
        ul.addContent(getNavLinkMainTree(configuration.getText("doclet.All_Packages")));
        div.addContent(ul);
    }

    protected Content getNavLinkPrevious() {
        if (prev == null) {
            return getNavLinkPrevious(null);
        } else {
            DocPath path = DocPath.relativePath(packagedoc, prev);
            return getNavLinkPrevious(path.resolve(DocPaths.PACKAGE_TREE));
        }
    }

    protected Content getNavLinkNext() {
        if (next == null) {
            return getNavLinkNext(null);
        } else {
            DocPath path = DocPath.relativePath(packagedoc, next);
            return getNavLinkNext(path.resolve(DocPaths.PACKAGE_TREE));
        }
    }

    protected Content getNavLinkPackage() {
        Content linkContent = getHyperLink(DocPaths.PACKAGE_SUMMARY,
                packageLabel);
        Content li = HtmlTree.LI(linkContent);
        return li;
    }
}
