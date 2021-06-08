package com.sun.tools.javac.file;

import javax.tools.JavaFileObject;
import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.text.Normalizer;

class RegularFileObject extends BaseFileObject {

    final static boolean isMacOS = System.getProperty("os.name", "").contains("OS X");
    final File file;
    private boolean hasParents = false;
    private String name;
    private Reference<File> absFileRef;

    public RegularFileObject(JavacFileManager fileManager, File f) {
        this(fileManager, f.getName(), f);
    }

    public RegularFileObject(JavacFileManager fileManager, String name, File f) {
        super(fileManager);
        if (f.isDirectory()) {
            throw new IllegalArgumentException("directories not supported");
        }
        this.name = name;
        this.file = f;
    }

    @Override
    public URI toUri() {
        return file.toURI().normalize();
    }

    @Override
    public String getName() {
        return file.getPath();
    }

    @Override
    public String getShortName() {
        return name;
    }

    @Override
    public Kind getKind() {
        return getKind(name);
    }

    @Override
    public InputStream openInputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        fileManager.flushCache(this);
        ensureParentDirectoriesExist();
        return new FileOutputStream(file);
    }

    @Override
    public CharBuffer getCharContent(boolean ignoreEncodingErrors) throws IOException {
        CharBuffer cb = fileManager.getCachedContent(this);
        if (cb == null) {
            InputStream in = new FileInputStream(file);
            try {
                ByteBuffer bb = fileManager.makeByteBuffer(in);
                JavaFileObject prev = fileManager.log.useSource(this);
                try {
                    cb = fileManager.decode(bb, ignoreEncodingErrors);
                } finally {
                    fileManager.log.useSource(prev);
                }
                fileManager.recycleByteBuffer(bb);
                if (!ignoreEncodingErrors) {
                    fileManager.cache(this, cb);
                }
            } finally {
                in.close();
            }
        }
        return cb;
    }

    @Override
    public Writer openWriter() throws IOException {
        fileManager.flushCache(this);
        ensureParentDirectoriesExist();
        return new OutputStreamWriter(new FileOutputStream(file), fileManager.getEncodingName());
    }

    @Override
    public long getLastModified() {
        return file.lastModified();
    }

    @Override
    public boolean delete() {
        return file.delete();
    }

    @Override
    protected CharsetDecoder getDecoder(boolean ignoreEncodingErrors) {
        return fileManager.getDecoder(fileManager.getEncodingName(), ignoreEncodingErrors);
    }

    @Override
    protected String inferBinaryName(Iterable<? extends File> path) {
        String fPath = file.getPath();

        for (File dir : path) {

            String dPath = dir.getPath();
            if (dPath.length() == 0)
                dPath = System.getProperty("user.dir");
            if (!dPath.endsWith(File.separator))
                dPath += File.separator;
            if (fPath.regionMatches(true, 0, dPath, 0, dPath.length())
                    && new File(fPath.substring(0, dPath.length())).equals(new File(dPath))) {
                String relativeName = fPath.substring(dPath.length());
                return removeExtension(relativeName).replace(File.separatorChar, '.');
            }
        }
        return null;
    }

    @Override
    public boolean isNameCompatible(String cn, Kind kind) {
        cn.getClass();

        if (kind == Kind.OTHER && getKind() != kind) {
            return false;
        }
        String n = cn + kind.extension;
        if (name.equals(n)) {
            return true;
        }
        if (isMacOS && Normalizer.isNormalized(name, Normalizer.Form.NFD)
                && Normalizer.isNormalized(n, Normalizer.Form.NFC)) {


            String normName = Normalizer.normalize(name, Normalizer.Form.NFC);
            if (normName.equals(n)) {
                this.name = normName;
                return true;
            }
        }
        if (name.equalsIgnoreCase(n)) {
            try {

                return file.getCanonicalFile().getName().equals(n);
            } catch (IOException e) {
            }
        }
        return false;
    }

    private void ensureParentDirectoriesExist() throws IOException {
        if (!hasParents) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    if (!parent.exists() || !parent.isDirectory()) {
                        throw new IOException("could not create parent directories");
                    }
                }
            }
            hasParents = true;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other)
            return true;
        if (!(other instanceof RegularFileObject))
            return false;
        RegularFileObject o = (RegularFileObject) other;
        return getAbsoluteFile().equals(o.getAbsoluteFile());
    }

    @Override
    public int hashCode() {
        return getAbsoluteFile().hashCode();
    }

    private File getAbsoluteFile() {
        File absFile = (absFileRef == null ? null : absFileRef.get());
        if (absFile == null) {
            absFile = file.getAbsoluteFile();
            absFileRef = new SoftReference<File>(absFile);
        }
        return absFile;
    }
}
