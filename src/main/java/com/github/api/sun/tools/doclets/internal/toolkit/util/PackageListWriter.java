package com.github.api.sun.tools.doclets.internal.toolkit.util;

import com.github.api.sun.javadoc.PackageDoc;
import com.github.api.sun.javadoc.RootDoc;
import com.github.api.sun.tools.doclets.internal.toolkit.Configuration;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;

public class PackageListWriter extends PrintWriter {
    private Configuration configuration;

    public PackageListWriter(Configuration configuration) throws IOException {
        super(DocFile.createFileForOutput(configuration, DocPaths.PACKAGE_LIST).openWriter());
        this.configuration = configuration;
    }

    public static void generate(Configuration configuration) {
        PackageListWriter packgen;
        try {
            packgen = new PackageListWriter(configuration);
            packgen.generatePackageListFile(configuration.root);
            packgen.close();
        } catch (IOException exc) {
            configuration.message.error("doclet.exception_encountered",
                    exc.toString(), DocPaths.PACKAGE_LIST);
            throw new DocletAbortException(exc);
        }
    }

    protected void generatePackageListFile(RootDoc root) {
        PackageDoc[] packages = configuration.packages;
        ArrayList<String> names = new ArrayList<String>();
        for (int i = 0; i < packages.length; i++) {


            if (!(configuration.nodeprecated && Util.isDeprecated(packages[i])))
                names.add(packages[i].name());
        }
        Collections.sort(names);
        for (int i = 0; i < names.size(); i++) {
            println(names.get(i));
        }
    }
}
