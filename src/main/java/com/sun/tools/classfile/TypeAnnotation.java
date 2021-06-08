package com.sun.tools.classfile;

import com.sun.tools.classfile.TypeAnnotation.Position.TypePathEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TypeAnnotation {
    public final ConstantPool constant_pool;
    public final Position position;
    public final Annotation annotation;

    TypeAnnotation(ClassReader cr) throws IOException, Annotation.InvalidAnnotation {
        constant_pool = cr.getConstantPool();
        position = read_position(cr);
        annotation = new Annotation(cr);
    }

    public TypeAnnotation(ConstantPool constant_pool,
                          Annotation annotation, Position position) {
        this.constant_pool = constant_pool;
        this.position = position;
        this.annotation = annotation;
    }

    private static Position read_position(ClassReader cr) throws IOException, Annotation.InvalidAnnotation {

        int tag = cr.readUnsignedByte();
        if (!TargetType.isValidTargetTypeValue(tag))
            throw new Annotation.InvalidAnnotation("TypeAnnotation: Invalid type annotation target type value: " + String.format("0x%02X", tag));
        TargetType type = TargetType.fromTargetTypeValue(tag);
        Position position = new Position();
        position.type = type;
        switch (type) {

            case INSTANCEOF:

            case NEW:

            case CONSTRUCTOR_REFERENCE:
            case METHOD_REFERENCE:
                position.offset = cr.readUnsignedShort();
                break;

            case LOCAL_VARIABLE:

            case RESOURCE_VARIABLE:
                int table_length = cr.readUnsignedShort();
                position.lvarOffset = new int[table_length];
                position.lvarLength = new int[table_length];
                position.lvarIndex = new int[table_length];
                for (int i = 0; i < table_length; ++i) {
                    position.lvarOffset[i] = cr.readUnsignedShort();
                    position.lvarLength[i] = cr.readUnsignedShort();
                    position.lvarIndex[i] = cr.readUnsignedShort();
                }
                break;

            case EXCEPTION_PARAMETER:
                position.exception_index = cr.readUnsignedShort();
                break;

            case METHOD_RECEIVER:

                break;

            case CLASS_TYPE_PARAMETER:
            case METHOD_TYPE_PARAMETER:
                position.parameter_index = cr.readUnsignedByte();
                break;

            case CLASS_TYPE_PARAMETER_BOUND:
            case METHOD_TYPE_PARAMETER_BOUND:
                position.parameter_index = cr.readUnsignedByte();
                position.bound_index = cr.readUnsignedByte();
                break;

            case CLASS_EXTENDS:
                int in = cr.readUnsignedShort();
                if (in == 0xFFFF)
                    in = -1;
                position.type_index = in;
                break;

            case THROWS:
                position.type_index = cr.readUnsignedShort();
                break;

            case METHOD_FORMAL_PARAMETER:
                position.parameter_index = cr.readUnsignedByte();
                break;

            case CAST:

            case CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
            case METHOD_INVOCATION_TYPE_ARGUMENT:
            case CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
            case METHOD_REFERENCE_TYPE_ARGUMENT:
                position.offset = cr.readUnsignedShort();
                position.type_index = cr.readUnsignedByte();
                break;

            case METHOD_RETURN:
            case FIELD:
                break;
            case UNKNOWN:
                throw new AssertionError("TypeAnnotation: UNKNOWN target type should never occur!");
            default:
                throw new AssertionError("TypeAnnotation: Unknown target type: " + type);
        }
        {
            int len = cr.readUnsignedByte();
            List<Integer> loc = new ArrayList<Integer>(len);
            for (int i = 0; i < len * TypePathEntry.bytesPerEntry; ++i)
                loc.add(cr.readUnsignedByte());
            position.location = Position.getTypePathFromBinary(loc);
        }
        return position;
    }

    private static int position_length(Position pos) {
        int n = 0;
        n += 1;
        switch (pos.type) {

            case INSTANCEOF:

            case NEW:

            case CONSTRUCTOR_REFERENCE:
            case METHOD_REFERENCE:
                n += 2;
                break;

            case LOCAL_VARIABLE:

            case RESOURCE_VARIABLE:
                n += 2;
                int table_length = pos.lvarOffset.length;
                n += 2 * table_length;
                n += 2 * table_length;
                n += 2 * table_length;
                break;

            case EXCEPTION_PARAMETER:
                n += 2;
                break;

            case METHOD_RECEIVER:

                break;

            case CLASS_TYPE_PARAMETER:
            case METHOD_TYPE_PARAMETER:
                n += 1;
                break;

            case CLASS_TYPE_PARAMETER_BOUND:
            case METHOD_TYPE_PARAMETER_BOUND:
                n += 1;
                n += 1;
                break;

            case CLASS_EXTENDS:
                n += 2;
                break;

            case THROWS:
                n += 2;
                break;

            case METHOD_FORMAL_PARAMETER:
                n += 1;
                break;

            case CAST:

            case CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
            case METHOD_INVOCATION_TYPE_ARGUMENT:
            case CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
            case METHOD_REFERENCE_TYPE_ARGUMENT:
                n += 2;
                n += 1;
                break;

            case METHOD_RETURN:
            case FIELD:
                break;
            case UNKNOWN:
                throw new AssertionError("TypeAnnotation: UNKNOWN target type should never occur!");
            default:
                throw new AssertionError("TypeAnnotation: Unknown target type: " + pos.type);
        }
        {
            n += 1;
            n += TypePathEntry.bytesPerEntry * pos.location.size();
        }
        return n;
    }

    public int length() {
        int n = annotation.length();
        n += position_length(position);
        return n;
    }

    @Override
    public String toString() {
        try {
            return "@" + constant_pool.getUTF8Value(annotation.type_index).substring(1) +
                    " pos: " + position.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return e.toString();
        }
    }

    public enum TargetType {

        CLASS_TYPE_PARAMETER(0x00),

        METHOD_TYPE_PARAMETER(0x01),

        CLASS_EXTENDS(0x10),

        CLASS_TYPE_PARAMETER_BOUND(0x11),

        METHOD_TYPE_PARAMETER_BOUND(0x12),

        FIELD(0x13),

        METHOD_RETURN(0x14),

        METHOD_RECEIVER(0x15),

        METHOD_FORMAL_PARAMETER(0x16),

        THROWS(0x17),

        LOCAL_VARIABLE(0x40, true),

        RESOURCE_VARIABLE(0x41, true),

        EXCEPTION_PARAMETER(0x42, true),

        INSTANCEOF(0x43, true),

        NEW(0x44, true),

        CONSTRUCTOR_REFERENCE(0x45, true),

        METHOD_REFERENCE(0x46, true),

        CAST(0x47, true),

        CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT(0x48, true),

        METHOD_INVOCATION_TYPE_ARGUMENT(0x49, true),

        CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT(0x4A, true),

        METHOD_REFERENCE_TYPE_ARGUMENT(0x4B, true),

        UNKNOWN(0xFF);
        private static final int MAXIMUM_TARGET_TYPE_VALUE = 0x4B;
        private static final TargetType[] targets;

        static {
            targets = new TargetType[MAXIMUM_TARGET_TYPE_VALUE + 1];
            TargetType[] alltargets = values();
            for (TargetType target : alltargets) {
                if (target.targetTypeValue != UNKNOWN.targetTypeValue)
                    targets[target.targetTypeValue] = target;
            }
            for (int i = 0; i <= MAXIMUM_TARGET_TYPE_VALUE; ++i) {
                if (targets[i] == null)
                    targets[i] = UNKNOWN;
            }
        }

        private final int targetTypeValue;
        private final boolean isLocal;

        TargetType(int targetTypeValue) {
            this(targetTypeValue, false);
        }

        TargetType(int targetTypeValue, boolean isLocal) {
            if (targetTypeValue < 0
                    || targetTypeValue > 255)
                throw new AssertionError("Attribute type value needs to be an unsigned byte: " + String.format("0x%02X", targetTypeValue));
            this.targetTypeValue = targetTypeValue;
            this.isLocal = isLocal;
        }

        public static boolean isValidTargetTypeValue(int tag) {
            if (tag == UNKNOWN.targetTypeValue)
                return true;
            return (tag >= 0 && tag < targets.length);
        }

        public static TargetType fromTargetTypeValue(int tag) {
            if (tag == UNKNOWN.targetTypeValue)
                return UNKNOWN;
            if (tag < 0 || tag >= targets.length)
                throw new AssertionError("Unknown TargetType: " + tag);
            return targets[tag];
        }

        public boolean isLocal() {
            return isLocal;
        }

        public int targetTypeValue() {
            return this.targetTypeValue;
        }
    }

    public static class Position {
        public TargetType type = TargetType.UNKNOWN;
        public List<TypePathEntry> location = new ArrayList<TypePathEntry>(0);
        public int pos = -1;
        public boolean isValidOffset = false;
        public int offset = -1;
        public int[] lvarOffset = null;
        public int[] lvarLength = null;
        public int[] lvarIndex = null;
        public int bound_index = Integer.MIN_VALUE;
        public int parameter_index = Integer.MIN_VALUE;
        public int type_index = Integer.MIN_VALUE;
        public int exception_index = Integer.MIN_VALUE;

        public Position() {
        }

        public static List<TypePathEntry> getTypePathFromBinary(List<Integer> list) {
            List<TypePathEntry> loc = new ArrayList<TypePathEntry>(list.size() / TypePathEntry.bytesPerEntry);
            int idx = 0;
            while (idx < list.size()) {
                if (idx + 1 == list.size()) {
                    throw new AssertionError("Could not decode type path: " + list);
                }
                loc.add(TypePathEntry.fromBinary(list.get(idx), list.get(idx + 1)));
                idx += 2;
            }
            return loc;
        }

        public static List<Integer> getBinaryFromTypePath(List<TypePathEntry> locs) {
            List<Integer> loc = new ArrayList<Integer>(locs.size() * TypePathEntry.bytesPerEntry);
            for (TypePathEntry tpe : locs) {
                loc.add(tpe.tag.tag);
                loc.add(tpe.arg);
            }
            return loc;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            sb.append(type);
            switch (type) {

                case INSTANCEOF:

                case NEW:

                case CONSTRUCTOR_REFERENCE:
                case METHOD_REFERENCE:
                    sb.append(", offset = ");
                    sb.append(offset);
                    break;

                case LOCAL_VARIABLE:

                case RESOURCE_VARIABLE:
                    if (lvarOffset == null) {
                        sb.append(", lvarOffset is null!");
                        break;
                    }
                    sb.append(", {");
                    for (int i = 0; i < lvarOffset.length; ++i) {
                        if (i != 0) sb.append("; ");
                        sb.append("start_pc = ");
                        sb.append(lvarOffset[i]);
                        sb.append(", length = ");
                        sb.append(lvarLength[i]);
                        sb.append(", index = ");
                        sb.append(lvarIndex[i]);
                    }
                    sb.append("}");
                    break;

                case METHOD_RECEIVER:

                    break;

                case CLASS_TYPE_PARAMETER:
                case METHOD_TYPE_PARAMETER:
                    sb.append(", param_index = ");
                    sb.append(parameter_index);
                    break;

                case CLASS_TYPE_PARAMETER_BOUND:
                case METHOD_TYPE_PARAMETER_BOUND:
                    sb.append(", param_index = ");
                    sb.append(parameter_index);
                    sb.append(", bound_index = ");
                    sb.append(bound_index);
                    break;

                case CLASS_EXTENDS:
                    sb.append(", type_index = ");
                    sb.append(type_index);
                    break;

                case THROWS:
                    sb.append(", type_index = ");
                    sb.append(type_index);
                    break;

                case EXCEPTION_PARAMETER:
                    sb.append(", exception_index = ");
                    sb.append(exception_index);
                    break;

                case METHOD_FORMAL_PARAMETER:
                    sb.append(", param_index = ");
                    sb.append(parameter_index);
                    break;

                case CAST:

                case CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT:
                case METHOD_INVOCATION_TYPE_ARGUMENT:
                case CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT:
                case METHOD_REFERENCE_TYPE_ARGUMENT:
                    sb.append(", offset = ");
                    sb.append(offset);
                    sb.append(", type_index = ");
                    sb.append(type_index);
                    break;

                case METHOD_RETURN:
                case FIELD:
                    break;
                case UNKNOWN:
                    sb.append(", position UNKNOWN!");
                    break;
                default:
                    throw new AssertionError("Unknown target type: " + type);
            }

            if (!location.isEmpty()) {
                sb.append(", location = (");
                sb.append(location);
                sb.append(")");
            }
            sb.append(", pos = ");
            sb.append(pos);
            sb.append(']');
            return sb.toString();
        }

        public boolean emitToClassfile() {
            return !type.isLocal() || isValidOffset;
        }

        public enum TypePathEntryKind {
            ARRAY(0),
            INNER_TYPE(1),
            WILDCARD(2),
            TYPE_ARGUMENT(3);
            public final int tag;

            TypePathEntryKind(int tag) {
                this.tag = tag;
            }
        }

        public static class TypePathEntry {

            public static final int bytesPerEntry = 2;
            public static final TypePathEntry ARRAY = new TypePathEntry(TypePathEntryKind.ARRAY);
            public static final TypePathEntry INNER_TYPE = new TypePathEntry(TypePathEntryKind.INNER_TYPE);
            public static final TypePathEntry WILDCARD = new TypePathEntry(TypePathEntryKind.WILDCARD);
            public final TypePathEntryKind tag;
            public final int arg;

            private TypePathEntry(TypePathEntryKind tag) {
                if (!(tag == TypePathEntryKind.ARRAY ||
                        tag == TypePathEntryKind.INNER_TYPE ||
                        tag == TypePathEntryKind.WILDCARD)) {
                    throw new AssertionError("Invalid TypePathEntryKind: " + tag);
                }
                this.tag = tag;
                this.arg = 0;
            }

            public TypePathEntry(TypePathEntryKind tag, int arg) {
                if (tag != TypePathEntryKind.TYPE_ARGUMENT) {
                    throw new AssertionError("Invalid TypePathEntryKind: " + tag);
                }
                this.tag = tag;
                this.arg = arg;
            }

            public static TypePathEntry fromBinary(int tag, int arg) {
                if (arg != 0 && tag != TypePathEntryKind.TYPE_ARGUMENT.tag) {
                    throw new AssertionError("Invalid TypePathEntry tag/arg: " + tag + "/" + arg);
                }
                switch (tag) {
                    case 0:
                        return ARRAY;
                    case 1:
                        return INNER_TYPE;
                    case 2:
                        return WILDCARD;
                    case 3:
                        return new TypePathEntry(TypePathEntryKind.TYPE_ARGUMENT, arg);
                    default:
                        throw new AssertionError("Invalid TypePathEntryKind tag: " + tag);
                }
            }

            @Override
            public String toString() {
                return tag.toString() +
                        (tag == TypePathEntryKind.TYPE_ARGUMENT ? ("(" + arg + ")") : "");
            }

            @Override
            public boolean equals(Object other) {
                if (!(other instanceof TypePathEntry)) {
                    return false;
                }
                TypePathEntry tpe = (TypePathEntry) other;
                return this.tag == tpe.tag && this.arg == tpe.arg;
            }

            @Override
            public int hashCode() {
                return this.tag.hashCode() * 17 + this.arg;
            }
        }
    }
}
