package com.sun.tools.doclets.standard;

import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.RootDoc;
import com.sun.tools.doclets.formats.html.HtmlDoclet;

public class Standard {
    public static int optionLength(String option) {
        return HtmlDoclet.optionLength(option);
    }

    public static boolean start(RootDoc root) {
        return HtmlDoclet.start(root);
    }

    public static boolean validOptions(String[][] options,
                                       DocErrorReporter reporter) {
        return HtmlDoclet.validOptions(options, reporter);
    }

    public static LanguageVersion languageVersion() {
        return HtmlDoclet.languageVersion();
    }
}
