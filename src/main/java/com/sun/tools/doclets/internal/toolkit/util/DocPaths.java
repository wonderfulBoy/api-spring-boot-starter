package com.sun.tools.doclets.internal.toolkit.util;

public class DocPaths {

    public static final DocPath ALLCLASSES_FRAME = DocPath.create("allclasses-frame.html");

    public static final DocPath ALLCLASSES_NOFRAME = DocPath.create("allclasses-noframe.html");

    public static final DocPath CLASS_USE = DocPath.create("class-use");

    public static final DocPath CONSTANT_VALUES = DocPath.create("constant-values.html");

    public static final DocPath DEPRECATED_LIST = DocPath.create("deprecated-list.html");

    public static final DocPath DOC_FILES = DocPath.create("doc-files");

    public static final DocPath HELP_DOC = DocPath.create("help-doc.html");

    public static final DocPath INDEX = DocPath.create("index.html");

    public static final DocPath INDEX_ALL = DocPath.create("index-all.html");

    public static final DocPath INDEX_FILES = DocPath.create("index-files");
    public static final DocPath JAVASCRIPT = DocPath.create("script.js");
    public static final DocPath OVERVIEW_FRAME = DocPath.create("overview-frame.html");
    public static final DocPath OVERVIEW_SUMMARY = DocPath.create("overview-summary.html");
    public static final DocPath OVERVIEW_TREE = DocPath.create("overview-tree.html");
    public static final DocPath PACKAGE_FRAME = DocPath.create("package-frame.html");
    public static final DocPath PACKAGE_LIST = DocPath.create("package-list");
    public static final DocPath PACKAGE_SUMMARY = DocPath.create("package-summary.html");
    public static final DocPath PACKAGE_TREE = DocPath.create("package-tree.html");
    public static final DocPath PACKAGE_USE = DocPath.create("package-use.html");
    public static final DocPath PROFILE_OVERVIEW_FRAME = DocPath.create("profile-overview-frame.html");
    public static final DocPath RESOURCES = DocPath.create("resources");
    public static final DocPath SERIALIZED_FORM = DocPath.create("serialized-form.html");
    public static final DocPath SOURCE_OUTPUT = DocPath.create("src-html");
    public static final DocPath STYLESHEET = DocPath.create("stylesheet.css");

    public static final DocPath indexN(int n) {
        return DocPath.create("index-" + n + ".html");
    }

    public static final DocPath profileFrame(String profileName) {
        return DocPath.create(profileName + "-frame.html");
    }

    public static final DocPath profilePackageFrame(String profileName) {
        return DocPath.create(profileName + "-package-frame.html");
    }

    public static final DocPath profilePackageSummary(String profileName) {
        return DocPath.create(profileName + "-package-summary.html");
    }

    public static final DocPath profileSummary(String profileName) {
        return DocPath.create(profileName + "-summary.html");
    }
}
