package com.github.api.sun.tools.doclets.internal.toolkit;

import com.github.api.sun.tools.doclets.internal.toolkit.util.DocletAbortException;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

public abstract class Content {

    protected static <T> T nullCheck(T t) {
        t.getClass();
        return t;
    }

    @Override
    public String toString() {
        StringWriter out = new StringWriter();
        try {
            write(out, true);
        } catch (IOException e) {

            throw new DocletAbortException(e);
        }
        return out.toString();
    }

    public abstract void addContent(Content content);

    public abstract void addContent(String stringContent);

    public abstract boolean write(Writer writer, boolean atNewline) throws IOException;

    public abstract boolean isEmpty();

    public boolean isValid() {
        return !isEmpty();
    }

    public int charCount() {
        return 0;
    }
}
