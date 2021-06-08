package com.github.api.sun.tools.doclets.standard;

import com.github.api.sun.javadoc.DocErrorReporter;
import com.github.api.sun.javadoc.LanguageVersion;
import com.github.api.sun.javadoc.RootDoc;
import com.github.api.sun.tools.doclets.formats.html.HtmlDoclet;

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
