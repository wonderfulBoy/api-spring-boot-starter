package com.sun.tools.classfile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class SourceDebugExtension_attribute extends Attribute {
    private static final Charset UTF8 = StandardCharsets.UTF_8;
    public final byte[] debug_extension;

    SourceDebugExtension_attribute(ClassReader cr, int name_index, int length) throws IOException {
        super(name_index, length);
        debug_extension = new byte[attribute_length];
        cr.readFully(debug_extension);
    }

    public SourceDebugExtension_attribute(ConstantPool constant_pool, byte[] debug_extension)
            throws ConstantPoolException {
        this(constant_pool.getUTF8Index(Attribute.SourceDebugExtension), debug_extension);
    }

    public SourceDebugExtension_attribute(int name_index, byte[] debug_extension) {
        super(name_index, debug_extension.length);
        this.debug_extension = debug_extension;
    }

    public String getValue() {
        return new String(debug_extension, UTF8);
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitSourceDebugExtension(this, data);
    }
}
