package com.sun.tools.javac.util;

import com.sun.tools.javac.code.Type;

public class Constants {

    public static Object decode(Object value, Type type) {
        if (value instanceof Integer) {
            int i = (Integer) value;
            switch (type.getTag()) {
                case BOOLEAN:
                    return i != 0;
                case CHAR:
                    return (char) i;
                case BYTE:
                    return (byte) i;
                case SHORT:
                    return (short) i;
            }
        }
        return value;
    }

    public static String format(Object value, Type type) {
        value = decode(value, type);
        switch (type.getTag()) {
            case BYTE:
                return formatByte((Byte) value);
            case LONG:
                return formatLong((Long) value);
            case FLOAT:
                return formatFloat((Float) value);
            case DOUBLE:
                return formatDouble((Double) value);
            case CHAR:
                return formatChar((Character) value);
        }
        if (value instanceof String)
            return formatString((String) value);
        return value + "";
    }

    public static String format(Object value) {
        if (value instanceof Byte) return formatByte((Byte) value);
        if (value instanceof Short) return formatShort((Short) value);
        if (value instanceof Long) return formatLong((Long) value);
        if (value instanceof Float) return formatFloat((Float) value);
        if (value instanceof Double) return formatDouble((Double) value);
        if (value instanceof Character) return formatChar((Character) value);
        if (value instanceof String) return formatString((String) value);
        if (value instanceof Integer ||
                value instanceof Boolean) return value.toString();
        else
            throw new IllegalArgumentException("Argument is not a primitive type or a string; it " +
                    ((value == null) ?
                            "is a null value." :
                            "has class " +
                                    value.getClass().getName()) + ".");
    }

    private static String formatByte(byte b) {
        return String.format("(byte)0x%02x", b);
    }

    private static String formatShort(short s) {
        return String.format("(short)%d", s);
    }

    private static String formatLong(long lng) {
        return lng + "L";
    }

    private static String formatFloat(float f) {
        if (Float.isNaN(f))
            return "0.0f/0.0f";
        else if (Float.isInfinite(f))
            return (f < 0) ? "-1.0f/0.0f" : "1.0f/0.0f";
        else
            return f + "f";
    }

    private static String formatDouble(double d) {
        if (Double.isNaN(d))
            return "0.0/0.0";
        else if (Double.isInfinite(d))
            return (d < 0) ? "-1.0/0.0" : "1.0/0.0";
        else
            return d + "";
    }

    private static String formatChar(char c) {
        return '\'' + Convert.quote(c) + '\'';
    }

    private static String formatString(String s) {
        return '"' + Convert.quote(s) + '"';
    }
}
