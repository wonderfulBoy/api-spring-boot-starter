package com.sun.tools.doclets.formats.html.markup;

import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocletAbortException;
import com.sun.tools.doclets.internal.toolkit.util.DocletConstants;

import java.io.IOException;
import java.io.Writer;

public class StringContent extends Content {
    private StringBuilder stringContent;

    public StringContent() {
        stringContent = new StringBuilder();
    }

    public StringContent(String initialContent) {
        stringContent = new StringBuilder();
        appendChars(initialContent);
    }

    @Override
    public void addContent(Content content) {
        throw new DocletAbortException("not supported");
    }

    @Override
    public void addContent(String strContent) {
        appendChars(strContent);
    }

    @Override
    public boolean isEmpty() {
        return (stringContent.length() == 0);
    }

    @Override
    public int charCount() {
        return RawHtml.charCount(stringContent.toString());
    }

    @Override
    public String toString() {
        return stringContent.toString();
    }

    @Override
    public boolean write(Writer out, boolean atNewline) throws IOException {
        String s = stringContent.toString();
        out.write(s);
        return s.endsWith(DocletConstants.NL);
    }

    private void appendChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '<':
                    stringContent.append("&lt;");
                    break;
                case '>':
                    stringContent.append("&gt;");
                    break;
                case '&':
                    stringContent.append("&amp;");
                    break;
                default:
                    stringContent.append(ch);
                    break;
            }
        }
    }
}
