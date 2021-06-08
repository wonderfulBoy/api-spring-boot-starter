package com.sun.tools.doclets.formats.html.markup;

import java.util.Locale;

public enum HtmlTag {
    A(BlockType.INLINE, EndTag.END),
    BLOCKQUOTE,
    BODY(BlockType.OTHER, EndTag.END),
    BR(BlockType.INLINE, EndTag.NOEND),
    CAPTION,
    CENTER,
    CODE(BlockType.INLINE, EndTag.END),
    DD,
    DIR,
    DIV,
    DL,
    DT,
    EM(BlockType.INLINE, EndTag.END),
    FONT(BlockType.INLINE, EndTag.END),
    FRAME(BlockType.OTHER, EndTag.NOEND),
    FRAMESET(BlockType.OTHER, EndTag.END),
    H1,
    H2,
    H3,
    H4,
    H5,
    H6,
    HEAD(BlockType.OTHER, EndTag.END),
    HR(BlockType.BLOCK, EndTag.NOEND),
    HTML(BlockType.OTHER, EndTag.END),
    I(BlockType.INLINE, EndTag.END),
    IMG(BlockType.INLINE, EndTag.NOEND),
    LI,
    LISTING,
    LINK(BlockType.OTHER, EndTag.NOEND),
    MENU,
    META(BlockType.OTHER, EndTag.NOEND),
    NOFRAMES(BlockType.OTHER, EndTag.END),
    NOSCRIPT(BlockType.OTHER, EndTag.END),
    OL,
    P,
    PRE,
    SCRIPT(BlockType.OTHER, EndTag.END),
    SMALL(BlockType.INLINE, EndTag.END),
    SPAN(BlockType.INLINE, EndTag.END),
    STRONG(BlockType.INLINE, EndTag.END),
    SUB(BlockType.INLINE, EndTag.END),
    TABLE,
    TBODY,
    TD,
    TH,
    TITLE(BlockType.OTHER, EndTag.END),
    TR,
    TT(BlockType.INLINE, EndTag.END),
    UL;
    public final BlockType blockType;
    public final EndTag endTag;
    public final String value;

    HtmlTag() {
        this(BlockType.BLOCK, EndTag.END);
    }

    HtmlTag(BlockType blockType, EndTag endTag) {
        this.blockType = blockType;
        this.endTag = endTag;
        this.value = name().toLowerCase(Locale.US);
    }

    public boolean endTagRequired() {
        return (endTag == EndTag.END);
    }

    public String toString() {
        return value;
    }

    public enum BlockType {
        BLOCK,
        INLINE,
        OTHER
    }

    public enum EndTag {
        END,
        NOEND
    }
}
