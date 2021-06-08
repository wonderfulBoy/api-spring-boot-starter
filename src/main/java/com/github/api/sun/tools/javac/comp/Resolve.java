package com.github.api.sun.tools.javac.comp;

import com.github.api.sun.source.tree.MemberReferenceTree.ReferenceMode;
import com.github.api.sun.tools.javac.api.Formattable.LocalizedString;
import com.github.api.sun.tools.javac.code.*;
import com.github.api.sun.tools.javac.code.Symbol.*;
import com.github.api.sun.tools.javac.code.Type.*;
import com.github.api.sun.tools.javac.comp.Attr.ResultInfo;
import com.github.api.sun.tools.javac.comp.Check.CheckContext;
import com.github.api.sun.tools.javac.comp.DeferredAttr.AttrMode;
import com.github.api.sun.tools.javac.comp.DeferredAttr.DeferredAttrContext;
import com.github.api.sun.tools.javac.comp.DeferredAttr.DeferredType;
import com.github.api.sun.tools.javac.comp.Infer.FreeTypeListener;
import com.github.api.sun.tools.javac.comp.Infer.InferenceContext;
import com.github.api.sun.tools.javac.comp.Resolve.MethodResolutionContext.Candidate;
import com.github.api.sun.tools.javac.comp.Resolve.MethodResolutionDiagHelper.DiagnosticRewriter;
import com.github.api.sun.tools.javac.comp.Resolve.MethodResolutionDiagHelper.Template;
import com.github.api.sun.tools.javac.jvm.ClassReader;
import com.github.api.sun.tools.javac.jvm.Target;
import com.github.api.sun.tools.javac.main.Option;
import com.github.api.sun.tools.javac.tree.JCTree;
import com.github.api.sun.tools.javac.tree.JCTree.*;
import com.github.api.sun.tools.javac.tree.JCTree.JCMemberReference.ReferenceKind;
import com.github.api.sun.tools.javac.tree.JCTree.JCPolyExpression.PolyKind;
import com.github.api.sun.tools.javac.tree.TreeInfo;
import com.github.api.sun.tools.javac.util.*;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.github.api.sun.tools.javac.util.List;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticType;

import javax.lang.model.element.ElementVisitor;
import java.util.*;

import static com.github.api.sun.tools.javac.code.Flags.*;
import static com.github.api.sun.tools.javac.code.Kinds.*;
import static com.github.api.sun.tools.javac.code.TypeTag.*;
import static com.github.api.sun.tools.javac.comp.Resolve.MethodResolutionPhase.*;
import static com.github.api.sun.tools.javac.tree.JCTree.Tag.IMPORT;

public class Resolve {
    protected static final Context.Key<Resolve> resolveKey =
            new Context.Key<Resolve>();
    public final boolean boxingEnabled;
    public final boolean varargsEnabled;
    public final boolean allowMethodHandles;
    public final boolean allowDefaultMethods;
    public final boolean allowStructuralMostSpecific;
    final EnumSet<VerboseResolutionMode> verboseResolutionMode;
    final List<MethodResolutionPhase> methodResolutionSteps = List.of(BASIC, BOX, VARARITY);
    private final boolean debugResolve;
    private final boolean compactMethodDiags;
    private final SymbolNotFoundError varNotFound;
    private final SymbolNotFoundError methodNotFound;
    private final SymbolNotFoundError methodWithCorrectStaticnessNotFound;
    private final SymbolNotFoundError typeNotFound;
    private final InapplicableMethodException inapplicableMethodException;
    private final LocalizedString noArgs = new LocalizedString("compiler.misc.no.args");
    Names names;
    Log log;
    Symtab syms;
    Attr attr;
    DeferredAttr deferredAttr;
    Check chk;
    Infer infer;
    ClassReader reader;
    TreeInfo treeinfo;
    Types types;
    JCDiagnostic.Factory diags;
    Scope polymorphicSignatureScope;
    MethodCheck nilMethodCheck = new MethodCheck() {
        public void argumentsAcceptable(Env<AttrContext> env, DeferredAttrContext deferredAttrContext, List<Type> argtypes, List<Type> formals, Warner warn) {

        }

        public MethodCheck mostSpecificCheck(List<Type> actuals, boolean strict) {
            return this;
        }
    };
    MethodCheck arityMethodCheck = new AbstractMethodCheck() {
        @Override
        void checkArg(DiagnosticPosition pos, boolean varargs, Type actual, Type formal, DeferredAttrContext deferredAttrContext, Warner warn) {

        }
    };
    MethodCheck resolveMethodCheck = new AbstractMethodCheck() {
        @Override
        void checkArg(DiagnosticPosition pos, boolean varargs, Type actual, Type formal, DeferredAttrContext deferredAttrContext, Warner warn) {
            ResultInfo mresult = methodCheckResult(varargs, formal, deferredAttrContext, warn);
            mresult.check(pos, actual);
        }

        @Override
        public void argumentsAcceptable(final Env<AttrContext> env,
                                        DeferredAttrContext deferredAttrContext,
                                        List<Type> argtypes,
                                        List<Type> formals,
                                        Warner warn) {
            super.argumentsAcceptable(env, deferredAttrContext, argtypes, formals, warn);

            if (deferredAttrContext.phase.isVarargsRequired()) {

                varargsAccessible(env, types.elemtype(formals.last()),
                        deferredAttrContext.inferenceContext);
            }
        }

        private void varargsAccessible(final Env<AttrContext> env, final Type t, final InferenceContext inferenceContext) {
            if (inferenceContext.free(t)) {
                inferenceContext.addFreeTypeListener(List.of(t), new FreeTypeListener() {
                    @Override
                    public void typesInferred(InferenceContext inferenceContext) {
                        varargsAccessible(env, inferenceContext.asInstType(t), inferenceContext);
                    }
                });
            } else {
                if (!isAccessible(env, t)) {
                    Symbol location = env.enclClass.sym;
                    reportMC(env.tree, MethodCheckDiag.INACCESSIBLE_VARARGS, inferenceContext, t, Kinds.kindName(location), location);
                }
            }
        }

        private ResultInfo methodCheckResult(final boolean varargsCheck, Type to,
                                             final DeferredAttrContext deferredAttrContext, Warner rsWarner) {
            CheckContext checkContext = new MethodCheckContext(!deferredAttrContext.phase.isBoxingRequired(), deferredAttrContext, rsWarner) {
                MethodCheckDiag methodDiag = varargsCheck ?
                        MethodCheckDiag.VARARG_MISMATCH : MethodCheckDiag.ARG_MISMATCH;

                @Override
                public void report(DiagnosticPosition pos, JCDiagnostic details) {
                    reportMC(pos, methodDiag, deferredAttrContext.inferenceContext, details);
                }
            };
            return new MethodResultInfo(to, checkContext);
        }

        @Override
        public MethodCheck mostSpecificCheck(List<Type> actuals, boolean strict) {
            return new MostSpecificCheck(strict, actuals);
        }
    };
    Warner noteWarner = new Warner();
    LogResolveHelper basicLogResolveHelper = new LogResolveHelper() {
        public boolean resolveDiagnosticNeeded(Type site, List<Type> argtypes, List<Type> typeargtypes) {
            return !site.isErroneous();
        }

        public List<Type> getArgumentTypes(ResolveError errSym, Symbol accessedSym, Name name, List<Type> argtypes) {
            return argtypes;
        }
    };
    Types.SimpleVisitor<Void, Env<AttrContext>> accessibilityChecker =
            new Types.SimpleVisitor<Void, Env<AttrContext>>() {
                void visit(List<Type> ts, Env<AttrContext> env) {
                    for (Type t : ts) {
                        visit(t, env);
                    }
                }

                public Void visitType(Type t, Env<AttrContext> env) {
                    return null;
                }

                @Override
                public Void visitArrayType(ArrayType t, Env<AttrContext> env) {
                    visit(t.elemtype, env);
                    return null;
                }

                @Override
                public Void visitClassType(ClassType t, Env<AttrContext> env) {
                    visit(t.getTypeArguments(), env);
                    if (!isAccessible(env, t, true)) {
                        accessBase(new AccessError(t.tsym), env.tree.pos(), env.enclClass.sym, t, t.tsym.name, true);
                    }
                    return null;
                }

                @Override
                public Void visitWildcardType(WildcardType t, Env<AttrContext> env) {
                    visit(t.type, env);
                    return null;
                }

                @Override
                public Void visitMethodType(MethodType t, Env<AttrContext> env) {
                    visit(t.getParameterTypes(), env);
                    visit(t.getReturnType(), env);
                    visit(t.getThrownTypes(), env);
                    return null;
                }
            };
    MethodResolutionContext currentResolutionContext = null;
    LogResolveHelper methodLogResolveHelper = new LogResolveHelper() {
        public boolean resolveDiagnosticNeeded(Type site, List<Type> argtypes, List<Type> typeargtypes) {
            return !site.isErroneous() &&
                    !Type.isErroneous(argtypes) &&
                    (typeargtypes == null || !Type.isErroneous(typeargtypes));
        }

        public List<Type> getArgumentTypes(ResolveError errSym, Symbol accessedSym, Name name, List<Type> argtypes) {
            return (syms.operatorNames.contains(name)) ?
                    argtypes :
                    Type.map(argtypes, new ResolveDeferredRecoveryMap(AttrMode.SPECULATIVE, accessedSym, currentResolutionContext.step));
        }
    };

    protected Resolve(Context context) {
        context.put(resolveKey, this);
        syms = Symtab.instance(context);
        varNotFound = new
                SymbolNotFoundError(ABSENT_VAR);
        methodNotFound = new
                SymbolNotFoundError(ABSENT_MTH);
        methodWithCorrectStaticnessNotFound = new
                SymbolNotFoundError(WRONG_STATICNESS,
                "method found has incorrect staticness");
        typeNotFound = new
                SymbolNotFoundError(ABSENT_TYP);
        names = Names.instance(context);
        log = Log.instance(context);
        attr = Attr.instance(context);
        deferredAttr = DeferredAttr.instance(context);
        chk = Check.instance(context);
        infer = Infer.instance(context);
        reader = ClassReader.instance(context);
        treeinfo = TreeInfo.instance(context);
        types = Types.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        Source source = Source.instance(context);
        boxingEnabled = source.allowBoxing();
        varargsEnabled = source.allowVarargs();
        Options options = Options.instance(context);
        debugResolve = options.isSet("debugresolve");
        compactMethodDiags = options.isSet(Option.XDIAGS, "compact") ||
                options.isUnset(Option.XDIAGS) && options.isUnset("rawDiagnostics");
        verboseResolutionMode = VerboseResolutionMode.getVerboseResolutionMode(options);
        Target target = Target.instance(context);
        allowMethodHandles = target.hasMethodHandles();
        allowDefaultMethods = source.allowDefaultMethods();
        allowStructuralMostSpecific = source.allowStructuralMostSpecific();
        polymorphicSignatureScope = new Scope(syms.noSymbol);
        inapplicableMethodException = new InapplicableMethodException(diags);
    }

    public static Resolve instance(Context context) {
        Resolve instance = context.get(resolveKey);
        if (instance == null)
            instance = new Resolve(context);
        return instance;
    }

    protected static boolean isStatic(Env<AttrContext> env) {
        return env.info.staticLevel > env.outer.info.staticLevel;
    }

    static boolean isInitializer(Env<AttrContext> env) {
        Symbol owner = env.info.scope.owner;
        return owner.isConstructor() ||
                owner.owner.kind == TYP &&
                        (owner.kind == VAR ||
                                owner.kind == MTH && (owner.flags() & BLOCK) != 0) &&
                        (owner.flags() & STATIC) == 0;
    }

    void reportVerboseResolutionDiagnostic(DiagnosticPosition dpos, Name name, Type site,
                                           List<Type> argtypes, List<Type> typeargtypes, Symbol bestSoFar) {
        boolean success = bestSoFar.kind < ERRONEOUS;
        if (success && !verboseResolutionMode.contains(VerboseResolutionMode.SUCCESS)) {
            return;
        } else if (!success && !verboseResolutionMode.contains(VerboseResolutionMode.FAILURE)) {
            return;
        }
        if (bestSoFar.name == names.init &&
                bestSoFar.owner == syms.objectType.tsym &&
                !verboseResolutionMode.contains(VerboseResolutionMode.OBJECT_INIT)) {
            return;
        } else if (site == syms.predefClass.type &&
                !verboseResolutionMode.contains(VerboseResolutionMode.PREDEF)) {
            return;
        } else if (currentResolutionContext.internalResolution &&
                !verboseResolutionMode.contains(VerboseResolutionMode.INTERNAL)) {
            return;
        }
        int pos = 0;
        int mostSpecificPos = -1;
        ListBuffer<JCDiagnostic> subDiags = new ListBuffer<>();
        for (Candidate c : currentResolutionContext.candidates) {
            if (currentResolutionContext.step != c.step ||
                    (c.isApplicable() && !verboseResolutionMode.contains(VerboseResolutionMode.APPLICABLE)) ||
                    (!c.isApplicable() && !verboseResolutionMode.contains(VerboseResolutionMode.INAPPLICABLE))) {
                continue;
            } else {
                subDiags.append(c.isApplicable() ?
                        getVerboseApplicableCandidateDiag(pos, c.sym, c.mtype) :
                        getVerboseInapplicableCandidateDiag(pos, c.sym, c.details));
                if (c.sym == bestSoFar)
                    mostSpecificPos = pos;
                pos++;
            }
        }
        String key = success ? "verbose.resolve.multi" : "verbose.resolve.multi.1";
        List<Type> argtypes2 = Type.map(argtypes,
                deferredAttr.new RecoveryDeferredTypeMap(AttrMode.SPECULATIVE, bestSoFar, currentResolutionContext.step));
        JCDiagnostic main = diags.note(log.currentSource(), dpos, key, name,
                site.tsym, mostSpecificPos, currentResolutionContext.step,
                methodArguments(argtypes2),
                methodArguments(typeargtypes));
        JCDiagnostic d = new JCDiagnostic.MultilineDiagnostic(main, subDiags.toList());
        log.report(d);
    }

    JCDiagnostic getVerboseApplicableCandidateDiag(int pos, Symbol sym, Type inst) {
        JCDiagnostic subDiag = null;
        if (sym.type.hasTag(FORALL)) {
            subDiag = diags.fragment("partial.inst.sig", inst);
        }
        String key = subDiag == null ?
                "applicable.method.found" :
                "applicable.method.found.1";
        return diags.fragment(key, pos, sym, subDiag);
    }

    JCDiagnostic getVerboseInapplicableCandidateDiag(int pos, Symbol sym, JCDiagnostic subDiag) {
        return diags.fragment("not.applicable.method.found", pos, sym, subDiag);
    }

    public boolean isAccessible(Env<AttrContext> env, TypeSymbol c) {
        return isAccessible(env, c, false);
    }

    public boolean isAccessible(Env<AttrContext> env, TypeSymbol c, boolean checkInner) {
        boolean isAccessible = false;
        switch ((short) (c.flags() & AccessFlags)) {
            case PRIVATE:
                isAccessible =
                        env.enclClass.sym.outermostClass() ==
                                c.owner.outermostClass();
                break;
            case 0:
                isAccessible =
                        env.toplevel.packge == c.owner
                                ||
                                env.toplevel.packge == c.packge()
                                ||


                                env.enclMethod != null &&
                                        (env.enclMethod.mods.flags & ANONCONSTR) != 0;
                break;
            default:
            case PUBLIC:
                isAccessible = true;
                break;
            case PROTECTED:
                isAccessible =
                        env.toplevel.packge == c.owner
                                ||
                                env.toplevel.packge == c.packge()
                                ||
                                isInnerSubClass(env.enclClass.sym, c.owner);
                break;
        }
        return (checkInner == false || c.type.getEnclosingType() == Type.noType) ?
                isAccessible :
                isAccessible && isAccessible(env, c.type.getEnclosingType(), checkInner);
    }

    private boolean isInnerSubClass(ClassSymbol c, Symbol base) {
        while (c != null && !c.isSubClass(base, types)) {
            c = c.owner.enclClass();
        }
        return c != null;
    }

    boolean isAccessible(Env<AttrContext> env, Type t) {
        return isAccessible(env, t, false);
    }

    boolean isAccessible(Env<AttrContext> env, Type t, boolean checkInner) {
        return (t.hasTag(ARRAY))
                ? isAccessible(env, types.upperBound(types.elemtype(t)))
                : isAccessible(env, t.tsym, checkInner);
    }

    public boolean isAccessible(Env<AttrContext> env, Type site, Symbol sym) {
        return isAccessible(env, site, sym, false);
    }

    public boolean isAccessible(Env<AttrContext> env, Type site, Symbol sym, boolean checkInner) {
        if (sym.name == names.init && sym.owner != site.tsym) return false;
        switch ((short) (sym.flags() & AccessFlags)) {
            case PRIVATE:
                return
                        (env.enclClass.sym == sym.owner
                                ||
                                env.enclClass.sym.outermostClass() ==
                                        sym.owner.outermostClass())
                                &&
                                sym.isInheritedIn(site.tsym, types);
            case 0:
                return
                        (env.toplevel.packge == sym.owner.owner
                                ||
                                env.toplevel.packge == sym.packge())
                                &&
                                isAccessible(env, site, checkInner)
                                &&
                                sym.isInheritedIn(site.tsym, types)
                                &&
                                notOverriddenIn(site, sym);
            case PROTECTED:
                return
                        (env.toplevel.packge == sym.owner.owner
                                ||
                                env.toplevel.packge == sym.packge()
                                ||
                                isProtectedAccessible(sym, env.enclClass.sym, site)
                                ||


                                env.info.selectSuper && (sym.flags() & STATIC) == 0 && sym.kind != TYP)
                                &&
                                isAccessible(env, site, checkInner)
                                &&
                                notOverriddenIn(site, sym);
            default:
                return isAccessible(env, site, checkInner) && notOverriddenIn(site, sym);
        }
    }

    private boolean notOverriddenIn(Type site, Symbol sym) {
        if (sym.kind != MTH || sym.isConstructor() || sym.isStatic())
            return true;
        else {
            Symbol s2 = ((MethodSymbol) sym).implementation(site.tsym, types, true);
            return (s2 == null || s2 == sym || sym.owner == s2.owner ||
                    !types.isSubSignature(types.memberType(site, s2), types.memberType(site, sym)));
        }
    }

    private boolean isProtectedAccessible(Symbol sym, ClassSymbol c, Type site) {
        Type newSite = site.hasTag(TYPEVAR) ? site.getUpperBound() : site;
        while (c != null &&
                !(c.isSubClass(sym.owner, types) &&
                        (c.flags() & INTERFACE) == 0 &&


                        ((sym.flags() & STATIC) != 0 || sym.kind == TYP || newSite.tsym.isSubClass(c, types))))
            c = c.owner.enclClass();
        return c != null;
    }

    void checkAccessibleType(Env<AttrContext> env, Type t) {
        accessibilityChecker.visit(t, env);
    }

    Type rawInstantiate(Env<AttrContext> env,
                        Type site,
                        Symbol m,
                        ResultInfo resultInfo,
                        List<Type> argtypes,
                        List<Type> typeargtypes,
                        boolean allowBoxing,
                        boolean useVarargs,
                        Warner warn) throws Infer.InferenceException {
        Type mt = types.memberType(site, m);


        List<Type> tvars = List.nil();
        if (typeargtypes == null) typeargtypes = List.nil();
        if (!mt.hasTag(FORALL) && typeargtypes.nonEmpty()) {


        } else if (mt.hasTag(FORALL) && typeargtypes.nonEmpty()) {
            ForAll pmt = (ForAll) mt;
            if (typeargtypes.length() != pmt.tvars.length())
                throw inapplicableMethodException.setMessage("arg.length.mismatch");

            List<Type> formals = pmt.tvars;
            List<Type> actuals = typeargtypes;
            while (formals.nonEmpty() && actuals.nonEmpty()) {
                List<Type> bounds = types.subst(types.getBounds((TypeVar) formals.head),
                        pmt.tvars, typeargtypes);
                for (; bounds.nonEmpty(); bounds = bounds.tail)
                    if (!types.isSubtypeUnchecked(actuals.head, bounds.head, warn))
                        throw inapplicableMethodException.setMessage("explicit.param.do.not.conform.to.bounds", actuals.head, bounds);
                formals = formals.tail;
                actuals = actuals.tail;
            }
            mt = types.subst(pmt.qtype, pmt.tvars, typeargtypes);
        } else if (mt.hasTag(FORALL)) {
            ForAll pmt = (ForAll) mt;
            List<Type> tvars1 = types.newInstances(pmt.tvars);
            tvars = tvars.appendList(tvars1);
            mt = types.subst(pmt.qtype, pmt.tvars, tvars1);
        }

        boolean instNeeded = tvars.tail != null;
        for (List<Type> l = argtypes;
             l.tail != null && !instNeeded;
             l = l.tail) {
            if (l.head.hasTag(FORALL)) instNeeded = true;
        }
        if (instNeeded)
            return infer.instantiateMethod(env,
                    tvars,
                    (MethodType) mt,
                    resultInfo,
                    m,
                    argtypes,
                    allowBoxing,
                    useVarargs,
                    currentResolutionContext,
                    warn);
        DeferredAttrContext dc = currentResolutionContext.deferredAttrContext(m, infer.emptyContext, resultInfo, warn);
        currentResolutionContext.methodCheck.argumentsAcceptable(env, dc,
                argtypes, mt.getParameterTypes(), warn);
        dc.complete();
        return mt;
    }

    Type checkMethod(Env<AttrContext> env,
                     Type site,
                     Symbol m,
                     ResultInfo resultInfo,
                     List<Type> argtypes,
                     List<Type> typeargtypes,
                     Warner warn) {
        MethodResolutionContext prevContext = currentResolutionContext;
        try {
            currentResolutionContext = new MethodResolutionContext();
            currentResolutionContext.attrMode = AttrMode.CHECK;
            if (env.tree.hasTag(Tag.REFERENCE)) {


                currentResolutionContext.methodCheck =
                        new MethodReferenceCheck(resultInfo.checkContext.inferenceContext());
            }
            MethodResolutionPhase step = currentResolutionContext.step = env.info.pendingResolutionPhase;
            return rawInstantiate(env, site, m, resultInfo, argtypes, typeargtypes,
                    step.isBoxingRequired(), step.isVarargsRequired(), warn);
        } finally {
            currentResolutionContext = prevContext;
        }
    }

    Type instantiate(Env<AttrContext> env,
                     Type site,
                     Symbol m,
                     ResultInfo resultInfo,
                     List<Type> argtypes,
                     List<Type> typeargtypes,
                     boolean allowBoxing,
                     boolean useVarargs,
                     Warner warn) {
        try {
            return rawInstantiate(env, site, m, resultInfo, argtypes, typeargtypes,
                    allowBoxing, useVarargs, warn);
        } catch (InapplicableMethodException ex) {
            return null;
        }
    }

    List<Type> dummyArgs(int length) {
        ListBuffer<Type> buf = new ListBuffer<>();
        for (int i = 0; i < length; i++) {
            buf.append(Type.noType);
        }
        return buf.toList();
    }

    Symbol findField(Env<AttrContext> env,
                     Type site,
                     Name name,
                     TypeSymbol c) {
        while (c.type.hasTag(TYPEVAR))
            c = c.type.getUpperBound().tsym;
        Symbol bestSoFar = varNotFound;
        Symbol sym;
        Scope.Entry e = c.members().lookup(name);
        while (e.scope != null) {
            if (e.sym.kind == VAR && (e.sym.flags_field & SYNTHETIC) == 0) {
                return isAccessible(env, site, e.sym)
                        ? e.sym : new AccessError(env, site, e.sym);
            }
            e = e.next();
        }
        Type st = types.supertype(c.type);
        if (st != null && (st.hasTag(CLASS) || st.hasTag(TYPEVAR))) {
            sym = findField(env, site, name, st.tsym);
            if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }
        for (List<Type> l = types.interfaces(c.type);
             bestSoFar.kind != AMBIGUOUS && l.nonEmpty();
             l = l.tail) {
            sym = findField(env, site, name, l.head.tsym);
            if (bestSoFar.exists() && sym.exists() &&
                    sym.owner != bestSoFar.owner)
                bestSoFar = new AmbiguityError(bestSoFar, sym);
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }
        return bestSoFar;
    }

    public VarSymbol resolveInternalField(DiagnosticPosition pos, Env<AttrContext> env,
                                          Type site, Name name) {
        Symbol sym = findField(env, site, name, site.tsym);
        if (sym.kind == VAR) return (VarSymbol) sym;
        else throw new FatalError(
                diags.fragment("fatal.err.cant.locate.field",
                        name));
    }

    Symbol findVar(Env<AttrContext> env, Name name) {
        Symbol bestSoFar = varNotFound;
        Symbol sym;
        Env<AttrContext> env1 = env;
        boolean staticOnly = false;
        while (env1.outer != null) {
            if (isStatic(env1)) staticOnly = true;
            Scope.Entry e = env1.info.scope.lookup(name);
            while (e.scope != null &&
                    (e.sym.kind != VAR ||
                            (e.sym.flags_field & SYNTHETIC) != 0))
                e = e.next();
            sym = (e.scope != null)
                    ? e.sym
                    : findField(
                    env1, env1.enclClass.sym.type, name, env1.enclClass.sym);
            if (sym.exists()) {
                if (staticOnly &&
                        sym.kind == VAR &&
                        sym.owner.kind == TYP &&
                        (sym.flags() & STATIC) == 0)
                    return new StaticError(sym);
                else
                    return sym;
            } else if (sym.kind < bestSoFar.kind) {
                bestSoFar = sym;
            }
            if ((env1.enclClass.sym.flags() & STATIC) != 0) staticOnly = true;
            env1 = env1.outer;
        }
        sym = findField(env, syms.predefClass.type, name, syms.predefClass);
        if (sym.exists())
            return sym;
        if (bestSoFar.exists())
            return bestSoFar;
        Symbol origin = null;
        for (Scope sc : new Scope[]{env.toplevel.namedImportScope, env.toplevel.starImportScope}) {
            Scope.Entry e = sc.lookup(name);
            for (; e.scope != null; e = e.next()) {
                sym = e.sym;
                if (sym.kind != VAR)
                    continue;

                if (bestSoFar.kind < AMBIGUOUS && sym.owner != bestSoFar.owner)
                    return new AmbiguityError(bestSoFar, sym);
                else if (bestSoFar.kind >= VAR) {
                    origin = e.getOrigin().owner;
                    bestSoFar = isAccessible(env, origin.type, sym)
                            ? sym : new AccessError(env, origin.type, sym);
                }
            }
            if (bestSoFar.exists()) break;
        }
        if (bestSoFar.kind == VAR && bestSoFar.owner.type != origin.type)
            return bestSoFar.clone(origin);
        else
            return bestSoFar;
    }

    @SuppressWarnings("fallthrough")
    Symbol selectBest(Env<AttrContext> env,
                      Type site,
                      List<Type> argtypes,
                      List<Type> typeargtypes,
                      Symbol sym,
                      Symbol bestSoFar,
                      boolean allowBoxing,
                      boolean useVarargs,
                      boolean operator) {
        if (sym.kind == ERR ||
                !sym.isInheritedIn(site.tsym, types)) {
            return bestSoFar;
        } else if (useVarargs && (sym.flags() & VARARGS) == 0) {
            return bestSoFar.kind >= ERRONEOUS ?
                    new BadVarargsMethod((ResolveError) bestSoFar) :
                    bestSoFar;
        }
        Assert.check(sym.kind < AMBIGUOUS);
        try {
            Type mt = rawInstantiate(env, site, sym, null, argtypes, typeargtypes,
                    allowBoxing, useVarargs, types.noWarnings);
            if (!operator || verboseResolutionMode.contains(VerboseResolutionMode.PREDEF))
                currentResolutionContext.addApplicableCandidate(sym, mt);
        } catch (InapplicableMethodException ex) {
            if (!operator)
                currentResolutionContext.addInapplicableCandidate(sym, ex.getDiagnostic());
            switch (bestSoFar.kind) {
                case ABSENT_MTH:
                    return new InapplicableSymbolError(currentResolutionContext);
                case WRONG_MTH:
                    if (operator) return bestSoFar;
                    bestSoFar = new InapplicableSymbolsError(currentResolutionContext);
                default:
                    return bestSoFar;
            }
        }
        if (!isAccessible(env, site, sym)) {
            return (bestSoFar.kind == ABSENT_MTH)
                    ? new AccessError(env, site, sym)
                    : bestSoFar;
        }
        return (bestSoFar.kind > AMBIGUOUS)
                ? sym
                : mostSpecific(argtypes, sym, bestSoFar, env, site,
                allowBoxing && operator, useVarargs);
    }

    Symbol mostSpecific(List<Type> argtypes, Symbol m1,
                        Symbol m2,
                        Env<AttrContext> env,
                        final Type site,
                        boolean allowBoxing,
                        boolean useVarargs) {
        switch (m2.kind) {
            case MTH:
                if (m1 == m2) return m1;
                boolean m1SignatureMoreSpecific =
                        signatureMoreSpecific(argtypes, env, site, m1, m2, allowBoxing, useVarargs);
                boolean m2SignatureMoreSpecific =
                        signatureMoreSpecific(argtypes, env, site, m2, m1, allowBoxing, useVarargs);
                if (m1SignatureMoreSpecific && m2SignatureMoreSpecific) {
                    Type mt1 = types.memberType(site, m1);
                    Type mt2 = types.memberType(site, m2);
                    if (!types.overrideEquivalent(mt1, mt2))
                        return ambiguityError(m1, m2);


                    if ((m1.flags() & BRIDGE) != (m2.flags() & BRIDGE))
                        return ((m1.flags() & BRIDGE) != 0) ? m2 : m1;

                    TypeSymbol m1Owner = (TypeSymbol) m1.owner;
                    TypeSymbol m2Owner = (TypeSymbol) m2.owner;
                    if (types.asSuper(m1Owner.type, m2Owner) != null &&
                            ((m1.owner.flags_field & INTERFACE) == 0 ||
                                    (m2.owner.flags_field & INTERFACE) != 0) &&
                            m1.overrides(m2, m1Owner, types, false))
                        return m1;
                    if (types.asSuper(m2Owner.type, m1Owner) != null &&
                            ((m2.owner.flags_field & INTERFACE) == 0 ||
                                    (m1.owner.flags_field & INTERFACE) != 0) &&
                            m2.overrides(m1, m2Owner, types, false))
                        return m2;
                    boolean m1Abstract = (m1.flags() & ABSTRACT) != 0;
                    boolean m2Abstract = (m2.flags() & ABSTRACT) != 0;
                    if (m1Abstract && !m2Abstract) return m2;
                    if (m2Abstract && !m1Abstract) return m1;

                    return ambiguityError(m1, m2);
                }
                if (m1SignatureMoreSpecific) return m1;
                if (m2SignatureMoreSpecific) return m2;
                return ambiguityError(m1, m2);
            case AMBIGUOUS:

                AmbiguityError e = (AmbiguityError) m2.baseSymbol();
                for (Symbol s : e.ambiguousSyms) {
                    if (mostSpecific(argtypes, m1, s, env, site, allowBoxing, useVarargs) != m1) {
                        return e.addAmbiguousSymbol(m1);
                    }
                }
                return m1;
            default:
                throw new AssertionError();
        }
    }

    private boolean signatureMoreSpecific(List<Type> actuals, Env<AttrContext> env, Type site, Symbol m1, Symbol m2, boolean allowBoxing, boolean useVarargs) {
        noteWarner.clear();
        int maxLength = Math.max(
                Math.max(m1.type.getParameterTypes().length(), actuals.length()),
                m2.type.getParameterTypes().length());
        MethodResolutionContext prevResolutionContext = currentResolutionContext;
        try {
            currentResolutionContext = new MethodResolutionContext();
            currentResolutionContext.step = prevResolutionContext.step;
            currentResolutionContext.methodCheck =
                    prevResolutionContext.methodCheck.mostSpecificCheck(actuals, !allowBoxing);
            Type mst = instantiate(env, site, m2, null,
                    adjustArgs(types.lowerBounds(types.memberType(site, m1).getParameterTypes()), m1, maxLength, useVarargs), null,
                    allowBoxing, useVarargs, noteWarner);
            return mst != null &&
                    !noteWarner.hasLint(Lint.LintCategory.UNCHECKED);
        } finally {
            currentResolutionContext = prevResolutionContext;
        }
    }

    List<Type> adjustArgs(List<Type> args, Symbol msym, int length, boolean allowVarargs) {
        if ((msym.flags() & VARARGS) != 0 && allowVarargs) {
            Type varargsElem = types.elemtype(args.last());
            if (varargsElem == null) {
                Assert.error("Bad varargs = " + args.last() + " " + msym);
            }
            List<Type> newArgs = args.reverse().tail.prepend(varargsElem).reverse();
            while (newArgs.length() < length) {
                newArgs = newArgs.append(newArgs.last());
            }
            return newArgs;
        } else {
            return args;
        }
    }

    Type mostSpecificReturnType(Type mt1, Type mt2) {
        Type rt1 = mt1.getReturnType();
        Type rt2 = mt2.getReturnType();
        if (mt1.hasTag(FORALL) && mt2.hasTag(FORALL)) {

            rt1 = types.subst(rt1, mt1.getTypeArguments(), mt2.getTypeArguments());
        }

        if (types.isSubtype(rt1, rt2)) {
            return mt1;
        } else if (types.isSubtype(rt2, rt1)) {
            return mt2;
        } else if (types.returnTypeSubstitutable(mt1, mt2)) {
            return mt1;
        } else if (types.returnTypeSubstitutable(mt2, mt1)) {
            return mt2;
        } else {
            return null;
        }
    }

    Symbol ambiguityError(Symbol m1, Symbol m2) {
        if (((m1.flags() | m2.flags()) & CLASH) != 0) {
            return (m1.flags() & CLASH) == 0 ? m1 : m2;
        } else {
            return new AmbiguityError(m1, m2);
        }
    }

    Symbol findMethodInScope(Env<AttrContext> env,
                             Type site,
                             Name name,
                             List<Type> argtypes,
                             List<Type> typeargtypes,
                             Scope sc,
                             Symbol bestSoFar,
                             boolean allowBoxing,
                             boolean useVarargs,
                             boolean operator,
                             boolean abstractok) {
        for (Symbol s : sc.getElementsByName(name, new LookupFilter(abstractok))) {
            bestSoFar = selectBest(env, site, argtypes, typeargtypes, s,
                    bestSoFar, allowBoxing, useVarargs, operator);
        }
        return bestSoFar;
    }

    Symbol findMethod(Env<AttrContext> env,
                      Type site,
                      Name name,
                      List<Type> argtypes,
                      List<Type> typeargtypes,
                      boolean allowBoxing,
                      boolean useVarargs,
                      boolean operator) {
        Symbol bestSoFar = methodNotFound;
        bestSoFar = findMethod(env,
                site,
                name,
                argtypes,
                typeargtypes,
                site.tsym.type,
                bestSoFar,
                allowBoxing,
                useVarargs,
                operator);
        return bestSoFar;
    }

    private Symbol findMethod(Env<AttrContext> env,
                              Type site,
                              Name name,
                              List<Type> argtypes,
                              List<Type> typeargtypes,
                              Type intype,
                              Symbol bestSoFar,
                              boolean allowBoxing,
                              boolean useVarargs,
                              boolean operator) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Type>[] itypes = (List<Type>[]) new List[]{List.<Type>nil(), List.<Type>nil()};
        InterfaceLookupPhase iphase = InterfaceLookupPhase.ABSTRACT_OK;
        for (TypeSymbol s : superclasses(intype)) {
            bestSoFar = findMethodInScope(env, site, name, argtypes, typeargtypes,
                    s.members(), bestSoFar, allowBoxing, useVarargs, operator, true);
            if (name == names.init) return bestSoFar;
            iphase = (iphase == null) ? null : iphase.update(s, this);
            if (iphase != null) {
                for (Type itype : types.interfaces(s.type)) {
                    itypes[iphase.ordinal()] = types.union(types.closure(itype), itypes[iphase.ordinal()]);
                }
            }
        }
        Symbol concrete = bestSoFar.kind < ERR &&
                (bestSoFar.flags() & ABSTRACT) == 0 ?
                bestSoFar : methodNotFound;
        for (InterfaceLookupPhase iphase2 : InterfaceLookupPhase.values()) {
            if (iphase2 == InterfaceLookupPhase.DEFAULT_OK && !allowDefaultMethods) break;

            for (Type itype : itypes[iphase2.ordinal()]) {
                if (!itype.isInterface()) continue;
                if (iphase2 == InterfaceLookupPhase.DEFAULT_OK &&
                        (itype.tsym.flags() & DEFAULT) == 0) continue;
                bestSoFar = findMethodInScope(env, site, name, argtypes, typeargtypes,
                        itype.tsym.members(), bestSoFar, allowBoxing, useVarargs, operator, true);
                if (concrete != bestSoFar &&
                        concrete.kind < ERR && bestSoFar.kind < ERR &&
                        types.isSubSignature(concrete.type, bestSoFar.type)) {


                    bestSoFar = concrete;
                }
            }
        }
        return bestSoFar;
    }

    Iterable<TypeSymbol> superclasses(final Type intype) {
        return new Iterable<TypeSymbol>() {
            public Iterator<TypeSymbol> iterator() {
                return new Iterator<TypeSymbol>() {
                    List<TypeSymbol> seen = List.nil();
                    TypeSymbol currentSym = symbolFor(intype);
                    TypeSymbol prevSym = null;

                    public boolean hasNext() {
                        if (currentSym == syms.noSymbol) {
                            currentSym = symbolFor(types.supertype(prevSym.type));
                        }
                        return currentSym != null;
                    }

                    public TypeSymbol next() {
                        prevSym = currentSym;
                        currentSym = syms.noSymbol;
                        Assert.check(prevSym != null || prevSym != syms.noSymbol);
                        return prevSym;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    TypeSymbol symbolFor(Type t) {
                        if (!t.hasTag(CLASS) &&
                                !t.hasTag(TYPEVAR)) {
                            return null;
                        }
                        while (t.hasTag(TYPEVAR))
                            t = t.getUpperBound();
                        if (seen.contains(t.tsym)) {


                            return null;
                        }
                        seen = seen.prepend(t.tsym);
                        return t.tsym;
                    }
                };
            }
        };
    }

    Symbol findFun(Env<AttrContext> env, Name name,
                   List<Type> argtypes, List<Type> typeargtypes,
                   boolean allowBoxing, boolean useVarargs) {
        Symbol bestSoFar = methodNotFound;
        Symbol sym;
        Env<AttrContext> env1 = env;
        boolean staticOnly = false;
        while (env1.outer != null) {
            if (isStatic(env1)) staticOnly = true;
            sym = findMethod(
                    env1, env1.enclClass.sym.type, name, argtypes, typeargtypes,
                    allowBoxing, useVarargs, false);
            if (sym.exists()) {
                if (staticOnly &&
                        sym.kind == MTH &&
                        sym.owner.kind == TYP &&
                        (sym.flags() & STATIC) == 0) return new StaticError(sym);
                else return sym;
            } else if (sym.kind < bestSoFar.kind) {
                bestSoFar = sym;
            }
            if ((env1.enclClass.sym.flags() & STATIC) != 0) staticOnly = true;
            env1 = env1.outer;
        }
        sym = findMethod(env, syms.predefClass.type, name, argtypes,
                typeargtypes, allowBoxing, useVarargs, false);
        if (sym.exists())
            return sym;
        Scope.Entry e = env.toplevel.namedImportScope.lookup(name);
        for (; e.scope != null; e = e.next()) {
            sym = e.sym;
            Type origin = e.getOrigin().owner.type;
            if (sym.kind == MTH) {
                if (e.sym.owner.type != origin)
                    sym = sym.clone(e.getOrigin().owner);
                if (!isAccessible(env, origin, sym))
                    sym = new AccessError(env, origin, sym);
                bestSoFar = selectBest(env, origin,
                        argtypes, typeargtypes,
                        sym, bestSoFar,
                        allowBoxing, useVarargs, false);
            }
        }
        if (bestSoFar.exists())
            return bestSoFar;
        e = env.toplevel.starImportScope.lookup(name);
        for (; e.scope != null; e = e.next()) {
            sym = e.sym;
            Type origin = e.getOrigin().owner.type;
            if (sym.kind == MTH) {
                if (e.sym.owner.type != origin)
                    sym = sym.clone(e.getOrigin().owner);
                if (!isAccessible(env, origin, sym))
                    sym = new AccessError(env, origin, sym);
                bestSoFar = selectBest(env, origin,
                        argtypes, typeargtypes,
                        sym, bestSoFar,
                        allowBoxing, useVarargs, false);
            }
        }
        return bestSoFar;
    }

    Symbol loadClass(Env<AttrContext> env, Name name) {
        try {
            ClassSymbol c = reader.loadClass(name);
            return isAccessible(env, c) ? c : new AccessError(c);
        } catch (ClassReader.BadClassFile err) {
            throw err;
        } catch (CompletionFailure ex) {
            return typeNotFound;
        }
    }

    Symbol findImmediateMemberType(Env<AttrContext> env,
                                   Type site,
                                   Name name,
                                   TypeSymbol c) {
        Scope.Entry e = c.members().lookup(name);
        while (e.scope != null) {
            if (e.sym.kind == TYP) {
                return isAccessible(env, site, e.sym)
                        ? e.sym
                        : new AccessError(env, site, e.sym);
            }
            e = e.next();
        }
        return typeNotFound;
    }

    Symbol findInheritedMemberType(Env<AttrContext> env,
                                   Type site,
                                   Name name,
                                   TypeSymbol c) {
        Symbol bestSoFar = typeNotFound;
        Symbol sym;
        Type st = types.supertype(c.type);
        if (st != null && st.hasTag(CLASS)) {
            sym = findMemberType(env, site, name, st.tsym);
            if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }
        for (List<Type> l = types.interfaces(c.type);
             bestSoFar.kind != AMBIGUOUS && l.nonEmpty();
             l = l.tail) {
            sym = findMemberType(env, site, name, l.head.tsym);
            if (bestSoFar.kind < AMBIGUOUS && sym.kind < AMBIGUOUS &&
                    sym.owner != bestSoFar.owner)
                bestSoFar = new AmbiguityError(bestSoFar, sym);
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }
        return bestSoFar;
    }

    Symbol findMemberType(Env<AttrContext> env,
                          Type site,
                          Name name,
                          TypeSymbol c) {
        Symbol sym = findImmediateMemberType(env, site, name, c);
        if (sym != typeNotFound)
            return sym;
        return findInheritedMemberType(env, site, name, c);
    }

    Symbol findGlobalType(Env<AttrContext> env, Scope scope, Name name) {
        Symbol bestSoFar = typeNotFound;
        for (Scope.Entry e = scope.lookup(name); e.scope != null; e = e.next()) {
            Symbol sym = loadClass(env, e.sym.flatName());
            if (bestSoFar.kind == TYP && sym.kind == TYP &&
                    bestSoFar != sym)
                return new AmbiguityError(bestSoFar, sym);
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }
        return bestSoFar;
    }

    Symbol findTypeVar(Env<AttrContext> env, Name name, boolean staticOnly) {
        for (Scope.Entry e = env.info.scope.lookup(name);
             e.scope != null;
             e = e.next()) {
            if (e.sym.kind == TYP) {
                if (staticOnly &&
                        e.sym.type.hasTag(TYPEVAR) &&
                        e.sym.owner.kind == TYP)
                    return new StaticError(e.sym);
                return e.sym;
            }
        }
        return typeNotFound;
    }

    Symbol findType(Env<AttrContext> env, Name name) {
        Symbol bestSoFar = typeNotFound;
        Symbol sym;
        boolean staticOnly = false;
        for (Env<AttrContext> env1 = env; env1.outer != null; env1 = env1.outer) {
            if (isStatic(env1)) staticOnly = true;

            final Symbol tyvar = findTypeVar(env1, name, staticOnly);
            sym = findImmediateMemberType(env1, env1.enclClass.sym.type,
                    name, env1.enclClass.sym);


            if (tyvar != typeNotFound) {
                if (sym == typeNotFound ||
                        (tyvar.kind == TYP && tyvar.exists() &&
                                tyvar.owner.kind == MTH))
                    return tyvar;
            }


            if (sym == typeNotFound)
                sym = findInheritedMemberType(env1, env1.enclClass.sym.type,
                        name, env1.enclClass.sym);
            if (staticOnly && sym.kind == TYP &&
                    sym.type.hasTag(CLASS) &&
                    sym.type.getEnclosingType().hasTag(CLASS) &&
                    env1.enclClass.sym.type.isParameterized() &&
                    sym.type.getEnclosingType().isParameterized())
                return new StaticError(sym);
            else if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
            JCClassDecl encl = env1.baseClause ? (JCClassDecl) env1.tree : env1.enclClass;
            if ((encl.sym.flags() & STATIC) != 0)
                staticOnly = true;
        }
        if (!env.tree.hasTag(IMPORT)) {
            sym = findGlobalType(env, env.toplevel.namedImportScope, name);
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
            sym = findGlobalType(env, env.toplevel.packge.members(), name);
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
            sym = findGlobalType(env, env.toplevel.starImportScope, name);
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }
        return bestSoFar;
    }

    Symbol findIdent(Env<AttrContext> env, Name name, int kind) {
        Symbol bestSoFar = typeNotFound;
        Symbol sym;
        if ((kind & VAR) != 0) {
            sym = findVar(env, name);
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }
        if ((kind & TYP) != 0) {
            sym = findType(env, name);
            if (sym.kind == TYP) {
                reportDependence(env.enclClass.sym, sym);
            }
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }
        if ((kind & PCK) != 0) return reader.enterPackage(name);
        else return bestSoFar;
    }

    public void reportDependence(Symbol from, Symbol to) {

    }

    Symbol findIdentInPackage(Env<AttrContext> env, TypeSymbol pck,
                              Name name, int kind) {
        Name fullname = TypeSymbol.formFullName(name, pck);
        Symbol bestSoFar = typeNotFound;
        PackageSymbol pack = null;
        if ((kind & PCK) != 0) {
            pack = reader.enterPackage(fullname);
            if (pack.exists()) return pack;
        }
        if ((kind & TYP) != 0) {
            Symbol sym = loadClass(env, fullname);
            if (sym.exists()) {

                if (name == sym.name) return sym;
            } else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }
        return (pack != null) ? pack : bestSoFar;
    }

    Symbol findIdentInType(Env<AttrContext> env, Type site,
                           Name name, int kind) {
        Symbol bestSoFar = typeNotFound;
        Symbol sym;
        if ((kind & VAR) != 0) {
            sym = findField(env, site, name, site.tsym);
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }
        if ((kind & TYP) != 0) {
            sym = findMemberType(env, site, name, site.tsym);
            if (sym.exists()) return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
        }
        return bestSoFar;
    }

    Symbol accessInternal(Symbol sym,
                          DiagnosticPosition pos,
                          Symbol location,
                          Type site,
                          Name name,
                          boolean qualified,
                          List<Type> argtypes,
                          List<Type> typeargtypes,
                          LogResolveHelper logResolveHelper) {
        if (sym.kind >= AMBIGUOUS) {
            ResolveError errSym = (ResolveError) sym;
            sym = errSym.access(name, qualified ? site.tsym : syms.noSymbol);
            argtypes = logResolveHelper.getArgumentTypes(errSym, sym, name, argtypes);
            if (logResolveHelper.resolveDiagnosticNeeded(site, argtypes, typeargtypes)) {
                logResolveError(errSym, pos, location, site, name, argtypes, typeargtypes);
            }
        }
        return sym;
    }

    Symbol accessMethod(Symbol sym,
                        DiagnosticPosition pos,
                        Symbol location,
                        Type site,
                        Name name,
                        boolean qualified,
                        List<Type> argtypes,
                        List<Type> typeargtypes) {
        return accessInternal(sym, pos, location, site, name, qualified, argtypes, typeargtypes, methodLogResolveHelper);
    }

    Symbol accessMethod(Symbol sym,
                        DiagnosticPosition pos,
                        Type site,
                        Name name,
                        boolean qualified,
                        List<Type> argtypes,
                        List<Type> typeargtypes) {
        return accessMethod(sym, pos, site.tsym, site, name, qualified, argtypes, typeargtypes);
    }

    Symbol accessBase(Symbol sym,
                      DiagnosticPosition pos,
                      Symbol location,
                      Type site,
                      Name name,
                      boolean qualified) {
        return accessInternal(sym, pos, location, site, name, qualified, List.nil(), null, basicLogResolveHelper);
    }

    Symbol accessBase(Symbol sym,
                      DiagnosticPosition pos,
                      Type site,
                      Name name,
                      boolean qualified) {
        return accessBase(sym, pos, site.tsym, site, name, qualified);
    }

    void checkNonAbstract(DiagnosticPosition pos, Symbol sym) {
        if ((sym.flags() & ABSTRACT) != 0 && (sym.flags() & DEFAULT) == 0)
            log.error(pos, "abstract.cant.be.accessed.directly",
                    kindName(sym), sym, sym.location());
    }

    public void printscopes(Scope s) {
        while (s != null) {
            if (s.owner != null)
                System.err.print(s.owner + ": ");
            for (Scope.Entry e = s.elems; e != null; e = e.sibling) {
                if ((e.sym.flags() & ABSTRACT) != 0)
                    System.err.print("abstract ");
                System.err.print(e.sym + " ");
            }
            System.err.println();
            s = s.next;
        }
    }

    void printscopes(Env<AttrContext> env) {
        while (env.outer != null) {
            System.err.println("------------------------------");
            printscopes(env.info.scope);
            env = env.outer;
        }
    }

    public void printscopes(Type t) {
        while (t.hasTag(CLASS)) {
            printscopes(t.tsym.members());
            t = types.supertype(t);
        }
    }

    Symbol resolveIdent(DiagnosticPosition pos, Env<AttrContext> env,
                        Name name, int kind) {
        return accessBase(
                findIdent(env, name, kind),
                pos, env.enclClass.sym.type, name, false);
    }

    Symbol resolveMethod(DiagnosticPosition pos,
                         Env<AttrContext> env,
                         Name name,
                         List<Type> argtypes,
                         List<Type> typeargtypes) {
        return lookupMethod(env, pos, env.enclClass.sym, resolveMethodCheck,
                new BasicLookupHelper(name, env.enclClass.sym.type, argtypes, typeargtypes) {
                    @Override
                    Symbol doLookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                        return findFun(env, name, argtypes, typeargtypes,
                                phase.isBoxingRequired(),
                                phase.isVarargsRequired());
                    }
                });
    }

    Symbol resolveQualifiedMethod(DiagnosticPosition pos, Env<AttrContext> env,
                                  Type site, Name name, List<Type> argtypes,
                                  List<Type> typeargtypes) {
        return resolveQualifiedMethod(pos, env, site.tsym, site, name, argtypes, typeargtypes);
    }

    Symbol resolveQualifiedMethod(DiagnosticPosition pos, Env<AttrContext> env,
                                  Symbol location, Type site, Name name, List<Type> argtypes,
                                  List<Type> typeargtypes) {
        return resolveQualifiedMethod(new MethodResolutionContext(), pos, env, location, site, name, argtypes, typeargtypes);
    }

    private Symbol resolveQualifiedMethod(MethodResolutionContext resolveContext,
                                          DiagnosticPosition pos, Env<AttrContext> env,
                                          Symbol location, Type site, Name name, List<Type> argtypes,
                                          List<Type> typeargtypes) {
        return lookupMethod(env, pos, location, resolveContext, new BasicLookupHelper(name, site, argtypes, typeargtypes) {
            @Override
            Symbol doLookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                return findMethod(env, site, name, argtypes, typeargtypes,
                        phase.isBoxingRequired(),
                        phase.isVarargsRequired(), false);
            }

            @Override
            Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym) {
                if (sym.kind >= AMBIGUOUS) {
                    sym = super.access(env, pos, location, sym);
                } else if (allowMethodHandles) {
                    MethodSymbol msym = (MethodSymbol) sym;
                    if ((msym.flags() & SIGNATURE_POLYMORPHIC) != 0) {
                        return findPolymorphicSignatureInstance(env, sym, argtypes);
                    }
                }
                return sym;
            }
        });
    }

    Symbol findPolymorphicSignatureInstance(Env<AttrContext> env,
                                            final Symbol spMethod,
                                            List<Type> argtypes) {
        Type mtype = infer.instantiatePolymorphicSignatureInstance(env,
                (MethodSymbol) spMethod, currentResolutionContext, argtypes);
        for (Symbol sym : polymorphicSignatureScope.getElementsByName(spMethod.name)) {
            if (types.isSameType(mtype, sym.type)) {
                return sym;
            }
        }

        long flags = ABSTRACT | HYPOTHETICAL | spMethod.flags() & Flags.AccessFlags;
        Symbol msym = new MethodSymbol(flags, spMethod.name, mtype, spMethod.owner) {
            @Override
            public Symbol baseSymbol() {
                return spMethod;
            }
        };
        polymorphicSignatureScope.enter(msym);
        return msym;
    }

    public MethodSymbol resolveInternalMethod(DiagnosticPosition pos, Env<AttrContext> env,
                                              Type site, Name name,
                                              List<Type> argtypes,
                                              List<Type> typeargtypes) {
        MethodResolutionContext resolveContext = new MethodResolutionContext();
        resolveContext.internalResolution = true;
        Symbol sym = resolveQualifiedMethod(resolveContext, pos, env, site.tsym,
                site, name, argtypes, typeargtypes);
        if (sym.kind == MTH) return (MethodSymbol) sym;
        else throw new FatalError(
                diags.fragment("fatal.err.cant.locate.meth",
                        name));
    }

    Symbol resolveConstructor(DiagnosticPosition pos,
                              Env<AttrContext> env,
                              Type site,
                              List<Type> argtypes,
                              List<Type> typeargtypes) {
        return resolveConstructor(new MethodResolutionContext(), pos, env, site, argtypes, typeargtypes);
    }

    private Symbol resolveConstructor(MethodResolutionContext resolveContext,
                                      final DiagnosticPosition pos,
                                      Env<AttrContext> env,
                                      Type site,
                                      List<Type> argtypes,
                                      List<Type> typeargtypes) {
        return lookupMethod(env, pos, site.tsym, resolveContext, new BasicLookupHelper(names.init, site, argtypes, typeargtypes) {
            @Override
            Symbol doLookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                return findConstructor(pos, env, site, argtypes, typeargtypes,
                        phase.isBoxingRequired(),
                        phase.isVarargsRequired());
            }
        });
    }

    public MethodSymbol resolveInternalConstructor(DiagnosticPosition pos, Env<AttrContext> env,
                                                   Type site,
                                                   List<Type> argtypes,
                                                   List<Type> typeargtypes) {
        MethodResolutionContext resolveContext = new MethodResolutionContext();
        resolveContext.internalResolution = true;
        Symbol sym = resolveConstructor(resolveContext, pos, env, site, argtypes, typeargtypes);
        if (sym.kind == MTH) return (MethodSymbol) sym;
        else throw new FatalError(
                diags.fragment("fatal.err.cant.locate.ctor", site));
    }

    Symbol findConstructor(DiagnosticPosition pos, Env<AttrContext> env,
                           Type site, List<Type> argtypes,
                           List<Type> typeargtypes,
                           boolean allowBoxing,
                           boolean useVarargs) {
        Symbol sym = findMethod(env, site,
                names.init, argtypes,
                typeargtypes, allowBoxing,
                useVarargs, false);
        chk.checkDeprecated(pos, env.info.scope.owner, sym);
        return sym;
    }

    Symbol resolveDiamond(DiagnosticPosition pos,
                          Env<AttrContext> env,
                          Type site,
                          List<Type> argtypes,
                          List<Type> typeargtypes) {
        return lookupMethod(env, pos, site.tsym, resolveMethodCheck,
                new BasicLookupHelper(names.init, site, argtypes, typeargtypes) {
                    @Override
                    Symbol doLookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                        return findDiamond(env, site, argtypes, typeargtypes,
                                phase.isBoxingRequired(),
                                phase.isVarargsRequired());
                    }

                    @Override
                    Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym) {
                        if (sym.kind >= AMBIGUOUS) {
                            if (sym.kind != WRONG_MTH && sym.kind != WRONG_MTHS) {
                                sym = super.access(env, pos, location, sym);
                            } else {
                                final JCDiagnostic details = sym.kind == WRONG_MTH ?
                                        ((InapplicableSymbolError) sym).errCandidate().snd :
                                        null;
                                sym = new InapplicableSymbolError(sym.kind, "diamondError", currentResolutionContext) {
                                    @Override
                                    JCDiagnostic getDiagnostic(DiagnosticType dkind, DiagnosticPosition pos,
                                                               Symbol location, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes) {
                                        String key = details == null ?
                                                "cant.apply.diamond" :
                                                "cant.apply.diamond.1";
                                        return diags.create(dkind, log.currentSource(), pos, key,
                                                diags.fragment("diamond", site.tsym), details);
                                    }
                                };
                                sym = accessMethod(sym, pos, site, names.init, true, argtypes, typeargtypes);
                                env.info.pendingResolutionPhase = currentResolutionContext.step;
                            }
                        }
                        return sym;
                    }
                });
    }

    private Symbol findDiamond(Env<AttrContext> env,
                               Type site,
                               List<Type> argtypes,
                               List<Type> typeargtypes,
                               boolean allowBoxing,
                               boolean useVarargs) {
        Symbol bestSoFar = methodNotFound;
        for (Scope.Entry e = site.tsym.members().lookup(names.init);
             e.scope != null;
             e = e.next()) {
            final Symbol sym = e.sym;

            if (sym.kind == MTH &&
                    (sym.flags_field & SYNTHETIC) == 0) {
                List<Type> oldParams = e.sym.type.hasTag(FORALL) ?
                        ((ForAll) sym.type).tvars :
                        List.nil();
                Type constrType = new ForAll(site.tsym.type.getTypeArguments().appendList(oldParams),
                        types.createMethodTypeWithReturn(sym.type.asMethodType(), site));
                MethodSymbol newConstr = new MethodSymbol(sym.flags(), names.init, constrType, site.tsym) {
                    @Override
                    public Symbol baseSymbol() {
                        return sym;
                    }
                };
                bestSoFar = selectBest(env, site, argtypes, typeargtypes,
                        newConstr,
                        bestSoFar,
                        allowBoxing,
                        useVarargs,
                        false);
            }
        }
        return bestSoFar;
    }

    Symbol resolveOperator(DiagnosticPosition pos, Tag optag,
                           Env<AttrContext> env, List<Type> argtypes) {
        MethodResolutionContext prevResolutionContext = currentResolutionContext;
        try {
            currentResolutionContext = new MethodResolutionContext();
            Name name = treeinfo.operatorName(optag);
            return lookupMethod(env, pos, syms.predefClass, currentResolutionContext,
                    new BasicLookupHelper(name, syms.predefClass.type, argtypes, null, BOX) {
                        @Override
                        Symbol doLookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                            return findMethod(env, site, name, argtypes, typeargtypes,
                                    phase.isBoxingRequired(),
                                    phase.isVarargsRequired(), true);
                        }

                        @Override
                        Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym) {
                            return accessMethod(sym, pos, env.enclClass.sym.type, name,
                                    false, argtypes, null);
                        }
                    });
        } finally {
            currentResolutionContext = prevResolutionContext;
        }
    }

    Symbol resolveUnaryOperator(DiagnosticPosition pos, Tag optag, Env<AttrContext> env, Type arg) {
        return resolveOperator(pos, optag, env, List.of(arg));
    }

    Symbol resolveBinaryOperator(DiagnosticPosition pos,
                                 Tag optag,
                                 Env<AttrContext> env,
                                 Type left,
                                 Type right) {
        return resolveOperator(pos, optag, env, List.of(left, right));
    }

    Symbol getMemberReference(DiagnosticPosition pos,
                              Env<AttrContext> env,
                              JCMemberReference referenceTree,
                              Type site,
                              Name name) {
        site = types.capture(site);
        ReferenceLookupHelper lookupHelper = makeReferenceLookupHelper(
                referenceTree, site, name, List.nil(), null, VARARITY);
        Env<AttrContext> newEnv = env.dup(env.tree, env.info.dup());
        Symbol sym = lookupMethod(newEnv, env.tree.pos(), site.tsym,
                nilMethodCheck, lookupHelper);
        env.info.pendingResolutionPhase = newEnv.info.pendingResolutionPhase;
        return sym;
    }

    ReferenceLookupHelper makeReferenceLookupHelper(JCMemberReference referenceTree,
                                                    Type site,
                                                    Name name,
                                                    List<Type> argtypes,
                                                    List<Type> typeargtypes,
                                                    MethodResolutionPhase maxPhase) {
        ReferenceLookupHelper result;
        if (!name.equals(names.init)) {

            result =
                    new MethodReferenceLookupHelper(referenceTree, name, site, argtypes, typeargtypes, maxPhase);
        } else {
            if (site.hasTag(ARRAY)) {

                result =
                        new ArrayConstructorReferenceLookupHelper(referenceTree, site, argtypes, typeargtypes, maxPhase);
            } else {

                result =
                        new ConstructorReferenceLookupHelper(referenceTree, site, argtypes, typeargtypes, maxPhase);
            }
        }
        return result;
    }

    Symbol resolveMemberReferenceByArity(Env<AttrContext> env,
                                         JCMemberReference referenceTree,
                                         Type site,
                                         Name name,
                                         List<Type> argtypes,
                                         InferenceContext inferenceContext) {
        boolean isStaticSelector = TreeInfo.isStaticSelector(referenceTree.expr, names);
        site = types.capture(site);
        ReferenceLookupHelper boundLookupHelper = makeReferenceLookupHelper(
                referenceTree, site, name, argtypes, null, VARARITY);

        Env<AttrContext> boundEnv = env.dup(env.tree, env.info.dup());
        Symbol boundSym = lookupMethod(boundEnv, env.tree.pos(), site.tsym,
                arityMethodCheck, boundLookupHelper);
        if (isStaticSelector &&
                !name.equals(names.init) &&
                !boundSym.isStatic() &&
                boundSym.kind < ERRONEOUS) {
            boundSym = methodNotFound;
        }

        Symbol unboundSym = methodNotFound;
        ReferenceLookupHelper unboundLookupHelper = null;
        Env<AttrContext> unboundEnv = env.dup(env.tree, env.info.dup());
        if (isStaticSelector) {
            unboundLookupHelper = boundLookupHelper.unboundLookup(inferenceContext);
            unboundSym = lookupMethod(unboundEnv, env.tree.pos(), site.tsym,
                    arityMethodCheck, unboundLookupHelper);
            if (unboundSym.isStatic() &&
                    unboundSym.kind < ERRONEOUS) {
                unboundSym = methodNotFound;
            }
        }

        Symbol bestSym = choose(boundSym, unboundSym);
        env.info.pendingResolutionPhase = bestSym == unboundSym ?
                unboundEnv.info.pendingResolutionPhase :
                boundEnv.info.pendingResolutionPhase;
        return bestSym;
    }

    Pair<Symbol, ReferenceLookupHelper> resolveMemberReference(Env<AttrContext> env,
                                                               JCMemberReference referenceTree,
                                                               Type site,
                                                               Name name,
                                                               List<Type> argtypes,
                                                               List<Type> typeargtypes,
                                                               MethodCheck methodCheck,
                                                               InferenceContext inferenceContext,
                                                               AttrMode mode) {
        site = types.capture(site);
        ReferenceLookupHelper boundLookupHelper = makeReferenceLookupHelper(
                referenceTree, site, name, argtypes, typeargtypes, VARARITY);

        Env<AttrContext> boundEnv = env.dup(env.tree, env.info.dup());
        Symbol origBoundSym;
        boolean staticErrorForBound = false;
        MethodResolutionContext boundSearchResolveContext = new MethodResolutionContext();
        boundSearchResolveContext.methodCheck = methodCheck;
        Symbol boundSym = origBoundSym = lookupMethod(boundEnv, env.tree.pos(),
                site.tsym, boundSearchResolveContext, boundLookupHelper);
        SearchResultKind boundSearchResultKind = SearchResultKind.NOT_APPLICABLE_MATCH;
        boolean isStaticSelector = TreeInfo.isStaticSelector(referenceTree.expr, names);
        boolean shouldCheckForStaticness = isStaticSelector &&
                referenceTree.getMode() == ReferenceMode.INVOKE;
        if (boundSym.kind != WRONG_MTHS && boundSym.kind != WRONG_MTH) {
            if (shouldCheckForStaticness) {
                if (!boundSym.isStatic()) {
                    staticErrorForBound = true;
                    if (hasAnotherApplicableMethod(
                            boundSearchResolveContext, boundSym, true)) {
                        boundSearchResultKind = SearchResultKind.BAD_MATCH_MORE_SPECIFIC;
                    } else {
                        boundSearchResultKind = SearchResultKind.BAD_MATCH;
                        if (boundSym.kind < ERRONEOUS) {
                            boundSym = methodWithCorrectStaticnessNotFound;
                        }
                    }
                } else if (boundSym.kind < ERRONEOUS) {
                    boundSearchResultKind = SearchResultKind.GOOD_MATCH;
                }
            }
        }

        Symbol origUnboundSym = null;
        Symbol unboundSym = methodNotFound;
        ReferenceLookupHelper unboundLookupHelper = null;
        Env<AttrContext> unboundEnv = env.dup(env.tree, env.info.dup());
        SearchResultKind unboundSearchResultKind = SearchResultKind.NOT_APPLICABLE_MATCH;
        boolean staticErrorForUnbound = false;
        if (isStaticSelector) {
            unboundLookupHelper = boundLookupHelper.unboundLookup(inferenceContext);
            MethodResolutionContext unboundSearchResolveContext =
                    new MethodResolutionContext();
            unboundSearchResolveContext.methodCheck = methodCheck;
            unboundSym = origUnboundSym = lookupMethod(unboundEnv, env.tree.pos(),
                    site.tsym, unboundSearchResolveContext, unboundLookupHelper);
            if (unboundSym.kind != WRONG_MTH && unboundSym.kind != WRONG_MTHS) {
                if (shouldCheckForStaticness) {
                    if (unboundSym.isStatic()) {
                        staticErrorForUnbound = true;
                        if (hasAnotherApplicableMethod(
                                unboundSearchResolveContext, unboundSym, false)) {
                            unboundSearchResultKind = SearchResultKind.BAD_MATCH_MORE_SPECIFIC;
                        } else {
                            unboundSearchResultKind = SearchResultKind.BAD_MATCH;
                            if (unboundSym.kind < ERRONEOUS) {
                                unboundSym = methodWithCorrectStaticnessNotFound;
                            }
                        }
                    } else if (unboundSym.kind < ERRONEOUS) {
                        unboundSearchResultKind = SearchResultKind.GOOD_MATCH;
                    }
                }
            }
        }

        Pair<Symbol, ReferenceLookupHelper> res;
        Symbol bestSym = choose(boundSym, unboundSym);
        if (bestSym.kind < ERRONEOUS && (staticErrorForBound || staticErrorForUnbound)) {
            if (staticErrorForBound) {
                boundSym = methodWithCorrectStaticnessNotFound;
            }
            if (staticErrorForUnbound) {
                unboundSym = methodWithCorrectStaticnessNotFound;
            }
            bestSym = choose(boundSym, unboundSym);
        }
        if (bestSym == methodWithCorrectStaticnessNotFound && mode == AttrMode.CHECK) {
            Symbol symToPrint = origBoundSym;
            String errorFragmentToPrint = "non-static.cant.be.ref";
            if (staticErrorForBound && staticErrorForUnbound) {
                if (unboundSearchResultKind == SearchResultKind.BAD_MATCH_MORE_SPECIFIC) {
                    symToPrint = origUnboundSym;
                    errorFragmentToPrint = "static.method.in.unbound.lookup";
                }
            } else {
                if (!staticErrorForBound) {
                    symToPrint = origUnboundSym;
                    errorFragmentToPrint = "static.method.in.unbound.lookup";
                }
            }
            log.error(referenceTree.expr.pos(), "invalid.mref",
                    Kinds.kindName(referenceTree.getMode()),
                    diags.fragment(errorFragmentToPrint,
                            Kinds.kindName(symToPrint), symToPrint));
        }
        res = new Pair<>(bestSym,
                bestSym == unboundSym ? unboundLookupHelper : boundLookupHelper);
        env.info.pendingResolutionPhase = bestSym == unboundSym ?
                unboundEnv.info.pendingResolutionPhase :
                boundEnv.info.pendingResolutionPhase;
        return res;
    }

    boolean hasAnotherApplicableMethod(MethodResolutionContext resolutionContext,
                                       Symbol bestSoFar, boolean staticMth) {
        for (Candidate c : resolutionContext.candidates) {
            if (resolutionContext.step != c.step ||
                    !c.isApplicable() ||
                    c.sym == bestSoFar) {
                continue;
            } else {
                if (c.sym.isStatic() == staticMth) {
                    return true;
                }
            }
        }
        return false;
    }

    private Symbol choose(Symbol boundSym, Symbol unboundSym) {
        if (lookupSuccess(boundSym) && lookupSuccess(unboundSym)) {
            return ambiguityError(boundSym, unboundSym);
        } else if (lookupSuccess(boundSym) ||
                (canIgnore(unboundSym) && !canIgnore(boundSym))) {
            return boundSym;
        } else if (lookupSuccess(unboundSym) ||
                (canIgnore(boundSym) && !canIgnore(unboundSym))) {
            return unboundSym;
        } else {
            return boundSym;
        }
    }

    private boolean lookupSuccess(Symbol s) {
        return s.kind == MTH || s.kind == AMBIGUOUS;
    }

    private boolean canIgnore(Symbol s) {
        switch (s.kind) {
            case ABSENT_MTH:
                return true;
            case WRONG_MTH:
                InapplicableSymbolError errSym =
                        (InapplicableSymbolError) s;
                return new Template(MethodCheckDiag.ARITY_MISMATCH.regex())
                        .matches(errSym.errCandidate().snd);
            case WRONG_MTHS:
                InapplicableSymbolsError errSyms =
                        (InapplicableSymbolsError) s;
                return errSyms.filterCandidates(errSyms.mapCandidates()).isEmpty();
            case WRONG_STATICNESS:
                return false;
            default:
                return false;
        }
    }

    Symbol lookupMethod(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, MethodCheck methodCheck, LookupHelper lookupHelper) {
        MethodResolutionContext resolveContext = new MethodResolutionContext();
        resolveContext.methodCheck = methodCheck;
        return lookupMethod(env, pos, location, resolveContext, lookupHelper);
    }

    Symbol lookupMethod(Env<AttrContext> env, DiagnosticPosition pos, Symbol location,
                        MethodResolutionContext resolveContext, LookupHelper lookupHelper) {
        MethodResolutionContext prevResolutionContext = currentResolutionContext;
        try {
            Symbol bestSoFar = methodNotFound;
            currentResolutionContext = resolveContext;
            for (MethodResolutionPhase phase : methodResolutionSteps) {
                if (!phase.isApplicable(boxingEnabled, varargsEnabled) ||
                        lookupHelper.shouldStop(bestSoFar, phase)) break;
                MethodResolutionPhase prevPhase = currentResolutionContext.step;
                Symbol prevBest = bestSoFar;
                currentResolutionContext.step = phase;
                Symbol sym = lookupHelper.lookup(env, phase);
                lookupHelper.debug(pos, sym);
                bestSoFar = phase.mergeResults(bestSoFar, sym);
                env.info.pendingResolutionPhase = (prevBest == bestSoFar) ? prevPhase : phase;
            }
            return lookupHelper.access(env, pos, location, bestSoFar);
        } finally {
            currentResolutionContext = prevResolutionContext;
        }
    }

    Symbol resolveSelf(DiagnosticPosition pos,
                       Env<AttrContext> env,
                       TypeSymbol c,
                       Name name) {
        Env<AttrContext> env1 = env;
        boolean staticOnly = false;
        while (env1.outer != null) {
            if (isStatic(env1)) staticOnly = true;
            if (env1.enclClass.sym == c) {
                Symbol sym = env1.info.scope.lookup(name).sym;
                if (sym != null) {
                    if (staticOnly) sym = new StaticError(sym);
                    return accessBase(sym, pos, env.enclClass.sym.type,
                            name, true);
                }
            }
            if ((env1.enclClass.sym.flags() & STATIC) != 0) staticOnly = true;
            env1 = env1.outer;
        }
        if (allowDefaultMethods && c.isInterface() &&
                name == names._super && !isStatic(env) &&
                types.isDirectSuperInterface(c, env.enclClass.sym)) {

            for (Type t : pruneInterfaces(env.enclClass.type)) {
                if (t.tsym == c) {
                    env.info.defaultSuperCallSite = t;
                    return new VarSymbol(0, names._super,
                            types.asSuper(env.enclClass.type, c), env.enclClass.sym);
                }
            }

            for (Type i : types.interfaces(env.enclClass.type)) {
                if (i.tsym.isSubClass(c, types) && i.tsym != c) {
                    log.error(pos, "illegal.default.super.call", c,
                            diags.fragment("redundant.supertype", c, i));
                    return syms.errSymbol;
                }
            }
            Assert.error();
        }
        log.error(pos, "not.encl.class", c);
        return syms.errSymbol;
    }

    private List<Type> pruneInterfaces(Type t) {
        ListBuffer<Type> result = new ListBuffer<>();
        for (Type t1 : types.interfaces(t)) {
            boolean shouldAdd = true;
            for (Type t2 : types.interfaces(t)) {
                if (t1 != t2 && types.isSubtypeNoCapture(t2, t1)) {
                    shouldAdd = false;
                }
            }
            if (shouldAdd) {
                result.append(t1);
            }
        }
        return result.toList();
    }

    Symbol resolveSelfContaining(DiagnosticPosition pos,
                                 Env<AttrContext> env,
                                 Symbol member,
                                 boolean isSuperCall) {
        Symbol sym = resolveSelfContainingInternal(env, member, isSuperCall);
        if (sym == null) {
            log.error(pos, "encl.class.required", member);
            return syms.errSymbol;
        } else {
            return accessBase(sym, pos, env.enclClass.sym.type, sym.name, true);
        }
    }

    boolean hasEnclosingInstance(Env<AttrContext> env, Type type) {
        Symbol encl = resolveSelfContainingInternal(env, type.tsym, false);
        return encl != null && encl.kind < ERRONEOUS;
    }

    private Symbol resolveSelfContainingInternal(Env<AttrContext> env,
                                                 Symbol member,
                                                 boolean isSuperCall) {
        Name name = names._this;
        Env<AttrContext> env1 = isSuperCall ? env.outer : env;
        boolean staticOnly = false;
        if (env1 != null) {
            while (env1 != null && env1.outer != null) {
                if (isStatic(env1)) staticOnly = true;
                if (env1.enclClass.sym.isSubClass(member.owner, types)) {
                    Symbol sym = env1.info.scope.lookup(name).sym;
                    if (sym != null) {
                        if (staticOnly) sym = new StaticError(sym);
                        return sym;
                    }
                }
                if ((env1.enclClass.sym.flags() & STATIC) != 0)
                    staticOnly = true;
                env1 = env1.outer;
            }
        }
        return null;
    }

    Type resolveImplicitThis(DiagnosticPosition pos, Env<AttrContext> env, Type t) {
        return resolveImplicitThis(pos, env, t, false);
    }

    Type resolveImplicitThis(DiagnosticPosition pos, Env<AttrContext> env, Type t, boolean isSuperCall) {
        Type thisType = (((t.tsym.owner.kind & (MTH | VAR)) != 0)
                ? resolveSelf(pos, env, t.getEnclosingType().tsym, names._this)
                : resolveSelfContaining(pos, env, t.tsym, isSuperCall)).type;
        if (env.info.isSelfCall && thisType.tsym == env.enclClass.sym)
            log.error(pos, "cant.ref.before.ctor.called", "this");
        return thisType;
    }

    public void logAccessErrorInternal(Env<AttrContext> env, JCTree tree, Type type) {
        AccessError error = new AccessError(env, env.enclClass.type, type.tsym);
        logResolveError(error, tree.pos(), env.enclClass.sym, env.enclClass.type, null, null, null);
    }

    private void logResolveError(ResolveError error,
                                 DiagnosticPosition pos,
                                 Symbol location,
                                 Type site,
                                 Name name,
                                 List<Type> argtypes,
                                 List<Type> typeargtypes) {
        JCDiagnostic d = error.getDiagnostic(DiagnosticType.ERROR,
                pos, location, site, name, argtypes, typeargtypes);
        if (d != null) {
            d.setFlag(DiagnosticFlag.RESOLVE_ERROR);
            log.report(d);
        }
    }

    public Object methodArguments(List<Type> argtypes) {
        if (argtypes == null || argtypes.isEmpty()) {
            return noArgs;
        } else {
            ListBuffer<Object> diagArgs = new ListBuffer<>();
            for (Type t : argtypes) {
                if (t.hasTag(DEFERRED)) {
                    diagArgs.append(((DeferredType) t).tree);
                } else {
                    diagArgs.append(t);
                }
            }
            return diagArgs;
        }
    }

    enum VerboseResolutionMode {
        SUCCESS("success"),
        FAILURE("failure"),
        APPLICABLE("applicable"),
        INAPPLICABLE("inapplicable"),
        DEFERRED_INST("deferred-inference"),
        PREDEF("predef"),
        OBJECT_INIT("object-init"),
        INTERNAL("internal");
        final String opt;

        VerboseResolutionMode(String opt) {
            this.opt = opt;
        }

        static EnumSet<VerboseResolutionMode> getVerboseResolutionMode(Options opts) {
            String s = opts.get("verboseResolution");
            EnumSet<VerboseResolutionMode> res = EnumSet.noneOf(VerboseResolutionMode.class);
            if (s == null) return res;
            if (s.contains("all")) {
                res = EnumSet.allOf(VerboseResolutionMode.class);
            }
            Collection<String> args = Arrays.asList(s.split(","));
            for (VerboseResolutionMode mode : values()) {
                if (args.contains(mode.opt)) {
                    res.add(mode);
                } else if (args.contains("-" + mode.opt)) {
                    res.remove(mode);
                }
            }
            return res;
        }
    }

    enum MethodCheckDiag {

        ARITY_MISMATCH("arg.length.mismatch", "infer.arg.length.mismatch"),

        ARG_MISMATCH("no.conforming.assignment.exists", "infer.no.conforming.assignment.exists"),

        VARARG_MISMATCH("varargs.argument.mismatch", "infer.varargs.argument.mismatch"),

        INACCESSIBLE_VARARGS("inaccessible.varargs.type", "inaccessible.varargs.type");
        final String basicKey;
        final String inferKey;

        MethodCheckDiag(String basicKey, String inferKey) {
            this.basicKey = basicKey;
            this.inferKey = inferKey;
        }

        String regex() {
            return String.format("([a-z]*\\.)*(%s|%s)", basicKey, inferKey);
        }
    }

    enum InterfaceLookupPhase {
        ABSTRACT_OK() {
            @Override
            InterfaceLookupPhase update(Symbol s, Resolve rs) {


                if ((s.flags() & (ABSTRACT | INTERFACE | ENUM)) != 0) {
                    return this;
                } else if (rs.allowDefaultMethods) {
                    return DEFAULT_OK;
                } else {
                    return null;
                }
            }
        },
        DEFAULT_OK() {
            @Override
            InterfaceLookupPhase update(Symbol s, Resolve rs) {
                return this;
            }
        };

        abstract InterfaceLookupPhase update(Symbol s, Resolve rs);
    }

    enum SearchResultKind {
        GOOD_MATCH,
        BAD_MATCH_MORE_SPECIFIC,
        BAD_MATCH,
        NOT_APPLICABLE_MATCH
    }

    enum MethodResolutionPhase {
        BASIC(false, false),
        BOX(true, false),
        VARARITY(true, true) {
            @Override
            public Symbol mergeResults(Symbol bestSoFar, Symbol sym) {
                switch (sym.kind) {
                    case WRONG_MTH:
                        return (bestSoFar.kind == WRONG_MTH || bestSoFar.kind == WRONG_MTHS) ?
                                bestSoFar :
                                sym;
                    case ABSENT_MTH:
                        return bestSoFar;
                    default:
                        return sym;
                }
            }
        };
        final boolean isBoxingRequired;
        final boolean isVarargsRequired;

        MethodResolutionPhase(boolean isBoxingRequired, boolean isVarargsRequired) {
            this.isBoxingRequired = isBoxingRequired;
            this.isVarargsRequired = isVarargsRequired;
        }

        public boolean isBoxingRequired() {
            return isBoxingRequired;
        }

        public boolean isVarargsRequired() {
            return isVarargsRequired;
        }

        public boolean isApplicable(boolean boxingEnabled, boolean varargsEnabled) {
            return (varargsEnabled || !isVarargsRequired) &&
                    (boxingEnabled || !isBoxingRequired);
        }

        public Symbol mergeResults(Symbol prev, Symbol sym) {
            return sym;
        }
    }

    interface MethodCheck {

        void argumentsAcceptable(Env<AttrContext> env,
                                 DeferredAttrContext deferredAttrContext,
                                 List<Type> argtypes,
                                 List<Type> formals,
                                 Warner warn);

        MethodCheck mostSpecificCheck(List<Type> actuals, boolean strict);
    }

    interface LogResolveHelper {
        boolean resolveDiagnosticNeeded(Type site, List<Type> argtypes, List<Type> typeargtypes);

        List<Type> getArgumentTypes(ResolveError errSym, Symbol accessedSym, Name name, List<Type> argtypes);
    }

    public static class InapplicableMethodException extends RuntimeException {
        private static final long serialVersionUID = 0;
        JCDiagnostic diagnostic;
        JCDiagnostic.Factory diags;

        InapplicableMethodException(JCDiagnostic.Factory diags) {
            this.diagnostic = null;
            this.diags = diags;
        }

        InapplicableMethodException setMessage() {
            return setMessage((JCDiagnostic) null);
        }

        InapplicableMethodException setMessage(String key) {
            return setMessage(key != null ? diags.fragment(key) : null);
        }

        InapplicableMethodException setMessage(String key, Object... args) {
            return setMessage(key != null ? diags.fragment(key, args) : null);
        }

        InapplicableMethodException setMessage(JCDiagnostic diag) {
            this.diagnostic = diag;
            return this;
        }

        public JCDiagnostic getDiagnostic() {
            return diagnostic;
        }
    }

    static class MethodResolutionDiagHelper {

        static final Template skip = new Template("") {
            @Override
            boolean matches(Object d) {
                return true;
            }
        };
        static final Map<Template, DiagnosticRewriter> rewriters =
                new LinkedHashMap<Template, DiagnosticRewriter>();

        static {
            String argMismatchRegex = MethodCheckDiag.ARG_MISMATCH.regex();
            rewriters.put(new Template(argMismatchRegex, skip),
                    new DiagnosticRewriter() {
                        @Override
                        public JCDiagnostic rewriteDiagnostic(JCDiagnostic.Factory diags,
                                                              DiagnosticPosition preferedPos, DiagnosticSource preferredSource,
                                                              DiagnosticType preferredKind, JCDiagnostic d) {
                            JCDiagnostic cause = (JCDiagnostic) d.getArgs()[0];
                            return diags.create(preferredKind, preferredSource, d.getDiagnosticPosition(),
                                    "prob.found.req", cause);
                        }
                    });
        }

        interface DiagnosticRewriter {
            JCDiagnostic rewriteDiagnostic(JCDiagnostic.Factory diags,
                                           DiagnosticPosition preferedPos, DiagnosticSource preferredSource,
                                           DiagnosticType preferredKind, JCDiagnostic d);
        }

        static class Template {

            String regex;

            Template[] subTemplates;

            Template(String key, Template... subTemplates) {
                this.regex = key;
                this.subTemplates = subTemplates;
            }

            boolean matches(Object o) {
                JCDiagnostic d = (JCDiagnostic) o;
                Object[] args = d.getArgs();
                if (!d.getCode().matches(regex) ||
                        subTemplates.length != d.getArgs().length) {
                    return false;
                }
                for (int i = 0; i < args.length; i++) {
                    if (!subTemplates[i].matches(args[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
    }

    abstract class AbstractMethodCheck implements MethodCheck {
        @Override
        public void argumentsAcceptable(final Env<AttrContext> env,
                                        DeferredAttrContext deferredAttrContext,
                                        List<Type> argtypes,
                                        List<Type> formals,
                                        Warner warn) {

            boolean useVarargs = deferredAttrContext.phase.isVarargsRequired();
            List<JCExpression> trees = TreeInfo.args(env.tree);

            InferenceContext inferenceContext = deferredAttrContext.inferenceContext;
            Type varargsFormal = useVarargs ? formals.last() : null;
            if (varargsFormal == null &&
                    argtypes.size() != formals.size()) {
                reportMC(env.tree, MethodCheckDiag.ARITY_MISMATCH, inferenceContext);
            }
            while (argtypes.nonEmpty() && formals.head != varargsFormal) {
                DiagnosticPosition pos = trees != null ? trees.head : null;
                checkArg(pos, false, argtypes.head, formals.head, deferredAttrContext, warn);
                argtypes = argtypes.tail;
                formals = formals.tail;
                trees = trees != null ? trees.tail : trees;
            }
            if (formals.head != varargsFormal) {
                reportMC(env.tree, MethodCheckDiag.ARITY_MISMATCH, inferenceContext);
            }
            if (useVarargs) {


                final Type elt = types.elemtype(varargsFormal);
                while (argtypes.nonEmpty()) {
                    DiagnosticPosition pos = trees != null ? trees.head : null;
                    checkArg(pos, true, argtypes.head, elt, deferredAttrContext, warn);
                    argtypes = argtypes.tail;
                    trees = trees != null ? trees.tail : trees;
                }
            }
        }

        abstract void checkArg(DiagnosticPosition pos, boolean varargs, Type actual, Type formal, DeferredAttrContext deferredAttrContext, Warner warn);

        protected void reportMC(DiagnosticPosition pos, MethodCheckDiag diag, InferenceContext inferenceContext, Object... args) {
            boolean inferDiag = inferenceContext != infer.emptyContext;
            InapplicableMethodException ex = inferDiag ?
                    infer.inferenceException : inapplicableMethodException;
            if (inferDiag && (!diag.inferKey.equals(diag.basicKey))) {
                Object[] args2 = new Object[args.length + 1];
                System.arraycopy(args, 0, args2, 1, args.length);
                args2[0] = inferenceContext.inferenceVars();
                args = args2;
            }
            String key = inferDiag ? diag.inferKey : diag.basicKey;
            throw ex.setMessage(diags.create(DiagnosticType.FRAGMENT, log.currentSource(), pos, key, args));
        }

        public MethodCheck mostSpecificCheck(List<Type> actuals, boolean strict) {
            return nilMethodCheck;
        }
    }

    class MethodReferenceCheck extends AbstractMethodCheck {
        InferenceContext pendingInferenceContext;

        MethodReferenceCheck(InferenceContext pendingInferenceContext) {
            this.pendingInferenceContext = pendingInferenceContext;
        }

        @Override
        void checkArg(DiagnosticPosition pos, boolean varargs, Type actual, Type formal, DeferredAttrContext deferredAttrContext, Warner warn) {
            ResultInfo mresult = methodCheckResult(varargs, formal, deferredAttrContext, warn);
            mresult.check(pos, actual);
        }

        private ResultInfo methodCheckResult(final boolean varargsCheck, Type to,
                                             final DeferredAttrContext deferredAttrContext, Warner rsWarner) {
            CheckContext checkContext = new MethodCheckContext(!deferredAttrContext.phase.isBoxingRequired(), deferredAttrContext, rsWarner) {
                MethodCheckDiag methodDiag = varargsCheck ?
                        MethodCheckDiag.VARARG_MISMATCH : MethodCheckDiag.ARG_MISMATCH;

                @Override
                public boolean compatible(Type found, Type req, Warner warn) {
                    found = pendingInferenceContext.asFree(found);
                    req = infer.returnConstraintTarget(found, req);
                    return super.compatible(found, req, warn);
                }

                @Override
                public void report(DiagnosticPosition pos, JCDiagnostic details) {
                    reportMC(pos, methodDiag, deferredAttrContext.inferenceContext, details);
                }
            };
            return new MethodResultInfo(to, checkContext);
        }

        @Override
        public MethodCheck mostSpecificCheck(List<Type> actuals, boolean strict) {
            return new MostSpecificCheck(strict, actuals);
        }
    }

    abstract class MethodCheckContext implements CheckContext {
        boolean strict;
        DeferredAttrContext deferredAttrContext;
        Warner rsWarner;

        public MethodCheckContext(boolean strict, DeferredAttrContext deferredAttrContext, Warner rsWarner) {
            this.strict = strict;
            this.deferredAttrContext = deferredAttrContext;
            this.rsWarner = rsWarner;
        }

        public boolean compatible(Type found, Type req, Warner warn) {
            return strict ?
                    types.isSubtypeUnchecked(found, deferredAttrContext.inferenceContext.asFree(req), warn) :
                    types.isConvertible(found, deferredAttrContext.inferenceContext.asFree(req), warn);
        }

        public void report(DiagnosticPosition pos, JCDiagnostic details) {
            throw inapplicableMethodException.setMessage(details);
        }

        public Warner checkWarner(DiagnosticPosition pos, Type found, Type req) {
            return rsWarner;
        }

        public InferenceContext inferenceContext() {
            return deferredAttrContext.inferenceContext;
        }

        public DeferredAttrContext deferredAttrContext() {
            return deferredAttrContext;
        }
    }

    class MethodResultInfo extends ResultInfo {
        public MethodResultInfo(Type pt, CheckContext checkContext) {
            attr.super(VAL, pt, checkContext);
        }

        @Override
        protected Type check(DiagnosticPosition pos, Type found) {
            if (found.hasTag(DEFERRED)) {
                DeferredType dt = (DeferredType) found;
                return dt.check(this);
            } else {
                return super.check(pos, chk.checkNonVoid(pos, types.capture(U(found.baseType()))));
            }
        }

        private Type U(Type found) {
            return found == pt ?
                    found : types.upperBound(found);
        }

        @Override
        protected MethodResultInfo dup(Type newPt) {
            return new MethodResultInfo(newPt, checkContext);
        }

        @Override
        protected ResultInfo dup(CheckContext newContext) {
            return new MethodResultInfo(pt, newContext);
        }
    }

    class MostSpecificCheck implements MethodCheck {
        boolean strict;
        List<Type> actuals;

        MostSpecificCheck(boolean strict, List<Type> actuals) {
            this.strict = strict;
            this.actuals = actuals;
        }

        @Override
        public void argumentsAcceptable(final Env<AttrContext> env,
                                        DeferredAttrContext deferredAttrContext,
                                        List<Type> formals1,
                                        List<Type> formals2,
                                        Warner warn) {
            formals2 = adjustArgs(formals2, deferredAttrContext.msym, formals1.length(), deferredAttrContext.phase.isVarargsRequired());
            while (formals2.nonEmpty()) {
                ResultInfo mresult = methodCheckResult(formals2.head, deferredAttrContext, warn, actuals.head);
                mresult.check(null, formals1.head);
                formals1 = formals1.tail;
                formals2 = formals2.tail;
                actuals = actuals.isEmpty() ? actuals : actuals.tail;
            }
        }

        ResultInfo methodCheckResult(Type to, DeferredAttrContext deferredAttrContext,
                                     Warner rsWarner, Type actual) {
            return attr.new ResultInfo(Kinds.VAL, to,
                    new MostSpecificCheckContext(strict, deferredAttrContext, rsWarner, actual));
        }

        public MethodCheck mostSpecificCheck(List<Type> actuals, boolean strict) {
            Assert.error("Cannot get here!");
            return null;
        }

        class MostSpecificCheckContext extends MethodCheckContext {
            Type actual;

            public MostSpecificCheckContext(boolean strict, DeferredAttrContext deferredAttrContext, Warner rsWarner, Type actual) {
                super(strict, deferredAttrContext, rsWarner);
                this.actual = actual;
            }

            public boolean compatible(Type found, Type req, Warner warn) {
                if (!allowStructuralMostSpecific || actual == null) {
                    return super.compatible(found, req, warn);
                } else {
                    switch (actual.getTag()) {
                        case DEFERRED:
                            DeferredType dt = (DeferredType) actual;
                            DeferredType.SpeculativeCache.Entry e = dt.speculativeCache.get(deferredAttrContext.msym, deferredAttrContext.phase);
                            return (e == null || e.speculativeTree == deferredAttr.stuckTree)
                                    ? super.compatible(found, req, warn) :
                                    mostSpecific(found, req, e.speculativeTree, warn);
                        default:
                            return standaloneMostSpecific(found, req, actual, warn);
                    }
                }
            }

            private boolean mostSpecific(Type t, Type s, JCTree tree, Warner warn) {
                MostSpecificChecker msc = new MostSpecificChecker(t, s, warn);
                msc.scan(tree);
                return msc.result;
            }

            boolean polyMostSpecific(Type t1, Type t2, Warner warn) {
                return (!t1.isPrimitive() && t2.isPrimitive()) || super.compatible(t1, t2, warn);
            }

            boolean standaloneMostSpecific(Type t1, Type t2, Type exprType, Warner warn) {
                return (exprType.isPrimitive() == t1.isPrimitive()
                        && exprType.isPrimitive() != t2.isPrimitive()) || super.compatible(t1, t2, warn);
            }

            class MostSpecificChecker extends DeferredAttr.PolyScanner {
                final Type t;
                final Type s;
                final Warner warn;
                boolean result;

                MostSpecificChecker(Type t, Type s, Warner warn) {
                    this.t = t;
                    this.s = s;
                    this.warn = warn;
                    result = true;
                }

                @Override
                void skip(JCTree tree) {
                    result &= standaloneMostSpecific(t, s, tree.type, warn);
                }

                @Override
                public void visitConditional(JCConditional tree) {
                    if (tree.polyKind == PolyKind.STANDALONE) {
                        result &= standaloneMostSpecific(t, s, tree.type, warn);
                    } else {
                        super.visitConditional(tree);
                    }
                }

                @Override
                public void visitApply(JCMethodInvocation tree) {
                    result &= (tree.polyKind == PolyKind.STANDALONE)
                            ? standaloneMostSpecific(t, s, tree.type, warn)
                            : polyMostSpecific(t, s, warn);
                }

                @Override
                public void visitNewClass(JCNewClass tree) {
                    result &= (tree.polyKind == PolyKind.STANDALONE)
                            ? standaloneMostSpecific(t, s, tree.type, warn)
                            : polyMostSpecific(t, s, warn);
                }

                @Override
                public void visitReference(JCMemberReference tree) {
                    if (types.isFunctionalInterface(t.tsym) &&
                            types.isFunctionalInterface(s.tsym)) {
                        Type desc_t = types.findDescriptorType(t);
                        Type desc_s = types.findDescriptorType(s);
                        if (types.isSameTypes(desc_t.getParameterTypes(),
                                inferenceContext().asFree(desc_s.getParameterTypes()))) {
                            if (types.asSuper(t, s.tsym) != null ||
                                    types.asSuper(s, t.tsym) != null) {
                                result &= MostSpecificCheckContext.super.compatible(t, s, warn);
                            } else if (!desc_s.getReturnType().hasTag(VOID)) {

                                Type ret_t = desc_t.getReturnType();
                                Type ret_s = desc_s.getReturnType();
                                result &= ((tree.refPolyKind == PolyKind.STANDALONE)
                                        ? standaloneMostSpecific(ret_t, ret_s, tree.sym.type.getReturnType(), warn)
                                        : polyMostSpecific(ret_t, ret_s, warn));
                            } else {
                                return;
                            }
                        }
                    } else {
                        result &= false;
                    }
                }

                @Override
                public void visitLambda(JCLambda tree) {
                    if (types.isFunctionalInterface(t.tsym) &&
                            types.isFunctionalInterface(s.tsym)) {
                        Type desc_t = types.findDescriptorType(t);
                        Type desc_s = types.findDescriptorType(s);
                        if (types.isSameTypes(desc_t.getParameterTypes(),
                                inferenceContext().asFree(desc_s.getParameterTypes()))) {
                            if (types.asSuper(t, s.tsym) != null ||
                                    types.asSuper(s, t.tsym) != null) {
                                result &= MostSpecificCheckContext.super.compatible(t, s, warn);
                            } else if (!desc_s.getReturnType().hasTag(VOID)) {

                                Type ret_t = desc_t.getReturnType();
                                Type ret_s = desc_s.getReturnType();
                                scanLambdaBody(tree, ret_t, ret_s);
                            } else {
                                return;
                            }
                        }
                    } else {
                        result &= false;
                    }
                }

                void scanLambdaBody(JCLambda lambda, final Type t, final Type s) {
                    if (lambda.getBodyKind() == JCLambda.BodyKind.EXPRESSION) {
                        result &= MostSpecificCheckContext.this.mostSpecific(t, s, lambda.body, warn);
                    } else {
                        DeferredAttr.LambdaReturnScanner lambdaScanner =
                                new DeferredAttr.LambdaReturnScanner() {
                                    @Override
                                    public void visitReturn(JCReturn tree) {
                                        if (tree.expr != null) {
                                            result &= MostSpecificCheckContext.this.mostSpecific(t, s, tree.expr, warn);
                                        }
                                    }
                                };
                        lambdaScanner.scan(lambda.body);
                    }
                }
            }
        }
    }

    class LookupFilter implements Filter<Symbol> {
        boolean abstractOk;

        LookupFilter(boolean abstractOk) {
            this.abstractOk = abstractOk;
        }

        public boolean accepts(Symbol s) {
            long flags = s.flags();
            return s.kind == MTH &&
                    (flags & SYNTHETIC) == 0 &&
                    (abstractOk ||
                            (flags & DEFAULT) != 0 ||
                            (flags & ABSTRACT) == 0);
        }
    }

    class ResolveDeferredRecoveryMap extends DeferredAttr.RecoveryDeferredTypeMap {
        public ResolveDeferredRecoveryMap(AttrMode mode, Symbol msym, MethodResolutionPhase step) {
            deferredAttr.super(mode, msym, step);
        }

        @Override
        protected Type typeOf(DeferredType dt) {
            Type res = super.typeOf(dt);
            if (!res.isErroneous()) {
                switch (TreeInfo.skipParens(dt.tree).getTag()) {
                    case LAMBDA:
                    case REFERENCE:
                        return dt;
                    case CONDEXPR:
                        return res == Type.recoveryType ?
                                dt : res;
                }
            }
            return res;
        }
    }

    abstract class LookupHelper {

        Name name;

        Type site;

        List<Type> argtypes;

        List<Type> typeargtypes;

        MethodResolutionPhase maxPhase;

        LookupHelper(Name name, Type site, List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            this.name = name;
            this.site = site;
            this.argtypes = argtypes;
            this.typeargtypes = typeargtypes;
            this.maxPhase = maxPhase;
        }

        protected boolean shouldStop(Symbol sym, MethodResolutionPhase phase) {
            return phase.ordinal() > maxPhase.ordinal() ||
                    sym.kind < ERRONEOUS || sym.kind == AMBIGUOUS;
        }

        abstract Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase);

        void debug(DiagnosticPosition pos, Symbol sym) {

        }

        abstract Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym);
    }

    abstract class BasicLookupHelper extends LookupHelper {
        BasicLookupHelper(Name name, Type site, List<Type> argtypes, List<Type> typeargtypes) {
            this(name, site, argtypes, typeargtypes, MethodResolutionPhase.VARARITY);
        }

        BasicLookupHelper(Name name, Type site, List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(name, site, argtypes, typeargtypes, maxPhase);
        }

        @Override
        final Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
            Symbol sym = doLookup(env, phase);
            if (sym.kind == AMBIGUOUS) {
                AmbiguityError a_err = (AmbiguityError) sym.baseSymbol();
                sym = a_err.mergeAbstracts(site);
            }
            return sym;
        }

        abstract Symbol doLookup(Env<AttrContext> env, MethodResolutionPhase phase);

        @Override
        Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym) {
            if (sym.kind >= AMBIGUOUS) {

                sym = accessMethod(sym, pos, location, site, name, true, argtypes, typeargtypes);
            }
            return sym;
        }

        @Override
        void debug(DiagnosticPosition pos, Symbol sym) {
            reportVerboseResolutionDiagnostic(pos, name, site, argtypes, typeargtypes, sym);
        }
    }

    abstract class ReferenceLookupHelper extends LookupHelper {

        JCMemberReference referenceTree;

        ReferenceLookupHelper(JCMemberReference referenceTree, Name name, Type site,
                              List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(name, site, argtypes, typeargtypes, maxPhase);
            this.referenceTree = referenceTree;
        }

        ReferenceLookupHelper unboundLookup(InferenceContext inferenceContext) {

            return new ReferenceLookupHelper(referenceTree, name, site, argtypes, typeargtypes, maxPhase) {
                @Override
                ReferenceLookupHelper unboundLookup(InferenceContext inferenceContext) {
                    return this;
                }

                @Override
                Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                    return methodNotFound;
                }

                @Override
                ReferenceKind referenceKind(Symbol sym) {
                    Assert.error();
                    return null;
                }
            };
        }

        abstract ReferenceKind referenceKind(Symbol sym);

        Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym) {
            if (sym.kind == AMBIGUOUS) {
                AmbiguityError a_err = (AmbiguityError) sym.baseSymbol();
                sym = a_err.mergeAbstracts(site);
            }

            return sym;
        }
    }

    class MethodReferenceLookupHelper extends ReferenceLookupHelper {
        MethodReferenceLookupHelper(JCMemberReference referenceTree, Name name, Type site,
                                    List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(referenceTree, name, site, argtypes, typeargtypes, maxPhase);
        }

        @Override
        final Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
            return findMethod(env, site, name, argtypes, typeargtypes,
                    phase.isBoxingRequired(), phase.isVarargsRequired(), syms.operatorNames.contains(name));
        }

        @Override
        ReferenceLookupHelper unboundLookup(InferenceContext inferenceContext) {
            if (TreeInfo.isStaticSelector(referenceTree.expr, names) &&
                    argtypes.nonEmpty() &&
                    (argtypes.head.hasTag(NONE) ||
                            types.isSubtypeUnchecked(inferenceContext.asFree(argtypes.head), site))) {
                return new UnboundMethodReferenceLookupHelper(referenceTree, name,
                        site, argtypes, typeargtypes, maxPhase);
            } else {
                return super.unboundLookup(inferenceContext);
            }
        }

        @Override
        ReferenceKind referenceKind(Symbol sym) {
            if (sym.isStatic()) {
                return ReferenceKind.STATIC;
            } else {
                Name selName = TreeInfo.name(referenceTree.getQualifierExpression());
                return selName != null && selName == names._super ?
                        ReferenceKind.SUPER :
                        ReferenceKind.BOUND;
            }
        }
    }

    class UnboundMethodReferenceLookupHelper extends MethodReferenceLookupHelper {
        UnboundMethodReferenceLookupHelper(JCMemberReference referenceTree, Name name, Type site,
                                           List<Type> argtypes, List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(referenceTree, name, site, argtypes.tail, typeargtypes, maxPhase);
            if (site.isRaw() && !argtypes.head.hasTag(NONE)) {
                Type asSuperSite = types.asSuper(argtypes.head, site.tsym);
                this.site = asSuperSite;
            }
        }

        @Override
        ReferenceLookupHelper unboundLookup(InferenceContext inferenceContext) {
            return this;
        }

        @Override
        ReferenceKind referenceKind(Symbol sym) {
            return ReferenceKind.UNBOUND;
        }
    }

    class ArrayConstructorReferenceLookupHelper extends ReferenceLookupHelper {
        ArrayConstructorReferenceLookupHelper(JCMemberReference referenceTree, Type site, List<Type> argtypes,
                                              List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(referenceTree, names.init, site, argtypes, typeargtypes, maxPhase);
        }

        @Override
        protected Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
            Scope sc = new Scope(syms.arrayClass);
            MethodSymbol arrayConstr = new MethodSymbol(PUBLIC, name, null, site.tsym);
            arrayConstr.type = new MethodType(List.of(syms.intType), site, List.nil(), syms.methodClass);
            sc.enter(arrayConstr);
            return findMethodInScope(env, site, name, argtypes, typeargtypes, sc, methodNotFound, phase.isBoxingRequired(), phase.isVarargsRequired(), false, false);
        }

        @Override
        ReferenceKind referenceKind(Symbol sym) {
            return ReferenceKind.ARRAY_CTOR;
        }
    }

    class ConstructorReferenceLookupHelper extends ReferenceLookupHelper {
        boolean needsInference;

        ConstructorReferenceLookupHelper(JCMemberReference referenceTree, Type site, List<Type> argtypes,
                                         List<Type> typeargtypes, MethodResolutionPhase maxPhase) {
            super(referenceTree, names.init, site, argtypes, typeargtypes, maxPhase);
            if (site.isRaw()) {
                this.site = new ClassType(site.getEnclosingType(), site.tsym.type.getTypeArguments(), site.tsym);
                needsInference = true;
            }
        }

        @Override
        protected Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
            Symbol sym = needsInference ?
                    findDiamond(env, site, argtypes, typeargtypes, phase.isBoxingRequired(), phase.isVarargsRequired()) :
                    findMethod(env, site, name, argtypes, typeargtypes,
                            phase.isBoxingRequired(), phase.isVarargsRequired(), syms.operatorNames.contains(name));
            return sym.kind != MTH ||
                    site.getEnclosingType().hasTag(NONE) ||
                    hasEnclosingInstance(env, site) ?
                    sym : new InvalidSymbolError(Kinds.MISSING_ENCL, sym, null) {
                @Override
                JCDiagnostic getDiagnostic(DiagnosticType dkind, DiagnosticPosition pos, Symbol location, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes) {
                    return diags.create(dkind, log.currentSource(), pos,
                            "cant.access.inner.cls.constr", site.tsym.name, argtypes, site.getEnclosingType());
                }
            };
        }

        @Override
        ReferenceKind referenceKind(Symbol sym) {
            return site.getEnclosingType().hasTag(NONE) ?
                    ReferenceKind.TOPLEVEL : ReferenceKind.IMPLICIT_INNER;
        }
    }

    abstract class ResolveError extends Symbol {

        final String debugName;

        ResolveError(int kind, String debugName) {
            super(kind, 0, null, null, null);
            this.debugName = debugName;
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            throw new AssertionError();
        }

        @Override
        public String toString() {
            return debugName;
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        public boolean isStatic() {
            return false;
        }

        protected Symbol access(Name name, TypeSymbol location) {
            return types.createErrorType(name, location, syms.errSymbol.type).tsym;
        }

        abstract JCDiagnostic getDiagnostic(DiagnosticType dkind,
                                            DiagnosticPosition pos,
                                            Symbol location,
                                            Type site,
                                            Name name,
                                            List<Type> argtypes,
                                            List<Type> typeargtypes);
    }

    abstract class InvalidSymbolError extends ResolveError {

        Symbol sym;

        InvalidSymbolError(int kind, Symbol sym, String debugName) {
            super(kind, debugName);
            this.sym = sym;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public String toString() {
            return super.toString() + " wrongSym=" + sym;
        }

        @Override
        public Symbol access(Name name, TypeSymbol location) {
            if ((sym.kind & ERRONEOUS) == 0 && (sym.kind & TYP) != 0)
                return types.createErrorType(name, location, sym.type).tsym;
            else
                return sym;
        }
    }

    class SymbolNotFoundError extends ResolveError {
        SymbolNotFoundError(int kind) {
            this(kind, "symbol not found error");
        }

        SymbolNotFoundError(int kind, String debugName) {
            super(kind, debugName);
        }

        @Override
        JCDiagnostic getDiagnostic(DiagnosticType dkind,
                                   DiagnosticPosition pos,
                                   Symbol location,
                                   Type site,
                                   Name name,
                                   List<Type> argtypes,
                                   List<Type> typeargtypes) {
            argtypes = argtypes == null ? List.nil() : argtypes;
            typeargtypes = typeargtypes == null ? List.nil() : typeargtypes;
            if (name == names.error)
                return null;
            if (syms.operatorNames.contains(name)) {
                boolean isUnaryOp = argtypes.size() == 1;
                String key = argtypes.size() == 1 ?
                        "operator.cant.be.applied" :
                        "operator.cant.be.applied.1";
                Type first = argtypes.head;
                Type second = !isUnaryOp ? argtypes.tail.head : null;
                return diags.create(dkind, log.currentSource(), pos,
                        key, name, first, second);
            }
            boolean hasLocation = false;
            if (location == null) {
                location = site.tsym;
            }
            if (!location.name.isEmpty()) {
                if (location.kind == PCK && !site.tsym.exists()) {
                    return diags.create(dkind, log.currentSource(), pos,
                            "doesnt.exist", location);
                }
                hasLocation = !location.name.equals(names._this) &&
                        !location.name.equals(names._super);
            }
            boolean isConstructor = (kind == ABSENT_MTH || kind == WRONG_STATICNESS) &&
                    name == names.init;
            KindName kindname = isConstructor ? KindName.CONSTRUCTOR : absentKind(kind);
            Name idname = isConstructor ? site.tsym.name : name;
            String errKey = getErrorKey(kindname, typeargtypes.nonEmpty(), hasLocation);
            if (hasLocation) {
                return diags.create(dkind, log.currentSource(), pos,
                        errKey, kindname, idname,
                        typeargtypes, args(argtypes),
                        getLocationDiag(location, site));
            } else {
                return diags.create(dkind, log.currentSource(), pos,
                        errKey, kindname, idname,
                        typeargtypes, args(argtypes));
            }
        }

        private Object args(List<Type> args) {
            return args.isEmpty() ? args : methodArguments(args);
        }

        private String getErrorKey(KindName kindname, boolean hasTypeArgs, boolean hasLocation) {
            String key = "cant.resolve";
            String suffix = hasLocation ? ".location" : "";
            switch (kindname) {
                case METHOD:
                case CONSTRUCTOR: {
                    suffix += ".args";
                    suffix += hasTypeArgs ? ".params" : "";
                }
            }
            return key + suffix;
        }

        private JCDiagnostic getLocationDiag(Symbol location, Type site) {
            if (location.kind == VAR) {
                return diags.fragment("location.1",
                        kindName(location),
                        location,
                        location.type);
            } else {
                return diags.fragment("location",
                        typeKindName(site),
                        site,
                        null);
            }
        }
    }

    class InapplicableSymbolError extends ResolveError {
        protected MethodResolutionContext resolveContext;

        InapplicableSymbolError(MethodResolutionContext context) {
            this(WRONG_MTH, "inapplicable symbol error", context);
        }

        protected InapplicableSymbolError(int kind, String debugName, MethodResolutionContext context) {
            super(kind, debugName);
            this.resolveContext = context;
        }

        @Override
        public String toString() {
            return super.toString();
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        JCDiagnostic getDiagnostic(DiagnosticType dkind,
                                   DiagnosticPosition pos,
                                   Symbol location,
                                   Type site,
                                   Name name,
                                   List<Type> argtypes,
                                   List<Type> typeargtypes) {
            if (name == names.error)
                return null;
            if (syms.operatorNames.contains(name)) {
                boolean isUnaryOp = argtypes.size() == 1;
                String key = argtypes.size() == 1 ?
                        "operator.cant.be.applied" :
                        "operator.cant.be.applied.1";
                Type first = argtypes.head;
                Type second = !isUnaryOp ? argtypes.tail.head : null;
                return diags.create(dkind, log.currentSource(), pos,
                        key, name, first, second);
            } else {
                Pair<Symbol, JCDiagnostic> c = errCandidate();
                if (compactMethodDiags) {
                    for (Map.Entry<Template, DiagnosticRewriter> _entry :
                            MethodResolutionDiagHelper.rewriters.entrySet()) {
                        if (_entry.getKey().matches(c.snd)) {
                            JCDiagnostic simpleDiag =
                                    _entry.getValue().rewriteDiagnostic(diags, pos,
                                            log.currentSource(), dkind, c.snd);
                            simpleDiag.setFlag(DiagnosticFlag.COMPRESSED);
                            return simpleDiag;
                        }
                    }
                }
                Symbol ws = c.fst.asMemberOf(site, types);
                return diags.create(dkind, log.currentSource(), pos,
                        "cant.apply.symbol",
                        kindName(ws),
                        ws.name == names.init ? ws.owner.name : ws.name,
                        methodArguments(ws.type.getParameterTypes()),
                        methodArguments(argtypes),
                        kindName(ws.owner),
                        ws.owner.type,
                        c.snd);
            }
        }

        @Override
        public Symbol access(Name name, TypeSymbol location) {
            return types.createErrorType(name, location, syms.errSymbol.type).tsym;
        }

        protected Pair<Symbol, JCDiagnostic> errCandidate() {
            Candidate bestSoFar = null;
            for (Candidate c : resolveContext.candidates) {
                if (c.isApplicable()) continue;
                bestSoFar = c;
            }
            Assert.checkNonNull(bestSoFar);
            return new Pair<Symbol, JCDiagnostic>(bestSoFar.sym, bestSoFar.details);
        }
    }

    class InapplicableSymbolsError extends InapplicableSymbolError {
        InapplicableSymbolsError(MethodResolutionContext context) {
            super(WRONG_MTHS, "inapplicable symbols", context);
        }

        @Override
        JCDiagnostic getDiagnostic(DiagnosticType dkind,
                                   DiagnosticPosition pos,
                                   Symbol location,
                                   Type site,
                                   Name name,
                                   List<Type> argtypes,
                                   List<Type> typeargtypes) {
            Map<Symbol, JCDiagnostic> candidatesMap = mapCandidates();
            Map<Symbol, JCDiagnostic> filteredCandidates = compactMethodDiags ?
                    filterCandidates(candidatesMap) :
                    mapCandidates();
            if (filteredCandidates.isEmpty()) {
                filteredCandidates = candidatesMap;
            }
            boolean truncatedDiag = candidatesMap.size() != filteredCandidates.size();
            if (filteredCandidates.size() > 1) {
                JCDiagnostic err = diags.create(dkind,
                        null,
                        truncatedDiag ?
                                EnumSet.of(DiagnosticFlag.COMPRESSED) :
                                EnumSet.noneOf(DiagnosticFlag.class),
                        log.currentSource(),
                        pos,
                        "cant.apply.symbols",
                        name == names.init ? KindName.CONSTRUCTOR : absentKind(kind),
                        name == names.init ? site.tsym.name : name,
                        methodArguments(argtypes));
                return new JCDiagnostic.MultilineDiagnostic(err, candidateDetails(filteredCandidates, site));
            } else if (filteredCandidates.size() == 1) {
                Map.Entry<Symbol, JCDiagnostic> _e =
                        filteredCandidates.entrySet().iterator().next();
                final Pair<Symbol, JCDiagnostic> p = new Pair<Symbol, JCDiagnostic>(_e.getKey(), _e.getValue());
                JCDiagnostic d = new InapplicableSymbolError(resolveContext) {
                    @Override
                    protected Pair<Symbol, JCDiagnostic> errCandidate() {
                        return p;
                    }
                }.getDiagnostic(dkind, pos,
                        location, site, name, argtypes, typeargtypes);
                if (truncatedDiag) {
                    d.setFlag(DiagnosticFlag.COMPRESSED);
                }
                return d;
            } else {
                return new SymbolNotFoundError(ABSENT_MTH).getDiagnostic(dkind, pos,
                        location, site, name, argtypes, typeargtypes);
            }
        }

        private Map<Symbol, JCDiagnostic> mapCandidates() {
            Map<Symbol, JCDiagnostic> candidates = new LinkedHashMap<Symbol, JCDiagnostic>();
            for (Candidate c : resolveContext.candidates) {
                if (c.isApplicable()) continue;
                candidates.put(c.sym, c.details);
            }
            return candidates;
        }

        Map<Symbol, JCDiagnostic> filterCandidates(Map<Symbol, JCDiagnostic> candidatesMap) {
            Map<Symbol, JCDiagnostic> candidates = new LinkedHashMap<Symbol, JCDiagnostic>();
            for (Map.Entry<Symbol, JCDiagnostic> _entry : candidatesMap.entrySet()) {
                JCDiagnostic d = _entry.getValue();
                if (!new Template(MethodCheckDiag.ARITY_MISMATCH.regex()).matches(d)) {
                    candidates.put(_entry.getKey(), d);
                }
            }
            return candidates;
        }

        private List<JCDiagnostic> candidateDetails(Map<Symbol, JCDiagnostic> candidatesMap, Type site) {
            List<JCDiagnostic> details = List.nil();
            for (Map.Entry<Symbol, JCDiagnostic> _entry : candidatesMap.entrySet()) {
                Symbol sym = _entry.getKey();
                JCDiagnostic detailDiag = diags.fragment("inapplicable.method",
                        Kinds.kindName(sym),
                        sym.location(site, types),
                        sym.asMemberOf(site, types),
                        _entry.getValue());
                details = details.prepend(detailDiag);
            }


            return details;
        }
    }

    class AccessError extends InvalidSymbolError {
        private Env<AttrContext> env;
        private Type site;

        AccessError(Symbol sym) {
            this(null, null, sym);
        }

        AccessError(Env<AttrContext> env, Type site, Symbol sym) {
            super(HIDDEN, sym, "access error");
            this.env = env;
            this.site = site;
            if (debugResolve)
                log.error("proc.messager", sym + " @ " + site + " is inaccessible.");
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        JCDiagnostic getDiagnostic(DiagnosticType dkind,
                                   DiagnosticPosition pos,
                                   Symbol location,
                                   Type site,
                                   Name name,
                                   List<Type> argtypes,
                                   List<Type> typeargtypes) {
            if (sym.owner.type.hasTag(ERROR))
                return null;
            if (sym.name == names.init && sym.owner != site.tsym) {
                return new SymbolNotFoundError(ABSENT_MTH).getDiagnostic(dkind,
                        pos, location, site, name, argtypes, typeargtypes);
            } else if ((sym.flags() & PUBLIC) != 0
                    || (env != null && this.site != null
                    && !isAccessible(env, this.site))) {
                return diags.create(dkind, log.currentSource(),
                        pos, "not.def.access.class.intf.cant.access",
                        sym, sym.location());
            } else if ((sym.flags() & (PRIVATE | PROTECTED)) != 0) {
                return diags.create(dkind, log.currentSource(),
                        pos, "report.access", sym,
                        asFlagSet(sym.flags() & (PRIVATE | PROTECTED)),
                        sym.location());
            } else {
                return diags.create(dkind, log.currentSource(),
                        pos, "not.def.public.cant.access", sym, sym.location());
            }
        }
    }

    class StaticError extends InvalidSymbolError {
        StaticError(Symbol sym) {
            super(STATICERR, sym, "static error");
        }

        @Override
        JCDiagnostic getDiagnostic(DiagnosticType dkind,
                                   DiagnosticPosition pos,
                                   Symbol location,
                                   Type site,
                                   Name name,
                                   List<Type> argtypes,
                                   List<Type> typeargtypes) {
            Symbol errSym = ((sym.kind == TYP && sym.type.hasTag(CLASS))
                    ? types.erasure(sym.type).tsym
                    : sym);
            return diags.create(dkind, log.currentSource(), pos,
                    "non-static.cant.be.ref", kindName(sym), errSym);
        }
    }

    class AmbiguityError extends ResolveError {

        List<Symbol> ambiguousSyms = List.nil();

        AmbiguityError(Symbol sym1, Symbol sym2) {
            super(AMBIGUOUS, "ambiguity error");
            ambiguousSyms = flatten(sym2).appendList(flatten(sym1));
        }

        @Override
        public boolean exists() {
            return true;
        }

        private List<Symbol> flatten(Symbol sym) {
            if (sym.kind == AMBIGUOUS) {
                return ((AmbiguityError) sym.baseSymbol()).ambiguousSyms;
            } else {
                return List.of(sym);
            }
        }

        AmbiguityError addAmbiguousSymbol(Symbol s) {
            ambiguousSyms = ambiguousSyms.prepend(s);
            return this;
        }

        @Override
        JCDiagnostic getDiagnostic(DiagnosticType dkind,
                                   DiagnosticPosition pos,
                                   Symbol location,
                                   Type site,
                                   Name name,
                                   List<Type> argtypes,
                                   List<Type> typeargtypes) {
            List<Symbol> diagSyms = ambiguousSyms.reverse();
            Symbol s1 = diagSyms.head;
            Symbol s2 = diagSyms.tail.head;
            Name sname = s1.name;
            if (sname == names.init) sname = s1.owner.name;
            return diags.create(dkind, log.currentSource(),
                    pos, "ref.ambiguous", sname,
                    kindName(s1),
                    s1,
                    s1.location(site, types),
                    kindName(s2),
                    s2,
                    s2.location(site, types));
        }

        Symbol mergeAbstracts(Type site) {
            List<Symbol> ambiguousInOrder = ambiguousSyms.reverse();
            for (Symbol s : ambiguousInOrder) {
                Type mt = types.memberType(site, s);
                boolean found = true;
                List<Type> allThrown = mt.getThrownTypes();
                for (Symbol s2 : ambiguousInOrder) {
                    Type mt2 = types.memberType(site, s2);
                    if ((s2.flags() & ABSTRACT) == 0 ||
                            !types.overrideEquivalent(mt, mt2) ||
                            !types.isSameTypes(s.erasure(types).getParameterTypes(),
                                    s2.erasure(types).getParameterTypes())) {

                        return this;
                    }
                    Type mst = mostSpecificReturnType(mt, mt2);
                    if (mst == null || mst != mt) {
                        found = false;
                        break;
                    }
                    allThrown = chk.intersect(allThrown, mt2.getThrownTypes());
                }
                if (found) {


                    return (allThrown == mt.getThrownTypes()) ?
                            s : new MethodSymbol(
                            s.flags(),
                            s.name,
                            types.createMethodTypeWithThrown(mt, allThrown),
                            s.owner);
                }
            }
            return this;
        }

        @Override
        protected Symbol access(Name name, TypeSymbol location) {
            Symbol firstAmbiguity = ambiguousSyms.last();
            return firstAmbiguity.kind == TYP ?
                    types.createErrorType(name, location, firstAmbiguity.type).tsym :
                    firstAmbiguity;
        }
    }

    class BadVarargsMethod extends ResolveError {
        ResolveError delegatedError;

        BadVarargsMethod(ResolveError delegatedError) {
            super(delegatedError.kind, "badVarargs");
            this.delegatedError = delegatedError;
        }

        @Override
        public Symbol baseSymbol() {
            return delegatedError.baseSymbol();
        }

        @Override
        protected Symbol access(Name name, TypeSymbol location) {
            return delegatedError.access(name, location);
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        JCDiagnostic getDiagnostic(DiagnosticType dkind, DiagnosticPosition pos, Symbol location, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes) {
            return delegatedError.getDiagnostic(dkind, pos, location, site, name, argtypes, typeargtypes);
        }
    }

    class MethodResolutionContext {
        MethodResolutionPhase step = null;
        MethodCheck methodCheck = resolveMethodCheck;
        private List<Candidate> candidates = List.nil();
        private boolean internalResolution = false;
        private AttrMode attrMode = AttrMode.SPECULATIVE;

        void addInapplicableCandidate(Symbol sym, JCDiagnostic details) {
            Candidate c = new Candidate(currentResolutionContext.step, sym, details, null);
            candidates = candidates.append(c);
        }

        void addApplicableCandidate(Symbol sym, Type mtype) {
            Candidate c = new Candidate(currentResolutionContext.step, sym, null, mtype);
            candidates = candidates.append(c);
        }

        DeferredAttrContext deferredAttrContext(Symbol sym, InferenceContext inferenceContext, ResultInfo pendingResult, Warner warn) {
            return deferredAttr.new DeferredAttrContext(attrMode, sym, step, inferenceContext, pendingResult != null ? pendingResult.checkContext.deferredAttrContext() : deferredAttr.emptyDeferredAttrContext, warn);
        }

        AttrMode attrMode() {
            return attrMode;
        }

        boolean internal() {
            return internalResolution;
        }

        @SuppressWarnings("overrides")
        class Candidate {
            final MethodResolutionPhase step;
            final Symbol sym;
            final JCDiagnostic details;
            final Type mtype;

            private Candidate(MethodResolutionPhase step, Symbol sym, JCDiagnostic details, Type mtype) {
                this.step = step;
                this.sym = sym;
                this.details = details;
                this.mtype = mtype;
            }

            @Override
            public boolean equals(Object o) {
                if (o instanceof Candidate) {
                    Symbol s1 = this.sym;
                    Symbol s2 = ((Candidate) o).sym;
                    return (s1 != s2 &&
                            (s1.overrides(s2, s1.owner.type.tsym, types, false) ||
                                    (s2.overrides(s1, s2.owner.type.tsym, types, false)))) ||
                            ((s1.isConstructor() || s2.isConstructor()) && s1.owner != s2.owner);
                }
                return false;
            }

            boolean isApplicable() {
                return mtype != null;
            }
        }
    }
}
