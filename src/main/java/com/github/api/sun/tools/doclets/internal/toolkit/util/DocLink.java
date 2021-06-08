package com.github.api.sun.tools.doclets.internal.toolkit.util;

public class DocLink {
    final String path;
    final String query;
    final String fragment;

    public DocLink(DocPath path) {
        this(path.getPath(), null, null);
    }

    public DocLink(DocPath path, String query, String fragment) {
        this(path.getPath(), query, fragment);
    }

    public DocLink(String path, String query, String fragment) {
        this.path = path;
        this.query = query;
        this.fragment = fragment;
    }

    public static DocLink fragment(String fragment) {
        return new DocLink((String) null, null, fragment);
    }

    private static boolean isEmpty(String s) {
        return (s == null) || s.isEmpty();
    }

    @Override
    public String toString() {

        if (path != null && isEmpty(query) && isEmpty(fragment))
            return path;
        StringBuilder sb = new StringBuilder();
        if (path != null)
            sb.append(path);
        if (!isEmpty(query))
            sb.append("?").append(query);
        if (!isEmpty(fragment))
            sb.append("#").append(fragment);
        return sb.toString();
    }
}
