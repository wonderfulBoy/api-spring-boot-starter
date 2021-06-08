package com.sun.tools.doclets.formats.html.markup;

public enum HtmlAttr {
    ALT,
    BORDER,
    CELLPADDING,
    CELLSPACING,
    CLASS,
    CLEAR,
    COLS,
    CONTENT,
    HREF,
    HTTP_EQUIV("http-equiv"),
    ID,
    LANG,
    NAME,
    ONLOAD,
    REL,
    ROWS,
    SCOPE,
    SCROLLING,
    SRC,
    SUMMARY,
    TARGET,
    TITLE,
    TYPE,
    WIDTH;
    private final String value;

    HtmlAttr() {
        this.value = name().toLowerCase();
    }

    HtmlAttr(String name) {
        this.value = name;
    }

    public String toString() {
        return value;
    }
}
