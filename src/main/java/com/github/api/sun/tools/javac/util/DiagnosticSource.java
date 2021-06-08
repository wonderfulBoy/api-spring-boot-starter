package com.github.api.sun.tools.javac.util;

import com.github.api.sun.tools.javac.file.JavacFileManager;
import com.github.api.sun.tools.javac.tree.EndPosTable;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.CharBuffer;

import static com.github.api.sun.tools.javac.util.LayoutCharacters.*;

public class DiagnosticSource {

    public static final DiagnosticSource NO_SOURCE = new DiagnosticSource() {
        @Override
        protected boolean findLine(int pos) {
            return false;
        }
    };
    protected JavaFileObject fileObject;
    protected EndPosTable endPosTable;
    protected SoftReference<char[]> refBuf;
    protected char[] buf;
    protected int bufLen;
    protected int lineStart;
    protected int line;
    protected AbstractLog log;

    public DiagnosticSource(JavaFileObject fo, AbstractLog log) {
        this.fileObject = fo;
        this.log = log;
    }

    private DiagnosticSource() {
    }

    public JavaFileObject getFile() {
        return fileObject;
    }

    public int getLineNumber(int pos) {
        try {
            if (findLine(pos)) {
                return line;
            }
            return 0;
        } finally {
            buf = null;
        }
    }

    public int getColumnNumber(int pos, boolean expandTabs) {
        try {
            if (findLine(pos)) {
                int column = 0;
                for (int bp = lineStart; bp < pos; bp++) {
                    if (bp >= bufLen) {
                        return 0;
                    }
                    if (buf[bp] == '\t' && expandTabs) {
                        column = (column / TabInc * TabInc) + TabInc;
                    } else {
                        column++;
                    }
                }
                return column + 1;
            }
            return 0;
        } finally {
            buf = null;
        }
    }

    public String getLine(int pos) {
        try {
            if (!findLine(pos))
                return null;
            int lineEnd = lineStart;
            while (lineEnd < bufLen && buf[lineEnd] != CR && buf[lineEnd] != LF)
                lineEnd++;
            if (lineEnd - lineStart == 0)
                return null;
            return new String(buf, lineStart, lineEnd - lineStart);
        } finally {
            buf = null;
        }
    }

    public EndPosTable getEndPosTable() {
        return endPosTable;
    }

    public void setEndPosTable(EndPosTable t) {
        if (endPosTable != null && endPosTable != t)
            throw new IllegalStateException("endPosTable already set");
        endPosTable = t;
    }

    protected boolean findLine(int pos) {
        if (pos == Position.NOPOS)
            return false;
        try {

            if (buf == null && refBuf != null)
                buf = refBuf.get();
            if (buf == null) {
                buf = initBuf(fileObject);
                lineStart = 0;
                line = 1;
            } else if (lineStart > pos) {
                lineStart = 0;
                line = 1;
            }
            int bp = lineStart;
            while (bp < bufLen && bp < pos) {
                switch (buf[bp++]) {
                    case CR:
                        if (bp < bufLen && buf[bp] == LF) bp++;
                        line++;
                        lineStart = bp;
                        break;
                    case LF:
                        line++;
                        lineStart = bp;
                        break;
                }
            }
            return bp <= bufLen;
        } catch (IOException e) {
            log.directError("source.unavailable");
            buf = new char[0];
            return false;
        }
    }

    protected char[] initBuf(JavaFileObject fileObject) throws IOException {
        char[] buf;
        CharSequence cs = fileObject.getCharContent(true);
        if (cs instanceof CharBuffer) {
            CharBuffer cb = (CharBuffer) cs;
            buf = JavacFileManager.toArray(cb);
            bufLen = cb.limit();
        } else {
            buf = cs.toString().toCharArray();
            bufLen = buf.length;
        }
        refBuf = new SoftReference<char[]>(buf);
        return buf;
    }
}
