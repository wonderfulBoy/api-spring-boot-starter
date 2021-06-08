package com.github.api.sun.tools.javac.file;

import com.github.api.sun.tools.javac.util.Context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class FSInfo {

    protected FSInfo() {
    }

    protected FSInfo(Context context) {
        context.put(FSInfo.class, this);
    }

    public static FSInfo instance(Context context) {
        FSInfo instance = context.get(FSInfo.class);
        if (instance == null)
            instance = new FSInfo();
        return instance;
    }

    public File getCanonicalFile(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    public boolean exists(File file) {
        return file.exists();
    }

    public boolean isDirectory(File file) {
        return file.isDirectory();
    }

    public boolean isFile(File file) {
        return file.isFile();
    }

    public List<File> getJarClassPath(File file) throws IOException {
        String parent = file.getParent();
        JarFile jarFile = new JarFile(file);
        try {
            Manifest man = jarFile.getManifest();
            if (man == null)
                return Collections.emptyList();
            Attributes attr = man.getMainAttributes();
            if (attr == null)
                return Collections.emptyList();
            String path = attr.getValue(Attributes.Name.CLASS_PATH);
            if (path == null)
                return Collections.emptyList();
            List<File> list = new ArrayList<File>();
            for (StringTokenizer st = new StringTokenizer(path); st.hasMoreTokens(); ) {
                String elt = st.nextToken();
                File f = (parent == null ? new File(elt) : new File(parent, elt));
                list.add(f);
            }
            return list;
        } finally {
            jarFile.close();
        }
    }
}
