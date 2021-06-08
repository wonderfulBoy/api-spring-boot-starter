package com.github.api.sun.tools.javac;

import java.io.PrintWriter;

@jdk.Exported
public class Main {

    public static void main(String[] args) throws Exception {
        System.exit(compile(args));
    }

    public static int compile(String[] args) {
        com.github.api.sun.tools.javac.main.Main compiler =
                new com.github.api.sun.tools.javac.main.Main("javac");
        return compiler.compile(args).exitCode;
    }

    public static int compile(String[] args, PrintWriter out) {
        com.github.api.sun.tools.javac.main.Main compiler =
                new com.github.api.sun.tools.javac.main.Main("javac", out);
        return compiler.compile(args).exitCode;
    }
}
