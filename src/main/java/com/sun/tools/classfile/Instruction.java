package com.sun.tools.classfile;

public class Instruction {

    private byte[] bytes;

    private int pc;

    public Instruction(byte[] bytes, int pc) {
        this.bytes = bytes;
        this.pc = pc;
    }

    private static int align(int n) {
        return (n + 3) & ~3;
    }

    public int getPC() {
        return pc;
    }

    public int getByte(int offset) {
        return bytes[pc + offset];
    }

    public int getUnsignedByte(int offset) {
        return getByte(offset) & 0xff;
    }

    public int getShort(int offset) {
        return (getByte(offset) << 8) | getUnsignedByte(offset + 1);
    }

    public int getUnsignedShort(int offset) {
        return getShort(offset) & 0xFFFF;
    }

    public int getInt(int offset) {
        return (getShort(offset) << 16) | (getUnsignedShort(offset + 2));
    }

    public Opcode getOpcode() {
        int b = getUnsignedByte(0);
        switch (b) {
            case Opcode.NONPRIV:
            case Opcode.PRIV:
            case Opcode.WIDE:
                return Opcode.get(b, getUnsignedByte(1));
        }
        return Opcode.get(b);
    }

    public String getMnemonic() {
        Opcode opcode = getOpcode();
        if (opcode == null)
            return "bytecode " + getUnsignedByte(0);
        else
            return opcode.toString().toLowerCase();
    }

    public int length() {
        Opcode opcode = getOpcode();
        if (opcode == null)
            return 1;
        switch (opcode) {
            case TABLESWITCH: {
                int pad = align(pc + 1) - pc;
                int low = getInt(pad + 4);
                int high = getInt(pad + 8);
                return pad + 12 + 4 * (high - low + 1);
            }
            case LOOKUPSWITCH: {
                int pad = align(pc + 1) - pc;
                int npairs = getInt(pad + 4);
                return pad + 8 + 8 * npairs;
            }
            default:
                return opcode.kind.length;
        }
    }

    public Kind getKind() {
        Opcode opcode = getOpcode();
        return (opcode != null ? opcode.kind : Kind.UNKNOWN);
    }

    public <R, P> R accept(KindVisitor<R, P> visitor, P p) {
        switch (getKind()) {
            case NO_OPERANDS:
                return visitor.visitNoOperands(this, p);
            case ATYPE:
                return visitor.visitArrayType(
                        this, TypeKind.get(getUnsignedByte(1)), p);
            case BRANCH:
                return visitor.visitBranch(this, getShort(1), p);
            case BRANCH_W:
                return visitor.visitBranch(this, getInt(1), p);
            case BYTE:
                return visitor.visitValue(this, getByte(1), p);
            case CPREF:
                return visitor.visitConstantPoolRef(this, getUnsignedByte(1), p);
            case CPREF_W:
                return visitor.visitConstantPoolRef(this, getUnsignedShort(1), p);
            case CPREF_W_UBYTE:
            case CPREF_W_UBYTE_ZERO:
                return visitor.visitConstantPoolRefAndValue(
                        this, getUnsignedShort(1), getUnsignedByte(3), p);
            case DYNAMIC: {
                switch (getOpcode()) {
                    case TABLESWITCH: {
                        int pad = align(pc + 1) - pc;
                        int default_ = getInt(pad);
                        int low = getInt(pad + 4);
                        int high = getInt(pad + 8);
                        int[] values = new int[high - low + 1];
                        for (int i = 0; i < values.length; i++)
                            values[i] = getInt(pad + 12 + 4 * i);
                        return visitor.visitTableSwitch(
                                this, default_, low, high, values, p);
                    }
                    case LOOKUPSWITCH: {
                        int pad = align(pc + 1) - pc;
                        int default_ = getInt(pad);
                        int npairs = getInt(pad + 4);
                        int[] matches = new int[npairs];
                        int[] offsets = new int[npairs];
                        for (int i = 0; i < npairs; i++) {
                            matches[i] = getInt(pad + 8 + i * 8);
                            offsets[i] = getInt(pad + 12 + i * 8);
                        }
                        return visitor.visitLookupSwitch(
                                this, default_, npairs, matches, offsets, p);
                    }
                    default:
                        throw new IllegalStateException();
                }
            }
            case LOCAL:
                return visitor.visitLocal(this, getUnsignedByte(1), p);
            case LOCAL_BYTE:
                return visitor.visitLocalAndValue(
                        this, getUnsignedByte(1), getByte(2), p);
            case SHORT:
                return visitor.visitValue(this, getShort(1), p);
            case WIDE_NO_OPERANDS:
                return visitor.visitNoOperands(this, p);
            case WIDE_LOCAL:
                return visitor.visitLocal(this, getUnsignedShort(2), p);
            case WIDE_CPREF_W:
                return visitor.visitConstantPoolRef(this, getUnsignedShort(2), p);
            case WIDE_CPREF_W_SHORT:
                return visitor.visitConstantPoolRefAndValue(
                        this, getUnsignedShort(2), getUnsignedByte(4), p);
            case WIDE_LOCAL_SHORT:
                return visitor.visitLocalAndValue(
                        this, getUnsignedShort(2), getShort(4), p);
            case UNKNOWN:
                return visitor.visitUnknown(this, p);
            default:
                throw new IllegalStateException();
        }
    }

    public enum Kind {

        NO_OPERANDS(1),

        ATYPE(2),

        BRANCH(3),

        BRANCH_W(5),

        BYTE(2),

        CPREF(2),

        CPREF_W(3),

        CPREF_W_UBYTE(4),

        CPREF_W_UBYTE_ZERO(5),

        DYNAMIC(-1),

        LOCAL(2),

        LOCAL_BYTE(3),

        SHORT(3),

        WIDE_NO_OPERANDS(2),

        WIDE_LOCAL(4),

        WIDE_CPREF_W(4),

        WIDE_CPREF_W_SHORT(6),

        WIDE_LOCAL_SHORT(6),

        UNKNOWN(1);

        public final int length;

        Kind(int length) {
            this.length = length;
        }
    }

    public enum TypeKind {
        T_BOOLEAN(4, "boolean"),
        T_CHAR(5, "char"),
        T_FLOAT(6, "float"),
        T_DOUBLE(7, "double"),
        T_BYTE(8, "byte"),
        T_SHORT(9, "short"),
        T_INT(10, "int"),
        T_LONG(11, "long");

        public final int value;
        public final String name;

        TypeKind(int value, String name) {
            this.value = value;
            this.name = name;
        }

        public static TypeKind get(int value) {
            switch (value) {
                case 4:
                    return T_BOOLEAN;
                case 5:
                    return T_CHAR;
                case 6:
                    return T_FLOAT;
                case 7:
                    return T_DOUBLE;
                case 8:
                    return T_BYTE;
                case 9:
                    return T_SHORT;
                case 10:
                    return T_INT;
                case 11:
                    return T_LONG;
                default:
                    return null;
            }
        }
    }
    public interface KindVisitor<R, P> {

        R visitNoOperands(Instruction instr, P p);

        R visitArrayType(Instruction instr, TypeKind kind, P p);

        R visitBranch(Instruction instr, int offset, P p);

        R visitConstantPoolRef(Instruction instr, int index, P p);

        R visitConstantPoolRefAndValue(Instruction instr, int index, int value, P p);

        R visitLocal(Instruction instr, int index, P p);

        R visitLocalAndValue(Instruction instr, int index, int value, P p);

        R visitLookupSwitch(Instruction instr, int default_, int npairs, int[] matches, int[] offsets, P p);

        R visitTableSwitch(Instruction instr, int default_, int low, int high, int[] offsets, P p);

        R visitValue(Instruction instr, int value, P p);

        R visitUnknown(Instruction instr, P p);
    }
}
