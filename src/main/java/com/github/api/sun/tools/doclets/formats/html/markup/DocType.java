package com.github.api.sun.tools.doclets.formats.html.markup;

import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocletAbortException;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocletConstants;

import java.io.IOException;
import java.io.Writer;

public class DocType extends Content {
    public static final DocType TRANSITIONAL =
            new DocType("Transitional", "http://www.w3.org/TR/html4/loose.dtd");
    public static final DocType FRAMESET =
            new DocType("Frameset", "http://www.w3.org/TR/html4/frameset.dtd");
    private String docType;

    private DocType(String type, String dtd) {
        docType = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 " + type +
                "//EN\" \"" + dtd + "\">" + DocletConstants.NL;
    }

    public void addContent(Content content) {
        throw new DocletAbortException("not supported");
    }

    public void addContent(String stringContent) {
        throw new DocletAbortException("not supported");
    }

    public boolean isEmpty() {
        return (docType.length() == 0);
    }

    @Override
    public boolean write(Writer out, boolean atNewline) throws IOException {
        out.write(docType);
        return true;
    }
}
