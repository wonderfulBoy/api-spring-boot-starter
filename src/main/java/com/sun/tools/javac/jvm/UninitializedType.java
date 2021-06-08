package com.sun.tools.javac.jvm;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;

import static com.sun.tools.javac.code.TypeTag.UNINITIALIZED_OBJECT;
import static com.sun.tools.javac.code.TypeTag.UNINITIALIZED_THIS;

class UninitializedType extends Type.DelegatedType {
    public final int offset;

    private UninitializedType(TypeTag tag, Type qtype, int offset) {
        super(tag, qtype);
        this.offset = offset;
    }

    public static UninitializedType uninitializedThis(Type qtype) {
        return new UninitializedType(UNINITIALIZED_THIS, qtype, -1);
    }

    public static UninitializedType uninitializedObject(Type qtype, int offset) {
        return new UninitializedType(UNINITIALIZED_OBJECT, qtype, offset);
    }

    Type initializedType() {
        return qtype;
    }
}
