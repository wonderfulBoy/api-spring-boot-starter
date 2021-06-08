package com.sun.tools.doclets.formats.html.markup;

import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocletAbortException;
import com.sun.tools.doclets.internal.toolkit.util.DocletConstants;

import java.io.IOException;
import java.io.Writer;

public class Comment extends Content {
    private String commentText;

    public Comment(String comment) {
        commentText = nullCheck(comment);
    }

    public void addContent(Content content) {
        throw new DocletAbortException("not supported");
    }

    public void addContent(String stringContent) {
        throw new DocletAbortException("not supported");
    }

    public boolean isEmpty() {
        return commentText.isEmpty();
    }

    @Override
    public boolean write(Writer out, boolean atNewline) throws IOException {
        if (!atNewline)
            out.write(DocletConstants.NL);
        out.write("<!-- ");
        out.write(commentText);
        out.write(" -->" + DocletConstants.NL);
        return true;
    }
}
