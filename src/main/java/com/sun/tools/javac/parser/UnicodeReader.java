package com.sun.tools.javac.parser;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.ArrayUtils;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import java.nio.CharBuffer;
import java.util.Arrays;

import static com.sun.tools.javac.util.LayoutCharacters.EOI;

public class UnicodeReader {

    final static boolean surrogatesSupported = surrogatesSupported();
    protected final int buflen;
    protected char[] buf;
    protected int bp;
    protected char ch;
    protected int unicodeConversionBp = -1;
    protected Log log;
    protected Names names;
    protected char[] sbuf = new char[128];
    protected int sp;

    protected UnicodeReader(ScannerFactory sf, CharBuffer buffer) {
        this(sf, JavacFileManager.toArray(buffer), buffer.limit());
    }

    protected UnicodeReader(ScannerFactory sf, char[] input, int inputLength) {
        log = sf.log;
        names = sf.names;
        if (inputLength == input.length) {
            if (input.length > 0 && Character.isWhitespace(input[input.length - 1])) {
                inputLength--;
            } else {
                input = Arrays.copyOf(input, inputLength + 1);
            }
        }
        buf = input;
        buflen = inputLength;
        buf[buflen] = EOI;
        bp = -1;
        scanChar();
    }

    private static boolean surrogatesSupported() {
        try {
            Character.isHighSurrogate('a');
            return true;
        } catch (NoSuchMethodError ex) {
            return false;
        }
    }

    protected void scanChar() {
        if (bp < buflen) {
            ch = buf[++bp];
            if (ch == '\\') {
                convertUnicode();
            }
        }
    }

    protected void scanCommentChar() {
        scanChar();
        if (ch == '\\') {
            if (peekChar() == '\\' && !isUnicode()) {
                skipChar();
            } else {
                convertUnicode();
            }
        }
    }

    protected void putChar(char ch, boolean scan) {
        sbuf = ArrayUtils.ensureCapacity(sbuf, sp);
        sbuf[sp++] = ch;
        if (scan)
            scanChar();
    }

    protected void putChar(char ch) {
        putChar(ch, false);
    }

    protected void putChar(boolean scan) {
        putChar(ch, scan);
    }

    Name name() {
        return names.fromChars(sbuf, 0, sp);
    }

    String chars() {
        return new String(sbuf, 0, sp);
    }

    protected void convertUnicode() {
        if (ch == '\\' && unicodeConversionBp != bp) {
            bp++;
            ch = buf[bp];
            if (ch == 'u') {
                do {
                    bp++;
                    ch = buf[bp];
                } while (ch == 'u');
                int limit = bp + 3;
                if (limit < buflen) {
                    int d = digit(bp, 16);
                    int code = d;
                    while (bp < limit && d >= 0) {
                        bp++;
                        ch = buf[bp];
                        d = digit(bp, 16);
                        code = (code << 4) + d;
                    }
                    if (d >= 0) {
                        ch = (char) code;
                        unicodeConversionBp = bp;
                        return;
                    }
                }
                log.error(bp, "illegal.unicode.esc");
            } else {
                bp--;
                ch = '\\';
            }
        }
    }

    protected char scanSurrogates() {
        if (surrogatesSupported && Character.isHighSurrogate(ch)) {
            char high = ch;
            scanChar();
            if (Character.isLowSurrogate(ch)) {
                return high;
            }
            ch = high;
        }
        return 0;
    }

    protected int digit(int pos, int base) {
        char c = ch;
        int result = Character.digit(c, base);
        if (result >= 0 && c > 0x7f) {
            log.error(pos + 1, "illegal.nonascii.digit");
            ch = "0123456789abcdef".charAt(result);
        }
        return result;
    }

    protected boolean isUnicode() {
        return unicodeConversionBp == bp;
    }

    protected void skipChar() {
        bp++;
    }

    protected char peekChar() {
        return buf[bp + 1];
    }

    public char[] getRawCharacters() {
        char[] chars = new char[buflen];
        System.arraycopy(buf, 0, chars, 0, buflen);
        return chars;
    }

    public char[] getRawCharacters(int beginIndex, int endIndex) {
        int length = endIndex - beginIndex;
        char[] chars = new char[length];
        System.arraycopy(buf, beginIndex, chars, 0, length);
        return chars;
    }
}
