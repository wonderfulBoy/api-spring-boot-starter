package com.sun.tools.javadoc;

import com.sun.javadoc.*;

class PrimitiveType implements Type {

    static final PrimitiveType voidType = new PrimitiveType("void");
    static final PrimitiveType booleanType = new PrimitiveType("boolean");
    static final PrimitiveType byteType = new PrimitiveType("byte");
    static final PrimitiveType charType = new PrimitiveType("char");
    static final PrimitiveType shortType = new PrimitiveType("short");
    static final PrimitiveType intType = new PrimitiveType("int");
    static final PrimitiveType longType = new PrimitiveType("long");
    static final PrimitiveType floatType = new PrimitiveType("float");
    static final PrimitiveType doubleType = new PrimitiveType("double");
    static final PrimitiveType errorType = new PrimitiveType("");
    private final String name;

    PrimitiveType(String name) {
        this.name = name;
    }

    public String typeName() {
        return name;
    }

    public Type getElementType() {
        return null;
    }

    public String qualifiedTypeName() {
        return name;
    }

    public String simpleTypeName() {
        return name;
    }

    public String dimension() {
        return "";
    }

    public ClassDoc asClassDoc() {
        return null;
    }

    public AnnotationTypeDoc asAnnotationTypeDoc() {
        return null;
    }

    public ParameterizedType asParameterizedType() {
        return null;
    }

    public TypeVariable asTypeVariable() {
        return null;
    }

    public WildcardType asWildcardType() {
        return null;
    }

    public AnnotatedType asAnnotatedType() {
        return null;
    }

    public String toString() {
        return qualifiedTypeName();
    }

    public boolean isPrimitive() {
        return true;
    }
}
