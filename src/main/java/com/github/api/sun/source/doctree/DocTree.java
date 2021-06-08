package com.github.api.sun.source.doctree;

@jdk.Exported
public interface DocTree {
    Kind getKind();

    <R, D> R accept(DocTreeVisitor<R, D> visitor, D data);

    @jdk.Exported
    enum Kind {
        ATTRIBUTE,
        AUTHOR("author"),
        CODE("code"),
        COMMENT,
        DEPRECATED("deprecated"),
        DOC_COMMENT,
        DOC_ROOT("docRoot"),
        END_ELEMENT,
        ENTITY,
        ERRONEOUS,
        EXCEPTION("exception"),
        IDENTIFIER,
        INHERIT_DOC("inheritDoc"),
        LINK("link"),
        LINK_PLAIN("linkplain"),
        LITERAL("literal"),
        PARAM("param"),
        REFERENCE,
        RETURN("return"),
        SEE("see"),
        SERIAL("serial"),
        SERIAL_DATA("serialData"),
        SERIAL_FIELD("serialField"),
        SINCE("since"),
        START_ELEMENT,
        TEXT,
        THROWS("throws"),
        UNKNOWN_BLOCK_TAG,
        UNKNOWN_INLINE_TAG,
        VALUE("value"),
        VERSION("version"),
        OTHER;
        public final String tagName;

        Kind() {
            tagName = null;
        }

        Kind(String tagName) {
            this.tagName = tagName;
        }
    }
}
