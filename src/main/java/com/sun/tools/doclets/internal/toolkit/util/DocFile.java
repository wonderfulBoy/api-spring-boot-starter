package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.tools.doclets.internal.toolkit.Configuration;

import javax.tools.JavaFileManager.Location;
import java.io.*;

public abstract class DocFile {

    protected final Location location;
    protected final DocPath path;
    private final Configuration configuration;

    protected DocFile(Configuration configuration) {
        this.configuration = configuration;
        this.location = null;
        this.path = null;
    }

    protected DocFile(Configuration configuration, Location location, DocPath path) {
        this.configuration = configuration;
        this.location = location;
        this.path = path;
    }

    public static DocFile createFileForDirectory(Configuration configuration, String file) {
        return DocFileFactory.getFactory(configuration).createFileForDirectory(file);
    }

    public static DocFile createFileForInput(Configuration configuration, String file) {
        return DocFileFactory.getFactory(configuration).createFileForInput(file);
    }

    public static DocFile createFileForOutput(Configuration configuration, DocPath path) {
        return DocFileFactory.getFactory(configuration).createFileForOutput(path);
    }

    public static Iterable<DocFile> list(Configuration configuration, Location location, DocPath path) {
        return DocFileFactory.getFactory(configuration).list(location, path);
    }

    public abstract InputStream openInputStream() throws IOException;

    public abstract OutputStream openOutputStream() throws IOException;

    public abstract Writer openWriter() throws IOException;

    public void copyFile(DocFile fromFile) throws IOException {
        InputStream input = fromFile.openInputStream();
        OutputStream output = openOutputStream();
        try {
            byte[] bytearr = new byte[1024];
            int len;
            while ((len = input.read(bytearr)) != -1) {
                output.write(bytearr, 0, len);
            }
        } catch (FileNotFoundException exc) {
        } catch (SecurityException exc) {
        } finally {
            input.close();
            output.close();
        }
    }

    public void copyResource(DocPath resource, boolean overwrite, boolean replaceNewLine) {
        if (exists() && !overwrite)
            return;
        try {
            InputStream in = Configuration.class.getResourceAsStream(resource.getPath());
            if (in == null)
                return;
            OutputStream out = openOutputStream();
            try {
                if (!replaceNewLine) {
                    byte[] buf = new byte[2048];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } else {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    BufferedWriter writer;
                    if (configuration.docencoding == null) {
                        writer = new BufferedWriter(new OutputStreamWriter(out));
                    } else {
                        writer = new BufferedWriter(new OutputStreamWriter(out,
                                configuration.docencoding));
                    }
                    try {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            writer.write(line);
                            writer.write(DocletConstants.NL);
                        }
                    } finally {
                        reader.close();
                        writer.close();
                    }
                }
            } finally {
                in.close();
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new DocletAbortException(e);
        }
    }

    public abstract boolean canRead();

    public abstract boolean canWrite();

    public abstract boolean exists();

    public abstract String getName();

    public abstract String getPath();

    public abstract boolean isAbsolute();

    public abstract boolean isDirectory();

    public abstract boolean isFile();

    public abstract boolean isSameFile(DocFile other);

    public abstract Iterable<DocFile> list() throws IOException;

    public abstract boolean mkdirs();

    public abstract DocFile resolve(DocPath p);

    public abstract DocFile resolve(String p);

    public abstract DocFile resolveAgainst(Location locn);
}
