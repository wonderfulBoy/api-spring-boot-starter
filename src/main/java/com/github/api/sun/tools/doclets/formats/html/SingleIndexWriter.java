package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.tools.doclets.formats.html.markup.HtmlStyle;
import com.github.api.sun.tools.doclets.formats.html.markup.HtmlTag;
import com.github.api.sun.tools.doclets.formats.html.markup.HtmlTree;
import com.github.api.sun.tools.doclets.formats.html.markup.StringContent;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPath;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocPaths;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocletAbortException;
import com.github.api.sun.tools.doclets.internal.toolkit.util.IndexBuilder;

import java.io.IOException;

public class SingleIndexWriter extends AbstractIndexWriter {

    public SingleIndexWriter(ConfigurationImpl configuration,
                             DocPath filename,
                             IndexBuilder indexbuilder) throws IOException {
        super(configuration, filename, indexbuilder);
    }

    public static void generate(ConfigurationImpl configuration,
                                IndexBuilder indexbuilder) {
        SingleIndexWriter indexgen;
        DocPath filename = DocPaths.INDEX_ALL;
        try {
            indexgen = new SingleIndexWriter(configuration,
                    filename, indexbuilder);
            indexgen.generateIndexFile();
            indexgen.close();
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), filename);
            throw new DocletAbortException(exc);
        }
    }

    protected void generateIndexFile() throws IOException {
        String title = configuration.getText("doclet.Window_Single_Index");
        Content body = getBody(true, getWindowTitle(title));
        addTop(body);
        addNavLinks(true, body);
        HtmlTree divTree = new HtmlTree(HtmlTag.DIV);
        divTree.addStyle(HtmlStyle.contentContainer);
        addLinksForIndexes(divTree);
        for (int i = 0; i < indexbuilder.elements().length; i++) {
            Character unicode = (Character) ((indexbuilder.elements())[i]);
            addContents(unicode, indexbuilder.getMemberList(unicode), divTree);
        }
        addLinksForIndexes(divTree);
        body.addContent(divTree);
        addNavLinks(false, body);
        addBottom(body);
        printHtmlDocument(null, true, body);
    }

    protected void addLinksForIndexes(Content contentTree) {
        for (int i = 0; i < indexbuilder.elements().length; i++) {
            String unicode = (indexbuilder.elements())[i].toString();
            contentTree.addContent(
                    getHyperLink(getNameForIndex(unicode),
                            new StringContent(unicode)));
            contentTree.addContent(getSpace());
        }
    }
}
