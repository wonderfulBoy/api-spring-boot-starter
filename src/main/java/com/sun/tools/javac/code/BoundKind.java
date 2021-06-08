package com.sun.tools.javac.code;

public enum BoundKind {
    EXTENDS("? extends "),
    SUPER("? super "),
    UNBOUND("?");
    private final String name;

    BoundKind(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }
}
