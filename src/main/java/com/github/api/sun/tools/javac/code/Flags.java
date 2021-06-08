package com.github.api.sun.tools.javac.code;

import com.github.api.sun.tools.javac.util.Assert;

import javax.lang.model.element.Modifier;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class Flags {
    public static final int PUBLIC = 1;
    public static final int PRIVATE = 1 << 1;
    public static final int PROTECTED = 1 << 2;
    public static final int STATIC = 1 << 3;
    public static final int FINAL = 1 << 4;
    public static final int SYNCHRONIZED = 1 << 5;
    public static final int VOLATILE = 1 << 6;
    public static final int TRANSIENT = 1 << 7;
    public static final int NATIVE = 1 << 8;
    public static final int INTERFACE = 1 << 9;
    public static final int ABSTRACT = 1 << 10;
    public static final int STRICTFP = 1 << 11;
    public static final int SYNTHETIC = 1 << 12;
    public static final int ANNOTATION = 1 << 13;
    public static final int ENUM = 1 << 14;
    public static final int MANDATED = 1 << 15;
    public static final int StandardFlags = 0x0fff;
    public static final int ACC_SUPER = 0x0020;
    public static final int ACC_BRIDGE = 0x0040;
    public static final int ACC_VARARGS = 0x0080;
    public static final int DEPRECATED = 1 << 17;
    public static final int HASINIT = 1 << 18;
    public static final int BLOCK = 1 << 20;
    public static final int IPROXY = 1 << 21;
    public static final int NOOUTERTHIS = 1 << 22;
    public static final int EXISTS = 1 << 23;
    public static final int COMPOUND = 1 << 24;
    public static final int CLASS_SEEN = 1 << 25;
    public static final int SOURCE_SEEN = 1 << 26;
    public static final int LOCKED = 1 << 27;
    public static final int UNATTRIBUTED = 1 << 28;
    public static final int ANONCONSTR = 1 << 29;
    public static final int ACYCLIC = 1 << 30;
    public static final long BRIDGE = 1L << 31;
    public static final long PARAMETER = 1L << 33;
    public static final long VARARGS = 1L << 34;
    public static final long ACYCLIC_ANN = 1L << 35;
    public static final long GENERATEDCONSTR = 1L << 36;
    public static final long HYPOTHETICAL = 1L << 37;
    public static final long PROPRIETARY = 1L << 38;
    public static final long UNION = 1L << 39;
    public static final long OVERRIDE_BRIDGE = 1L << 40;
    public static final long EFFECTIVELY_FINAL = 1L << 41;
    public static final long CLASH = 1L << 42;
    public static final long DEFAULT = 1L << 43;
    public static final long AUXILIARY = 1L << 44;
    public static final long NOT_IN_PROFILE = 1L << 45;
    public static final long BAD_OVERRIDE = 1L << 45;
    public static final long SIGNATURE_POLYMORPHIC = 1L << 46;
    public static final long THROWS = 1L << 47;
    public static final long POTENTIALLY_AMBIGUOUS = 1L << 48;
    public static final long LAMBDA_METHOD = 1L << 49;
    public static final int
            AccessFlags = PUBLIC | PROTECTED | PRIVATE,
            LocalClassFlags = FINAL | ABSTRACT | STRICTFP | ENUM | SYNTHETIC,
            MemberClassFlags = LocalClassFlags | INTERFACE | AccessFlags,
            ClassFlags = LocalClassFlags | INTERFACE | PUBLIC | ANNOTATION,
            InterfaceVarFlags = FINAL | STATIC | PUBLIC,
            VarFlags = AccessFlags | FINAL | STATIC |
                    VOLATILE | TRANSIENT | ENUM,
            ConstructorFlags = AccessFlags,
            InterfaceMethodFlags = ABSTRACT | PUBLIC,
            MethodFlags = AccessFlags | ABSTRACT | STATIC | NATIVE |
                    SYNCHRONIZED | FINAL | STRICTFP;
    public static final long
            ExtendedStandardFlags = (long) StandardFlags | DEFAULT,
            ModifierFlags = ((long) StandardFlags & ~INTERFACE) | DEFAULT,
            InterfaceMethodMask = ABSTRACT | STATIC | PUBLIC | STRICTFP | DEFAULT,
            AnnotationTypeElementMask = ABSTRACT | PUBLIC,
            LocalVarFlags = FINAL | PARAMETER;
    private static final Map<Long, Set<Modifier>> modifierSets =
            new java.util.concurrent.ConcurrentHashMap<Long, Set<Modifier>>(64);

    private Flags() {
    }

    public static String toString(long flags) {
        StringBuilder buf = new StringBuilder();
        String sep = "";
        for (Flag flag : asFlagSet(flags)) {
            buf.append(sep);
            buf.append(flag);
            sep = " ";
        }
        return buf.toString();
    }

    public static EnumSet<Flag> asFlagSet(long flags) {
        EnumSet<Flag> flagSet = EnumSet.noneOf(Flag.class);
        for (Flag flag : Flag.values()) {
            if ((flags & flag.value) != 0) {
                flagSet.add(flag);
                flags &= ~flag.value;
            }
        }
        Assert.check(flags == 0, "Flags parameter contains unknown flags " + flags);
        return flagSet;
    }

    public static Set<Modifier> asModifierSet(long flags) {
        Set<Modifier> modifiers = modifierSets.get(flags);
        if (modifiers == null) {
            modifiers = EnumSet.noneOf(Modifier.class);
            if (0 != (flags & PUBLIC)) modifiers.add(Modifier.PUBLIC);
            if (0 != (flags & PROTECTED)) modifiers.add(Modifier.PROTECTED);
            if (0 != (flags & PRIVATE)) modifiers.add(Modifier.PRIVATE);
            if (0 != (flags & ABSTRACT)) modifiers.add(Modifier.ABSTRACT);
            if (0 != (flags & STATIC)) modifiers.add(Modifier.STATIC);
            if (0 != (flags & FINAL)) modifiers.add(Modifier.FINAL);
            if (0 != (flags & TRANSIENT)) modifiers.add(Modifier.TRANSIENT);
            if (0 != (flags & VOLATILE)) modifiers.add(Modifier.VOLATILE);
            if (0 != (flags & SYNCHRONIZED))
                modifiers.add(Modifier.SYNCHRONIZED);
            if (0 != (flags & NATIVE)) modifiers.add(Modifier.NATIVE);
            if (0 != (flags & STRICTFP)) modifiers.add(Modifier.STRICTFP);
            if (0 != (flags & DEFAULT)) modifiers.add(Modifier.DEFAULT);
            modifiers = Collections.unmodifiableSet(modifiers);
            modifierSets.put(flags, modifiers);
        }
        return modifiers;
    }

    public static boolean isStatic(Symbol symbol) {
        return (symbol.flags() & STATIC) != 0;
    }

    public static boolean isEnum(Symbol symbol) {
        return (symbol.flags() & ENUM) != 0;
    }

    public static boolean isConstant(Symbol.VarSymbol symbol) {
        return symbol.getConstValue() != null;
    }

    public enum Flag {
        PUBLIC(Flags.PUBLIC),
        PRIVATE(Flags.PRIVATE),
        PROTECTED(Flags.PROTECTED),
        STATIC(Flags.STATIC),
        FINAL(Flags.FINAL),
        SYNCHRONIZED(Flags.SYNCHRONIZED),
        VOLATILE(Flags.VOLATILE),
        TRANSIENT(Flags.TRANSIENT),
        NATIVE(Flags.NATIVE),
        INTERFACE(Flags.INTERFACE),
        ABSTRACT(Flags.ABSTRACT),
        DEFAULT(Flags.DEFAULT),
        STRICTFP(Flags.STRICTFP),
        BRIDGE(Flags.BRIDGE),
        SYNTHETIC(Flags.SYNTHETIC),
        ANNOTATION(Flags.ANNOTATION),
        DEPRECATED(Flags.DEPRECATED),
        HASINIT(Flags.HASINIT),
        BLOCK(Flags.BLOCK),
        ENUM(Flags.ENUM),
        MANDATED(Flags.MANDATED),
        IPROXY(Flags.IPROXY),
        NOOUTERTHIS(Flags.NOOUTERTHIS),
        EXISTS(Flags.EXISTS),
        COMPOUND(Flags.COMPOUND),
        CLASS_SEEN(Flags.CLASS_SEEN),
        SOURCE_SEEN(Flags.SOURCE_SEEN),
        LOCKED(Flags.LOCKED),
        UNATTRIBUTED(Flags.UNATTRIBUTED),
        ANONCONSTR(Flags.ANONCONSTR),
        ACYCLIC(Flags.ACYCLIC),
        PARAMETER(Flags.PARAMETER),
        VARARGS(Flags.VARARGS),
        ACYCLIC_ANN(Flags.ACYCLIC_ANN),
        GENERATEDCONSTR(Flags.GENERATEDCONSTR),
        HYPOTHETICAL(Flags.HYPOTHETICAL),
        PROPRIETARY(Flags.PROPRIETARY),
        UNION(Flags.UNION),
        OVERRIDE_BRIDGE(Flags.OVERRIDE_BRIDGE),
        EFFECTIVELY_FINAL(Flags.EFFECTIVELY_FINAL),
        CLASH(Flags.CLASH),
        AUXILIARY(Flags.AUXILIARY),
        NOT_IN_PROFILE(Flags.NOT_IN_PROFILE),
        BAD_OVERRIDE(Flags.BAD_OVERRIDE),
        SIGNATURE_POLYMORPHIC(Flags.SIGNATURE_POLYMORPHIC),
        THROWS(Flags.THROWS),
        LAMBDA_METHOD(Flags.LAMBDA_METHOD);
        final long value;
        final String lowercaseName;

        Flag(long flag) {
            this.value = flag;
            this.lowercaseName = name().toLowerCase();
        }

        @Override
        public String toString() {
            return lowercaseName;
        }
    }
}
