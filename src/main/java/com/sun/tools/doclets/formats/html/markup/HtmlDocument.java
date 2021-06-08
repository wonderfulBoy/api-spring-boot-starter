package com.sun.tools.doclets.formats.html.markup;

import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocletAbortException;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HtmlDocument extends Content {
    private List<Content> docContent = Collections.emptyList();

    public HtmlDocument(Content docType, Content docComment, Content htmlTree) {
        docContent = new ArrayList<Content>();
        addContent(nullCheck(docType));
        addContent(nullCheck(docComment));
        addContent(nullCheck(htmlTree));
    }

    public HtmlDocument(Content docType, Content htmlTree) {
        docContent = new ArrayList<Content>();
        addContent(nullCheck(docType));
        addContent(nullCheck(htmlTree));
    }

    public final void addContent(Content htmlContent) {
        if (htmlContent.isValid())
            docContent.add(htmlContent);
    }

    public void addContent(String stringContent) {
        throw new DocletAbortException("not supported");
    }

    public boolean isEmpty() {
        return (docContent.isEmpty());
    }

    public boolean write(Writer out, boolean atNewline) throws IOException {
        for (Content c : docContent)
            atNewline = c.write(out, atNewline);
        return atNewline;
    }
}
