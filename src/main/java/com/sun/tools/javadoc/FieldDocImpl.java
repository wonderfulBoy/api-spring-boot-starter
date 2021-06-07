package com.sun.tools.javadoc;

import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.SerialFieldTag;
import com.sun.javadoc.SourcePosition;
import com.sun.javadoc.Type;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;

import java.lang.reflect.Modifier;

import static com.sun.tools.javac.code.TypeTag.BOOLEAN;

public class FieldDocImpl extends MemberDocImpl implements FieldDoc {

    protected final VarSymbol sym;
    private String name;
    private String qualifiedName;

    public FieldDocImpl(DocEnv env, VarSymbol sym, TreePath treePath) {
        super(env, sym, treePath);
        this.sym = sym;
    }

    public FieldDocImpl(DocEnv env, VarSymbol sym) {
        this(env, sym, null);
    }

    static String constantValueExpression(Object cb) {
        if (cb == null) return null;
        if (cb instanceof Character) return sourceForm(((Character) cb).charValue());
        if (cb instanceof Byte) return sourceForm(((Byte) cb).byteValue());
        if (cb instanceof String) return sourceForm((String) cb);
        if (cb instanceof Double) return sourceForm(((Double) cb).doubleValue(), 'd');
        if (cb instanceof Float) return sourceForm(((Float) cb).doubleValue(), 'f');
        if (cb instanceof Long) return cb + "L";
        return cb.toString();
    }

    private static String sourceForm(double v, char suffix) {
        if (Double.isNaN(v))
            return "0" + suffix + "/0" + suffix;
        if (v == Double.POSITIVE_INFINITY)
            return "1" + suffix + "/0" + suffix;
        if (v == Double.NEGATIVE_INFINITY)
            return "-1" + suffix + "/0" + suffix;
        return v + (suffix == 'f' || suffix == 'F' ? "" + suffix : "");
    }

    private static String sourceForm(char c) {
        StringBuilder buf = new StringBuilder(8);
        buf.append('\'');
        sourceChar(c, buf);
        buf.append('\'');
        return buf.toString();
    }

    private static String sourceForm(byte c) {
        return "0x" + Integer.toString(c & 0xff, 16);
    }

    private static String sourceForm(String s) {
        StringBuilder buf = new StringBuilder(s.length() + 5);
        buf.append('\"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sourceChar(c, buf);
        }
        buf.append('\"');
        return buf.toString();
    }

    private static void sourceChar(char c, StringBuilder buf) {
        switch (c) {
            case '\b':
                buf.append("\\b");
                return;
            case '\t':
                buf.append("\\t");
                return;
            case '\n':
                buf.append("\\n");
                return;
            case '\f':
                buf.append("\\f");
                return;
            case '\r':
                buf.append("\\r");
                return;
            case '\"':
                buf.append("\\\"");
                return;
            case '\'':
                buf.append("\\\'");
                return;
            case '\\':
                buf.append("\\\\");
                return;
            default:
                if (isPrintableAscii(c)) {
                    buf.append(c);
                    return;
                }
                unicodeEscape(c, buf);
                return;
        }
    }

    private static void unicodeEscape(char c, StringBuilder buf) {
        final String chars = "0123456789abcdef";
        buf.append("\\u");
        buf.append(chars.charAt(15 & (c >> 12)));
        buf.append(chars.charAt(15 & (c >> 8)));
        buf.append(chars.charAt(15 & (c >> 4)));
        buf.append(chars.charAt(15 & (c >> 0)));
    }

    private static boolean isPrintableAscii(char c) {
        return c >= ' ' && c <= '~';
    }

    protected long getFlags() {
        return sym.flags();
    }

    protected ClassSymbol getContainingClass() {
        return sym.enclClass();
    }

    public Type type() {
        return TypeMaker.getType(env, sym.type, false);
    }

    public Object constantValue() {
        Object result = sym.getConstValue();
        if (result != null && sym.type.hasTag(BOOLEAN))
            result = Boolean.valueOf(((Integer) result).intValue() != 0);
        return result;
    }

    public String constantValueExpression() {
        return constantValueExpression(constantValue());
    }

    public boolean isIncluded() {
        return containingClass().isIncluded() && env.shouldDocument(sym);
    }

    @Override
    public boolean isField() {
        return !isEnumConstant();
    }

    @Override
    public boolean isEnumConstant() {
        return (getFlags() & Flags.ENUM) != 0 &&
                !env.legacyDoclet;
    }

    public boolean isTransient() {
        return Modifier.isTransient(getModifiers());
    }

    public boolean isVolatile() {
        return Modifier.isVolatile(getModifiers());
    }

    public boolean isSynthetic() {
        return (getFlags() & Flags.SYNTHETIC) != 0;
    }

    public SerialFieldTag[] serialFieldTags() {
        return comment().serialFieldTags();
    }

    public String name() {
        if (name == null) {
            name = sym.name.toString();
        }
        return name;
    }

    public String qualifiedName() {
        if (qualifiedName == null) {
            qualifiedName = sym.enclClass().getQualifiedName() + "." + name();
        }
        return qualifiedName;
    }

    @Override
    public SourcePosition position() {
        if (sym.enclClass().sourcefile == null) return null;
        return SourcePositionImpl.make(sym.enclClass().sourcefile,
                (tree == null) ? 0 : tree.pos,
                lineMap);
    }
}
