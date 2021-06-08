package com.github.api.sun.javadoc;

import java.io.File;

public interface SourcePosition {
    File file();

    int line();

    int column();

    String toString();
}
