package com.github.api.sun.tools.javac.parser;

import com.github.api.sun.source.doctree.AttributeTree.ValueKind;
import com.github.api.sun.tools.javac.parser.DocCommentParser.TagParser.Kind;
import com.github.api.sun.tools.javac.parser.Tokens.Comment;
import com.github.api.sun.tools.javac.parser.Tokens.TokenKind;
import com.github.api.sun.tools.javac.tree.DCTree;
import com.github.api.sun.tools.javac.tree.DCTree.*;
import com.github.api.sun.tools.javac.tree.DocTreeMaker;
import com.github.api.sun.tools.javac.tree.JCTree;
import com.github.api.sun.tools.javac.util.List;
import com.github.api.sun.tools.javac.util.*;

import java.text.BreakIterator;
import java.util.*;

import static com.github.api.sun.tools.javac.util.LayoutCharacters.EOI;

public class DocCommentParser {
    final ParserFactory fac;
    final DiagnosticSource diagSource;
    final Comment comment;
    final DocTreeMaker m;
    final Names names;
    protected char[] buf;
    protected int bp;
    protected int buflen;
    protected char ch;
    BreakIterator sentenceBreaker;
    int textStart = -1;
    int lastNonWhite = -1;
    boolean newline = true;
    Map<Name, TagParser> tagParsers;
    Set<String> htmlBlockTags = new HashSet<String>(Arrays.asList(
            "h1", "h2", "h3", "h4", "h5", "h6", "p", "pre"));

    DocCommentParser(ParserFactory fac, DiagnosticSource diagSource, Comment comment) {
        this.fac = fac;
        this.diagSource = diagSource;
        this.comment = comment;
        names = fac.names;
        m = fac.docTreeMaker;
        Locale locale = (fac.locale == null) ? Locale.getDefault() : fac.locale;
        Options options = fac.options;
        boolean useBreakIterator = options.isSet("breakIterator");
        if (useBreakIterator || !locale.getLanguage().equals(Locale.ENGLISH.getLanguage()))
            sentenceBreaker = BreakIterator.getSentenceInstance(locale);
        initTagParsers();
    }

    DCDocComment parse() {
        String c = comment.getText();
        buf = new char[c.length() + 1];
        c.getChars(0, c.length(), buf, 0);
        buf[buf.length - 1] = EOI;
        buflen = buf.length - 1;
        bp = -1;
        nextChar();
        List<DCTree> body = blockContent();
        List<DCTree> tags = blockTags();

        ListBuffer<DCTree> fs = new ListBuffer<DCTree>();
        loop:
        for (; body.nonEmpty(); body = body.tail) {
            DCTree t = body.head;
            switch (t.getKind()) {
                case TEXT:
                    String s = ((DCText) t).getBody();
                    int i = getSentenceBreak(s);
                    if (i > 0) {
                        int i0 = i;
                        while (i0 > 0 && isWhitespace(s.charAt(i0 - 1)))
                            i0--;
                        fs.add(m.at(t.pos).Text(s.substring(0, i0)));
                        int i1 = i;
                        while (i1 < s.length() && isWhitespace(s.charAt(i1)))
                            i1++;
                        body = body.tail;
                        if (i1 < s.length())
                            body = body.prepend(m.at(t.pos + i1).Text(s.substring(i1)));
                        break loop;
                    } else if (body.tail.nonEmpty()) {
                        if (isSentenceBreak(body.tail.head)) {
                            int i0 = s.length() - 1;
                            while (i0 > 0 && isWhitespace(s.charAt(i0)))
                                i0--;
                            fs.add(m.at(t.pos).Text(s.substring(0, i0 + 1)));
                            body = body.tail;
                            break loop;
                        }
                    }
                    break;
                case START_ELEMENT:
                case END_ELEMENT:
                    if (isSentenceBreak(t))
                        break loop;
                    break;
            }
            fs.add(t);
        }
        @SuppressWarnings("unchecked")
        DCTree first = getFirst(fs.toList(), body, tags);
        int pos = (first == null) ? Position.NOPOS : first.pos;
        DCDocComment dc = m.at(pos).DocComment(comment, fs.toList(), body, tags);
        return dc;
    }

    void nextChar() {
        ch = buf[bp < buflen ? ++bp : buflen];
        switch (ch) {
            case '\f':
            case '\n':
            case '\r':
                newline = true;
        }
    }

    @SuppressWarnings("fallthrough")
    protected List<DCTree> blockContent() {
        ListBuffer<DCTree> trees = new ListBuffer<DCTree>();
        textStart = -1;
        loop:
        while (bp < buflen) {
            switch (ch) {
                case '\n':
                case '\r':
                case '\f':
                    newline = true;

                case ' ':
                case '\t':
                    nextChar();
                    break;
                case '&':
                    entity(trees);
                    break;
                case '<':
                    newline = false;
                    addPendingText(trees, bp - 1);
                    trees.add(html());
                    if (textStart == -1) {
                        textStart = bp;
                        lastNonWhite = -1;
                    }
                    break;
                case '>':
                    newline = false;
                    addPendingText(trees, bp - 1);
                    trees.add(m.at(bp).Erroneous(newString(bp, bp + 1), diagSource, "dc.bad.gt"));
                    nextChar();
                    if (textStart == -1) {
                        textStart = bp;
                        lastNonWhite = -1;
                    }
                    break;
                case '{':
                    inlineTag(trees);
                    break;
                case '@':
                    if (newline) {
                        addPendingText(trees, lastNonWhite);
                        break loop;
                    }

                default:
                    newline = false;
                    if (textStart == -1)
                        textStart = bp;
                    lastNonWhite = bp;
                    nextChar();
            }
        }
        if (lastNonWhite != -1)
            addPendingText(trees, lastNonWhite);
        return trees.toList();
    }

    protected List<DCTree> blockTags() {
        ListBuffer<DCTree> tags = new ListBuffer<DCTree>();
        while (ch == '@')
            tags.add(blockTag());
        return tags.toList();
    }

    protected DCTree blockTag() {
        int p = bp;
        try {
            nextChar();
            if (isIdentifierStart(ch)) {
                Name name = readTagName();
                TagParser tp = tagParsers.get(name);
                if (tp == null) {
                    List<DCTree> content = blockContent();
                    return m.at(p).UnknownBlockTag(name, content);
                } else {
                    switch (tp.getKind()) {
                        case BLOCK:
                            return tp.parse(p);
                        case INLINE:
                            return erroneous("dc.bad.inline.tag", p);
                    }
                }
            }
            blockContent();
            return erroneous("dc.no.tag.name", p);
        } catch (ParseException e) {
            blockContent();
            return erroneous(e.getMessage(), p);
        }
    }

    protected void inlineTag(ListBuffer<DCTree> list) {
        newline = false;
        nextChar();
        if (ch == '@') {
            addPendingText(list, bp - 2);
            list.add(inlineTag());
            textStart = bp;
            lastNonWhite = -1;
        } else {
            if (textStart == -1)
                textStart = bp - 1;
            lastNonWhite = bp;
        }
    }

    protected DCTree inlineTag() {
        int p = bp - 1;
        try {
            nextChar();
            if (isIdentifierStart(ch)) {
                Name name = readTagName();
                skipWhitespace();
                TagParser tp = tagParsers.get(name);
                if (tp == null) {
                    DCTree text = inlineText();
                    if (text != null) {
                        nextChar();
                        return m.at(p).UnknownInlineTag(name, List.of(text)).setEndPos(bp);
                    }
                } else if (tp.getKind() == Kind.INLINE) {
                    DCEndPosTree<?> tree = (DCEndPosTree<?>) tp.parse(p);
                    if (tree != null) {
                        return tree.setEndPos(bp);
                    }
                } else {
                    inlineText();
                    nextChar();
                }
            }
            return erroneous("dc.no.tag.name", p);
        } catch (ParseException e) {
            return erroneous(e.getMessage(), p);
        }
    }

    protected DCTree inlineText() throws ParseException {
        skipWhitespace();
        int pos = bp;
        int depth = 1;
        loop:
        while (bp < buflen) {
            switch (ch) {
                case '\n':
                case '\r':
                case '\f':
                    newline = true;
                    break;
                case ' ':
                case '\t':
                    break;
                case '{':
                    newline = false;
                    lastNonWhite = bp;
                    depth++;
                    break;
                case '}':
                    if (--depth == 0) {
                        return m.at(pos).Text(newString(pos, bp));
                    }
                    newline = false;
                    lastNonWhite = bp;
                    break;
                case '@':
                    if (newline)
                        break loop;
                    newline = false;
                    lastNonWhite = bp;
                    break;
                default:
                    newline = false;
                    lastNonWhite = bp;
                    break;
            }
            nextChar();
        }
        throw new ParseException("dc.unterminated.inline.tag");
    }


    @SuppressWarnings("fallthrough")
    protected DCReference reference(boolean allowMember) throws ParseException {
        int pos = bp;
        int depth = 0;


        loop:
        while (bp < buflen) {
            switch (ch) {
                case '\n':
                case '\r':
                case '\f':
                    newline = true;

                case ' ':
                case '\t':
                    if (depth == 0)
                        break loop;
                    break;
                case '(':
                case '<':
                    newline = false;
                    depth++;
                    break;
                case ')':
                case '>':
                    newline = false;
                    --depth;
                    break;
                case '}':
                    if (bp == pos)
                        return null;
                    newline = false;
                    break loop;
                case '@':
                    if (newline)
                        break loop;

                default:
                    newline = false;
            }
            nextChar();
        }
        if (depth != 0)
            throw new ParseException("dc.unterminated.signature");
        String sig = newString(pos, bp);

        JCTree qualExpr;
        Name member;
        List<JCTree> paramTypes;
        Log.DeferredDiagnosticHandler deferredDiagnosticHandler
                = new Log.DeferredDiagnosticHandler(fac.log);
        try {
            int hash = sig.indexOf("#");
            int lparen = sig.indexOf("(", hash + 1);
            if (hash == -1) {
                if (lparen == -1) {
                    qualExpr = parseType(sig);
                    member = null;
                } else {
                    qualExpr = null;
                    member = parseMember(sig.substring(0, lparen));
                }
            } else {
                qualExpr = (hash == 0) ? null : parseType(sig.substring(0, hash));
                if (lparen == -1)
                    member = parseMember(sig.substring(hash + 1));
                else
                    member = parseMember(sig.substring(hash + 1, lparen));
            }
            if (lparen < 0) {
                paramTypes = null;
            } else {
                int rparen = sig.indexOf(")", lparen);
                if (rparen != sig.length() - 1)
                    throw new ParseException("dc.ref.bad.parens");
                paramTypes = parseParams(sig.substring(lparen + 1, rparen));
            }
            if (!deferredDiagnosticHandler.getDiagnostics().isEmpty())
                throw new ParseException("dc.ref.syntax.error");
        } finally {
            fac.log.popDiagnosticHandler(deferredDiagnosticHandler);
        }
        return m.at(pos).Reference(sig, qualExpr, member, paramTypes).setEndPos(bp);
    }

    JCTree parseType(String s) throws ParseException {
        JavacParser p = fac.newParser(s, false, false, false);
        JCTree tree = p.parseType();
        if (p.token().kind != TokenKind.EOF)
            throw new ParseException("dc.ref.unexpected.input");
        return tree;
    }

    Name parseMember(String s) throws ParseException {
        JavacParser p = fac.newParser(s, false, false, false);
        Name name = p.ident();
        if (p.token().kind != TokenKind.EOF)
            throw new ParseException("dc.ref.unexpected.input");
        return name;
    }

    List<JCTree> parseParams(String s) throws ParseException {
        if (s.trim().isEmpty())
            return List.nil();
        JavacParser p = fac.newParser(s.replace("...", "[]"), false, false, false);
        ListBuffer<JCTree> paramTypes = new ListBuffer<JCTree>();
        paramTypes.add(p.parseType());
        if (p.token().kind == TokenKind.IDENTIFIER)
            p.nextToken();
        while (p.token().kind == TokenKind.COMMA) {
            p.nextToken();
            paramTypes.add(p.parseType());
            if (p.token().kind == TokenKind.IDENTIFIER)
                p.nextToken();
        }
        if (p.token().kind != TokenKind.EOF)
            throw new ParseException("dc.ref.unexpected.input");
        return paramTypes.toList();
    }

    @SuppressWarnings("fallthrough")
    protected DCIdentifier identifier() throws ParseException {
        skipWhitespace();
        int pos = bp;
        if (isJavaIdentifierStart(ch)) {
            Name name = readJavaIdentifier();
            return m.at(pos).Identifier(name);
        }
        throw new ParseException("dc.identifier.expected");
    }

    @SuppressWarnings("fallthrough")
    protected DCText quotedString() {
        int pos = bp;
        nextChar();
        loop:
        while (bp < buflen) {
            switch (ch) {
                case '\n':
                case '\r':
                case '\f':
                    newline = true;
                    break;
                case ' ':
                case '\t':
                    break;
                case '"':
                    nextChar();
                    // trim trailing white-space?
                    return m.at(pos).Text(newString(pos, bp));
                case '@':
                    if (newline)
                        break loop;
            }
            nextChar();
        }
        return null;
    }

    /**
     * Read general text content of an inline tag, including HTML entities and elements.
     * Matching pairs of { } are skipped; the text is terminated by the first
     * unmatched }. It is an error if the beginning of the next tag is detected.
     */
    @SuppressWarnings("fallthrough")
    protected List<DCTree> inlineContent() {
        ListBuffer<DCTree> trees = new ListBuffer<DCTree>();
        skipWhitespace();
        int pos = bp;
        int depth = 1;
        textStart = -1;
        loop:
        while (bp < buflen) {
            switch (ch) {
                case '\n':
                case '\r':
                case '\f':
                    newline = true;
                    // fall through
                case ' ':
                case '\t':
                    nextChar();
                    break;
                case '&':
                    entity(trees);
                    break;
                case '<':
                    newline = false;
                    addPendingText(trees, bp - 1);
                    trees.add(html());
                    break;
                case '{':
                    newline = false;
                    depth++;
                    nextChar();
                    break;
                case '}':
                    newline = false;
                    if (--depth == 0) {
                        addPendingText(trees, bp - 1);
                        nextChar();
                        return trees.toList();
                    }
                    nextChar();
                    break;
                case '@':
                    if (newline)
                        break loop;
                    // fallthrough
                default:
                    if (textStart == -1)
                        textStart = bp;
                    nextChar();
                    break;
            }
        }
        return List.of(erroneous("dc.unterminated.inline.tag", pos));
    }

    protected void entity(ListBuffer<DCTree> list) {
        newline = false;
        addPendingText(list, bp - 1);
        list.add(entity());
        if (textStart == -1) {
            textStart = bp;
            lastNonWhite = -1;
        }
    }

    /**
     * Read an HTML entity.
     * {@literal &identifier; } or {@literal &#digits; } or {@literal &#xhex-digits; }
     */
    protected DCTree entity() {
        int p = bp;
        nextChar();
        Name name = null;
        boolean checkSemi = false;
        if (ch == '#') {
            int namep = bp;
            nextChar();
            if (isDecimalDigit(ch)) {
                nextChar();
                while (isDecimalDigit(ch))
                    nextChar();
                name = names.fromChars(buf, namep, bp - namep);
            } else if (ch == 'x' || ch == 'X') {
                nextChar();
                if (isHexDigit(ch)) {
                    nextChar();
                    while (isHexDigit(ch))
                        nextChar();
                    name = names.fromChars(buf, namep, bp - namep);
                }
            }
        } else if (isIdentifierStart(ch)) {
            name = readIdentifier();
        }
        if (name == null)
            return erroneous("dc.bad.entity", p);
        else {
            if (ch != ';')
                return erroneous("dc.missing.semicolon", p);
            nextChar();
            return m.at(p).Entity(name);
        }
    }

    /**
     * Read the start or end of an HTML tag, or an HTML comment
     * {@literal <identifier attrs> } or {@literal </identifier> }
     */
    protected DCTree html() {
        int p = bp;
        nextChar();
        if (isIdentifierStart(ch)) {
            Name name = readIdentifier();
            List<DCTree> attrs = htmlAttrs();
            if (attrs != null) {
                boolean selfClosing = false;
                if (ch == '/') {
                    nextChar();
                    selfClosing = true;
                }
                if (ch == '>') {
                    nextChar();
                    return m.at(p).StartElement(name, attrs, selfClosing).setEndPos(bp);
                }
            }
        } else if (ch == '/') {
            nextChar();
            if (isIdentifierStart(ch)) {
                Name name = readIdentifier();
                skipWhitespace();
                if (ch == '>') {
                    nextChar();
                    return m.at(p).EndElement(name);
                }
            }
        } else if (ch == '!') {
            nextChar();
            if (ch == '-') {
                nextChar();
                if (ch == '-') {
                    nextChar();
                    while (bp < buflen) {
                        int dash = 0;
                        while (ch == '-') {
                            dash++;
                            nextChar();
                        }
                        // strictly speaking, a comment should not contain "--"
                        // so dash > 2 is an error, dash == 2 implies ch == '>'
                        if (dash >= 2 && ch == '>') {
                            nextChar();
                            return m.at(p).Comment(newString(p, bp));
                        }
                        nextChar();
                    }
                }
            }
        }
        bp = p + 1;
        ch = buf[bp];
        return erroneous("dc.malformed.html", p);
    }

    /**
     * Read a series of HTML attributes, terminated by {@literal > }.
     * Each attribute is of the form {@literal identifier[=value] }.
     * "value" may be unquoted, single-quoted, or double-quoted.
     */
    protected List<DCTree> htmlAttrs() {
        ListBuffer<DCTree> attrs = new ListBuffer<DCTree>();
        skipWhitespace();
        loop:
        while (isIdentifierStart(ch)) {
            int namePos = bp;
            Name name = readIdentifier();
            skipWhitespace();
            List<DCTree> value = null;
            ValueKind vkind = ValueKind.EMPTY;
            if (ch == '=') {
                ListBuffer<DCTree> v = new ListBuffer<DCTree>();
                nextChar();
                skipWhitespace();
                if (ch == '\'' || ch == '"') {
                    vkind = (ch == '\'') ? ValueKind.SINGLE : ValueKind.DOUBLE;
                    char quote = ch;
                    nextChar();
                    textStart = bp;
                    while (bp < buflen && ch != quote) {
                        if (newline && ch == '@') {
                            attrs.add(erroneous("dc.unterminated.string", namePos));


                            break loop;
                        }
                        attrValueChar(v);
                    }
                    addPendingText(v, bp - 1);
                    nextChar();
                } else {
                    vkind = ValueKind.UNQUOTED;
                    textStart = bp;
                    while (bp < buflen && !isUnquotedAttrValueTerminator(ch)) {
                        attrValueChar(v);
                    }
                    addPendingText(v, bp - 1);
                }
                skipWhitespace();
                value = v.toList();
            }
            DCAttribute attr = m.at(namePos).Attribute(name, vkind, value);
            attrs.add(attr);
        }
        return attrs.toList();
    }

    protected void attrValueChar(ListBuffer<DCTree> list) {
        switch (ch) {
            case '&':
                entity(list);
                break;
            case '{':
                inlineTag(list);
                break;
            default:
                nextChar();
        }
    }

    protected void addPendingText(ListBuffer<DCTree> list, int textEnd) {
        if (textStart != -1) {
            if (textStart <= textEnd) {
                list.add(m.at(textStart).Text(newString(textStart, textEnd + 1)));
            }
            textStart = -1;
        }
    }

    protected DCErroneous erroneous(String code, int pos) {
        int i = bp - 1;
        loop:
        while (i > pos) {
            switch (buf[i]) {
                case '\f':
                case '\n':
                case '\r':
                    newline = true;
                    break;
                case '\t':
                case ' ':
                    break;
                default:
                    break loop;
            }
            i--;
        }
        textStart = -1;
        return m.at(pos).Erroneous(newString(pos, i + 1), diagSource, code);
    }

    @SuppressWarnings("unchecked")
    <T> T getFirst(List<T>... lists) {
        for (List<T> list : lists) {
            if (list.nonEmpty())
                return list.head;
        }
        return null;
    }

    protected boolean isIdentifierStart(char ch) {
        return Character.isUnicodeIdentifierStart(ch);
    }

    protected Name readIdentifier() {
        int start = bp;
        nextChar();
        while (bp < buflen && Character.isUnicodeIdentifierPart(ch))
            nextChar();
        return names.fromChars(buf, start, bp - start);
    }

    protected Name readTagName() {
        int start = bp;
        nextChar();
        while (bp < buflen && (Character.isUnicodeIdentifierPart(ch) || ch == '.'))
            nextChar();
        return names.fromChars(buf, start, bp - start);
    }

    protected boolean isJavaIdentifierStart(char ch) {
        return Character.isJavaIdentifierStart(ch);
    }

    protected Name readJavaIdentifier() {
        int start = bp;
        nextChar();
        while (bp < buflen && Character.isJavaIdentifierPart(ch))
            nextChar();
        return names.fromChars(buf, start, bp - start);
    }

    protected boolean isDecimalDigit(char ch) {
        return ('0' <= ch && ch <= '9');
    }

    protected boolean isHexDigit(char ch) {
        return ('0' <= ch && ch <= '9')
                || ('a' <= ch && ch <= 'f')
                || ('A' <= ch && ch <= 'F');
    }

    protected boolean isUnquotedAttrValueTerminator(char ch) {
        switch (ch) {
            case '\f':
            case '\n':
            case '\r':
            case '\t':
            case ' ':
            case '"':
            case '\'':
            case '`':
            case '=':
            case '<':
            case '>':
                return true;
            default:
                return false;
        }
    }

    protected boolean isWhitespace(char ch) {
        return Character.isWhitespace(ch);
    }

    protected void skipWhitespace() {
        while (isWhitespace(ch))
            nextChar();
    }

    protected int getSentenceBreak(String s) {
        if (sentenceBreaker != null) {
            sentenceBreaker.setText(s);
            int i = sentenceBreaker.next();
            return (i == s.length()) ? -1 : i;
        }
        // scan for period followed by whitespace
        boolean period = false;
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case '.':
                    period = true;
                    break;
                case ' ':
                case '\f':
                case '\n':
                case '\r':
                case '\t':
                    if (period)
                        return i;
                    break;
                default:
                    period = false;
                    break;
            }
        }
        return -1;
    }

    protected boolean isSentenceBreak(Name n) {
        return htmlBlockTags.contains(n.toString().toLowerCase());
    }

    protected boolean isSentenceBreak(DCTree t) {
        switch (t.getKind()) {
            case START_ELEMENT:
                return isSentenceBreak(((DCStartElement) t).getName());
            case END_ELEMENT:
                return isSentenceBreak(((DCEndElement) t).getName());
        }
        return false;
    }

    /**
     * @param start position of first character of string
     * @param end   position of character beyond last character to be included
     */
    String newString(int start, int end) {
        return new String(buf, start, end - start);
    }

    /**
     * @see <a href="http:
     */
    private void initTagParsers() {
        TagParser[] parsers = {

                new TagParser(Kind.BLOCK, DCTree.Kind.AUTHOR) {
                    public DCTree parse(int pos) {
                        List<DCTree> name = blockContent();
                        return m.at(pos).Author(name);
                    }
                },

                new TagParser(Kind.INLINE, DCTree.Kind.CODE) {
                    public DCTree parse(int pos) throws ParseException {
                        DCTree text = inlineText();
                        nextChar();
                        return m.at(pos).Code((DCText) text);
                    }
                },

                new TagParser(Kind.BLOCK, DCTree.Kind.DEPRECATED) {
                    public DCTree parse(int pos) {
                        List<DCTree> reason = blockContent();
                        return m.at(pos).Deprecated(reason);
                    }
                },

                new TagParser(Kind.INLINE, DCTree.Kind.DOC_ROOT) {
                    public DCTree parse(int pos) throws ParseException {
                        if (ch == '}') {
                            nextChar();
                            return m.at(pos).DocRoot();
                        }
                        inlineText();
                        nextChar();
                        throw new ParseException("dc.unexpected.content");
                    }
                },

                new TagParser(Kind.BLOCK, DCTree.Kind.EXCEPTION) {
                    public DCTree parse(int pos) throws ParseException {
                        skipWhitespace();
                        DCReference ref = reference(false);
                        List<DCTree> description = blockContent();
                        return m.at(pos).Exception(ref, description);
                    }
                },

                new TagParser(Kind.INLINE, DCTree.Kind.INHERIT_DOC) {
                    public DCTree parse(int pos) throws ParseException {
                        if (ch == '}') {
                            nextChar();
                            return m.at(pos).InheritDoc();
                        }
                        inlineText();
                        nextChar();
                        throw new ParseException("dc.unexpected.content");
                    }
                },

                new TagParser(Kind.INLINE, DCTree.Kind.LINK) {
                    public DCTree parse(int pos) throws ParseException {
                        DCReference ref = reference(true);
                        List<DCTree> label = inlineContent();
                        return m.at(pos).Link(ref, label);
                    }
                },

                new TagParser(Kind.INLINE, DCTree.Kind.LINK_PLAIN) {
                    public DCTree parse(int pos) throws ParseException {
                        DCReference ref = reference(true);
                        List<DCTree> label = inlineContent();
                        return m.at(pos).LinkPlain(ref, label);
                    }
                },

                new TagParser(Kind.INLINE, DCTree.Kind.LITERAL) {
                    public DCTree parse(int pos) throws ParseException {
                        DCTree text = inlineText();
                        nextChar();
                        return m.at(pos).Literal((DCText) text);
                    }
                },

                new TagParser(Kind.BLOCK, DCTree.Kind.PARAM) {
                    public DCTree parse(int pos) throws ParseException {
                        skipWhitespace();
                        boolean typaram = false;
                        if (ch == '<') {
                            typaram = true;
                            nextChar();
                        }
                        DCIdentifier id = identifier();
                        if (typaram) {
                            if (ch != '>')
                                throw new ParseException("dc.gt.expected");
                            nextChar();
                        }
                        skipWhitespace();
                        List<DCTree> desc = blockContent();
                        return m.at(pos).Param(typaram, id, desc);
                    }
                },

                new TagParser(Kind.BLOCK, DCTree.Kind.RETURN) {
                    public DCTree parse(int pos) {
                        List<DCTree> description = blockContent();
                        return m.at(pos).Return(description);
                    }
                },

                new TagParser(Kind.BLOCK, DCTree.Kind.SEE) {
                    public DCTree parse(int pos) throws ParseException {
                        skipWhitespace();
                        switch (ch) {
                            case '"':
                                DCText string = quotedString();
                                if (string != null) {
                                    skipWhitespace();
                                    if (ch == '@')
                                        return m.at(pos).See(List.of(string));
                                }
                                break;
                            case '<':
                                List<DCTree> html = blockContent();
                                if (html != null)
                                    return m.at(pos).See(html);
                                break;
                            case '@':
                                if (newline)
                                    throw new ParseException("dc.no.content");
                                break;
                            case EOI:
                                if (bp == buf.length - 1)
                                    throw new ParseException("dc.no.content");
                                break;
                            default:
                                if (isJavaIdentifierStart(ch) || ch == '#') {
                                    DCReference ref = reference(true);
                                    List<DCTree> description = blockContent();
                                    return m.at(pos).See(description.prepend(ref));
                                }
                        }
                        throw new ParseException("dc.unexpected.content");
                    }
                },
                // @serialData data-description
                new TagParser(Kind.BLOCK, DCTree.Kind.SERIAL_DATA) {
                    public DCTree parse(int pos) {
                        List<DCTree> description = blockContent();
                        return m.at(pos).SerialData(description);
                    }
                },
                // @serialField field-name field-type description
                new TagParser(Kind.BLOCK, DCTree.Kind.SERIAL_FIELD) {
                    public DCTree parse(int pos) throws ParseException {
                        skipWhitespace();
                        DCIdentifier name = identifier();
                        skipWhitespace();
                        DCReference type = reference(false);
                        List<DCTree> description = null;
                        if (isWhitespace(ch)) {
                            skipWhitespace();
                            description = blockContent();
                        }
                        return m.at(pos).SerialField(name, type, description);
                    }
                },
                // @serial field-description | include | exclude
                new TagParser(Kind.BLOCK, DCTree.Kind.SERIAL) {
                    public DCTree parse(int pos) {
                        List<DCTree> description = blockContent();
                        return m.at(pos).Serial(description);
                    }
                },
                // @since since-text
                new TagParser(Kind.BLOCK, DCTree.Kind.SINCE) {
                    public DCTree parse(int pos) {
                        List<DCTree> description = blockContent();
                        return m.at(pos).Since(description);
                    }
                },
                // @throws class-name description
                new TagParser(Kind.BLOCK, DCTree.Kind.THROWS) {
                    public DCTree parse(int pos) throws ParseException {
                        skipWhitespace();
                        DCReference ref = reference(false);
                        List<DCTree> description = blockContent();
                        return m.at(pos).Throws(ref, description);
                    }
                },
                // {@value package.class#field}
                new TagParser(Kind.INLINE, DCTree.Kind.VALUE) {
                    public DCTree parse(int pos) throws ParseException {
                        DCReference ref = reference(true);
                        skipWhitespace();
                        if (ch == '}') {
                            nextChar();
                            return m.at(pos).Value(ref);
                        }
                        nextChar();
                        throw new ParseException("dc.unexpected.content");
                    }
                },
                // @version version-text
                new TagParser(Kind.BLOCK, DCTree.Kind.VERSION) {
                    public DCTree parse(int pos) {
                        List<DCTree> description = blockContent();
                        return m.at(pos).Version(description);
                    }
                },
        };
        tagParsers = new HashMap<Name, TagParser>();
        for (TagParser p : parsers)
            tagParsers.put(names.fromString(p.getTreeKind().tagName), p);
    }

    static class ParseException extends Exception {
        private static final long serialVersionUID = 0;

        ParseException(String key) {
            super(key);
        }
    }

    static abstract class TagParser {
        Kind kind;
        DCTree.Kind treeKind;
        TagParser(Kind k, DCTree.Kind tk) {
            kind = k;
            treeKind = tk;
        }

        Kind getKind() {
            return kind;
        }

        DCTree.Kind getTreeKind() {
            return treeKind;
        }

        abstract DCTree parse(int pos) throws ParseException;

        enum Kind {INLINE, BLOCK}
    }
}
