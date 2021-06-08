package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.tools.doclets.internal.toolkit.Configuration;

import javax.tools.DocumentationTool;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardLocation;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class SimpleDocFileFactory extends DocFileFactory {
    public SimpleDocFileFactory(Configuration configuration) {
        super(configuration);
    }

    public DocFile createFileForDirectory(String file) {
        return new SimpleDocFile(new File(file));
    }

    public DocFile createFileForInput(String file) {
        return new SimpleDocFile(new File(file));
    }

    public DocFile createFileForOutput(DocPath path) {
        return new SimpleDocFile(DocumentationTool.Location.DOCUMENTATION_OUTPUT, path);
    }

    @Override
    Iterable<DocFile> list(Location location, DocPath path) {
        if (location != StandardLocation.SOURCE_PATH)
            throw new IllegalArgumentException();
        Set<DocFile> files = new LinkedHashSet<DocFile>();
        for (String s : configuration.sourcepath.split(File.pathSeparator)) {
            if (s.isEmpty())
                continue;
            File f = new File(s);
            if (f.isDirectory()) {
                f = new File(f, path.getPath());
                if (f.exists())
                    files.add(new SimpleDocFile(f));
            }
        }
        return files;
    }

    class SimpleDocFile extends DocFile {
        private File file;

        private SimpleDocFile(File file) {
            super(configuration);
            this.file = file;
        }

        private SimpleDocFile(Location location, DocPath path) {
            super(configuration, location, path);
            String destDirName = configuration.destDirName;
            this.file = destDirName.isEmpty() ? new File(path.getPath())
                    : new File(destDirName, path.getPath());
        }

        public InputStream openInputStream() throws FileNotFoundException {
            return new BufferedInputStream(new FileInputStream(file));
        }

        public OutputStream openOutputStream() throws IOException {
            if (location != DocumentationTool.Location.DOCUMENTATION_OUTPUT)
                throw new IllegalStateException();
            createDirectoryForFile(file);
            return new BufferedOutputStream(new FileOutputStream(file));
        }

        public Writer openWriter() throws IOException {
            if (location != DocumentationTool.Location.DOCUMENTATION_OUTPUT)
                throw new IllegalStateException();
            createDirectoryForFile(file);
            FileOutputStream fos = new FileOutputStream(file);
            if (configuration.docencoding == null) {
                return new BufferedWriter(new OutputStreamWriter(fos));
            } else {
                return new BufferedWriter(new OutputStreamWriter(fos, configuration.docencoding));
            }
        }

        public boolean canRead() {
            return file.canRead();
        }

        public boolean canWrite() {
            return file.canRead();
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
            if (!(other instanceof SimpleDocFile))
                return false;
            try {
                return file.exists()
                        && file.getCanonicalFile().equals(((SimpleDocFile) other).file.getCanonicalFile());
            } catch (IOException e) {
                return false;
            }
        }

        public Iterable<DocFile> list() {
            List<DocFile> files = new ArrayList<DocFile>();
            for (File f : file.listFiles()) {
                files.add(new SimpleDocFile(f));
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
                return new SimpleDocFile(new File(file, p));
            } else {
                return new SimpleDocFile(location, path.resolve(p));
            }
        }

        public DocFile resolveAgainst(Location locn) {
            if (locn != DocumentationTool.Location.DOCUMENTATION_OUTPUT)
                throw new IllegalArgumentException();
            return new SimpleDocFile(
                    new File(configuration.destDirName, file.getPath()));
        }

        private void createDirectoryForFile(File file) {
            File dir = file.getParentFile();
            if (dir == null || dir.exists() || dir.mkdirs())
                return;
            configuration.message.error(
                    "doclet.Unable_to_create_directory_0", dir.getPath());
            throw new DocletAbortException("can't create directory");
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("DocFile[");
            if (location != null)
                sb.append("locn:").append(location).append(",");
            if (path != null)
                sb.append("path:").append(path.getPath()).append(",");
            sb.append("file:").append(file);
            sb.append("]");
            return sb.toString();
        }
    }
}
