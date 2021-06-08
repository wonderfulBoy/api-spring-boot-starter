package com.sun.tools.doclets.formats.html;

import com.sun.javadoc.PackageDoc;
import com.sun.tools.doclets.formats.html.markup.*;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.*;

import java.io.IOException;

public class TreeWriter extends AbstractTreeWriter {

    private PackageDoc[] packages;

    private boolean classesonly;

    public TreeWriter(ConfigurationImpl configuration,
                      DocPath filename, ClassTree classtree)
            throws IOException {
        super(configuration, filename, classtree);
        packages = configuration.packages;
        classesonly = packages.length == 0;
    }

    public static void generate(ConfigurationImpl configuration,
                                ClassTree classtree) {
        TreeWriter treegen;
        DocPath filename = DocPaths.OVERVIEW_TREE;
        try {
            treegen = new TreeWriter(configuration, filename, classtree);
            treegen.generateTreeFile();
            treegen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }

    public void generateTreeFile() throws IOException {
        Content body = getTreeHeader();
        Content headContent = getResource("doclet.Hierarchy_For_All_Packages");
        Content heading = HtmlTree.HEADING(HtmlConstants.TITLE_HEADING, false,
                HtmlStyle.title, headContent);
        Content div = HtmlTree.DIV(HtmlStyle.header, heading);
        addPackageTreeLinks(div);
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

    protected void addPackageTreeLinks(Content contentTree) {

        if (packages.length == 1 && packages[0].name().length() == 0) {
            return;
        }
        if (!classesonly) {
            Content span = HtmlTree.SPAN(HtmlStyle.packageHierarchyLabel,
                    getResource("doclet.Package_Hierarchies"));
            contentTree.addContent(span);
            HtmlTree ul = new HtmlTree(HtmlTag.UL);
            ul.addStyle(HtmlStyle.horizontal);
            for (int i = 0; i < packages.length; i++) {


                if (packages[i].name().length() == 0 ||
                        (configuration.nodeprecated && Util.isDeprecated(packages[i]))) {
                    continue;
                }
                DocPath link = pathString(packages[i], DocPaths.PACKAGE_TREE);
                Content li = HtmlTree.LI(getHyperLink(
                        link, new StringContent(packages[i].name())));
                if (i < packages.length - 1) {
                    li.addContent(", ");
                }
                ul.addContent(li);
            }
            contentTree.addContent(ul);
        }
    }

    protected Content getTreeHeader() {
        String title = configuration.getText("doclet.Window_Class_Hierarchy");
        Content bodyTree = getBody(true, getWindowTitle(title));
        addTop(bodyTree);
        addNavLinks(true, bodyTree);
        return bodyTree;
    }
}
