package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.tools.doclets.internal.toolkit.Configuration;

import javax.tools.DocumentationTool;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Extern {

    private final Configuration configuration;
    private Map<String, Item> packageToItemMap;
    private boolean linkoffline = false;

    public Extern(Configuration configuration) {
        this.configuration = configuration;
    }

    public boolean isExternal(ProgramElementDoc doc) {
        if (packageToItemMap == null) {
            return false;
        }
        return packageToItemMap.get(doc.containingPackage().name()) != null;
    }

    public DocLink getExternalLink(String pkgName,
                                   DocPath relativepath, String filename) {
        return getExternalLink(pkgName, relativepath, filename, null);
    }

    public DocLink getExternalLink(String pkgName,
                                   DocPath relativepath, String filename, String memberName) {
        Item fnd = findPackageItem(pkgName);
        if (fnd == null)
            return null;
        DocPath p = fnd.relative ?
                relativepath.resolve(fnd.path).resolve(filename) :
                DocPath.create(fnd.path).resolve(filename);
        return new DocLink(p, "is-external=true", memberName);
    }

    public boolean link(String url, String pkglisturl,
                        DocErrorReporter reporter, boolean linkoffline) {
        this.linkoffline = linkoffline;
        try {
            url = adjustEndFileSeparator(url);
            if (isUrl(pkglisturl)) {
                readPackageListFromURL(url, toURL(pkglisturl));
            } else {
                readPackageListFromFile(url, DocFile.createFileForInput(configuration, pkglisturl));
            }
            return true;
        } catch (Fault f) {
            reporter.printWarning(f.getMessage());
            return false;
        }
    }

    private URL toURL(String url) throws Fault {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new Fault(configuration.getText("doclet.MalformedURL", url), e);
        }
    }

    private Item findPackageItem(String pkgName) {
        if (packageToItemMap == null) {
            return null;
        }
        return packageToItemMap.get(pkgName);
    }

    private String adjustEndFileSeparator(String url) {
        return url.endsWith("/") ? url : url + '/';
    }

    private void readPackageListFromURL(String urlpath, URL pkglisturlpath)
            throws Fault {
        try {
            URL link = pkglisturlpath.toURI().resolve(DocPaths.PACKAGE_LIST.getPath()).toURL();
            readPackageList(link.openStream(), urlpath, false);
        } catch (URISyntaxException exc) {
            throw new Fault(configuration.getText("doclet.MalformedURL", pkglisturlpath.toString()), exc);
        } catch (MalformedURLException exc) {
            throw new Fault(configuration.getText("doclet.MalformedURL", pkglisturlpath.toString()), exc);
        } catch (IOException exc) {
            throw new Fault(configuration.getText("doclet.URL_error", pkglisturlpath.toString()), exc);
        }
    }

    private void readPackageListFromFile(String path, DocFile pkgListPath)
            throws Fault {
        DocFile file = pkgListPath.resolve(DocPaths.PACKAGE_LIST);
        if (!(file.isAbsolute() || linkoffline)) {
            file = file.resolveAgainst(DocumentationTool.Location.DOCUMENTATION_OUTPUT);
        }
        try {
            if (file.exists() && file.canRead()) {
                boolean pathIsRelative =
                        !DocFile.createFileForInput(configuration, path).isAbsolute()
                                && !isUrl(path);
                readPackageList(file.openInputStream(), path, pathIsRelative);
            } else {
                throw new Fault(configuration.getText("doclet.File_error", file.getPath()), null);
            }
        } catch (IOException exc) {
            throw new Fault(configuration.getText("doclet.File_error", file.getPath()), exc);
        }
    }

    private void readPackageList(InputStream input, String path,
                                 boolean relative)
            throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(input));
        StringBuilder strbuf = new StringBuilder();
        try {
            int c;
            while ((c = in.read()) >= 0) {
                char ch = (char) c;
                if (ch == '\n' || ch == '\r') {
                    if (strbuf.length() > 0) {
                        String packname = strbuf.toString();
                        String packpath = path +
                                packname.replace('.', '/') + '/';
                        new Item(packname, packpath, relative);
                        strbuf.setLength(0);
                    }
                } else {
                    strbuf.append(ch);
                }
            }
        } finally {
            input.close();
        }
    }

    public boolean isUrl(String urlCandidate) {
        try {
            new URL(urlCandidate);

            return true;
        } catch (MalformedURLException e) {

            return false;
        }
    }

    private class Item {

        final String packageName;

        final String path;

        final boolean relative;

        Item(String packageName, String path, boolean relative) {
            this.packageName = packageName;
            this.path = path;
            this.relative = relative;
            if (packageToItemMap == null) {
                packageToItemMap = new HashMap<String, Item>();
            }
            if (!packageToItemMap.containsKey(packageName)) {
                packageToItemMap.put(packageName, this);
            }
        }

        public String toString() {
            return packageName + (relative ? " -> " : " => ") + path;
        }
    }

    private class Fault extends Exception {
        private static final long serialVersionUID = 0;

        Fault(String msg, Exception cause) {
            super(msg, cause);
        }
    }
}
