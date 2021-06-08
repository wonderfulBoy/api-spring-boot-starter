package com.github.api.sun.tools.javadoc;

public class Main {
    private Main() {
    }

    public static void main(String... args) {
        System.exit(execute(args));
    }

    public static int execute(String... args) {
        Start jdoc = new Start();
        return jdoc.begin(args);
    }
}
