package com.sun.tools.javac.code;

import com.sun.tools.javac.api.Messages;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import java.util.Locale;

import static com.sun.tools.javac.code.BoundKind.UNBOUND;
import static com.sun.tools.javac.code.Flags.COMPOUND;
import static com.sun.tools.javac.code.Flags.VARARGS;
import static com.sun.tools.javac.code.TypeTag.CLASS;
import static com.sun.tools.javac.code.TypeTag.FORALL;

public abstract class Printer implements Type.Visitor<String, Locale>, Symbol.Visitor<String, Locale> {

    static final int PRIME = 997;
    List<Type> seenCaptured = List.nil();

    protected Printer() {
    }

    public static Printer createStandardPrinter(final Messages messages) {
        return new Printer() {
            @Override
            protected String localize(Locale locale, String key, Object... args) {
                return messages.getLocalizedString(locale, key, args);
            }

            @Override
            protected String capturedVarId(CapturedType t, Locale locale) {
                return (t.hashCode() & 0xFFFFFFFFL) % PRIME + "";
            }
        };
    }

    protected abstract String localize(Locale locale, String key, Object... args);

    protected abstract String capturedVarId(CapturedType t, Locale locale);

    public String visitTypes(List<Type> ts, Locale locale) {
        ListBuffer<String> sbuf = new ListBuffer<>();
        for (Type t : ts) {
            sbuf.append(visit(t, locale));
        }
        return sbuf.toList().toString();
    }

    public String visitSymbols(List<Symbol> ts, Locale locale) {
        ListBuffer<String> sbuf = new ListBuffer<>();
        for (Symbol t : ts) {
            sbuf.append(visit(t, locale));
        }
        return sbuf.toList().toString();
    }

    public String visit(Type t, Locale locale) {
        return t.accept(this, locale);
    }

    public String visit(Symbol s, Locale locale) {
        return s.accept(this, locale);
    }

    @Override
    public String visitCapturedType(CapturedType t, Locale locale) {
        if (seenCaptured.contains(t))
            return localize(locale, "compiler.misc.type.captureof.1",
                    capturedVarId(t, locale));
        else {
            try {
                seenCaptured = seenCaptured.prepend(t);
                return localize(locale, "compiler.misc.type.captureof",
                        capturedVarId(t, locale),
                        visit(t.wildcard, locale));
            } finally {
                seenCaptured = seenCaptured.tail;
            }
        }
    }

    @Override
    public String visitForAll(ForAll t, Locale locale) {
        return "<" + visitTypes(t.tvars, locale) + ">" + visit(t.qtype, locale);
    }

    @Override
    public String visitUndetVar(UndetVar t, Locale locale) {
        if (t.inst != null) {
            return visit(t.inst, locale);
        } else {
            return visit(t.qtype, locale) + "?";
        }
    }

    @Override
    public String visitArrayType(ArrayType t, Locale locale) {
        StringBuilder res = new StringBuilder();
        printBaseElementType(t, res, locale);
        printBrackets(t, res, locale);
        return res.toString();
    }

    void printBaseElementType(Type t, StringBuilder sb, Locale locale) {
        Type arrel = t;
        while (arrel.hasTag(TypeTag.ARRAY)) {
            arrel = arrel.unannotatedType();
            arrel = ((ArrayType) arrel).elemtype;
        }
        sb.append(visit(arrel, locale));
    }

    void printBrackets(Type t, StringBuilder sb, Locale locale) {
        Type arrel = t;
        while (arrel.hasTag(TypeTag.ARRAY)) {
            if (arrel.isAnnotated()) {
                sb.append(' ');
                sb.append(arrel.getAnnotationMirrors());
                sb.append(' ');
            }
            sb.append("[]");
            arrel = arrel.unannotatedType();
            arrel = ((ArrayType) arrel).elemtype;
        }
    }

    @Override
    public String visitClassType(ClassType t, Locale locale) {
        StringBuilder buf = new StringBuilder();
        if (t.getEnclosingType().hasTag(CLASS) && t.tsym.owner.kind == Kinds.TYP) {
            buf.append(visit(t.getEnclosingType(), locale));
            buf.append('.');
            buf.append(className(t, false, locale));
        } else {
            buf.append(className(t, true, locale));
        }
        if (t.getTypeArguments().nonEmpty()) {
            buf.append('<');
            buf.append(visitTypes(t.getTypeArguments(), locale));
            buf.append('>');
        }
        return buf.toString();
    }

    @Override
    public String visitMethodType(MethodType t, Locale locale) {
        return "(" + printMethodArgs(t.argtypes, false, locale) + ")" + visit(t.restype, locale);
    }

    @Override
    public String visitPackageType(PackageType t, Locale locale) {
        return t.tsym.getQualifiedName().toString();
    }

    @Override
    public String visitWildcardType(WildcardType t, Locale locale) {
        StringBuilder s = new StringBuilder();
        s.append(t.kind);
        if (t.kind != UNBOUND) {
            s.append(visit(t.type, locale));
        }
        return s.toString();
    }

    @Override
    public String visitErrorType(ErrorType t, Locale locale) {
        return visitType(t, locale);
    }

    @Override
    public String visitTypeVar(TypeVar t, Locale locale) {
        return visitType(t, locale);
    }

    @Override
    public String visitAnnotatedType(AnnotatedType t, Locale locale) {
        if (t.getAnnotationMirrors().nonEmpty()) {
            if (t.unannotatedType().hasTag(TypeTag.ARRAY)) {
                StringBuilder res = new StringBuilder();
                printBaseElementType(t, res, locale);
                printBrackets(t, res, locale);
                return res.toString();
            } else if (t.unannotatedType().hasTag(TypeTag.CLASS) &&
                    t.unannotatedType().getEnclosingType() != Type.noType) {
                return visit(t.unannotatedType().getEnclosingType(), locale) +
                        ". " +
                        t.getAnnotationMirrors() +
                        " " + className((ClassType) t.unannotatedType(), false, locale);
            } else {
                return t.getAnnotationMirrors() + " " + visit(t.unannotatedType(), locale);
            }
        } else {
            return visit(t.unannotatedType(), locale);
        }
    }

    public String visitType(Type t, Locale locale) {
        String s = (t.tsym == null || t.tsym.name == null)
                ? localize(locale, "compiler.misc.type.none")
                : t.tsym.name.toString();
        return s;
    }

    protected String className(ClassType t, boolean longform, Locale locale) {
        Symbol sym = t.tsym;
        if (sym.name.length() == 0 && (sym.flags() & COMPOUND) != 0) {
            StringBuilder s = new StringBuilder(visit(t.supertype_field, locale));
            for (List<Type> is = t.interfaces_field; is.nonEmpty(); is = is.tail) {
                s.append('&');
                s.append(visit(is.head, locale));
            }
            return s.toString();
        } else if (sym.name.length() == 0) {
            String s;
            ClassType norm = (ClassType) t.tsym.type;
            if (norm == null) {
                s = localize(locale, "compiler.misc.anonymous.class", (Object) null);
            } else if (norm.interfaces_field != null && norm.interfaces_field.nonEmpty()) {
                s = localize(locale, "compiler.misc.anonymous.class",
                        visit(norm.interfaces_field.head, locale));
            } else {
                s = localize(locale, "compiler.misc.anonymous.class",
                        visit(norm.supertype_field, locale));
            }
            return s;
        } else if (longform) {
            return sym.getQualifiedName().toString();
        } else {
            return sym.name.toString();
        }
    }

    protected String printMethodArgs(List<Type> args, boolean varArgs, Locale locale) {
        if (!varArgs) {
            return visitTypes(args, locale);
        } else {
            StringBuilder buf = new StringBuilder();
            while (args.tail.nonEmpty()) {
                buf.append(visit(args.head, locale));
                args = args.tail;
                buf.append(',');
            }
            if (args.head.unannotatedType().hasTag(TypeTag.ARRAY)) {
                buf.append(visit(((ArrayType) args.head.unannotatedType()).elemtype, locale));
                if (args.head.getAnnotationMirrors().nonEmpty()) {
                    buf.append(' ');
                    buf.append(args.head.getAnnotationMirrors());
                    buf.append(' ');
                }
                buf.append("...");
            } else {
                buf.append(visit(args.head, locale));
            }
            return buf.toString();
        }
    }

    @Override
    public String visitClassSymbol(ClassSymbol sym, Locale locale) {
        return sym.name.isEmpty()
                ? localize(locale, "compiler.misc.anonymous.class", sym.flatname)
                : sym.fullname.toString();
    }

    @Override
    public String visitMethodSymbol(MethodSymbol s, Locale locale) {
        if (s.isStaticOrInstanceInit()) {
            return s.owner.name.toString();
        } else {
            String ms = (s.name == s.name.table.names.init)
                    ? s.owner.name.toString()
                    : s.name.toString();
            if (s.type != null) {
                if (s.type.hasTag(FORALL)) {
                    ms = "<" + visitTypes(s.type.getTypeArguments(), locale) + ">" + ms;
                }
                ms += "(" + printMethodArgs(
                        s.type.getParameterTypes(),
                        (s.flags() & VARARGS) != 0,
                        locale) + ")";
            }
            return ms;
        }
    }

    @Override
    public String visitOperatorSymbol(OperatorSymbol s, Locale locale) {
        return visitMethodSymbol(s, locale);
    }

    @Override
    public String visitPackageSymbol(PackageSymbol s, Locale locale) {
        return s.isUnnamed()
                ? localize(locale, "compiler.misc.unnamed.package")
                : s.fullname.toString();
    }

    @Override
    public String visitTypeSymbol(TypeSymbol s, Locale locale) {
        return visitSymbol(s, locale);
    }

    @Override
    public String visitVarSymbol(VarSymbol s, Locale locale) {
        return visitSymbol(s, locale);
    }

    @Override
    public String visitSymbol(Symbol s, Locale locale) {
        return s.name.toString();
    }
}
