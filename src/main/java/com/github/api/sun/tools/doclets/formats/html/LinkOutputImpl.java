package com.github.api.sun.tools.doclets.formats.html;

import com.github.api.sun.tools.doclets.internal.toolkit.util.links.LinkOutput;

public class LinkOutputImpl implements LinkOutput {

    public StringBuilder output;

    public LinkOutputImpl() {
        output = new StringBuilder();
    }

    public void append(Object o) {
        output.append(o instanceof String ?
                (String) o : o.toString());
    }

    public void insert(int offset, Object o) {
        output.insert(offset, o.toString());
    }

    public String toString() {
        return output.toString();
    }
}
