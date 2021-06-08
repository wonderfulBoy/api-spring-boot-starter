package com.github.api.sun.source.util;

@jdk.Exported
public interface Plugin {
    String getName();

    void init(JavacTask task, String... args);
}
