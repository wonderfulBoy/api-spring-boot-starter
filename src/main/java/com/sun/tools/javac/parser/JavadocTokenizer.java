package com.sun.tools.javac.parser;

import com.sun.tools.javac.parser.Tokens.Comment;
import com.sun.tools.javac.parser.Tokens.Comment.CommentStyle;
import com.sun.tools.javac.util.Position;

import java.nio.CharBuffer;

import static com.sun.tools.javac.util.LayoutCharacters.*;

public class JavadocTokenizer extends JavaTokenizer {

    protected JavadocTokenizer(ScannerFactory fac, CharBuffer buffer) {
        super(fac, buffer);
    }

    protected JavadocTokenizer(ScannerFactory fac, char[] input, int inputLength) {
        super(fac, input, inputLength);
    }

    @Override
    protected Comment processComment(int pos, int endPos, CommentStyle style) {
        char[] buf = reader.getRawCharacters(pos, endPos);
        return new JavadocComment(new DocReader(fac, buf, buf.length, pos), style);
    }

    @Override
    public Position.LineMap getLineMap() {
        char[] buf = reader.getRawCharacters();
        return Position.makeLineMap(buf, buf.length, true);
    }

    static class DocReader extends UnicodeReader {
        int col;
        int startPos;

        int[] pbuf = new int[128];

        int pp = 0;

        DocReader(ScannerFactory fac, char[] input, int inputLength, int startPos) {
            super(fac, input, inputLength);
            this.startPos = startPos;
        }

        @Override
        protected void convertUnicode() {
            if (ch == '\\' && unicodeConversionBp != bp) {
                bp++;
                ch = buf[bp];
                col++;
                if (ch == 'u') {
                    do {
                        bp++;
                        ch = buf[bp];
                        col++;
                    } while (ch == 'u');
                    int limit = bp + 3;
                    if (limit < buflen) {
                        int d = digit(bp, 16);
                        int code = d;
                        while (bp < limit && d >= 0) {
                            bp++;
                            ch = buf[bp];
                            col++;
                            d = digit(bp, 16);
                            code = (code << 4) + d;
                        }
                        if (d >= 0) {
                            ch = (char) code;
                            unicodeConversionBp = bp;
                            return;
                        }
                    }

                } else {
                    bp--;
                    ch = '\\';
                    col--;
                }
            }
        }

        @Override
        protected void scanCommentChar() {
            scanChar();
            if (ch == '\\') {
                if (peekChar() == '\\' && !isUnicode()) {
                    putChar(ch, false);
                    bp++;
                    col++;
                } else {
                    convertUnicode();
                }
            }
        }

        @Override
        protected void scanChar() {
            bp++;
            ch = buf[bp];
            switch (ch) {
                case '\r':
                    col = 0;
                    break;
                case '\n':
                    if (bp == 0 || buf[bp - 1] != '\r') {
                        col = 0;
                    }
                    break;
                case '\t':
                    col = (col / TabInc * TabInc) + TabInc;
                    break;
                case '\\':
                    col++;
                    convertUnicode();
                    break;
                default:
                    col++;
                    break;
            }
        }

        @Override
        public void putChar(char ch, boolean scan) {


            if ((pp == 0)
                    || (sp - pbuf[pp - 2] != (startPos + bp) - pbuf[pp - 1])) {
                if (pp + 1 >= pbuf.length) {
                    int[] new_pbuf = new int[pbuf.length * 2];
                    System.arraycopy(pbuf, 0, new_pbuf, 0, pbuf.length);
                    pbuf = new_pbuf;
                }
                pbuf[pp] = sp;
                pbuf[pp + 1] = startPos + bp;
                pp += 2;
            }
            super.putChar(ch, scan);
        }
    }

    protected static class JavadocComment extends BasicComment<DocReader> {

        private String docComment = null;
        private int[] docPosns = null;

        JavadocComment(DocReader reader, CommentStyle cs) {
            super(reader, cs);
        }

        @Override
        public String getText() {
            if (!scanned && cs == CommentStyle.JAVADOC) {
                scanDocComment();
            }
            return docComment;
        }

        @Override
        public int getSourcePos(int pos) {


            if (pos == Position.NOPOS)
                return Position.NOPOS;
            if (pos < 0 || pos > docComment.length())
                throw new StringIndexOutOfBoundsException(String.valueOf(pos));
            if (docPosns == null)
                return Position.NOPOS;
            int start = 0;
            int end = docPosns.length;
            while (start < end - 2) {

                int index = ((start + end) / 4) * 2;
                if (docPosns[index] < pos)
                    start = index;
                else if (docPosns[index] == pos)
                    return docPosns[index + 1];
                else
                    end = index;
            }
            return docPosns[start + 1] + (pos - docPosns[start]);
        }

        @Override
        @SuppressWarnings("fallthrough")
        protected void scanDocComment() {
            try {
                boolean firstLine = true;

                comment_reader.scanCommentChar();

                comment_reader.scanCommentChar();

                while (comment_reader.bp < comment_reader.buflen && comment_reader.ch == '*') {
                    comment_reader.scanCommentChar();
                }

                if (comment_reader.bp < comment_reader.buflen && comment_reader.ch == '/') {
                    docComment = "";
                    return;
                }

                if (comment_reader.bp < comment_reader.buflen) {
                    if (comment_reader.ch == LF) {
                        comment_reader.scanCommentChar();
                        firstLine = false;
                    } else if (comment_reader.ch == CR) {
                        comment_reader.scanCommentChar();
                        if (comment_reader.ch == LF) {
                            comment_reader.scanCommentChar();
                            firstLine = false;
                        }
                    }
                }
                outerLoop:


                while (comment_reader.bp < comment_reader.buflen) {
                    int begin_bp = comment_reader.bp;
                    char begin_ch = comment_reader.ch;


                    wsLoop:
                    while (comment_reader.bp < comment_reader.buflen) {
                        switch (comment_reader.ch) {
                            case ' ':
                                comment_reader.scanCommentChar();
                                break;
                            case '\t':
                                comment_reader.col = ((comment_reader.col - 1) / TabInc * TabInc) + TabInc;
                                comment_reader.scanCommentChar();
                                break;
                            case FF:
                                comment_reader.col = 0;
                                comment_reader.scanCommentChar();
                                break;


                            default:


                                break wsLoop;
                        }
                    }


                    if (comment_reader.ch == '*') {

                        do {
                            comment_reader.scanCommentChar();
                        } while (comment_reader.ch == '*');

                        if (comment_reader.ch == '/') {


                            break outerLoop;
                        }
                    } else if (!firstLine) {


                        comment_reader.bp = begin_bp;
                        comment_reader.ch = begin_ch;
                    }


                    textLoop:
                    while (comment_reader.bp < comment_reader.buflen) {
                        switch (comment_reader.ch) {
                            case '*':


                                comment_reader.scanCommentChar();
                                if (comment_reader.ch == '/') {


                                    break outerLoop;
                                }


                                comment_reader.putChar('*', false);
                                break;
                            case ' ':
                            case '\t':
                                comment_reader.putChar(comment_reader.ch, false);
                                comment_reader.scanCommentChar();
                                break;
                            case FF:
                                comment_reader.scanCommentChar();
                                break textLoop;
                            case CR:
                                comment_reader.scanCommentChar();
                                if (comment_reader.ch != LF) {

                                    comment_reader.putChar((char) LF, false);
                                    break textLoop;
                                }

                            case LF:


                                comment_reader.putChar(comment_reader.ch, false);
                                comment_reader.scanCommentChar();
                                break textLoop;
                            default:

                                comment_reader.putChar(comment_reader.ch, false);
                                comment_reader.scanCommentChar();
                        }
                    }
                    firstLine = false;
                }
                if (comment_reader.sp > 0) {
                    int i = comment_reader.sp - 1;
                    trailLoop:
                    while (i > -1) {
                        switch (comment_reader.sbuf[i]) {
                            case '*':
                                i--;
                                break;
                            default:
                                break trailLoop;
                        }
                    }
                    comment_reader.sp = i + 1;

                    docComment = comment_reader.chars();
                    docPosns = new int[comment_reader.pp];
                    System.arraycopy(comment_reader.pbuf, 0, docPosns, 0, docPosns.length);
                } else {
                    docComment = "";
                }
            } finally {
                scanned = true;
                comment_reader = null;
                if (docComment != null &&
                        docComment.matches("(?sm).*^\\s*@deprecated( |$).*")) {
                    deprecatedFlag = true;
                }
            }
        }
    }
}
