package com.sun.tools.doclets.formats.html.markup;

import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocletConstants;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class HtmlTree extends Content {
    public static final Content EMPTY = new StringContent("");
    public static final BitSet NONENCODING_CHARS = new BitSet(256);

    static {

        for (int i = 'a'; i <= 'z'; i++) {
            NONENCODING_CHARS.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            NONENCODING_CHARS.set(i);
        }

        for (int i = '0'; i <= '9'; i++) {
            NONENCODING_CHARS.set(i);
        }

        String noEnc = ":/?#[]@!$&'()*+,;=";

        noEnc += "-._~";
        for (int i = 0; i < noEnc.length(); i++) {
            NONENCODING_CHARS.set(noEnc.charAt(i));
        }
    }

    private HtmlTag htmlTag;
    private Map<HtmlAttr, String> attrs = Collections.emptyMap();
    private List<Content> content = Collections.emptyList();

    public HtmlTree(HtmlTag tag) {
        htmlTag = nullCheck(tag);
    }

    public HtmlTree(HtmlTag tag, Content... contents) {
        this(tag);
        for (Content content : contents)
            addContent(content);
    }

    private static String escapeHtmlChars(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {

                case '<':
                case '>':
                case '&':
                    StringBuilder sb = new StringBuilder(s.substring(0, i));
                    for (; i < s.length(); i++) {
                        ch = s.charAt(i);
                        switch (ch) {
                            case '<':
                                sb.append("&lt;");
                                break;
                            case '>':
                                sb.append("&gt;");
                                break;
                            case '&':
                                sb.append("&amp;");
                                break;
                            default:
                                sb.append(ch);
                                break;
                        }
                    }
                    return sb.toString();
            }
        }
        return s;
    }

    private static String encodeURL(String url) {
        byte[] urlBytes = url.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < urlBytes.length; i++) {
            int c = urlBytes[i];
            if (NONENCODING_CHARS.get(c & 0xFF)) {
                sb.append((char) c);
            } else {
                sb.append(String.format("%%%02X", c & 0xFF));
            }
        }
        return sb.toString();
    }

    public static HtmlTree A(String ref, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.A, nullCheck(body));
        htmltree.addAttr(HtmlAttr.HREF, encodeURL(ref));
        return htmltree;
    }

    public static HtmlTree A_NAME(String name, Content body) {
        HtmlTree htmltree = HtmlTree.A_NAME(name);
        htmltree.addContent(nullCheck(body));
        return htmltree;
    }

    public static HtmlTree A_NAME(String name) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.A);
        htmltree.addAttr(HtmlAttr.NAME, nullCheck(name));
        return htmltree;
    }

    public static HtmlTree CAPTION(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.CAPTION, nullCheck(body));
        return htmltree;
    }

    public static HtmlTree CODE(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.CODE, nullCheck(body));
        return htmltree;
    }

    public static HtmlTree DD(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.DD, nullCheck(body));
        return htmltree;
    }

    public static HtmlTree DL(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.DL, nullCheck(body));
        return htmltree;
    }

    public static HtmlTree DIV(HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.DIV, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    public static HtmlTree DIV(Content body) {
        return DIV(null, body);
    }

    public static HtmlTree DT(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.DT, nullCheck(body));
        return htmltree;
    }

    public static HtmlTree FRAME(String src, String name, String title, String scrolling) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.FRAME);
        htmltree.addAttr(HtmlAttr.SRC, nullCheck(src));
        htmltree.addAttr(HtmlAttr.NAME, nullCheck(name));
        htmltree.addAttr(HtmlAttr.TITLE, nullCheck(title));
        if (scrolling != null)
            htmltree.addAttr(HtmlAttr.SCROLLING, scrolling);
        return htmltree;
    }

    public static HtmlTree FRAME(String src, String name, String title) {
        return FRAME(src, name, title, null);
    }

    public static HtmlTree FRAMESET(String cols, String rows, String title, String onload) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.FRAMESET);
        if (cols != null)
            htmltree.addAttr(HtmlAttr.COLS, cols);
        if (rows != null)
            htmltree.addAttr(HtmlAttr.ROWS, rows);
        htmltree.addAttr(HtmlAttr.TITLE, nullCheck(title));
        htmltree.addAttr(HtmlAttr.ONLOAD, nullCheck(onload));
        return htmltree;
    }

    public static HtmlTree HEADING(HtmlTag headingTag, boolean printTitle,
                                   HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(headingTag, nullCheck(body));
        if (printTitle)
            htmltree.setTitle(body);
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    public static HtmlTree HEADING(HtmlTag headingTag, HtmlStyle styleClass, Content body) {
        return HEADING(headingTag, false, styleClass, body);
    }

    public static HtmlTree HEADING(HtmlTag headingTag, boolean printTitle, Content body) {
        return HEADING(headingTag, printTitle, null, body);
    }

    public static HtmlTree HEADING(HtmlTag headingTag, Content body) {
        return HEADING(headingTag, false, null, body);
    }

    public static HtmlTree HTML(String lang, Content head, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.HTML, nullCheck(head), nullCheck(body));
        htmltree.addAttr(HtmlAttr.LANG, nullCheck(lang));
        return htmltree;
    }

    public static HtmlTree LI(Content body) {
        return LI(null, body);
    }

    public static HtmlTree LI(HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.LI, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    public static HtmlTree LINK(String rel, String type, String href, String title) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.LINK);
        htmltree.addAttr(HtmlAttr.REL, nullCheck(rel));
        htmltree.addAttr(HtmlAttr.TYPE, nullCheck(type));
        htmltree.addAttr(HtmlAttr.HREF, nullCheck(href));
        htmltree.addAttr(HtmlAttr.TITLE, nullCheck(title));
        return htmltree;
    }

    public static HtmlTree META(String httpEquiv, String content, String charSet) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.META);
        String contentCharset = content + "; charset=" + charSet;
        htmltree.addAttr(HtmlAttr.HTTP_EQUIV, nullCheck(httpEquiv));
        htmltree.addAttr(HtmlAttr.CONTENT, contentCharset);
        return htmltree;
    }

    public static HtmlTree META(String name, String content) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.META);
        htmltree.addAttr(HtmlAttr.NAME, nullCheck(name));
        htmltree.addAttr(HtmlAttr.CONTENT, nullCheck(content));
        return htmltree;
    }

    public static HtmlTree NOSCRIPT(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.NOSCRIPT, nullCheck(body));
        return htmltree;
    }

    public static HtmlTree P(Content body) {
        return P(null, body);
    }

    public static HtmlTree P(HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.P, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    public static HtmlTree SCRIPT(String type, String src) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.SCRIPT);
        htmltree.addAttr(HtmlAttr.TYPE, nullCheck(type));
        htmltree.addAttr(HtmlAttr.SRC, nullCheck(src));
        return htmltree;
    }

    public static HtmlTree SMALL(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.SMALL, nullCheck(body));
        return htmltree;
    }

    public static HtmlTree SPAN(Content body) {
        return SPAN(null, body);
    }

    public static HtmlTree SPAN(HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.SPAN, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    public static HtmlTree SPAN(String id, HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.SPAN, nullCheck(body));
        htmltree.addAttr(HtmlAttr.ID, nullCheck(id));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    public static HtmlTree TABLE(HtmlStyle styleClass, int border, int cellPadding,
                                 int cellSpacing, String summary, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.TABLE, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        htmltree.addAttr(HtmlAttr.BORDER, Integer.toString(border));
        htmltree.addAttr(HtmlAttr.CELLPADDING, Integer.toString(cellPadding));
        htmltree.addAttr(HtmlAttr.CELLSPACING, Integer.toString(cellSpacing));
        htmltree.addAttr(HtmlAttr.SUMMARY, nullCheck(summary));
        return htmltree;
    }

    public static HtmlTree TD(HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.TD, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        return htmltree;
    }

    public static HtmlTree TD(Content body) {
        return TD(null, body);
    }

    public static HtmlTree TH(HtmlStyle styleClass, String scope, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.TH, nullCheck(body));
        if (styleClass != null)
            htmltree.addStyle(styleClass);
        htmltree.addAttr(HtmlAttr.SCOPE, nullCheck(scope));
        return htmltree;
    }

    public static HtmlTree TH(String scope, Content body) {
        return TH(null, scope, body);
    }

    public static HtmlTree TITLE(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.TITLE, nullCheck(body));
        return htmltree;
    }

    public static HtmlTree TR(Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.TR, nullCheck(body));
        return htmltree;
    }

    public static HtmlTree UL(HtmlStyle styleClass, Content body) {
        HtmlTree htmltree = new HtmlTree(HtmlTag.UL, nullCheck(body));
        htmltree.addStyle(nullCheck(styleClass));
        return htmltree;
    }

    private static String stripHtml(Content body) {
        String rawString = body.toString();

        rawString = rawString.replaceAll("\\<.*?>", " ");

        rawString = rawString.replaceAll("\\b\\s{2,}\\b", " ");

        return rawString.trim();
    }

    public void addAttr(HtmlAttr attrName, String attrValue) {
        if (attrs.isEmpty())
            attrs = new LinkedHashMap<HtmlAttr, String>(3);
        attrs.put(nullCheck(attrName), escapeHtmlChars(attrValue));
    }

    public void setTitle(Content body) {
        addAttr(HtmlAttr.TITLE, stripHtml(body));
    }

    public void addStyle(HtmlStyle style) {
        addAttr(HtmlAttr.CLASS, style.toString());
    }

    public void addContent(Content tagContent) {
        if (tagContent instanceof ContentBuilder) {
            for (Content content : ((ContentBuilder) tagContent).contents) {
                addContent(content);
            }
        } else if (tagContent == HtmlTree.EMPTY || tagContent.isValid()) {
            if (content.isEmpty())
                content = new ArrayList<Content>();
            content.add(tagContent);
        }
    }

    public void addContent(String stringContent) {
        if (!content.isEmpty()) {
            Content lastContent = content.get(content.size() - 1);
            if (lastContent instanceof StringContent)
                lastContent.addContent(stringContent);
            else
                addContent(new StringContent(stringContent));
        } else
            addContent(new StringContent(stringContent));
    }

    public int charCount() {
        int n = 0;
        for (Content c : content)
            n += c.charCount();
        return n;
    }

    public boolean isEmpty() {
        return (!hasContent() && !hasAttrs());
    }

    public boolean hasContent() {
        return (!content.isEmpty());
    }

    public boolean hasAttrs() {
        return (!attrs.isEmpty());
    }

    public boolean hasAttr(HtmlAttr attrName) {
        return (attrs.containsKey(attrName));
    }

    public boolean isValid() {
        switch (htmlTag) {
            case A:
                return (hasAttr(HtmlAttr.NAME) || (hasAttr(HtmlAttr.HREF) && hasContent()));
            case BR:
                return (!hasContent() && (!hasAttrs() || hasAttr(HtmlAttr.CLEAR)));
            case FRAME:
                return (hasAttr(HtmlAttr.SRC) && !hasContent());
            case HR:
                return (!hasContent());
            case IMG:
                return (hasAttr(HtmlAttr.SRC) && hasAttr(HtmlAttr.ALT) && !hasContent());
            case LINK:
                return (hasAttr(HtmlAttr.HREF) && !hasContent());
            case META:
                return (hasAttr(HtmlAttr.CONTENT) && !hasContent());
            case SCRIPT:
                return ((hasAttr(HtmlAttr.TYPE) && hasAttr(HtmlAttr.SRC) && !hasContent()) ||
                        (hasAttr(HtmlAttr.TYPE) && hasContent()));
            default:
                return hasContent();
        }
    }

    public boolean isInline() {
        return (htmlTag.blockType == HtmlTag.BlockType.INLINE);
    }

    @Override
    public boolean write(Writer out, boolean atNewline) throws IOException {
        if (!isInline() && !atNewline)
            out.write(DocletConstants.NL);
        String tagString = htmlTag.toString();
        out.write("<");
        out.write(tagString);
        Iterator<HtmlAttr> iterator = attrs.keySet().iterator();
        HtmlAttr key;
        String value;
        while (iterator.hasNext()) {
            key = iterator.next();
            value = attrs.get(key);
            out.write(" ");
            out.write(key.toString());
            if (!value.isEmpty()) {
                out.write("=\"");
                out.write(value);
                out.write("\"");
            }
        }
        out.write(">");
        boolean nl = false;
        for (Content c : content)
            nl = c.write(out, nl);
        if (htmlTag.endTagRequired()) {
            out.write("</");
            out.write(tagString);
            out.write(">");
        }
        if (!isInline()) {
            out.write(DocletConstants.NL);
            return true;
        } else {
            return false;
        }
    }
}
