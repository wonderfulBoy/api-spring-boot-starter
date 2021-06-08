package com.sun.tools.classfile;

public class DefaultAttribute extends Attribute {
    public final byte[] info;
    public final String reason;

    DefaultAttribute(ClassReader cr, int name_index, byte[] data) {
        this(cr, name_index, data, null);
    }

    DefaultAttribute(ClassReader cr, int name_index, byte[] data, String reason) {
        super(name_index, data.length);
        info = data;
        this.reason = reason;
    }

    public DefaultAttribute(ConstantPool constant_pool, int name_index, byte[] info) {
        this(constant_pool, name_index, info, null);
    }

    public DefaultAttribute(ConstantPool constant_pool, int name_index,
                            byte[] info, String reason) {
        super(name_index, info.length);
        this.info = info;
        this.reason = reason;
    }

    public <R, P> R accept(Visitor<R, P> visitor, P p) {
        return visitor.visitDefault(this, p);
    }
}
