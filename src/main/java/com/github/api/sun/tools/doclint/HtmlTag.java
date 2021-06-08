package com.github.api.sun.tools.doclint;

import javax.lang.model.element.Name;
import java.util.*;

import static com.github.api.sun.tools.doclint.HtmlTag.Attr.*;

public enum HtmlTag {
    A(BlockType.INLINE, EndKind.REQUIRED,
            attrs(AttrKind.OK, HREF, TARGET, NAME)),
    B(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),
    BIG(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),
    BLOCKQUOTE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),
    BODY(BlockType.OTHER, EndKind.REQUIRED),
    BR(BlockType.INLINE, EndKind.NONE,
            attrs(AttrKind.USE_CSS, CLEAR)),
    CAPTION(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_INLINE, Flag.EXPECT_CONTENT)),
    CENTER(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),
    CITE(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),
    CODE(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),
    DD(BlockType.LIST_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE, Flag.EXPECT_CONTENT)),
    DFN(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),
    DIV(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE)),
    DL(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.USE_CSS, COMPACT)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == DT) || (t == DD);
        }
    },
    DT(BlockType.LIST_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_INLINE, Flag.EXPECT_CONTENT)),
    EM(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.NO_NEST)),
    FONT(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.USE_CSS, SIZE, COLOR, FACE)),
    FRAME(BlockType.OTHER, EndKind.NONE),
    FRAMESET(BlockType.OTHER, EndKind.REQUIRED),
    H1(BlockType.BLOCK, EndKind.REQUIRED),
    H2(BlockType.BLOCK, EndKind.REQUIRED),
    H3(BlockType.BLOCK, EndKind.REQUIRED),
    H4(BlockType.BLOCK, EndKind.REQUIRED),
    H5(BlockType.BLOCK, EndKind.REQUIRED),
    H6(BlockType.BLOCK, EndKind.REQUIRED),
    HEAD(BlockType.OTHER, EndKind.REQUIRED),
    HR(BlockType.BLOCK, EndKind.NONE,
            attrs(AttrKind.OK, WIDTH)),
    HTML(BlockType.OTHER, EndKind.REQUIRED),
    I(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),
    IMG(BlockType.INLINE, EndKind.NONE,
            attrs(AttrKind.OK, SRC, ALT, HEIGHT, WIDTH),
            attrs(AttrKind.OBSOLETE, NAME),
            attrs(AttrKind.USE_CSS, ALIGN, HSPACE, VSPACE, BORDER)),
    LI(BlockType.LIST_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.OK, VALUE)),
    LINK(BlockType.OTHER, EndKind.NONE),
    MENU(BlockType.BLOCK, EndKind.REQUIRED) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == LI);
        }
    },
    META(BlockType.OTHER, EndKind.NONE),
    NOFRAMES(BlockType.OTHER, EndKind.REQUIRED),
    NOSCRIPT(BlockType.BLOCK, EndKind.REQUIRED),
    OL(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.OK, START, TYPE)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == LI);
        }
    },
    P(BlockType.BLOCK, EndKind.OPTIONAL,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.USE_CSS, ALIGN)),
    PRE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)) {
        @Override
        public boolean accepts(HtmlTag t) {
            switch (t) {
                case IMG:
                case BIG:
                case SMALL:
                case SUB:
                case SUP:
                    return false;
                default:
                    return (t.blockType == BlockType.INLINE);
            }
        }
    },
    SCRIPT(BlockType.OTHER, EndKind.REQUIRED),
    SMALL(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),
    SPAN(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),
    STRONG(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT)),
    SUB(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),
    SUP(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),
    TABLE(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.OK, SUMMARY, Attr.FRAME, RULES, BORDER,
                    CELLPADDING, CELLSPACING, WIDTH),
            attrs(AttrKind.USE_CSS, ALIGN, BGCOLOR)) {
        @Override
        public boolean accepts(HtmlTag t) {
            switch (t) {
                case CAPTION:
                case THEAD:
                case TBODY:
                case TFOOT:
                case TR:
                    return true;
                default:
                    return false;
            }
        }
    },
    TBODY(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.OK, ALIGN, CHAR, CHAROFF, VALIGN)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TR);
        }
    },
    TD(BlockType.TABLE_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.OK, COLSPAN, ROWSPAN, HEADERS, SCOPE, ABBR, AXIS,
                    ALIGN, CHAR, CHAROFF, VALIGN),
            attrs(AttrKind.USE_CSS, WIDTH, BGCOLOR, HEIGHT, NOWRAP)),
    TFOOT(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            attrs(AttrKind.OK, ALIGN, CHAR, CHAROFF, VALIGN)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TR);
        }
    },
    TH(BlockType.TABLE_ITEM, EndKind.OPTIONAL,
            EnumSet.of(Flag.ACCEPTS_BLOCK, Flag.ACCEPTS_INLINE),
            attrs(AttrKind.OK, COLSPAN, ROWSPAN, HEADERS, SCOPE, ABBR, AXIS,
                    ALIGN, CHAR, CHAROFF, VALIGN),
            attrs(AttrKind.USE_CSS, WIDTH, BGCOLOR, HEIGHT, NOWRAP)),
    THEAD(BlockType.TABLE_ITEM, EndKind.REQUIRED,
            attrs(AttrKind.OK, ALIGN, CHAR, CHAROFF, VALIGN)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TR);
        }
    },
    TITLE(BlockType.OTHER, EndKind.REQUIRED),
    TR(BlockType.TABLE_ITEM, EndKind.OPTIONAL,
            attrs(AttrKind.OK, ALIGN, CHAR, CHAROFF, VALIGN),
            attrs(AttrKind.USE_CSS, BGCOLOR)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == TH) || (t == TD);
        }
    },
    TT(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),
    U(BlockType.INLINE, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT, Flag.NO_NEST)),
    UL(BlockType.BLOCK, EndKind.REQUIRED,
            EnumSet.of(Flag.EXPECT_CONTENT),
            attrs(AttrKind.OK, COMPACT, TYPE)) {
        @Override
        public boolean accepts(HtmlTag t) {
            return (t == LI);
        }
    },
    VAR(BlockType.INLINE, EndKind.REQUIRED);
    private static final Map<String, HtmlTag> index = new HashMap<String, HtmlTag>();

    static {
        for (HtmlTag t : values()) {
            index.put(t.getText(), t);
        }
    }

    public final BlockType blockType;
    public final EndKind endKind;
    public final Set<Flag> flags;
    private final Map<Attr, AttrKind> attrs;

    HtmlTag(BlockType blockType, EndKind endKind, AttrMap... attrMaps) {
        this(blockType, endKind, Collections.emptySet(), attrMaps);
    }

    HtmlTag(BlockType blockType, EndKind endKind, Set<Flag> flags, AttrMap... attrMaps) {
        this.blockType = blockType;
        this.endKind = endKind;
        this.flags = flags;
        this.attrs = new EnumMap<Attr, AttrKind>(Attr.class);
        for (Map<Attr, AttrKind> m : attrMaps)
            this.attrs.putAll(m);
        attrs.put(Attr.CLASS, AttrKind.OK);
        attrs.put(Attr.ID, AttrKind.OK);
        attrs.put(Attr.STYLE, AttrKind.OK);
    }

    private static AttrMap attrs(AttrKind k, Attr... attrs) {
        AttrMap map = new AttrMap();
        for (Attr a : attrs) map.put(a, k);
        return map;
    }

    static HtmlTag get(Name tagName) {
        return index.get(toLowerCase(tagName.toString()));
    }

    private static String toLowerCase(String s) {
        return s.toLowerCase(Locale.US);
    }

    public boolean accepts(HtmlTag t) {
        if (flags.contains(Flag.ACCEPTS_BLOCK) && flags.contains(Flag.ACCEPTS_INLINE)) {
            return (t.blockType == BlockType.BLOCK) || (t.blockType == BlockType.INLINE);
        } else if (flags.contains(Flag.ACCEPTS_BLOCK)) {
            return (t.blockType == BlockType.BLOCK);
        } else if (flags.contains(Flag.ACCEPTS_INLINE)) {
            return (t.blockType == BlockType.INLINE);
        } else
            switch (blockType) {
                case BLOCK:
                case INLINE:
                    return (t.blockType == BlockType.INLINE);
                case OTHER:
                    return true;
                default:
                    throw new AssertionError(this + ":" + t);
            }
    }

    public boolean acceptsText() {
        return accepts(B);
    }

    public String getText() {
        return toLowerCase(name());
    }

    public Attr getAttr(Name attrName) {
        return Attr.index.get(toLowerCase(attrName.toString()));
    }

    public AttrKind getAttrKind(Name attrName) {
        AttrKind k = attrs.get(getAttr(attrName));
        return (k == null) ? AttrKind.INVALID : k;
    }

    public enum BlockType {
        BLOCK,
        INLINE,
        LIST_ITEM,
        TABLE_ITEM,
        OTHER
    }

    public enum EndKind {
        NONE,
        OPTIONAL,
        REQUIRED
    }

    public enum Flag {
        ACCEPTS_BLOCK,
        ACCEPTS_INLINE,
        EXPECT_CONTENT,
        NO_NEST
    }

    public enum Attr {
        ABBR,
        ALIGN,
        ALT,
        AXIS,
        BGCOLOR,
        BORDER,
        CELLSPACING,
        CELLPADDING,
        CHAR,
        CHAROFF,
        CLEAR,
        CLASS,
        COLOR,
        COLSPAN,
        COMPACT,
        FACE,
        FRAME,
        HEADERS,
        HEIGHT,
        HREF,
        HSPACE,
        ID,
        NAME,
        NOWRAP,
        REVERSED,
        ROWSPAN,
        RULES,
        SCOPE,
        SIZE,
        SPACE,
        SRC,
        START,
        STYLE,
        SUMMARY,
        TARGET,
        TYPE,
        VALIGN,
        VALUE,
        VSPACE,
        WIDTH;
        static final Map<String, Attr> index = new HashMap<String, Attr>();

        static {
            for (Attr t : values()) {
                index.put(t.getText(), t);
            }
        }

        public String getText() {
            return toLowerCase(name());
        }
    }

    public enum AttrKind {
        INVALID,
        OBSOLETE,
        USE_CSS,
        OK
    }

    private static class AttrMap extends EnumMap<Attr, AttrKind> {
        private static final long serialVersionUID = 0;

        AttrMap() {
            super(Attr.class);
        }
    }
}
