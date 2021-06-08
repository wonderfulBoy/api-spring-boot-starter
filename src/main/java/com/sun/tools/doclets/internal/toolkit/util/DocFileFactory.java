package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.tools.doclets.internal.toolkit.Configuration;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardJavaFileManager;
import java.util.Map;
import java.util.WeakHashMap;

abstract class DocFileFactory {
    private static final Map<Configuration, DocFileFactory> factories =
            new WeakHashMap<Configuration, DocFileFactory>();
    protected Configuration configuration;

    protected DocFileFactory(Configuration configuration) {
        this.configuration = configuration;
    }

    static synchronized DocFileFactory getFactory(Configuration configuration) {
        DocFileFactory f = factories.get(configuration);
        if (f == null) {
            JavaFileManager fm = configuration.getFileManager();
            if (fm instanceof StandardJavaFileManager)
                f = new StandardDocFileFactory(configuration);
            else {
                try {
                    Class<?> pathFileManagerClass =
                            Class.forName("com.sun.tools.javac.nio.PathFileManager");
                    if (pathFileManagerClass.isAssignableFrom(fm.getClass()))
                        f = new PathDocFileFactory(configuration);
                } catch (Throwable t) {
                    throw new IllegalStateException(t);
                }
            }
            factories.put(configuration, f);
        }
        return f;
    }

    abstract DocFile createFileForDirectory(String file);

    abstract DocFile createFileForInput(String file);

    abstract DocFile createFileForOutput(DocPath path);

    abstract Iterable<DocFile> list(Location location, DocPath path);
}
