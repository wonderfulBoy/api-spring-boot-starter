package com.github.api.sun.tools.doclets.internal.toolkit.util;

import com.github.api.sun.tools.doclets.internal.toolkit.Configuration;
import com.github.api.sun.tools.javac.nio.PathFileManager;

import javax.tools.DocumentationTool;
import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

class PathDocFileFactory extends DocFileFactory {
    private final PathFileManager fileManager;
    private final Path destDir;

    public PathDocFileFactory(Configuration configuration) {
        super(configuration);
        fileManager = (PathFileManager) configuration.getFileManager();
        if (!configuration.destDirName.isEmpty()
                || !fileManager.hasLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT)) {
            try {
                String dirName = configuration.destDirName.isEmpty() ? "." : configuration.destDirName;
                Path dir = fileManager.getDefaultFileSystem().getPath(dirName);
                fileManager.setLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT, Arrays.asList(dir));
            } catch (IOException e) {
                throw new DocletAbortException(e);
            }
        }
        destDir = fileManager.getLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT).iterator().next();
    }

    public DocFile createFileForDirectory(String file) {
        return new StandardDocFile(fileManager.getDefaultFileSystem().getPath(file));
    }

    public DocFile createFileForInput(String file) {
        return new StandardDocFile(fileManager.getDefaultFileSystem().getPath(file));
    }

    public DocFile createFileForOutput(DocPath path) {
        return new StandardDocFile(DocumentationTool.Location.DOCUMENTATION_OUTPUT, path);
    }

    @Override
    Iterable<DocFile> list(Location location, DocPath path) {
        if (location != StandardLocation.SOURCE_PATH)
            throw new IllegalArgumentException();
        Set<DocFile> files = new LinkedHashSet<DocFile>();
        if (fileManager.hasLocation(location)) {
            for (Path f : fileManager.getLocation(location)) {
                if (Files.isDirectory(f)) {
                    f = f.resolve(path.getPath());
                    if (Files.exists(f))
                        files.add(new StandardDocFile(f));
                }
            }
        }
        return files;
    }

    class StandardDocFile extends DocFile {
        private Path file;

        private StandardDocFile(Path file) {
            super(configuration);
            this.file = file;
        }

        private StandardDocFile(Location location, DocPath path) {
            super(configuration, location, path);
            this.file = destDir.resolve(path.getPath());
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
            return Files.isReadable(file);
        }

        public boolean canWrite() {
            return Files.isWritable(file);
        }

        public boolean exists() {
            return Files.exists(file);
        }

        public String getName() {
            return file.getFileName().toString();
        }

        public String getPath() {
            return file.toString();
        }

        public boolean isAbsolute() {
            return file.isAbsolute();
        }

        public boolean isDirectory() {
            return Files.isDirectory(file);
        }

        public boolean isFile() {
            return Files.isRegularFile(file);
        }

        public boolean isSameFile(DocFile other) {
            if (!(other instanceof StandardDocFile))
                return false;
            try {
                return Files.isSameFile(file, ((StandardDocFile) other).file);
            } catch (IOException e) {
                return false;
            }
        }

        public Iterable<DocFile> list() throws IOException {
            List<DocFile> files = new ArrayList<DocFile>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(file)) {
                for (Path f : ds) {
                    files.add(new StandardDocFile(f));
                }
            }
            return files;
        }

        public boolean mkdirs() {
            try {
                Files.createDirectories(file);
                return true;
            } catch (IOException e) {
                return false;
            }
        }

        public DocFile resolve(DocPath p) {
            return resolve(p.getPath());
        }

        public DocFile resolve(String p) {
            if (location == null && path == null) {
                return new StandardDocFile(file.resolve(p));
            } else {
                return new StandardDocFile(location, path.resolve(p));
            }
        }

        public DocFile resolveAgainst(Location locn) {
            if (locn != DocumentationTool.Location.DOCUMENTATION_OUTPUT)
                throw new IllegalArgumentException();
            return new StandardDocFile(destDir.resolve(file));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("PathDocFile[");
            if (location != null)
                sb.append("locn:").append(location).append(",");
            if (path != null)
                sb.append("path:").append(path.getPath()).append(",");
            sb.append("file:").append(file);
            sb.append("]");
            return sb.toString();
        }

        private JavaFileObject getJavaFileObjectForInput(Path file) {
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
