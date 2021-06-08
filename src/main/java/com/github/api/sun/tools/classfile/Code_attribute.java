package com.github.api.sun.tools.classfile;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class Code_attribute extends Attribute {
    public final int max_stack;
    public final int max_locals;
    public final int code_length;
    public final byte[] code;
    public final int exception_table_length;
    public final Exception_data[] exception_table;
    public final Attributes attributes;

    Code_attribute(ClassReader cr, int name_index, int length)
            throws IOException, ConstantPoolException {
        super(name_index, length);
        max_stack = cr.readUnsignedShort();
        max_locals = cr.readUnsignedShort();
        code_length = cr.readInt();
        code = new byte[code_length];
        cr.readFully(code);
        exception_table_length = cr.readUnsignedShort();
        exception_table = new Exception_data[exception_table_length];
        for (int i = 0; i < exception_table_length; i++)
            exception_table[i] = new Exception_data(cr);
        attributes = new Attributes(cr);
    }

    public int getByte(int offset) throws InvalidIndex {
        if (offset < 0 || offset >= code.length)
            throw new InvalidIndex(offset);
        return code[offset];
    }

    public int getUnsignedByte(int offset) throws InvalidIndex {
        if (offset < 0 || offset >= code.length)
            throw new InvalidIndex(offset);
        return code[offset] & 0xff;
    }

    public int getShort(int offset) throws InvalidIndex {
        if (offset < 0 || offset + 1 >= code.length)
            throw new InvalidIndex(offset);
        return (code[offset] << 8) | (code[offset + 1] & 0xFF);
    }

    public int getUnsignedShort(int offset) throws InvalidIndex {
        if (offset < 0 || offset + 1 >= code.length)
            throw new InvalidIndex(offset);
        return ((code[offset] << 8) | (code[offset + 1] & 0xFF)) & 0xFFFF;
    }

    public int getInt(int offset) throws InvalidIndex {
        if (offset < 0 || offset + 3 >= code.length)
            throw new InvalidIndex(offset);
        return (getShort(offset) << 16) | (getShort(offset + 2) & 0xFFFF);
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitCode(this, data);
    }

    public Iterable<Instruction> getInstructions() {
        return new Iterable<Instruction>() {
            public Iterator<Instruction> iterator() {
                return new Iterator<Instruction>() {
                    Instruction current = null;
                    int pc = 0;
                    Instruction next = new Instruction(code, pc);

                    public boolean hasNext() {
                        return (next != null);
                    }

                    public Instruction next() {
                        if (next == null)
                            throw new NoSuchElementException();
                        current = next;
                        pc += current.length();
                        next = (pc < code.length ? new Instruction(code, pc) : null);
                        return current;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException("Not supported.");
                    }
                };
            }
        };
    }

    public static class InvalidIndex extends AttributeException {
        private static final long serialVersionUID = -8904527774589382802L;
        public final int index;

        InvalidIndex(int index) {
            this.index = index;
        }

        @Override
        public String getMessage() {

            return "invalid index " + index + " in Code attribute";
        }
    }

    public static class Exception_data {
        public final int start_pc;
        public final int end_pc;
        public final int handler_pc;
        public final int catch_type;
        Exception_data(ClassReader cr) throws IOException {
            start_pc = cr.readUnsignedShort();
            end_pc = cr.readUnsignedShort();
            handler_pc = cr.readUnsignedShort();
            catch_type = cr.readUnsignedShort();
        }
    }
}
