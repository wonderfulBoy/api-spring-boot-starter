package com.github.api.sun.tools.javac.util;

public interface Filter<T> {

    boolean accepts(T t);
}
