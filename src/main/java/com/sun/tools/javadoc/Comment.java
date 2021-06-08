package com.sun.tools.javadoc;

import com.sun.javadoc.*;
import com.sun.tools.javac.util.ListBuffer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Comment {
    private static final Pattern prePat = Pattern.compile("(?i)<(/?)pre>");
    private final ListBuffer<Tag> tagList = new ListBuffer<Tag>();
    private final DocEnv docenv;
    private String text;

    Comment(final DocImpl holder, final String commentString) {
        this.docenv = holder.env;
        @SuppressWarnings("fallthrough")
        class CommentStringParser {
            void parseCommentStateMachine() {
                final int IN_TEXT = 1;
                final int TAG_GAP = 2;
                final int TAG_NAME = 3;
                int state = TAG_GAP;
                boolean newLine = true;
                String tagName = null;
                int tagStart = 0;
                int textStart = 0;
                int lastNonWhite = -1;
                int len = commentString.length();
                for (int inx = 0; inx < len; ++inx) {
                    char ch = commentString.charAt(inx);
                    boolean isWhite = Character.isWhitespace(ch);
                    switch (state) {
                        case TAG_NAME:
                            if (isWhite) {
                                tagName = commentString.substring(tagStart, inx);
                                state = TAG_GAP;
                            }
                            break;
                        case TAG_GAP:
                            if (isWhite) {
                                break;
                            }
                            textStart = inx;
                            state = IN_TEXT;
                        case IN_TEXT:
                            if (newLine && ch == '@') {
                                parseCommentComponent(tagName, textStart,
                                        lastNonWhite + 1);
                                tagStart = inx;
                                state = TAG_NAME;
                            }
                            break;
                    }
                    if (ch == '\n') {
                        newLine = true;
                    } else if (!isWhite) {
                        lastNonWhite = inx;
                        newLine = false;
                    }
                }
                switch (state) {
                    case TAG_NAME:
                        tagName = commentString.substring(tagStart, len);
                    case TAG_GAP:
                        textStart = len;
                    case IN_TEXT:
                        parseCommentComponent(tagName, textStart, lastNonWhite + 1);
                        break;
                }
            }

            void parseCommentComponent(String tagName,
                                       int from, int upto) {
                String tx = upto <= from ? "" : commentString.substring(from, upto);
                if (tagName == null) {
                    text = tx;
                } else {
                    TagImpl tag;
                    if (tagName.equals("@exception") || tagName.equals("@throws")) {
                        warnIfEmpty(tagName, tx);
                        tag = new ThrowsTagImpl(holder, tagName, tx);
                    } else if (tagName.equals("@param")) {
                        warnIfEmpty(tagName, tx);
                        tag = new ParamTagImpl(holder, tagName, tx);
                    } else if (tagName.equals("@see")) {
                        warnIfEmpty(tagName, tx);
                        tag = new SeeTagImpl(holder, tagName, tx);
                    } else if (tagName.equals("@serialField")) {
                        warnIfEmpty(tagName, tx);
                        tag = new SerialFieldTagImpl(holder, tagName, tx);
                    } else if (tagName.equals("@return")) {
                        warnIfEmpty(tagName, tx);
                        tag = new TagImpl(holder, tagName, tx);
                    } else if (tagName.equals("@author")) {
                        warnIfEmpty(tagName, tx);
                        tag = new TagImpl(holder, tagName, tx);
                    } else if (tagName.equals("@version")) {
                        warnIfEmpty(tagName, tx);
                        tag = new TagImpl(holder, tagName, tx);
                    } else {
                        tag = new TagImpl(holder, tagName, tx);
                    }
                    tagList.append(tag);
                }
            }

            void warnIfEmpty(String tagName, String tx) {
                if (tx.length() == 0) {

                }
            }
        }
        new CommentStringParser().parseCommentStateMachine();
    }

    static Tag[] getInlineTags(DocImpl holder, String inlinetext) {
        ListBuffer<Tag> taglist = new ListBuffer<Tag>();
        int delimend = 0, textstart = 0, len = inlinetext.length();
        boolean inPre = false;
        DocEnv docenv = holder.env;
        if (len == 0) {
            return taglist.toArray(new Tag[taglist.length()]);
        }
        while (true) {
            int linkstart;
            if ((linkstart = inlineTagFound(holder, inlinetext,
                    textstart)) == -1) {
                taglist.append(new TagImpl(holder, "Text",
                        inlinetext.substring(textstart)));
                break;
            } else {
                inPre = scanForPre(inlinetext, textstart, linkstart, inPre);
                int seetextstart = linkstart;
                for (int i = linkstart; i < inlinetext.length(); i++) {
                    char c = inlinetext.charAt(i);
                    if (Character.isWhitespace(c) ||
                            c == '}') {
                        seetextstart = i;
                        break;
                    }
                }
                String linkName = inlinetext.substring(linkstart + 2, seetextstart);
                if (!(inPre && (linkName.equals("code") || linkName.equals("literal")))) {
                    while (Character.isWhitespace(inlinetext.
                            charAt(seetextstart))) {
                        if (inlinetext.length() <= seetextstart) {
                            taglist.append(new TagImpl(holder, "Text",
                                    inlinetext.substring(textstart, seetextstart)));
                            docenv.warning(holder,
                                    "tag.Improper_Use_Of_Link_Tag",
                                    inlinetext);
                            return taglist.toArray(new Tag[taglist.length()]);
                        } else {
                            seetextstart++;
                        }
                    }
                }
                taglist.append(new TagImpl(holder, "Text",
                        inlinetext.substring(textstart, linkstart)));
                textstart = seetextstart;
                if ((delimend = findInlineTagDelim(inlinetext, textstart)) == -1) {
                    taglist.append(new TagImpl(holder, "Text",
                            inlinetext.substring(textstart)));
                    docenv.warning(holder,
                            "tag.End_delimiter_missing_for_possible_SeeTag",
                            inlinetext);
                    return taglist.toArray(new Tag[taglist.length()]);
                } else {
                    if (linkName.equals("see")
                            || linkName.equals("link")
                            || linkName.equals("linkplain")) {
                        taglist.append(new SeeTagImpl(holder, "@" + linkName,
                                inlinetext.substring(textstart, delimend)));
                    } else {
                        taglist.append(new TagImpl(holder, "@" + linkName,
                                inlinetext.substring(textstart, delimend)));
                    }
                    textstart = delimend + 1;
                }
            }
            if (textstart == inlinetext.length()) {
                break;
            }
        }
        return taglist.toArray(new Tag[taglist.length()]);
    }

    private static boolean scanForPre(String inlinetext, int start, int end, boolean inPre) {
        Matcher m = prePat.matcher(inlinetext).region(start, end);
        while (m.find()) {
            inPre = m.group(1).isEmpty();
        }
        return inPre;
    }

    private static int findInlineTagDelim(String inlineText, int searchStart) {
        int delimEnd, nestedOpenBrace;
        if ((delimEnd = inlineText.indexOf("}", searchStart)) == -1) {
            return -1;
        } else if (((nestedOpenBrace = inlineText.indexOf("{", searchStart)) != -1) &&
                nestedOpenBrace < delimEnd) {
            int nestedCloseBrace = findInlineTagDelim(inlineText, nestedOpenBrace + 1);
            return (nestedCloseBrace != -1) ?
                    findInlineTagDelim(inlineText, nestedCloseBrace + 1) :
                    -1;
        } else {
            return delimEnd;
        }
    }

    private static int inlineTagFound(DocImpl holder, String inlinetext, int start) {
        DocEnv docenv = holder.env;
        int linkstart = inlinetext.indexOf("{@", start);
        if (start == inlinetext.length() || linkstart == -1) {
            return -1;
        } else if (inlinetext.indexOf('}', linkstart) == -1) {
            docenv.warning(holder, "tag.Improper_Use_Of_Link_Tag",
                    inlinetext.substring(linkstart));
            return -1;
        } else {
            return linkstart;
        }
    }

    static Tag[] firstSentenceTags(DocImpl holder, String text) {
        DocLocale doclocale = holder.env.doclocale;
        return getInlineTags(holder,
                doclocale.localeSpecificFirstSentence(holder, text));
    }

    String commentText() {
        return text;
    }

    Tag[] tags() {
        return tagList.toArray(new Tag[tagList.length()]);
    }

    Tag[] tags(String tagname) {
        ListBuffer<Tag> found = new ListBuffer<Tag>();
        String target = tagname;
        if (target.charAt(0) != '@') {
            target = "@" + target;
        }
        for (Tag tag : tagList) {
            if (tag.kind().equals(target)) {
                found.append(tag);
            }
        }
        return found.toArray(new Tag[found.length()]);
    }

    ThrowsTag[] throwsTags() {
        ListBuffer<ThrowsTag> found = new ListBuffer<ThrowsTag>();
        for (Tag next : tagList) {
            if (next instanceof ThrowsTag) {
                found.append((ThrowsTag) next);
            }
        }
        return found.toArray(new ThrowsTag[found.length()]);
    }

    ParamTag[] paramTags() {
        return paramTags(false);
    }

    ParamTag[] typeParamTags() {
        return paramTags(true);
    }

    private ParamTag[] paramTags(boolean typeParams) {
        ListBuffer<ParamTag> found = new ListBuffer<ParamTag>();
        for (Tag next : tagList) {
            if (next instanceof ParamTag) {
                ParamTag p = (ParamTag) next;
                if (typeParams == p.isTypeParameter()) {
                    found.append(p);
                }
            }
        }
        return found.toArray(new ParamTag[found.length()]);
    }

    SeeTag[] seeTags() {
        ListBuffer<SeeTag> found = new ListBuffer<SeeTag>();
        for (Tag next : tagList) {
            if (next instanceof SeeTag) {
                found.append((SeeTag) next);
            }
        }
        return found.toArray(new SeeTag[found.length()]);
    }

    SerialFieldTag[] serialFieldTags() {
        ListBuffer<SerialFieldTag> found = new ListBuffer<SerialFieldTag>();
        for (Tag next : tagList) {
            if (next instanceof SerialFieldTag) {
                found.append((SerialFieldTag) next);
            }
        }
        return found.toArray(new SerialFieldTag[found.length()]);
    }

    @Override
    public String toString() {
        return text;
    }
}
