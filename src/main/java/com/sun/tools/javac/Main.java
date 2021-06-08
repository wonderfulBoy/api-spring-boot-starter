package com.sun.tools.javac;

import java.io.PrintWriter;

@jdk.Exported
public class Main {

    public static void main(String[] args) throws Exception {
        System.exit(compile(args));
    }

    public static int compile(String[] args) {
        com.sun.tools.javac.main.Main compiler =
                new com.sun.tools.javac.main.Main("javac");
        return compiler.compile(args).exitCode;
    }

    public static int compile(String[] args, PrintWriter out) {
        com.sun.tools.javac.main.Main compiler =
                new com.sun.tools.javac.main.Main("javac", out);
        return compiler.compile(args).exitCode;
    }
}
