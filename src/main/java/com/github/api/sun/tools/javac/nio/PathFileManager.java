package com.github.api.sun.tools.javac.nio;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

public interface PathFileManager extends JavaFileManager {

    FileSystem getDefaultFileSystem();

    void setDefaultFileSystem(FileSystem fs);

    Iterable<? extends JavaFileObject> getJavaFileObjectsFromPaths(
            Iterable<? extends Path> paths);

    Iterable<? extends JavaFileObject> getJavaFileObjects(Path... paths);

    Path getPath(FileObject fo);

    Iterable<? extends Path> getLocation(Location location);

    void setLocation(Location location, Iterable<? extends Path> searchPath) throws IOException;
}
