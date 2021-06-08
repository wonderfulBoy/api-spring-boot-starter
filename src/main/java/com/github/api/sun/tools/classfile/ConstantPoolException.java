package com.github.api.sun.tools.classfile;

public class ConstantPoolException extends Exception {
    private static final long serialVersionUID = -2324397349644754565L;
    public final int index;

    ConstantPoolException(int index) {
        this.index = index;
    }
}
