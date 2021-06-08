package com.github.api.sun.tools.javadoc;

import com.github.api.sun.javadoc.*;
import com.github.api.sun.source.util.TreePath;
import com.github.api.sun.tools.javac.code.Flags;
import com.github.api.sun.tools.javac.code.Symbol.ClassSymbol;
import com.github.api.sun.tools.javac.code.Symbol.MethodSymbol;
import com.github.api.sun.tools.javac.code.Symbol.VarSymbol;
import com.github.api.sun.tools.javac.code.Type;
import com.github.api.sun.tools.javac.util.List;
import com.github.api.sun.tools.javac.util.ListBuffer;

import java.lang.reflect.Modifier;
import java.text.CollationKey;

public abstract class ExecutableMemberDocImpl
        extends MemberDocImpl implements ExecutableMemberDoc {
    protected final MethodSymbol sym;

    public ExecutableMemberDocImpl(DocEnv env, MethodSymbol sym, TreePath treePath) {
        super(env, sym, treePath);
        this.sym = sym;
    }

    public ExecutableMemberDocImpl(DocEnv env, MethodSymbol sym) {
        this(env, sym, null);
    }

    protected long getFlags() {
        return sym.flags();
    }

    protected ClassSymbol getContainingClass() {
        return sym.enclClass();
    }

    public boolean isNative() {
        return Modifier.isNative(getModifiers());
    }

    public boolean isSynchronized() {
        return Modifier.isSynchronized(getModifiers());
    }

    public boolean isVarArgs() {
        return ((sym.flags() & Flags.VARARGS) != 0
                && !env.legacyDoclet);
    }

    public boolean isSynthetic() {
        return ((sym.flags() & Flags.SYNTHETIC) != 0);
    }

    public boolean isIncluded() {
        return containingClass().isIncluded() && env.shouldDocument(sym);
    }

    public ThrowsTag[] throwsTags() {
        return comment().throwsTags();
    }

    public ParamTag[] paramTags() {
        return comment().paramTags();
    }

    public ParamTag[] typeParamTags() {
        return env.legacyDoclet
                ? new ParamTag[0]
                : comment().typeParamTags();
    }

    public ClassDoc[] thrownExceptions() {
        ListBuffer<ClassDocImpl> l = new ListBuffer<ClassDocImpl>();
        for (Type ex : sym.type.getThrownTypes()) {
            ex = env.types.erasure(ex);
            ClassDocImpl cdi = env.getClassDoc((ClassSymbol) ex.tsym);
            if (cdi != null) l.append(cdi);
        }
        return l.toArray(new ClassDocImpl[l.length()]);
    }

    public com.github.api.sun.javadoc.Type[] thrownExceptionTypes() {
        return TypeMaker.getTypes(env, sym.type.getThrownTypes());
    }

    public Parameter[] parameters() {
        List<VarSymbol> params = sym.params();
        Parameter[] result = new Parameter[params.length()];
        int i = 0;
        for (VarSymbol param : params) {
            result[i++] = new ParameterImpl(env, param);
        }
        return result;
    }

    public com.github.api.sun.javadoc.Type receiverType() {
        Type recvtype = sym.type.asMethodType().recvtype;
        return (recvtype != null) ? TypeMaker.getType(env, recvtype, false, true) : null;
    }

    public TypeVariable[] typeParameters() {
        if (env.legacyDoclet) {
            return new TypeVariable[0];
        }
        TypeVariable[] res = new TypeVariable[sym.type.getTypeArguments().length()];
        TypeMaker.getTypes(env, sym.type.getTypeArguments(), res);
        return res;
    }

    public String signature() {
        return makeSignature(true);
    }

    public String flatSignature() {
        return makeSignature(false);
    }

    private String makeSignature(boolean full) {
        StringBuilder result = new StringBuilder();
        result.append("(");
        for (List<Type> types = sym.type.getParameterTypes(); types.nonEmpty(); ) {
            Type t = types.head;
            result.append(TypeMaker.getTypeString(env, t, full));
            types = types.tail;
            if (types.nonEmpty()) {
                result.append(", ");
            }
        }
        if (isVarArgs()) {
            int len = result.length();
            result.replace(len - 2, len, "...");
        }
        result.append(")");
        return result.toString();
    }

    protected String typeParametersString() {
        return TypeMaker.typeParametersString(env, sym, true);
    }

    @Override
    CollationKey generateKey() {
        String k = name() + flatSignature() + typeParametersString();
        k = k.replace(',', ' ').replace('&', ' ');
        return env.doclocale.collator.getCollationKey(k);
    }

    @Override
    public SourcePosition position() {
        if (sym.enclClass().sourcefile == null) return null;
        return SourcePositionImpl.make(sym.enclClass().sourcefile,
                (tree == null) ? 0 : tree.pos,
                lineMap);
    }
}
