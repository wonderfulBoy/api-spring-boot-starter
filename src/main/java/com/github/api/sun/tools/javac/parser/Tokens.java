package com.github.api.sun.tools.javac.parser;

import com.github.api.sun.tools.javac.api.Formattable;
import com.github.api.sun.tools.javac.api.Messages;
import com.github.api.sun.tools.javac.parser.Tokens.Token.Tag;
import com.github.api.sun.tools.javac.util.*;

import java.util.Locale;

public class Tokens {
    public static final Context.Key<Tokens> tokensKey =
            new Context.Key<Tokens>();
    public static final Token DUMMY =
            new Token(TokenKind.ERROR, 0, 0, null);
    private final Names names;
    private final TokenKind[] key;
    private int maxKey = 0;
    private Name[] tokenName = new Name[TokenKind.values().length];

    protected Tokens(Context context) {
        context.put(tokensKey, this);
        names = Names.instance(context);
        for (TokenKind t : TokenKind.values()) {
            if (t.name != null)
                enterKeyword(t.name, t);
            else
                tokenName[t.ordinal()] = null;
        }
        key = new TokenKind[maxKey + 1];
        for (int i = 0; i <= maxKey; i++) key[i] = TokenKind.IDENTIFIER;
        for (TokenKind t : TokenKind.values()) {
            if (t.name != null)
                key[tokenName[t.ordinal()].getIndex()] = t;
        }
    }

    public static Tokens instance(Context context) {
        Tokens instance = context.get(tokensKey);
        if (instance == null)
            instance = new Tokens(context);
        return instance;
    }

    private void enterKeyword(String s, TokenKind token) {
        Name n = names.fromString(s);
        tokenName[token.ordinal()] = n;
        if (n.getIndex() > maxKey) maxKey = n.getIndex();
    }

    TokenKind lookupKind(Name name) {
        return (name.getIndex() > maxKey) ? TokenKind.IDENTIFIER : key[name.getIndex()];
    }

    TokenKind lookupKind(String name) {
        return lookupKind(names.fromString(name));
    }

    public enum TokenKind implements Formattable, Filter<TokenKind> {
        EOF(),
        ERROR(),
        IDENTIFIER(Tag.NAMED),
        ABSTRACT("abstract"),
        ASSERT("assert", Tag.NAMED),
        BOOLEAN("boolean", Tag.NAMED),
        BREAK("break"),
        BYTE("byte", Tag.NAMED),
        CASE("case"),
        CATCH("catch"),
        CHAR("char", Tag.NAMED),
        CLASS("class"),
        CONST("const"),
        CONTINUE("continue"),
        DEFAULT("default"),
        DO("do"),
        DOUBLE("double", Tag.NAMED),
        ELSE("else"),
        ENUM("enum", Tag.NAMED),
        EXTENDS("extends"),
        FINAL("final"),
        FINALLY("finally"),
        FLOAT("float", Tag.NAMED),
        FOR("for"),
        GOTO("goto"),
        IF("if"),
        IMPLEMENTS("implements"),
        IMPORT("import"),
        INSTANCEOF("instanceof"),
        INT("int", Tag.NAMED),
        INTERFACE("interface"),
        LONG("long", Tag.NAMED),
        NATIVE("native"),
        NEW("new"),
        PACKAGE("package"),
        PRIVATE("private"),
        PROTECTED("protected"),
        PUBLIC("public"),
        RETURN("return"),
        SHORT("short", Tag.NAMED),
        STATIC("static"),
        STRICTFP("strictfp"),
        SUPER("super", Tag.NAMED),
        SWITCH("switch"),
        SYNCHRONIZED("synchronized"),
        THIS("this", Tag.NAMED),
        THROW("throw"),
        THROWS("throws"),
        TRANSIENT("transient"),
        TRY("try"),
        VOID("void", Tag.NAMED),
        VOLATILE("volatile"),
        WHILE("while"),
        INTLITERAL(Tag.NUMERIC),
        LONGLITERAL(Tag.NUMERIC),
        FLOATLITERAL(Tag.NUMERIC),
        DOUBLELITERAL(Tag.NUMERIC),
        CHARLITERAL(Tag.NUMERIC),
        STRINGLITERAL(Tag.STRING),
        TRUE("true", Tag.NAMED),
        FALSE("false", Tag.NAMED),
        NULL("null", Tag.NAMED),
        UNDERSCORE("_", Tag.NAMED),
        ARROW("->"),
        COLCOL("::"),
        LPAREN("("),
        RPAREN(")"),
        LBRACE("{"),
        RBRACE("}"),
        LBRACKET("["),
        RBRACKET("]"),
        SEMI(";"),
        COMMA(","),
        DOT("."),
        ELLIPSIS("..."),
        EQ("="),
        GT(">"),
        LT("<"),
        BANG("!"),
        TILDE("~"),
        QUES("?"),
        COLON(":"),
        EQEQ("=="),
        LTEQ("<="),
        GTEQ(">="),
        BANGEQ("!="),
        AMPAMP("&&"),
        BARBAR("||"),
        PLUSPLUS("++"),
        SUBSUB("--"),
        PLUS("+"),
        SUB("-"),
        STAR("*"),
        SLASH("/"),
        AMP("&"),
        BAR("|"),
        CARET("^"),
        PERCENT("%"),
        LTLT("<<"),
        GTGT(">>"),
        GTGTGT(">>>"),
        PLUSEQ("+="),
        SUBEQ("-="),
        STAREQ("*="),
        SLASHEQ("/="),
        AMPEQ("&="),
        BAREQ("|="),
        CARETEQ("^="),
        PERCENTEQ("%="),
        LTLTEQ("<<="),
        GTGTEQ(">>="),
        GTGTGTEQ(">>>="),
        MONKEYS_AT("@"),
        CUSTOM;
        public final String name;
        public final Tag tag;

        TokenKind() {
            this(null, Tag.DEFAULT);
        }

        TokenKind(String name) {
            this(name, Tag.DEFAULT);
        }

        TokenKind(Tag tag) {
            this(null, tag);
        }

        TokenKind(String name, Tag tag) {
            this.name = name;
            this.tag = tag;
        }

        public String toString() {
            switch (this) {
                case IDENTIFIER:
                    return "token.identifier";
                case CHARLITERAL:
                    return "token.character";
                case STRINGLITERAL:
                    return "token.string";
                case INTLITERAL:
                    return "token.integer";
                case LONGLITERAL:
                    return "token.long-integer";
                case FLOATLITERAL:
                    return "token.float";
                case DOUBLELITERAL:
                    return "token.double";
                case ERROR:
                    return "token.bad-symbol";
                case EOF:
                    return "token.end-of-input";
                case DOT:
                case COMMA:
                case SEMI:
                case LPAREN:
                case RPAREN:
                case LBRACKET:
                case RBRACKET:
                case LBRACE:
                case RBRACE:
                    return "'" + name + "'";
                default:
                    return name;
            }
        }

        public String getKind() {
            return "Token";
        }

        public String toString(Locale locale, Messages messages) {
            return name != null ? toString() : messages.getLocalizedString(locale, "compiler.misc." + toString());
        }

        @Override
        public boolean accepts(TokenKind that) {
            return this == that;
        }
    }

    public interface Comment {
        String getText();

        int getSourcePos(int index);

        CommentStyle getStyle();

        boolean isDeprecated();

        enum CommentStyle {
            LINE,
            BLOCK,
            JAVADOC,
        }
    }

    public static class Token {

        public final TokenKind kind;
        public final int pos;
        public final int endPos;
        public final List<Comment> comments;

        Token(TokenKind kind, int pos, int endPos, List<Comment> comments) {
            this.kind = kind;
            this.pos = pos;
            this.endPos = endPos;
            this.comments = comments;
            checkKind();
        }

        Token[] split(Tokens tokens) {
            if (kind.name.length() < 2 || kind.tag != Tag.DEFAULT) {
                throw new AssertionError("Cant split" + kind);
            }
            TokenKind t1 = tokens.lookupKind(kind.name.substring(0, 1));
            TokenKind t2 = tokens.lookupKind(kind.name.substring(1));
            if (t1 == null || t2 == null) {
                throw new AssertionError("Cant split - bad subtokens");
            }
            return new Token[]{
                    new Token(t1, pos, pos + t1.name.length(), comments),
                    new Token(t2, pos + t1.name.length(), endPos, null)
            };
        }

        protected void checkKind() {
            if (kind.tag != Tag.DEFAULT) {
                throw new AssertionError("Bad token kind - expected " + Tag.STRING);
            }
        }

        public Name name() {
            throw new UnsupportedOperationException();
        }

        public String stringVal() {
            throw new UnsupportedOperationException();
        }

        public int radix() {
            throw new UnsupportedOperationException();
        }

        public Comment comment(Comment.CommentStyle style) {
            List<Comment> comments = getComments(Comment.CommentStyle.JAVADOC);
            return comments.isEmpty() ?
                    null :
                    comments.head;
        }

        public boolean deprecatedFlag() {
            for (Comment c : getComments(Comment.CommentStyle.JAVADOC)) {
                if (c.isDeprecated()) {
                    return true;
                }
            }
            return false;
        }

        private List<Comment> getComments(Comment.CommentStyle style) {
            if (comments == null) {
                return List.nil();
            } else {
                ListBuffer<Comment> buf = new ListBuffer<>();
                for (Comment c : comments) {
                    if (c.getStyle() == style) {
                        buf.add(c);
                    }
                }
                return buf.toList();
            }
        }

        enum Tag {
            DEFAULT,
            NAMED,
            STRING,
            NUMERIC
        }
    }

    final static class NamedToken extends Token {

        public final Name name;

        public NamedToken(TokenKind kind, int pos, int endPos, Name name, List<Comment> comments) {
            super(kind, pos, endPos, comments);
            this.name = name;
        }

        protected void checkKind() {
            if (kind.tag != Tag.NAMED) {
                throw new AssertionError("Bad token kind - expected " + Tag.NAMED);
            }
        }

        @Override
        public Name name() {
            return name;
        }
    }

    static class StringToken extends Token {

        public final String stringVal;

        public StringToken(TokenKind kind, int pos, int endPos, String stringVal, List<Comment> comments) {
            super(kind, pos, endPos, comments);
            this.stringVal = stringVal;
        }

        protected void checkKind() {
            if (kind.tag != Tag.STRING) {
                throw new AssertionError("Bad token kind - expected " + Tag.STRING);
            }
        }

        @Override
        public String stringVal() {
            return stringVal;
        }
    }

    final static class NumericToken extends StringToken {

        public final int radix;

        public NumericToken(TokenKind kind, int pos, int endPos, String stringVal, int radix, List<Comment> comments) {
            super(kind, pos, endPos, stringVal, comments);
            this.radix = radix;
        }

        protected void checkKind() {
            if (kind.tag != Tag.NUMERIC) {
                throw new AssertionError("Bad token kind - expected " + Tag.NUMERIC);
            }
        }

        @Override
        public int radix() {
            return radix;
        }
    }
}
