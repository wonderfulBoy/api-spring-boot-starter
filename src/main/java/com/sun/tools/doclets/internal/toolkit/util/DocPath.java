package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;

public class DocPath {
    public static final DocPath empty = new DocPath("");
    public static final DocPath parent = new DocPath("..");
    private final String path;

    protected DocPath(String p) {
        path = (p.endsWith("/") ? p.substring(0, p.length() - 1) : p);
    }

    public static DocPath create(String p) {
        return (p == null) || p.isEmpty() ? empty : new DocPath(p);
    }

    public static DocPath forClass(ClassDoc cd) {
        return (cd == null) ? empty :
                forPackage(cd.containingPackage()).resolve(forName(cd));
    }

    public static DocPath forName(ClassDoc cd) {
        return (cd == null) ? empty : new DocPath(cd.name() + ".html");
    }

    public static DocPath forPackage(ClassDoc cd) {
        return (cd == null) ? empty : forPackage(cd.containingPackage());
    }

    public static DocPath forPackage(PackageDoc pd) {
        return (pd == null) ? empty : DocPath.create(pd.name().replace('.', '/'));
    }

    public static DocPath forRoot(PackageDoc pd) {
        String name = (pd == null) ? "" : pd.name();
        if (name.isEmpty())
            return empty;
        return new DocPath(name.replace('.', '/').replaceAll("[^/]+", ".."));
    }

    public static DocPath relativePath(PackageDoc from, PackageDoc to) {
        return forRoot(from).resolve(forPackage(to));
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof DocPath) && path.equals(((DocPath) other).path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    public DocPath basename() {
        int sep = path.lastIndexOf("/");
        return (sep == -1) ? this : new DocPath(path.substring(sep + 1));
    }

    public DocPath parent() {
        int sep = path.lastIndexOf("/");
        return (sep == -1) ? empty : new DocPath(path.substring(0, sep));
    }

    public DocPath resolve(String p) {
        if (p == null || p.isEmpty())
            return this;
        if (path.isEmpty())
            return new DocPath(p);
        return new DocPath(path + "/" + p);
    }

    public DocPath resolve(DocPath p) {
        if (p == null || p.isEmpty())
            return this;
        if (path.isEmpty())
            return p;
        return new DocPath(path + "/" + p.getPath());
    }

    public DocPath invert() {
        return new DocPath(path.replaceAll("[^/]+", ".."));
    }

    public boolean isEmpty() {
        return path.isEmpty();
    }

    public DocLink fragment(String fragment) {
        return new DocLink(path, null, fragment);
    }

    public DocLink query(String query) {
        return new DocLink(path, query, null);
    }


    public String getPath() {
        return path;
    }
}
