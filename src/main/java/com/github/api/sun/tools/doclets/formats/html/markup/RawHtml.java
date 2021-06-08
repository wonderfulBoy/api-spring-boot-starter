package com.github.api.sun.tools.doclets.formats.html.markup;

import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocletAbortException;
import com.github.api.sun.tools.doclets.internal.toolkit.util.DocletConstants;

import java.io.IOException;
import java.io.Writer;

public class RawHtml extends Content {
    public static final Content nbsp = new RawHtml("&nbsp;");
    private String rawHtmlContent;

    public RawHtml(String rawHtml) {
        rawHtmlContent = nullCheck(rawHtml);
    }

    static int charCount(String htmlText) {
        State state = State.TEXT;
        int count = 0;
        for (int i = 0; i < htmlText.length(); i++) {
            char c = htmlText.charAt(i);
            switch (state) {
                case TEXT:
                    switch (c) {
                        case '<':
                            state = State.TAG;
                            break;
                        case '&':
                            state = State.ENTITY;
                            count++;
                            break;
                        default:
                            count++;
                    }
                    break;
                case ENTITY:
                    if (!Character.isLetterOrDigit(c))
                        state = State.TEXT;
                    break;
                case TAG:
                    switch (c) {
                        case '"':
                            state = State.STRING;
                            break;
                        case '>':
                            state = State.TEXT;
                            break;
                    }
                    break;
                case STRING:
                    switch (c) {
                        case '"':
                            state = State.TAG;
                            break;
                    }
            }
        }
        return count;
    }

    public void addContent(Content content) {
        throw new DocletAbortException("not supported");
    }

    public void addContent(String stringContent) {
        throw new DocletAbortException("not supported");
    }

    public boolean isEmpty() {
        return rawHtmlContent.isEmpty();
    }

    @Override
    public String toString() {
        return rawHtmlContent;
    }

    @Override
    public int charCount() {
        return charCount(rawHtmlContent);
    }

    @Override
    public boolean write(Writer out, boolean atNewline) throws IOException {
        out.write(rawHtmlContent);
        return rawHtmlContent.endsWith(DocletConstants.NL);
    }

    private enum State {TEXT, ENTITY, TAG, STRING}
}
