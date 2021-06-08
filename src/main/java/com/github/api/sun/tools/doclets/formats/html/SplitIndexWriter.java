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

public class SplitIndexWriter extends AbstractIndexWriter {

    protected int prev;

    protected int next;

    public SplitIndexWriter(ConfigurationImpl configuration,
                            DocPath path,
                            IndexBuilder indexbuilder,
                            int prev, int next) throws IOException {
        super(configuration, path, indexbuilder);
        this.prev = prev;
        this.next = next;
    }

    public static void generate(ConfigurationImpl configuration,
                                IndexBuilder indexbuilder) {
        SplitIndexWriter indexgen;
        DocPath filename = DocPath.empty;
        DocPath path = DocPaths.INDEX_FILES;
        try {
            for (int i = 0; i < indexbuilder.elements().length; i++) {
                int j = i + 1;
                int prev = (j == 1) ? -1 : i;
                int next = (j == indexbuilder.elements().length) ? -1 : j + 1;
                filename = DocPaths.indexN(j);
                indexgen = new SplitIndexWriter(configuration,
                        path.resolve(filename),
                        indexbuilder, prev, next);
                indexgen.generateIndexFile((Character) indexbuilder.
                        elements()[i]);
                indexgen.close();
            }
        } catch (IOException exc) {
            configuration.standardmessage.error(
                    "doclet.exception_encountered",
                    exc.toString(), filename.getPath());
            throw new DocletAbortException(exc);
        }
    }

    protected void generateIndexFile(Character unicode) throws IOException {
        String title = configuration.getText("doclet.Window_Split_Index",
                unicode.toString());
        Content body = getBody(true, getWindowTitle(title));
        addTop(body);
        addNavLinks(true, body);
        HtmlTree divTree = new HtmlTree(HtmlTag.DIV);
        divTree.addStyle(HtmlStyle.contentContainer);
        addLinksForIndexes(divTree);
        addContents(unicode, indexbuilder.getMemberList(unicode), divTree);
        addLinksForIndexes(divTree);
        body.addContent(divTree);
        addNavLinks(false, body);
        addBottom(body);
        printHtmlDocument(null, true, body);
    }

    protected void addLinksForIndexes(Content contentTree) {
        Object[] unicodeChars = indexbuilder.elements();
        for (int i = 0; i < unicodeChars.length; i++) {
            int j = i + 1;
            contentTree.addContent(getHyperLink(DocPaths.indexN(j),
                    new StringContent(unicodeChars[i].toString())));
            contentTree.addContent(getSpace());
        }
    }

    public Content getNavLinkPrevious() {
        Content prevletterLabel = getResource("doclet.Prev_Letter");
        if (prev == -1) {
            return HtmlTree.LI(prevletterLabel);
        } else {
            Content prevLink = getHyperLink(DocPaths.indexN(prev),
                    prevletterLabel);
            return HtmlTree.LI(prevLink);
        }
    }

    public Content getNavLinkNext() {
        Content nextletterLabel = getResource("doclet.Next_Letter");
        if (next == -1) {
            return HtmlTree.LI(nextletterLabel);
        } else {
            Content nextLink = getHyperLink(DocPaths.indexN(next),
                    nextletterLabel);
            return HtmlTree.LI(nextLink);
        }
    }
}
