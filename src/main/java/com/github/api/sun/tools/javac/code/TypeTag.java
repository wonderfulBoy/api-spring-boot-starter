package com.github.api.sun.tools.javac.code;

import com.github.api.sun.source.tree.Tree.Kind;

import javax.lang.model.type.TypeKind;

import static com.github.api.sun.tools.javac.code.TypeTag.NumericClasses.*;

public enum TypeTag {

    BYTE(BYTE_CLASS, BYTE_SUPERCLASSES, true),

    CHAR(CHAR_CLASS, CHAR_SUPERCLASSES, true),

    SHORT(SHORT_CLASS, SHORT_SUPERCLASSES, true),

    LONG(LONG_CLASS, LONG_SUPERCLASSES, true),

    FLOAT(FLOAT_CLASS, FLOAT_SUPERCLASSES, true),

    INT(INT_CLASS, INT_SUPERCLASSES, true),

    DOUBLE(DOUBLE_CLASS, DOUBLE_CLASS, true),

    BOOLEAN(0, 0, true),

    VOID,

    CLASS,

    ARRAY,

    METHOD,

    PACKAGE,

    TYPEVAR,

    WILDCARD,

    FORALL,

    DEFERRED,

    BOT,

    NONE,

    ERROR,

    UNKNOWN,

    UNDETVAR,

    UNINITIALIZED_THIS,
    UNINITIALIZED_OBJECT;
    final int superClasses;
    final int numericClass;
    final boolean isPrimitive;

    TypeTag() {
        this(0, 0, false);
    }

    TypeTag(int numericClass, int superClasses, boolean isPrimitive) {
        this.superClasses = superClasses;
        this.numericClass = numericClass;
        this.isPrimitive = isPrimitive;
    }

    public static int getTypeTagCount() {

        return (UNDETVAR.ordinal() + 1);
    }

    public boolean isStrictSubRangeOf(TypeTag tag) {

        return (this.superClasses & tag.numericClass) != 0 && this != tag;
    }

    public boolean isSubRangeOf(TypeTag tag) {
        return (this.superClasses & tag.numericClass) != 0;
    }

    public Kind getKindLiteral() {
        switch (this) {
            case INT:
                return Kind.INT_LITERAL;
            case LONG:
                return Kind.LONG_LITERAL;
            case FLOAT:
                return Kind.FLOAT_LITERAL;
            case DOUBLE:
                return Kind.DOUBLE_LITERAL;
            case BOOLEAN:
                return Kind.BOOLEAN_LITERAL;
            case CHAR:
                return Kind.CHAR_LITERAL;
            case CLASS:
                return Kind.STRING_LITERAL;
            case BOT:
                return Kind.NULL_LITERAL;
            default:
                throw new AssertionError("unknown literal kind " + this);
        }
    }

    public TypeKind getPrimitiveTypeKind() {
        switch (this) {
            case BOOLEAN:
                return TypeKind.BOOLEAN;
            case BYTE:
                return TypeKind.BYTE;
            case SHORT:
                return TypeKind.SHORT;
            case INT:
                return TypeKind.INT;
            case LONG:
                return TypeKind.LONG;
            case CHAR:
                return TypeKind.CHAR;
            case FLOAT:
                return TypeKind.FLOAT;
            case DOUBLE:
                return TypeKind.DOUBLE;
            case VOID:
                return TypeKind.VOID;
            default:
                throw new AssertionError("unknown primitive type " + this);
        }
    }

    public static class NumericClasses {
        public static final int BYTE_CLASS = 1;
        public static final int CHAR_CLASS = 2;
        public static final int SHORT_CLASS = 4;
        public static final int INT_CLASS = 8;
        public static final int LONG_CLASS = 16;
        public static final int FLOAT_CLASS = 32;
        public static final int DOUBLE_CLASS = 64;
        static final int BYTE_SUPERCLASSES = BYTE_CLASS | SHORT_CLASS | INT_CLASS |
                LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;
        static final int CHAR_SUPERCLASSES = CHAR_CLASS | INT_CLASS |
                LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;
        static final int SHORT_SUPERCLASSES = SHORT_CLASS | INT_CLASS |
                LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;
        static final int INT_SUPERCLASSES = INT_CLASS | LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;
        static final int LONG_SUPERCLASSES = LONG_CLASS | FLOAT_CLASS | DOUBLE_CLASS;
        static final int FLOAT_SUPERCLASSES = FLOAT_CLASS | DOUBLE_CLASS;
    }
}
