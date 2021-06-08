package com.sun.tools.javac.code;

import com.sun.tools.javac.util.Assert;

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
            Assert.error("Attribute type value needs to be an unsigned byte: " + String.format("0x%02X", targetTypeValue));
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
            Assert.error("Unknown TargetType: " + tag);
        return targets[tag];
    }

    public boolean isLocal() {
        return isLocal;
    }

    public int targetTypeValue() {
        return this.targetTypeValue;
    }
}
