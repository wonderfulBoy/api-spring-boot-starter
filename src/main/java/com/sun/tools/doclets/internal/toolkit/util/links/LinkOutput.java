package com.sun.tools.doclets.internal.toolkit.util.links;

public interface LinkOutput {

    void append(Object o);

    void insert(int offset, Object o);
}
