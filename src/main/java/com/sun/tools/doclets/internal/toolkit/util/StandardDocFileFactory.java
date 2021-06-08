package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.tools.doclets.internal.toolkit.Configuration;
import com.sun.tools.javac.util.Assert;

import javax.tools.*;
import javax.tools.JavaFileManager.Location;
import java.io.*;
import java.util.*;

class StandardDocFileFactory extends DocFileFactory {
    private final StandardJavaFileManager fileManager;
    private File destDir;

    public StandardDocFileFactory(Configuration configuration) {
        super(configuration);
        fileManager = (StandardJavaFileManager) configuration.getFileManager();
    }

    private static File newFile(File dir, String path) {
        return (dir == null) ? new File(path) : new File(dir, path);
    }

    private File getDestDir() {
        if (destDir == null) {
            if (!configuration.destDirName.isEmpty()
                    || !fileManager.hasLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT)) {
                try {
                    String dirName = configuration.destDirName.isEmpty() ? "." : configuration.destDirName;
                    File dir = new File(dirName);
                    fileManager.setLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT, Arrays.asList(dir));
                } catch (IOException e) {
                    throw new DocletAbortException(e);
                }
            }
            destDir = fileManager.getLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT).iterator().next();
        }
        return destDir;
    }

    public DocFile createFileForDirectory(String file) {
        return new StandardDocFile(new File(file));
    }

    public DocFile createFileForInput(String file) {
        return new StandardDocFile(new File(file));
    }

    public DocFile createFileForOutput(DocPath path) {
        return new StandardDocFile(DocumentationTool.Location.DOCUMENTATION_OUTPUT, path);
    }

    @Override
    Iterable<DocFile> list(Location location, DocPath path) {
        if (location != StandardLocation.SOURCE_PATH)
            throw new IllegalArgumentException();
        Set<DocFile> files = new LinkedHashSet<DocFile>();
        Location l = fileManager.hasLocation(StandardLocation.SOURCE_PATH)
                ? StandardLocation.SOURCE_PATH : StandardLocation.CLASS_PATH;
        for (File f : fileManager.getLocation(l)) {
            if (f.isDirectory()) {
                f = new File(f, path.getPath());
                if (f.exists())
                    files.add(new StandardDocFile(f));
            }
        }
        return files;
    }

    class StandardDocFile extends DocFile {
        private File file;

        private StandardDocFile(File file) {
            super(configuration);
            this.file = file;
        }

        private StandardDocFile(Location location, DocPath path) {
            super(configuration, location, path);
            Assert.check(location == DocumentationTool.Location.DOCUMENTATION_OUTPUT);
            this.file = newFile(getDestDir(), path.getPath());
        }

        public InputStream openInputStream() throws IOException {
            JavaFileObject fo = getJavaFileObjectForInput(file);
            return new BufferedInputStream(fo.openInputStream());
        }

        public OutputStream openOutputStream() throws IOException {
            if (location != DocumentationTool.Location.DOCUMENTATION_OUTPUT)
                throw new IllegalStateException();
            OutputStream out = getFileObjectForOutput(path).openOutputStream();
            return new BufferedOutputStream(out);
        }

        public Writer openWriter() throws IOException {
            if (location != DocumentationTool.Location.DOCUMENTATION_OUTPUT)
                throw new IllegalStateException();
            OutputStream out = getFileObjectForOutput(path).openOutputStream();
            if (configuration.docencoding == null) {
                return new BufferedWriter(new OutputStreamWriter(out));
            } else {
                return new BufferedWriter(new OutputStreamWriter(out, configuration.docencoding));
            }
        }

        public boolean canRead() {
            return file.canRead();
        }

        public boolean canWrite() {
            return file.canWrite();
        }

        public boolean exists() {
            return file.exists();
        }

        public String getName() {
            return file.getName();
        }

        public String getPath() {
            return file.getPath();
        }

        public boolean isAbsolute() {
            return file.isAbsolute();
        }

        public boolean isDirectory() {
            return file.isDirectory();
        }

        public boolean isFile() {
            return file.isFile();
        }

        public boolean isSameFile(DocFile other) {
            if (!(other instanceof StandardDocFile))
                return false;
            try {
                return file.exists()
                        && file.getCanonicalFile().equals(((StandardDocFile) other).file.getCanonicalFile());
            } catch (IOException e) {
                return false;
            }
        }

        public Iterable<DocFile> list() {
            List<DocFile> files = new ArrayList<DocFile>();
            for (File f : file.listFiles()) {
                files.add(new StandardDocFile(f));
            }
            return files;
        }

        public boolean mkdirs() {
            return file.mkdirs();
        }

        public DocFile resolve(DocPath p) {
            return resolve(p.getPath());
        }

        public DocFile resolve(String p) {
            if (location == null && path == null) {
                return new StandardDocFile(new File(file, p));
            } else {
                return new StandardDocFile(location, path.resolve(p));
            }
        }

        public DocFile resolveAgainst(Location locn) {
            if (locn != DocumentationTool.Location.DOCUMENTATION_OUTPUT)
                throw new IllegalArgumentException();
            return new StandardDocFile(newFile(getDestDir(), file.getPath()));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("StandardDocFile[");
            if (location != null)
                sb.append("locn:").append(location).append(",");
            if (path != null)
                sb.append("path:").append(path.getPath()).append(",");
            sb.append("file:").append(file);
            sb.append("]");
            return sb.toString();
        }

        private JavaFileObject getJavaFileObjectForInput(File file) {
            return fileManager.getJavaFileObjects(file).iterator().next();
        }

        private FileObject getFileObjectForOutput(DocPath path) throws IOException {


            String p = path.getPath();
            int lastSep = -1;
            for (int i = 0; i < p.length(); i++) {
                char ch = p.charAt(i);
                if (ch == '/') {
                    lastSep = i;
                } else if (i == lastSep + 1 && !Character.isJavaIdentifierStart(ch)
                        || !Character.isJavaIdentifierPart(ch)) {
                    break;
                }
            }
            String pkg = (lastSep == -1) ? "" : p.substring(0, lastSep);
            String rest = p.substring(lastSep + 1);
            return fileManager.getFileForOutput(location, pkg, rest, null);
        }
    }
}
