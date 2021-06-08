package com.github.api.sun.tools.javac.comp;

import com.github.api.sun.tools.javac.code.*;
import com.github.api.sun.tools.javac.code.Symbol.MethodSymbol;
import com.github.api.sun.tools.javac.code.Symbol.TypeSymbol;
import com.github.api.sun.tools.javac.code.Symbol.TypeVariableSymbol;
import com.github.api.sun.tools.javac.code.Type.*;
import com.github.api.sun.tools.javac.code.Type.UndetVar.InferenceBound;
import com.github.api.sun.tools.javac.comp.DeferredAttr.AttrMode;
import com.github.api.sun.tools.javac.comp.Infer.GraphSolver.InferenceGraph;
import com.github.api.sun.tools.javac.comp.Infer.GraphSolver.InferenceGraph.Node;
import com.github.api.sun.tools.javac.comp.Resolve.InapplicableMethodException;
import com.github.api.sun.tools.javac.comp.Resolve.VerboseResolutionMode;
import com.github.api.sun.tools.javac.tree.JCTree;
import com.github.api.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.github.api.sun.tools.javac.tree.TreeInfo;
import com.github.api.sun.tools.javac.util.*;
import com.github.api.sun.tools.javac.util.GraphUtils.TarjanNode;
import com.github.api.sun.tools.javac.util.List;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import java.util.*;

import static com.github.api.sun.tools.javac.code.TypeTag.*;

public class Infer {
    public static final Type anyPoly = new JCNoType();
    protected static final Context.Key<Infer> inferKey =
            new Context.Key<Infer>();
    static final int MAX_INCORPORATION_STEPS = 100;
    protected final InferenceException inferenceException;
    final InferenceContext emptyContext = new InferenceContext(List.nil());
    Resolve rs;
    Check chk;
    Symtab syms;
    Types types;
    JCDiagnostic.Factory diags;
    Log log;
    boolean allowGraphInference;
    EnumSet<IncorporationStep> incorporationStepsLegacy = EnumSet.of(IncorporationStep.EQ_CHECK_LEGACY);
    EnumSet<IncorporationStep> incorporationStepsGraph =
            EnumSet.complementOf(EnumSet.of(IncorporationStep.EQ_CHECK_LEGACY));
    Map<IncorporationBinaryOp, Boolean> incorporationCache =
            new HashMap<IncorporationBinaryOp, Boolean>();

    protected Infer(Context context) {
        context.put(inferKey, this);
        rs = Resolve.instance(context);
        chk = Check.instance(context);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        log = Log.instance(context);
        inferenceException = new InferenceException(diags);
        Options options = Options.instance(context);
        allowGraphInference = Source.instance(context).allowGraphInference()
                && options.isUnset("useLegacyInference");
    }

    public static Infer instance(Context context) {
        Infer instance = context.get(inferKey);
        if (instance == null)
            instance = new Infer(context);
        return instance;
    }

    public Type instantiateMethod(Env<AttrContext> env,
                                  List<Type> tvars,
                                  MethodType mt,
                                  Attr.ResultInfo resultInfo,
                                  Symbol msym,
                                  List<Type> argtypes,
                                  boolean allowBoxing,
                                  boolean useVarargs,
                                  Resolve.MethodResolutionContext resolveContext,
                                  Warner warn) throws InferenceException {

        final InferenceContext inferenceContext = new InferenceContext(tvars);
        inferenceException.clear();
        try {
            DeferredAttr.DeferredAttrContext deferredAttrContext =
                    resolveContext.deferredAttrContext(msym, inferenceContext, resultInfo, warn);
            resolveContext.methodCheck.argumentsAcceptable(env, deferredAttrContext,
                    argtypes, mt.getParameterTypes(), warn);
            if (allowGraphInference &&
                    resultInfo != null &&
                    !warn.hasNonSilentLint(Lint.LintCategory.UNCHECKED)) {

                checkWithinBounds(inferenceContext, warn);
                Type newRestype = generateReturnConstraints(resultInfo, mt, inferenceContext);
                mt = (MethodType) types.createMethodTypeWithReturn(mt, newRestype);

                if (resultInfo.checkContext.inferenceContext().free(resultInfo.pt)) {

                    inferenceContext.dupTo(resultInfo.checkContext.inferenceContext());
                    deferredAttrContext.complete();
                    return mt;
                }
            }
            deferredAttrContext.complete();

            if (allowGraphInference) {
                inferenceContext.solve(warn);
            } else {
                inferenceContext.solveLegacy(true, warn, LegacyInferenceSteps.EQ_LOWER.steps);
            }
            mt = (MethodType) inferenceContext.asInstType(mt);
            if (!allowGraphInference &&
                    inferenceContext.restvars().nonEmpty() &&
                    resultInfo != null &&
                    !warn.hasNonSilentLint(Lint.LintCategory.UNCHECKED)) {
                generateReturnConstraints(resultInfo, mt, inferenceContext);
                inferenceContext.solveLegacy(false, warn, LegacyInferenceSteps.EQ_UPPER.steps);
                mt = (MethodType) inferenceContext.asInstType(mt);
            }
            if (resultInfo != null && rs.verboseResolutionMode.contains(VerboseResolutionMode.DEFERRED_INST)) {
                log.note(env.tree.pos, "deferred.method.inst", msym, mt, resultInfo.pt);
            }

            return mt;
        } finally {
            if (resultInfo != null || !allowGraphInference) {
                inferenceContext.notifyChange();
            } else {
                inferenceContext.notifyChange(inferenceContext.boundedVars());
            }
        }
    }

    Type generateReturnConstraints(Attr.ResultInfo resultInfo,
                                   MethodType mt, InferenceContext inferenceContext) {
        Type from = mt.getReturnType();
        if (mt.getReturnType().containsAny(inferenceContext.inferencevars) &&
                resultInfo.checkContext.inferenceContext() != emptyContext) {
            from = types.capture(from);

            for (Type t : from.getTypeArguments()) {
                if (t.hasTag(TYPEVAR) && ((TypeVar) t).isCaptured()) {
                    inferenceContext.addVar((TypeVar) t);
                }
            }
        }
        Type qtype1 = inferenceContext.asFree(from);
        Type to = returnConstraintTarget(qtype1, resultInfo.pt);
        Assert.check(allowGraphInference || !resultInfo.checkContext.inferenceContext().free(to),
                "legacy inference engine cannot handle constraints on both sides of a subtyping assertion");

        Warner retWarn = new Warner();
        if (!resultInfo.checkContext.compatible(qtype1, resultInfo.checkContext.inferenceContext().asFree(to), retWarn) ||

                (!allowGraphInference && retWarn.hasLint(Lint.LintCategory.UNCHECKED))) {
            throw inferenceException
                    .setMessage("infer.no.conforming.instance.exists",
                            inferenceContext.restvars(), mt.getReturnType(), to);
        }
        return from;
    }

    Type returnConstraintTarget(Type from, Type to) {
        if (from.hasTag(VOID)) {
            return syms.voidType;
        } else if (to.hasTag(NONE)) {
            return from.isPrimitive() ? from : syms.objectType;
        } else if (from.hasTag(UNDETVAR) && to.isPrimitive()) {
            if (!allowGraphInference) {

                return types.boxedClass(to).type;
            }

            UndetVar uv = (UndetVar) from;
            for (Type t : uv.getBounds(InferenceBound.EQ, InferenceBound.LOWER)) {
                Type boundAsPrimitive = types.unboxedType(t);
                if (boundAsPrimitive == null) continue;
                if (types.isConvertible(boundAsPrimitive, to)) {

                    return syms.objectType;
                }
            }
            return types.boxedClass(to).type;
        } else {
            return to;
        }
    }

    private void instantiateAsUninferredVars(List<Type> vars, InferenceContext inferenceContext) {
        ListBuffer<Type> todo = new ListBuffer<>();

        for (Type t : vars) {
            UndetVar uv = (UndetVar) inferenceContext.asFree(t);
            List<Type> upperBounds = uv.getBounds(InferenceBound.UPPER);
            if (Type.containsAny(upperBounds, vars)) {
                TypeSymbol fresh_tvar = new TypeVariableSymbol(Flags.SYNTHETIC, uv.qtype.tsym.name, null, uv.qtype.tsym.owner);
                fresh_tvar.type = new TypeVar(fresh_tvar, types.makeCompoundType(uv.getBounds(InferenceBound.UPPER)), null);
                todo.append(uv);
                uv.inst = fresh_tvar.type;
            } else if (upperBounds.nonEmpty()) {
                uv.inst = types.glb(upperBounds);
            } else {
                uv.inst = syms.objectType;
            }
        }

        List<Type> formals = vars;
        for (Type t : todo) {
            UndetVar uv = (UndetVar) t;
            TypeVar ct = (TypeVar) uv.inst;
            ct.bound = types.glb(inferenceContext.asInstTypes(types.getBounds(ct)));
            if (ct.bound.isErroneous()) {

                reportBoundError(uv, BoundErrorKind.BAD_UPPER);
            }
            formals = formals.tail;
        }
    }

    Type instantiatePolymorphicSignatureInstance(Env<AttrContext> env,
                                                 MethodSymbol spMethod,
                                                 Resolve.MethodResolutionContext resolveContext,
                                                 List<Type> argtypes) {
        final Type restype;


        switch (env.next.tree.getTag()) {
            case TYPECAST:
                JCTypeCast castTree = (JCTypeCast) env.next.tree;
                restype = (TreeInfo.skipParens(castTree.expr) == env.tree) ?
                        castTree.clazz.type :
                        syms.objectType;
                break;
            case EXEC:
                JCTree.JCExpressionStatement execTree =
                        (JCTree.JCExpressionStatement) env.next.tree;
                restype = (TreeInfo.skipParens(execTree.expr) == env.tree) ?
                        syms.voidType :
                        syms.objectType;
                break;
            default:
                restype = syms.objectType;
        }
        List<Type> paramtypes = Type.map(argtypes, new ImplicitArgType(spMethod, resolveContext.step));
        List<Type> exType = spMethod != null ?
                spMethod.getThrownTypes() :
                List.of(syms.throwableType);
        MethodType mtype = new MethodType(paramtypes,
                restype,
                exType,
                syms.methodClass);
        return mtype;
    }

    public Type instantiateFunctionalInterface(DiagnosticPosition pos, Type funcInterface,
                                               List<Type> paramTypes, Check.CheckContext checkContext) {
        if (types.capture(funcInterface) == funcInterface) {


            return funcInterface;
        } else {
            Type formalInterface = funcInterface.tsym.type;
            InferenceContext funcInterfaceContext =
                    new InferenceContext(funcInterface.tsym.type.getTypeArguments());
            Assert.check(paramTypes != null);


            List<Type> descParameterTypes = types.findDescriptorType(formalInterface).getParameterTypes();
            if (descParameterTypes.size() != paramTypes.size()) {
                checkContext.report(pos, diags.fragment("incompatible.arg.types.in.lambda"));
                return types.createErrorType(funcInterface);
            }
            for (Type p : descParameterTypes) {
                if (!types.isSameType(funcInterfaceContext.asFree(p), paramTypes.head)) {
                    checkContext.report(pos, diags.fragment("no.suitable.functional.intf.inst", funcInterface));
                    return types.createErrorType(funcInterface);
                }
                paramTypes = paramTypes.tail;
            }
            try {
                funcInterfaceContext.solve(funcInterfaceContext.boundedVars(), types.noWarnings);
            } catch (InferenceException ex) {
                checkContext.report(pos, diags.fragment("no.suitable.functional.intf.inst", funcInterface));
            }
            List<Type> actualTypeargs = funcInterface.getTypeArguments();
            for (Type t : funcInterfaceContext.undetvars) {
                UndetVar uv = (UndetVar) t;
                if (uv.inst == null) {
                    uv.inst = actualTypeargs.head;
                }
                actualTypeargs = actualTypeargs.tail;
            }
            Type owntype = funcInterfaceContext.asInstType(formalInterface);
            if (!chk.checkValidGenericType(owntype)) {


                checkContext.report(pos, diags.fragment("no.suitable.functional.intf.inst", funcInterface));
            }
            return owntype;
        }
    }

    void checkWithinBounds(InferenceContext inferenceContext,
                           Warner warn) throws InferenceException {
        MultiUndetVarListener mlistener = new MultiUndetVarListener(inferenceContext.undetvars);
        List<Type> saved_undet = inferenceContext.save();
        try {
            while (true) {
                mlistener.reset();
                if (!allowGraphInference) {


                    for (Type t : inferenceContext.undetvars) {
                        UndetVar uv = (UndetVar) t;
                        IncorporationStep.CHECK_BOUNDS.apply(uv, inferenceContext, warn);
                    }
                }
                for (Type t : inferenceContext.undetvars) {
                    UndetVar uv = (UndetVar) t;

                    EnumSet<IncorporationStep> incorporationSteps = allowGraphInference ?
                            incorporationStepsGraph : incorporationStepsLegacy;
                    for (IncorporationStep is : incorporationSteps) {
                        if (is.accepts(uv, inferenceContext)) {
                            is.apply(uv, inferenceContext, warn);
                        }
                    }
                }
                if (!mlistener.changed || !allowGraphInference) break;
            }
        } finally {
            mlistener.detach();
            if (incorporationCache.size() == MAX_INCORPORATION_STEPS) {
                inferenceContext.rollback(saved_undet);
            }
            incorporationCache.clear();
        }
    }

    void checkCompatibleUpperBounds(UndetVar uv, InferenceContext inferenceContext) {
        List<Type> hibounds =
                Type.filter(uv.getBounds(InferenceBound.UPPER), new BoundFilter(inferenceContext));
        Type hb = null;
        if (hibounds.isEmpty())
            hb = syms.objectType;
        else if (hibounds.tail.isEmpty())
            hb = hibounds.head;
        else
            hb = types.glb(hibounds);
        if (hb == null || hb.isErroneous())
            reportBoundError(uv, BoundErrorKind.BAD_UPPER);
    }

    void reportBoundError(UndetVar uv, BoundErrorKind bk) {
        throw bk.setMessage(inferenceException, uv);
    }

    enum IncorporationStep {

        CHECK_BOUNDS() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                uv.substBounds(inferenceContext.inferenceVars(), inferenceContext.instTypes(), infer.types);
                infer.checkCompatibleUpperBounds(uv, inferenceContext);
                if (uv.inst != null) {
                    Type inst = uv.inst;
                    for (Type u : uv.getBounds(InferenceBound.UPPER)) {
                        if (!isSubtype(inst, inferenceContext.asFree(u), warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.UPPER);
                        }
                    }
                    for (Type l : uv.getBounds(InferenceBound.LOWER)) {
                        if (!isSubtype(inferenceContext.asFree(l), inst, warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.LOWER);
                        }
                    }
                    for (Type e : uv.getBounds(InferenceBound.EQ)) {
                        if (!isSameType(inst, inferenceContext.asFree(e), infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.EQ);
                        }
                    }
                }
            }

            @Override
            boolean accepts(UndetVar uv, InferenceContext inferenceContext) {

                return true;
            }
        },

        EQ_CHECK_LEGACY() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                Type eq = null;
                for (Type e : uv.getBounds(InferenceBound.EQ)) {
                    Assert.check(!inferenceContext.free(e));
                    if (eq != null && !isSameType(e, eq, infer)) {
                        infer.reportBoundError(uv, BoundErrorKind.EQ);
                    }
                    eq = e;
                    for (Type l : uv.getBounds(InferenceBound.LOWER)) {
                        Assert.check(!inferenceContext.free(l));
                        if (!isSubtype(l, e, warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.BAD_EQ_LOWER);
                        }
                    }
                    for (Type u : uv.getBounds(InferenceBound.UPPER)) {
                        if (inferenceContext.free(u)) continue;
                        if (!isSubtype(e, u, warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.BAD_EQ_UPPER);
                        }
                    }
                }
            }
        },

        EQ_CHECK() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type e : uv.getBounds(InferenceBound.EQ)) {
                    if (e.containsAny(inferenceContext.inferenceVars())) continue;
                    for (Type u : uv.getBounds(InferenceBound.UPPER)) {
                        if (!isSubtype(e, inferenceContext.asFree(u), warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.BAD_EQ_UPPER);
                        }
                    }
                    for (Type l : uv.getBounds(InferenceBound.LOWER)) {
                        if (!isSubtype(inferenceContext.asFree(l), e, warn, infer)) {
                            infer.reportBoundError(uv, BoundErrorKind.BAD_EQ_LOWER);
                        }
                    }
                }
            }
        },

        CROSS_UPPER_LOWER() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b1 : uv.getBounds(InferenceBound.UPPER)) {
                    for (Type b2 : uv.getBounds(InferenceBound.LOWER)) {
                        isSubtype(inferenceContext.asFree(b2), inferenceContext.asFree(b1), warn, infer);
                    }
                }
            }
        },

        CROSS_UPPER_EQ() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b1 : uv.getBounds(InferenceBound.UPPER)) {
                    for (Type b2 : uv.getBounds(InferenceBound.EQ)) {
                        isSubtype(inferenceContext.asFree(b2), inferenceContext.asFree(b1), warn, infer);
                    }
                }
            }
        },

        CROSS_EQ_LOWER() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b1 : uv.getBounds(InferenceBound.EQ)) {
                    for (Type b2 : uv.getBounds(InferenceBound.LOWER)) {
                        isSubtype(inferenceContext.asFree(b2), inferenceContext.asFree(b1), warn, infer);
                    }
                }
            }
        },

        CROSS_EQ_EQ() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b1 : uv.getBounds(InferenceBound.EQ)) {
                    for (Type b2 : uv.getBounds(InferenceBound.EQ)) {
                        if (b1 != b2) {
                            isSameType(inferenceContext.asFree(b2), inferenceContext.asFree(b1), infer);
                        }
                    }
                }
            }
        },

        PROP_UPPER() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b : uv.getBounds(InferenceBound.UPPER)) {
                    if (inferenceContext.inferenceVars().contains(b)) {
                        UndetVar uv2 = (UndetVar) inferenceContext.asFree(b);
                        if (uv2.isCaptured()) continue;


                        addBound(InferenceBound.LOWER, uv2, inferenceContext.asInstType(uv.qtype), infer);

                        for (Type l : uv.getBounds(InferenceBound.LOWER)) {
                            addBound(InferenceBound.LOWER, uv2, inferenceContext.asInstType(l), infer);
                        }

                        for (Type u : uv2.getBounds(InferenceBound.UPPER)) {
                            addBound(InferenceBound.UPPER, uv, inferenceContext.asInstType(u), infer);
                        }
                    }
                }
            }
        },

        PROP_LOWER() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b : uv.getBounds(InferenceBound.LOWER)) {
                    if (inferenceContext.inferenceVars().contains(b)) {
                        UndetVar uv2 = (UndetVar) inferenceContext.asFree(b);
                        if (uv2.isCaptured()) continue;


                        addBound(InferenceBound.UPPER, uv2, inferenceContext.asInstType(uv.qtype), infer);

                        for (Type u : uv.getBounds(InferenceBound.UPPER)) {
                            addBound(InferenceBound.UPPER, uv2, inferenceContext.asInstType(u), infer);
                        }

                        for (Type l : uv2.getBounds(InferenceBound.LOWER)) {
                            addBound(InferenceBound.LOWER, uv, inferenceContext.asInstType(l), infer);
                        }
                    }
                }
            }
        },

        PROP_EQ() {
            public void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn) {
                Infer infer = inferenceContext.infer();
                for (Type b : uv.getBounds(InferenceBound.EQ)) {
                    if (inferenceContext.inferenceVars().contains(b)) {
                        UndetVar uv2 = (UndetVar) inferenceContext.asFree(b);
                        if (uv2.isCaptured()) continue;


                        addBound(InferenceBound.EQ, uv2, inferenceContext.asInstType(uv.qtype), infer);

                        for (InferenceBound ib : InferenceBound.values()) {
                            for (Type b2 : uv.getBounds(ib)) {
                                if (b2 != uv2) {
                                    addBound(ib, uv2, inferenceContext.asInstType(b2), infer);
                                }
                            }
                        }

                        for (InferenceBound ib : InferenceBound.values()) {
                            for (Type b2 : uv2.getBounds(ib)) {
                                if (b2 != uv) {
                                    addBound(ib, uv, inferenceContext.asInstType(b2), infer);
                                }
                            }
                        }
                    }
                }
            }
        };

        abstract void apply(UndetVar uv, InferenceContext inferenceContext, Warner warn);

        boolean accepts(UndetVar uv, InferenceContext inferenceContext) {
            return !uv.isCaptured();
        }

        boolean isSubtype(Type s, Type t, Warner warn, Infer infer) {
            return doIncorporationOp(IncorporationBinaryOpKind.IS_SUBTYPE, s, t, warn, infer);
        }

        boolean isSameType(Type s, Type t, Infer infer) {
            return doIncorporationOp(IncorporationBinaryOpKind.IS_SAME_TYPE, s, t, null, infer);
        }

        void addBound(InferenceBound ib, UndetVar uv, Type b, Infer infer) {
            doIncorporationOp(opFor(ib), uv, b, null, infer);
        }

        IncorporationBinaryOpKind opFor(InferenceBound boundKind) {
            switch (boundKind) {
                case EQ:
                    return IncorporationBinaryOpKind.ADD_EQ_BOUND;
                case LOWER:
                    return IncorporationBinaryOpKind.ADD_LOWER_BOUND;
                case UPPER:
                    return IncorporationBinaryOpKind.ADD_UPPER_BOUND;
                default:
                    Assert.error("Can't get here!");
                    return null;
            }
        }

        boolean doIncorporationOp(IncorporationBinaryOpKind opKind, Type op1, Type op2, Warner warn, Infer infer) {
            IncorporationBinaryOp newOp = infer.new IncorporationBinaryOp(opKind, op1, op2);
            Boolean res = infer.incorporationCache.get(newOp);
            if (res == null) {
                infer.incorporationCache.put(newOp, res = newOp.apply(warn));
            }
            return res;
        }
    }

    enum IncorporationBinaryOpKind {
        IS_SUBTYPE() {
            @Override
            boolean apply(Type op1, Type op2, Warner warn, Types types) {
                return types.isSubtypeUnchecked(op1, op2, warn);
            }
        },
        IS_SAME_TYPE() {
            @Override
            boolean apply(Type op1, Type op2, Warner warn, Types types) {
                return types.isSameType(op1, op2);
            }
        },
        ADD_UPPER_BOUND() {
            @Override
            boolean apply(Type op1, Type op2, Warner warn, Types types) {
                UndetVar uv = (UndetVar) op1;
                uv.addBound(InferenceBound.UPPER, op2, types);
                return true;
            }
        },
        ADD_LOWER_BOUND() {
            @Override
            boolean apply(Type op1, Type op2, Warner warn, Types types) {
                UndetVar uv = (UndetVar) op1;
                uv.addBound(InferenceBound.LOWER, op2, types);
                return true;
            }
        },
        ADD_EQ_BOUND() {
            @Override
            boolean apply(Type op1, Type op2, Warner warn, Types types) {
                UndetVar uv = (UndetVar) op1;
                uv.addBound(InferenceBound.EQ, op2, types);
                return true;
            }
        };

        abstract boolean apply(Type op1, Type op2, Warner warn, Types types);
    }

    enum BoundErrorKind {

        BAD_UPPER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("incompatible.upper.bounds", uv.qtype,
                        uv.getBounds(InferenceBound.UPPER));
            }
        },

        BAD_EQ_UPPER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("incompatible.eq.upper.bounds", uv.qtype,
                        uv.getBounds(InferenceBound.EQ), uv.getBounds(InferenceBound.UPPER));
            }
        },

        BAD_EQ_LOWER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("incompatible.eq.lower.bounds", uv.qtype,
                        uv.getBounds(InferenceBound.EQ), uv.getBounds(InferenceBound.LOWER));
            }
        },

        UPPER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("inferred.do.not.conform.to.upper.bounds", uv.inst,
                        uv.getBounds(InferenceBound.UPPER));
            }
        },

        LOWER() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("inferred.do.not.conform.to.lower.bounds", uv.inst,
                        uv.getBounds(InferenceBound.LOWER));
            }
        },

        EQ() {
            @Override
            InapplicableMethodException setMessage(InferenceException ex, UndetVar uv) {
                return ex.setMessage("inferred.do.not.conform.to.eq.bounds", uv.inst,
                        uv.getBounds(InferenceBound.EQ));
            }
        };

        abstract InapplicableMethodException setMessage(InferenceException ex, UndetVar uv);
    }

    enum InferenceStep {

        EQ(InferenceBound.EQ) {
            @Override
            Type solve(UndetVar uv, InferenceContext inferenceContext) {
                return filterBounds(uv, inferenceContext).head;
            }
        },

        LOWER(InferenceBound.LOWER) {
            @Override
            Type solve(UndetVar uv, InferenceContext inferenceContext) {
                Infer infer = inferenceContext.infer();
                List<Type> lobounds = filterBounds(uv, inferenceContext);

                Type owntype = lobounds.tail.tail == null ? lobounds.head : infer.types.lub(lobounds);
                if (owntype.isPrimitive() || owntype.hasTag(ERROR)) {
                    throw infer.inferenceException
                            .setMessage("no.unique.minimal.instance.exists",
                                    uv.qtype, lobounds);
                } else {
                    return owntype;
                }
            }
        },

        THROWS(InferenceBound.UPPER) {
            @Override
            public boolean accepts(UndetVar t, InferenceContext inferenceContext) {
                if ((t.qtype.tsym.flags() & Flags.THROWS) == 0) {

                    return false;
                }
                if (t.getBounds(InferenceBound.EQ, InferenceBound.LOWER, InferenceBound.UPPER)
                        .diff(t.getDeclaredBounds()).nonEmpty()) {

                    return false;
                }
                Infer infer = inferenceContext.infer();
                for (Type db : t.getDeclaredBounds()) {
                    if (t.isInterface()) continue;
                    if (infer.types.asSuper(infer.syms.runtimeExceptionType, db.tsym) != null) {

                        return true;
                    }
                }

                return false;
            }

            @Override
            Type solve(UndetVar uv, InferenceContext inferenceContext) {
                return inferenceContext.infer().syms.runtimeExceptionType;
            }
        },

        UPPER(InferenceBound.UPPER) {
            @Override
            Type solve(UndetVar uv, InferenceContext inferenceContext) {
                Infer infer = inferenceContext.infer();
                List<Type> hibounds = filterBounds(uv, inferenceContext);

                Type owntype = hibounds.tail.tail == null ? hibounds.head : infer.types.glb(hibounds);
                if (owntype.isPrimitive() || owntype.hasTag(ERROR)) {
                    throw infer.inferenceException
                            .setMessage("no.unique.maximal.instance.exists",
                                    uv.qtype, hibounds);
                } else {
                    return owntype;
                }
            }
        },

        UPPER_LEGACY(InferenceBound.UPPER) {
            @Override
            public boolean accepts(UndetVar t, InferenceContext inferenceContext) {
                return !inferenceContext.free(t.getBounds(ib)) && !t.isCaptured();
            }

            @Override
            Type solve(UndetVar uv, InferenceContext inferenceContext) {
                return UPPER.solve(uv, inferenceContext);
            }
        },

        CAPTURED(InferenceBound.UPPER) {
            @Override
            public boolean accepts(UndetVar t, InferenceContext inferenceContext) {
                return t.isCaptured() &&
                        !inferenceContext.free(t.getBounds(InferenceBound.UPPER, InferenceBound.LOWER));
            }

            @Override
            Type solve(UndetVar uv, InferenceContext inferenceContext) {
                Infer infer = inferenceContext.infer();
                Type upper = UPPER.filterBounds(uv, inferenceContext).nonEmpty() ?
                        UPPER.solve(uv, inferenceContext) :
                        infer.syms.objectType;
                Type lower = LOWER.filterBounds(uv, inferenceContext).nonEmpty() ?
                        LOWER.solve(uv, inferenceContext) :
                        infer.syms.botType;
                CapturedType prevCaptured = (CapturedType) uv.qtype;
                return new CapturedType(prevCaptured.tsym.name, prevCaptured.tsym.owner, upper, lower, prevCaptured.wildcard);
            }
        };
        final InferenceBound ib;

        InferenceStep(InferenceBound ib) {
            this.ib = ib;
        }

        abstract Type solve(UndetVar uv, InferenceContext inferenceContext);

        public boolean accepts(UndetVar t, InferenceContext inferenceContext) {
            return filterBounds(t, inferenceContext).nonEmpty() && !t.isCaptured();
        }

        List<Type> filterBounds(UndetVar uv, InferenceContext inferenceContext) {
            return Type.filter(uv.getBounds(ib), new BoundFilter(inferenceContext));
        }
    }

    enum LegacyInferenceSteps {
        EQ_LOWER(EnumSet.of(InferenceStep.EQ, InferenceStep.LOWER)),
        EQ_UPPER(EnumSet.of(InferenceStep.EQ, InferenceStep.UPPER_LEGACY));
        final EnumSet<InferenceStep> steps;

        LegacyInferenceSteps(EnumSet<InferenceStep> steps) {
            this.steps = steps;
        }
    }

    enum GraphInferenceSteps {
        EQ(EnumSet.of(InferenceStep.EQ)),
        EQ_LOWER(EnumSet.of(InferenceStep.EQ, InferenceStep.LOWER)),
        EQ_LOWER_THROWS_UPPER_CAPTURED(EnumSet.of(InferenceStep.EQ, InferenceStep.LOWER, InferenceStep.UPPER, InferenceStep.THROWS, InferenceStep.CAPTURED));
        final EnumSet<InferenceStep> steps;

        GraphInferenceSteps(EnumSet<InferenceStep> steps) {
            this.steps = steps;
        }
    }

    enum DependencyKind implements GraphUtils.DependencyKind {

        BOUND("dotted"),

        STUCK("dashed");
        final String dotSyle;

        DependencyKind(String dotSyle) {
            this.dotSyle = dotSyle;
        }

        @Override
        public String getDotStyle() {
            return dotSyle;
        }
    }


    interface GraphStrategy {

        Node pickNode(InferenceGraph g) throws NodeNotFoundException;

        boolean done();

        class NodeNotFoundException extends RuntimeException {
            private static final long serialVersionUID = 0;
            InferenceGraph graph;

            public NodeNotFoundException(InferenceGraph graph) {
                this.graph = graph;
            }
        }
    }

    interface FreeTypeListener {
        void typesInferred(InferenceContext inferenceContext);
    }

    public static class InferenceException extends InapplicableMethodException {
        private static final long serialVersionUID = 0;
        List<JCDiagnostic> messages = List.nil();

        InferenceException(JCDiagnostic.Factory diags) {
            super(diags);
        }

        @Override
        InapplicableMethodException setMessage() {

            return this;
        }

        @Override
        InapplicableMethodException setMessage(JCDiagnostic diag) {
            messages = messages.append(diag);
            return this;
        }

        @Override
        public JCDiagnostic getDiagnostic() {
            return messages.head;
        }

        void clear() {
            messages = List.nil();
        }
    }

    protected static class BoundFilter implements Filter<Type> {
        InferenceContext inferenceContext;

        public BoundFilter(InferenceContext inferenceContext) {
            this.inferenceContext = inferenceContext;
        }

        @Override
        public boolean accepts(Type t) {
            return !t.isErroneous() && !inferenceContext.free(t) &&
                    !t.hasTag(BOT);
        }
    }

    class ImplicitArgType extends DeferredAttr.DeferredTypeMap {
        public ImplicitArgType(Symbol msym, Resolve.MethodResolutionPhase phase) {
            rs.deferredAttr.super(AttrMode.SPECULATIVE, msym, phase);
        }

        public Type apply(Type t) {
            t = types.erasure(super.apply(t));
            if (t.hasTag(BOT))


                t = types.boxedClass(syms.voidType).type;
            return t;
        }
    }

    class MultiUndetVarListener implements UndetVar.UndetVarListener {
        boolean changed;
        List<Type> undetvars;

        public MultiUndetVarListener(List<Type> undetvars) {
            this.undetvars = undetvars;
            for (Type t : undetvars) {
                UndetVar uv = (UndetVar) t;
                uv.listener = this;
            }
        }

        public void varChanged(UndetVar uv, Set<InferenceBound> ibs) {

            if (incorporationCache.size() < MAX_INCORPORATION_STEPS) {
                changed = true;
            }
        }

        void reset() {
            changed = false;
        }

        void detach() {
            for (Type t : undetvars) {
                UndetVar uv = (UndetVar) t;
                uv.listener = null;
            }
        }
    }

    class IncorporationBinaryOp {
        IncorporationBinaryOpKind opKind;
        Type op1;
        Type op2;

        IncorporationBinaryOp(IncorporationBinaryOpKind opKind, Type op1, Type op2) {
            this.opKind = opKind;
            this.op1 = op1;
            this.op2 = op2;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof IncorporationBinaryOp)) {
                return false;
            } else {
                IncorporationBinaryOp that = (IncorporationBinaryOp) o;
                return opKind == that.opKind &&
                        types.isSameType(op1, that.op1, true) &&
                        types.isSameType(op2, that.op2, true);
            }
        }

        @Override
        public int hashCode() {
            int result = opKind.hashCode();
            result *= 127;
            result += types.hashCode(op1);
            result *= 127;
            result += types.hashCode(op2);
            return result;
        }

        boolean apply(Warner warn) {
            return opKind.apply(op1, op2, warn, types);
        }
    }

    abstract class LeafSolver implements GraphStrategy {
        public Node pickNode(InferenceGraph g) {
            if (g.nodes.isEmpty()) {

                throw new NodeNotFoundException(g);
            }
            return g.nodes.get(0);
        }

        boolean isSubtype(Type s, Type t, Warner warn, Infer infer) {
            return doIncorporationOp(IncorporationBinaryOpKind.IS_SUBTYPE, s, t, warn, infer);
        }

        boolean isSameType(Type s, Type t, Infer infer) {
            return doIncorporationOp(IncorporationBinaryOpKind.IS_SAME_TYPE, s, t, null, infer);
        }

        void addBound(InferenceBound ib, UndetVar uv, Type b, Infer infer) {
            doIncorporationOp(opFor(ib), uv, b, null, infer);
        }

        IncorporationBinaryOpKind opFor(InferenceBound boundKind) {
            switch (boundKind) {
                case EQ:
                    return IncorporationBinaryOpKind.ADD_EQ_BOUND;
                case LOWER:
                    return IncorporationBinaryOpKind.ADD_LOWER_BOUND;
                case UPPER:
                    return IncorporationBinaryOpKind.ADD_UPPER_BOUND;
                default:
                    Assert.error("Can't get here!");
                    return null;
            }
        }

        boolean doIncorporationOp(IncorporationBinaryOpKind opKind, Type op1, Type op2, Warner warn, Infer infer) {
            IncorporationBinaryOp newOp = infer.new IncorporationBinaryOp(opKind, op1, op2);
            Boolean res = infer.incorporationCache.get(newOp);
            if (res == null) {
                infer.incorporationCache.put(newOp, res = newOp.apply(warn));
            }
            return res;
        }
    }

    abstract class BestLeafSolver extends LeafSolver {

        final Map<Node, Pair<List<Node>, Integer>> treeCache =
                new HashMap<Node, Pair<List<Node>, Integer>>();
        final Pair<List<Node>, Integer> noPath =
                new Pair<List<Node>, Integer>(null, Integer.MAX_VALUE);
        List<Type> varsToSolve;

        BestLeafSolver(List<Type> varsToSolve) {
            this.varsToSolve = varsToSolve;
        }

        Pair<List<Node>, Integer> computeTreeToLeafs(Node n) {
            Pair<List<Node>, Integer> cachedPath = treeCache.get(n);
            if (cachedPath == null) {

                if (n.isLeaf()) {

                    cachedPath = new Pair<List<Node>, Integer>(List.of(n), n.data.length());
                } else {

                    Pair<List<Node>, Integer> path = new Pair<List<Node>, Integer>(List.of(n), n.data.length());
                    for (Node n2 : n.getAllDependencies()) {
                        if (n2 == n) continue;
                        Pair<List<Node>, Integer> subpath = computeTreeToLeafs(n2);
                        path = new Pair<List<Node>, Integer>(
                                path.fst.prependList(subpath.fst),
                                path.snd + subpath.snd);
                    }
                    cachedPath = path;
                }

                treeCache.put(n, cachedPath);
            }
            return cachedPath;
        }

        @Override
        public Node pickNode(final InferenceGraph g) {
            treeCache.clear();
            Pair<List<Node>, Integer> bestPath = noPath;
            for (Node n : g.nodes) {
                if (!Collections.disjoint(n.data, varsToSolve)) {
                    Pair<List<Node>, Integer> path = computeTreeToLeafs(n);


                    if (path.snd < bestPath.snd) {
                        bestPath = path;
                    }
                }
            }
            if (bestPath == noPath) {

                throw new NodeNotFoundException(g);
            }
            return bestPath.fst.head;
        }
    }

    class GraphSolver {
        InferenceContext inferenceContext;
        Map<Type, Set<Type>> stuckDeps;
        Warner warn;

        GraphSolver(InferenceContext inferenceContext, Map<Type, Set<Type>> stuckDeps, Warner warn) {
            this.inferenceContext = inferenceContext;
            this.stuckDeps = stuckDeps;
            this.warn = warn;
        }

        void solve(GraphStrategy sstrategy) {
            checkWithinBounds(inferenceContext, warn);
            InferenceGraph inferenceGraph = new InferenceGraph(stuckDeps);
            while (!sstrategy.done()) {
                Node nodeToSolve = sstrategy.pickNode(inferenceGraph);
                List<Type> varsToSolve = List.from(nodeToSolve.data);
                List<Type> saved_undet = inferenceContext.save();
                try {

                    outer:
                    while (Type.containsAny(inferenceContext.restvars(), varsToSolve)) {

                        for (GraphInferenceSteps step : GraphInferenceSteps.values()) {
                            if (inferenceContext.solveBasic(varsToSolve, step.steps)) {
                                checkWithinBounds(inferenceContext, warn);
                                continue outer;
                            }
                        }

                        throw inferenceException.setMessage();
                    }
                } catch (InferenceException ex) {

                    inferenceContext.rollback(saved_undet);
                    instantiateAsUninferredVars(varsToSolve, inferenceContext);
                    checkWithinBounds(inferenceContext, warn);
                }
                inferenceGraph.deleteNode(nodeToSolve);
            }
        }

        class InferenceGraph {

            ArrayList<Node> nodes;

            InferenceGraph(Map<Type, Set<Type>> optDeps) {
                initNodes(optDeps);
            }

            public Node findNode(Type t) {
                for (Node n : nodes) {
                    if (n.data.contains(t)) {
                        return n;
                    }
                }
                return null;
            }

            public void deleteNode(Node n) {
                Assert.check(nodes.contains(n));
                nodes.remove(n);
                notifyUpdate(n, null);
            }

            void notifyUpdate(Node from, Node to) {
                for (Node n : nodes) {
                    n.graphChanged(from, to);
                }
            }

            void initNodes(Map<Type, Set<Type>> stuckDeps) {

                nodes = new ArrayList<Node>();
                for (Type t : inferenceContext.restvars()) {
                    nodes.add(new Node(t));
                }

                for (Node n_i : nodes) {
                    Type i = n_i.data.first();
                    Set<Type> optDepsByNode = stuckDeps.get(i);
                    for (Node n_j : nodes) {
                        Type j = n_j.data.first();
                        UndetVar uv_i = (UndetVar) inferenceContext.asFree(i);
                        if (Type.containsAny(uv_i.getBounds(InferenceBound.values()), List.of(j))) {

                            n_i.addDependency(DependencyKind.BOUND, n_j);
                        }
                        if (optDepsByNode != null && optDepsByNode.contains(j)) {

                            n_i.addDependency(DependencyKind.STUCK, n_j);
                        }
                    }
                }

                ArrayList<Node> acyclicNodes = new ArrayList<Node>();
                for (List<? extends Node> conSubGraph : GraphUtils.tarjan(nodes)) {
                    if (conSubGraph.length() > 1) {
                        Node root = conSubGraph.head;
                        root.mergeWith(conSubGraph.tail);
                        for (Node n : conSubGraph) {
                            notifyUpdate(n, root);
                        }
                    }
                    acyclicNodes.add(conSubGraph.head);
                }
                nodes = acyclicNodes;
            }

            String toDot() {
                StringBuilder buf = new StringBuilder();
                for (Type t : inferenceContext.undetvars) {
                    UndetVar uv = (UndetVar) t;
                    buf.append(String.format("var %s - upper bounds = %s, lower bounds = %s, eq bounds = %s\\n",
                            uv.qtype, uv.getBounds(InferenceBound.UPPER), uv.getBounds(InferenceBound.LOWER),
                            uv.getBounds(InferenceBound.EQ)));
                }
                return GraphUtils.toDot(nodes, "inferenceGraph" + hashCode(), buf.toString());
            }

            class Node extends TarjanNode<ListBuffer<Type>> {

                EnumMap<DependencyKind, Set<Node>> deps;

                Node(Type ivar) {
                    super(ListBuffer.of(ivar));
                    this.deps = new EnumMap<DependencyKind, Set<Node>>(DependencyKind.class);
                }

                @Override
                public GraphUtils.DependencyKind[] getSupportedDependencyKinds() {
                    return DependencyKind.values();
                }

                @Override
                public String getDependencyName(GraphUtils.Node<ListBuffer<Type>> to, GraphUtils.DependencyKind dk) {
                    if (dk == DependencyKind.STUCK) return "";
                    else {
                        StringBuilder buf = new StringBuilder();
                        String sep = "";
                        for (Type from : data) {
                            UndetVar uv = (UndetVar) inferenceContext.asFree(from);
                            for (Type bound : uv.getBounds(InferenceBound.values())) {
                                if (bound.containsAny(List.from(to.data))) {
                                    buf.append(sep);
                                    buf.append(bound);
                                    sep = ",";
                                }
                            }
                        }
                        return buf.toString();
                    }
                }

                @Override
                public Iterable<? extends Node> getAllDependencies() {
                    return getDependencies(DependencyKind.values());
                }

                @Override
                public Iterable<? extends TarjanNode<ListBuffer<Type>>> getDependenciesByKind(GraphUtils.DependencyKind dk) {
                    return getDependencies((DependencyKind) dk);
                }

                protected Set<Node> getDependencies(DependencyKind... depKinds) {
                    Set<Node> buf = new LinkedHashSet<Node>();
                    for (DependencyKind dk : depKinds) {
                        Set<Node> depsByKind = deps.get(dk);
                        if (depsByKind != null) {
                            buf.addAll(depsByKind);
                        }
                    }
                    return buf;
                }

                protected void addDependency(DependencyKind dk, Node depToAdd) {
                    Set<Node> depsByKind = deps.get(dk);
                    if (depsByKind == null) {
                        depsByKind = new LinkedHashSet<Node>();
                        deps.put(dk, depsByKind);
                    }
                    depsByKind.add(depToAdd);
                }

                protected void addDependencies(DependencyKind dk, Set<Node> depsToAdd) {
                    for (Node n : depsToAdd) {
                        addDependency(dk, n);
                    }
                }

                protected Set<DependencyKind> removeDependency(Node n) {
                    Set<DependencyKind> removedKinds = new HashSet<>();
                    for (DependencyKind dk : DependencyKind.values()) {
                        Set<Node> depsByKind = deps.get(dk);
                        if (depsByKind == null) continue;
                        if (depsByKind.remove(n)) {
                            removedKinds.add(dk);
                        }
                    }
                    return removedKinds;
                }

                protected Set<Node> closure(DependencyKind... depKinds) {
                    boolean progress = true;
                    Set<Node> closure = new HashSet<Node>();
                    closure.add(this);
                    while (progress) {
                        progress = false;
                        for (Node n1 : new HashSet<Node>(closure)) {
                            progress = closure.addAll(n1.getDependencies(depKinds));
                        }
                    }
                    return closure;
                }

                protected boolean isLeaf() {

                    Set<Node> allDeps = getDependencies(DependencyKind.BOUND, DependencyKind.STUCK);
                    if (allDeps.isEmpty()) return true;
                    for (Node n : allDeps) {
                        if (n != this) {
                            return false;
                        }
                    }
                    return true;
                }

                protected void mergeWith(List<? extends Node> nodes) {
                    for (Node n : nodes) {
                        Assert.check(n.data.length() == 1, "Attempt to merge a compound node!");
                        data.appendList(n.data);
                        for (DependencyKind dk : DependencyKind.values()) {
                            addDependencies(dk, n.getDependencies(dk));
                        }
                    }

                    EnumMap<DependencyKind, Set<Node>> deps2 = new EnumMap<DependencyKind, Set<Node>>(DependencyKind.class);
                    for (DependencyKind dk : DependencyKind.values()) {
                        for (Node d : getDependencies(dk)) {
                            Set<Node> depsByKind = deps2.get(dk);
                            if (depsByKind == null) {
                                depsByKind = new LinkedHashSet<Node>();
                                deps2.put(dk, depsByKind);
                            }
                            if (data.contains(d.data.first())) {
                                depsByKind.add(this);
                            } else {
                                depsByKind.add(d);
                            }
                        }
                    }
                    deps = deps2;
                }

                private void graphChanged(Node from, Node to) {
                    for (DependencyKind dk : removeDependency(from)) {
                        if (to != null) {
                            addDependency(dk, to);
                        }
                    }
                }
            }
        }
    }

    class InferenceContext {

        List<Type> undetvars;

        List<Type> inferencevars;
        Map<FreeTypeListener, List<Type>> freeTypeListeners =
                new HashMap<FreeTypeListener, List<Type>>();
        List<FreeTypeListener> freetypeListeners = List.nil();
        Mapping fromTypeVarFun = new Mapping("fromTypeVarFunWithBounds") {

            public Type apply(Type t) {
                if (t.hasTag(TYPEVAR)) {
                    TypeVar tv = (TypeVar) t;
                    if (tv.isCaptured()) {
                        return new CapturedUndetVar((CapturedType) tv, types);
                    } else {
                        return new UndetVar(tv, types);
                    }
                } else {
                    return t.map(this);
                }
            }
        };

        public InferenceContext(List<Type> inferencevars) {
            this.undetvars = Type.map(inferencevars, fromTypeVarFun);
            this.inferencevars = inferencevars;
        }

        void addVar(TypeVar t) {
            this.undetvars = this.undetvars.prepend(fromTypeVarFun.apply(t));
            this.inferencevars = this.inferencevars.prepend(t);
        }

        List<Type> inferenceVars() {
            return inferencevars;
        }

        List<Type> restvars() {
            return filterVars(new Filter<UndetVar>() {
                public boolean accepts(UndetVar uv) {
                    return uv.inst == null;
                }
            });
        }

        List<Type> instvars() {
            return filterVars(new Filter<UndetVar>() {
                public boolean accepts(UndetVar uv) {
                    return uv.inst != null;
                }
            });
        }

        final List<Type> boundedVars() {
            return filterVars(new Filter<UndetVar>() {
                public boolean accepts(UndetVar uv) {
                    return uv.getBounds(InferenceBound.UPPER)
                            .diff(uv.getDeclaredBounds())
                            .appendList(uv.getBounds(InferenceBound.EQ, InferenceBound.LOWER)).nonEmpty();
                }
            });
        }

        private List<Type> filterVars(Filter<UndetVar> fu) {
            ListBuffer<Type> res = new ListBuffer<>();
            for (Type t : undetvars) {
                UndetVar uv = (UndetVar) t;
                if (fu.accepts(uv)) {
                    res.append(uv.qtype);
                }
            }
            return res.toList();
        }

        final boolean free(Type t) {
            return t.containsAny(inferencevars);
        }

        final boolean free(List<Type> ts) {
            for (Type t : ts) {
                if (free(t)) return true;
            }
            return false;
        }

        final List<Type> freeVarsIn(Type t) {
            ListBuffer<Type> buf = new ListBuffer<>();
            for (Type iv : inferenceVars()) {
                if (t.contains(iv)) {
                    buf.add(iv);
                }
            }
            return buf.toList();
        }

        final List<Type> freeVarsIn(List<Type> ts) {
            ListBuffer<Type> buf = new ListBuffer<>();
            for (Type t : ts) {
                buf.appendList(freeVarsIn(t));
            }
            ListBuffer<Type> buf2 = new ListBuffer<>();
            for (Type t : buf) {
                if (!buf2.contains(t)) {
                    buf2.add(t);
                }
            }
            return buf2.toList();
        }

        final Type asFree(Type t) {
            return types.subst(t, inferencevars, undetvars);
        }

        final List<Type> asFree(List<Type> ts) {
            ListBuffer<Type> buf = new ListBuffer<>();
            for (Type t : ts) {
                buf.append(asFree(t));
            }
            return buf.toList();
        }

        List<Type> instTypes() {
            ListBuffer<Type> buf = new ListBuffer<>();
            for (Type t : undetvars) {
                UndetVar uv = (UndetVar) t;
                buf.append(uv.inst != null ? uv.inst : uv.qtype);
            }
            return buf.toList();
        }

        Type asInstType(Type t) {
            return types.subst(t, inferencevars, instTypes());
        }

        List<Type> asInstTypes(List<Type> ts) {
            ListBuffer<Type> buf = new ListBuffer<>();
            for (Type t : ts) {
                buf.append(asInstType(t));
            }
            return buf.toList();
        }

        void addFreeTypeListener(List<Type> types, FreeTypeListener ftl) {
            freeTypeListeners.put(ftl, freeVarsIn(types));
        }

        void notifyChange() {
            notifyChange(inferencevars.diff(restvars()));
        }

        void notifyChange(List<Type> inferredVars) {
            InferenceException thrownEx = null;
            for (Map.Entry<FreeTypeListener, List<Type>> entry :
                    new HashMap<FreeTypeListener, List<Type>>(freeTypeListeners).entrySet()) {
                if (!Type.containsAny(entry.getValue(), inferencevars.diff(inferredVars))) {
                    try {
                        entry.getKey().typesInferred(this);
                        freeTypeListeners.remove(entry.getKey());
                    } catch (InferenceException ex) {
                        if (thrownEx == null) {
                            thrownEx = ex;
                        }
                    }
                }
            }


            if (thrownEx != null) {
                throw thrownEx;
            }
        }

        List<Type> save() {
            ListBuffer<Type> buf = new ListBuffer<>();
            for (Type t : undetvars) {
                UndetVar uv = (UndetVar) t;
                UndetVar uv2 = new UndetVar((TypeVar) uv.qtype, types);
                for (InferenceBound ib : InferenceBound.values()) {
                    for (Type b : uv.getBounds(ib)) {
                        uv2.addBound(ib, b, types);
                    }
                }
                uv2.inst = uv.inst;
                buf.add(uv2);
            }
            return buf.toList();
        }

        void rollback(List<Type> saved_undet) {
            Assert.check(saved_undet != null && saved_undet.length() == undetvars.length());

            for (Type t : undetvars) {
                UndetVar uv = (UndetVar) t;
                UndetVar uv_saved = (UndetVar) saved_undet.head;
                for (InferenceBound ib : InferenceBound.values()) {
                    uv.setBounds(ib, uv_saved.getBounds(ib));
                }
                uv.inst = uv_saved.inst;
                saved_undet = saved_undet.tail;
            }
        }

        void dupTo(final InferenceContext that) {
            that.inferencevars = that.inferencevars.appendList(inferencevars);
            that.undetvars = that.undetvars.appendList(undetvars);


            for (Type t : inferencevars) {
                that.freeTypeListeners.put(new FreeTypeListener() {
                    public void typesInferred(InferenceContext inferenceContext) {
                        InferenceContext.this.notifyChange();
                    }
                }, List.of(t));
            }
        }

        private void solve(GraphStrategy ss, Warner warn) {
            solve(ss, new HashMap<Type, Set<Type>>(), warn);
        }

        private void solve(GraphStrategy ss, Map<Type, Set<Type>> stuckDeps, Warner warn) {
            GraphSolver s = new GraphSolver(this, stuckDeps, warn);
            s.solve(ss);
        }

        public void solve(Warner warn) {
            solve(new LeafSolver() {
                public boolean done() {
                    return restvars().isEmpty();
                }
            }, warn);
        }

        public void solve(final List<Type> vars, Warner warn) {
            solve(new BestLeafSolver(vars) {
                public boolean done() {
                    return !free(asInstTypes(vars));
                }
            }, warn);
        }

        public void solveAny(List<Type> varsToSolve, Map<Type, Set<Type>> optDeps, Warner warn) {
            solve(new BestLeafSolver(varsToSolve.intersect(restvars())) {
                public boolean done() {
                    return instvars().intersect(varsToSolve).nonEmpty();
                }
            }, optDeps, warn);
        }

        private boolean solveBasic(EnumSet<InferenceStep> steps) {
            return solveBasic(inferencevars, steps);
        }

        private boolean solveBasic(List<Type> varsToSolve, EnumSet<InferenceStep> steps) {
            boolean changed = false;
            for (Type t : varsToSolve.intersect(restvars())) {
                UndetVar uv = (UndetVar) asFree(t);
                for (InferenceStep step : steps) {
                    if (step.accepts(uv, this)) {
                        uv.inst = step.solve(uv, this);
                        changed = true;
                        break;
                    }
                }
            }
            return changed;
        }

        public void solveLegacy(boolean partial, Warner warn, EnumSet<InferenceStep> steps) {
            while (true) {
                boolean stuck = !solveBasic(steps);
                if (restvars().isEmpty() || partial) {

                    break;
                } else if (stuck) {


                    instantiateAsUninferredVars(restvars(), this);
                    break;
                } else {


                    for (Type t : undetvars) {
                        UndetVar uv = (UndetVar) t;
                        uv.substBounds(inferenceVars(), instTypes(), types);
                    }
                }
            }
            checkWithinBounds(this, warn);
        }

        private Infer infer() {

            return Infer.this;
        }
    }

}
