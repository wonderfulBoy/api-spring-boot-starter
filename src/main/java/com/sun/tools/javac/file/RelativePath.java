package com.sun.tools.javac.file;

import javax.tools.JavaFileObject;
import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class RelativePath implements Comparable<RelativePath> {

    protected final String path;

    protected RelativePath(String p) {
        path = p;
    }

    public abstract RelativeDirectory dirname();

    public abstract String basename();

    public File getFile(File directory) {
        if (path.length() == 0)
            return directory;
        return new File(directory, path.replace('/', File.separatorChar));
    }

    public int compareTo(RelativePath other) {
        return path.compareTo(other.path);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof RelativePath))
            return false;
        return path.equals(((RelativePath) other).path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return "RelPath[" + path + "]";
    }

    public String getPath() {
        return path;
    }

    public static class RelativeDirectory extends RelativePath {
        public RelativeDirectory(String p) {
            super(p.length() == 0 || p.endsWith("/") ? p : p + "/");
        }

        public RelativeDirectory(RelativeDirectory d, String p) {
            this(d.path + p);
        }

        static RelativeDirectory forPackage(CharSequence packageName) {
            return new RelativeDirectory(packageName.toString().replace('.', '/'));
        }

        @Override
        public RelativeDirectory dirname() {
            int l = path.length();
            if (l == 0)
                return this;
            int sep = path.lastIndexOf('/', l - 2);
            return new RelativeDirectory(path.substring(0, sep + 1));
        }

        @Override
        public String basename() {
            int l = path.length();
            if (l == 0)
                return path;
            int sep = path.lastIndexOf('/', l - 2);
            return path.substring(sep + 1, l - 1);
        }

        boolean contains(RelativePath other) {
            return other.path.length() > path.length() && other.path.startsWith(path);
        }

        @Override
        public String toString() {
            return "RelativeDirectory[" + path + "]";
        }
    }

    public static class RelativeFile extends RelativePath {
        public RelativeFile(String p) {
            super(p);
            if (p.endsWith("/"))
                throw new IllegalArgumentException(p);
        }

        public RelativeFile(RelativeDirectory d, String p) {
            this(d.path + p);
        }

        RelativeFile(RelativeDirectory d, RelativePath p) {
            this(d, p.path);
        }

        static RelativeFile forClass(CharSequence className, JavaFileObject.Kind kind) {
            return new RelativeFile(className.toString().replace('.', '/') + kind.extension);
        }

        @Override
        public RelativeDirectory dirname() {
            int sep = path.lastIndexOf('/');
            return new RelativeDirectory(path.substring(0, sep + 1));
        }

        @Override
        public String basename() {
            int sep = path.lastIndexOf('/');
            return path.substring(sep + 1);
        }

        ZipEntry getZipEntry(ZipFile zip) {
            return zip.getEntry(path);
        }

        @Override
        public String toString() {
            return "RelativeFile[" + path + "]";
        }
    }
}
