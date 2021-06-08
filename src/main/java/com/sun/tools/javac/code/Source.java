package com.sun.tools.javac.code;

import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Options;

import javax.lang.model.SourceVersion;
import java.util.HashMap;
import java.util.Map;

import static com.sun.tools.javac.main.Option.SOURCE;
import static javax.lang.model.SourceVersion.*;

public enum Source {
    JDK1_2("1.2"),
    JDK1_3("1.3"),
    JDK1_4("1.4"),
    JDK1_5("1.5"),
    JDK1_6("1.6"),
    JDK1_7("1.7"),
    JDK1_8("1.8");
    public static final Source DEFAULT = JDK1_8;
    private static final Context.Key<Source> sourceKey
            = new Context.Key<Source>();
    private static final Map<String, Source> tab = new HashMap<String, Source>();

    static {
        for (Source s : values()) {
            tab.put(s.name, s);
        }
        tab.put("5", JDK1_5);
        tab.put("6", JDK1_6);
        tab.put("7", JDK1_7);
        tab.put("8", JDK1_8);
    }

    public final String name;

    Source(String name) {
        this.name = name;
    }

    public static Source instance(Context context) {
        Source instance = context.get(sourceKey);
        if (instance == null) {
            Options options = Options.instance(context);
            String sourceString = options.get(SOURCE);
            if (sourceString != null) instance = lookup(sourceString);
            if (instance == null) instance = DEFAULT;
            context.put(sourceKey, instance);
        }
        return instance;
    }

    public static Source lookup(String name) {
        return tab.get(name);
    }

    public static SourceVersion toSourceVersion(Source source) {
        switch (source) {
            case JDK1_2:
                return RELEASE_2;
            case JDK1_3:
                return RELEASE_3;
            case JDK1_4:
                return RELEASE_4;
            case JDK1_5:
                return RELEASE_5;
            case JDK1_6:
                return RELEASE_6;
            case JDK1_7:
                return RELEASE_7;
            case JDK1_8:
                return RELEASE_8;
            default:
                return null;
        }
    }

    public Target requiredTarget() {
        if (this.compareTo(JDK1_8) >= 0) return Target.JDK1_8;
        if (this.compareTo(JDK1_7) >= 0) return Target.JDK1_7;
        if (this.compareTo(JDK1_6) >= 0) return Target.JDK1_6;
        if (this.compareTo(JDK1_5) >= 0) return Target.JDK1_5;
        if (this.compareTo(JDK1_4) >= 0) return Target.JDK1_4;
        return Target.JDK1_1;
    }

    public boolean allowEncodingErrors() {
        return compareTo(JDK1_6) < 0;
    }

    public boolean allowAsserts() {
        return compareTo(JDK1_4) >= 0;
    }

    public boolean allowCovariantReturns() {
        return compareTo(JDK1_5) >= 0;
    }

    public boolean allowGenerics() {
        return compareTo(JDK1_5) >= 0;
    }

    public boolean allowDiamond() {
        return compareTo(JDK1_7) >= 0;
    }

    public boolean allowMulticatch() {
        return compareTo(JDK1_7) >= 0;
    }

    public boolean allowImprovedRethrowAnalysis() {
        return compareTo(JDK1_7) >= 0;
    }

    public boolean allowImprovedCatchAnalysis() {
        return compareTo(JDK1_7) >= 0;
    }

    public boolean allowEnums() {
        return compareTo(JDK1_5) >= 0;
    }

    public boolean allowForeach() {
        return compareTo(JDK1_5) >= 0;
    }

    public boolean allowStaticImport() {
        return compareTo(JDK1_5) >= 0;
    }

    public boolean allowBoxing() {
        return compareTo(JDK1_5) >= 0;
    }

    public boolean allowVarargs() {
        return compareTo(JDK1_5) >= 0;
    }

    public boolean allowAnnotations() {
        return compareTo(JDK1_5) >= 0;
    }

    public boolean allowHexFloats() {
        return compareTo(JDK1_5) >= 0;
    }

    public boolean allowAnonOuterThis() {
        return compareTo(JDK1_5) >= 0;
    }

    public boolean addBridges() {
        return compareTo(JDK1_5) >= 0;
    }

    public boolean enforceMandatoryWarnings() {
        return compareTo(JDK1_5) >= 0;
    }

    public boolean allowTryWithResources() {
        return compareTo(JDK1_7) >= 0;
    }

    public boolean allowBinaryLiterals() {
        return compareTo(JDK1_7) >= 0;
    }

    public boolean allowUnderscoresInLiterals() {
        return compareTo(JDK1_7) >= 0;
    }

    public boolean allowStringsInSwitch() {
        return compareTo(JDK1_7) >= 0;
    }

    public boolean allowSimplifiedVarargs() {
        return compareTo(JDK1_7) >= 0;
    }

    public boolean allowObjectToPrimitiveCast() {
        return compareTo(JDK1_7) >= 0;
    }

    public boolean allowPoly() {
        return compareTo(JDK1_8) >= 0;
    }

    public boolean allowLambda() {
        return compareTo(JDK1_8) >= 0;
    }

    public boolean allowMethodReferences() {
        return compareTo(JDK1_8) >= 0;
    }

    public boolean allowDefaultMethods() {
        return compareTo(JDK1_8) >= 0;
    }

    public boolean allowStaticInterfaceMethods() {
        return compareTo(JDK1_8) >= 0;
    }

    public boolean allowStrictMethodClashCheck() {
        return compareTo(JDK1_8) >= 0;
    }

    public boolean allowEffectivelyFinalInInnerClasses() {
        return compareTo(JDK1_8) >= 0;
    }

    public boolean allowTypeAnnotations() {
        return compareTo(JDK1_8) >= 0;
    }

    public boolean allowRepeatedAnnotations() {
        return compareTo(JDK1_8) >= 0;
    }

    public boolean allowIntersectionTypesInCast() {
        return compareTo(JDK1_8) >= 0;
    }

    public boolean allowGraphInference() {
        return compareTo(JDK1_8) >= 0;
    }

    public boolean allowStructuralMostSpecific() {
        return compareTo(JDK1_8) >= 0;
    }
}
