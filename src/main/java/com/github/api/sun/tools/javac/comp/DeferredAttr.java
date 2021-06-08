package com.github.api.sun.tools.javac.comp;

import com.github.api.sun.tools.javac.code.*;
import com.github.api.sun.tools.javac.code.Symbol.ClassSymbol;
import com.github.api.sun.tools.javac.code.Type.ForAll;
import com.github.api.sun.tools.javac.code.Type.Mapping;
import com.github.api.sun.tools.javac.comp.Attr.ResultInfo;
import com.github.api.sun.tools.javac.comp.Infer.InferenceContext;
import com.github.api.sun.tools.javac.comp.Resolve.MethodResolutionPhase;
import com.github.api.sun.tools.javac.tree.*;
import com.github.api.sun.tools.javac.tree.JCTree.*;
import com.github.api.sun.tools.javac.util.*;
import com.github.api.sun.tools.javac.util.List;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import java.util.*;

import static com.github.api.sun.tools.javac.code.TypeTag.*;
import static com.github.api.sun.tools.javac.tree.JCTree.Tag.*;

public class DeferredAttr extends JCTree.Visitor {
    protected static final Context.Key<DeferredAttr> deferredAttrKey =
            new Context.Key<DeferredAttr>();
    final Attr attr;
    final Check chk;
    final JCDiagnostic.Factory diags;
    final Enter enter;
    final Infer infer;
    final Resolve rs;
    final Log log;
    final Symtab syms;
    final TreeMaker make;
    final Types types;
    final JCTree stuckTree;
    final DeferredAttrContext emptyDeferredAttrContext;
    protected TreeScanner unenterScanner = new TreeScanner() {
        @Override
        public void visitClassDef(JCClassDecl tree) {
            ClassSymbol csym = tree.sym;


            if (csym == null) return;
            enter.typeEnvs.remove(csym);
            chk.compiled.remove(csym.flatname);
            syms.classes.remove(csym.flatname);
            super.visitClassDef(tree);
        }
    };
    DeferredTypeCompleter basicCompleter = new DeferredTypeCompleter() {
        public Type complete(DeferredType dt, ResultInfo resultInfo, DeferredAttrContext deferredAttrContext) {
            switch (deferredAttrContext.mode) {
                case SPECULATIVE:


                    Assert.check(dt.mode == null || dt.mode == AttrMode.SPECULATIVE);
                    JCTree speculativeTree = attribSpeculative(dt.tree, dt.env, resultInfo);
                    dt.speculativeCache.put(speculativeTree, resultInfo);
                    return speculativeTree.type;
                case CHECK:
                    Assert.check(dt.mode != null);
                    return attr.attribTree(dt.tree, dt.env, resultInfo);
            }
            Assert.error();
            return null;
        }
    };
    DeferredTypeCompleter dummyCompleter = new DeferredTypeCompleter() {
        public Type complete(DeferredType dt, ResultInfo resultInfo, DeferredAttrContext deferredAttrContext) {
            Assert.check(deferredAttrContext.mode == AttrMode.CHECK);
            return dt.tree.type = Type.stuckType;
        }
    };
    DeferredStuckPolicy dummyStuckPolicy = new DeferredStuckPolicy() {
        @Override
        public boolean isStuck() {
            return false;
        }

        @Override
        public Set<Type> stuckVars() {
            return Collections.emptySet();
        }

        @Override
        public Set<Type> depVars() {
            return Collections.emptySet();
        }
    };
    private EnumSet<Tag> deferredCheckerTags =
            EnumSet.of(LAMBDA, REFERENCE, PARENS, TYPECAST,
                    CONDEXPR, NEWCLASS, APPLY, LITERAL);

    protected DeferredAttr(Context context) {
        context.put(deferredAttrKey, this);
        attr = Attr.instance(context);
        chk = Check.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        enter = Enter.instance(context);
        infer = Infer.instance(context);
        rs = Resolve.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
        Names names = Names.instance(context);
        stuckTree = make.Ident(names.empty).setType(Type.stuckType);
        emptyDeferredAttrContext =
                new DeferredAttrContext(AttrMode.CHECK, null, MethodResolutionPhase.BOX, infer.emptyContext, null, null) {
                    @Override
                    void addDeferredAttrNode(DeferredType dt, ResultInfo ri, DeferredStuckPolicy deferredStuckPolicy) {
                        Assert.error("Empty deferred context!");
                    }

                    @Override
                    void complete() {
                        Assert.error("Empty deferred context!");
                    }
                };
    }

    public static DeferredAttr instance(Context context) {
        DeferredAttr instance = context.get(deferredAttrKey);
        if (instance == null)
            instance = new DeferredAttr(context);
        return instance;
    }

    JCTree attribSpeculative(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo) {
        final JCTree newTree = new TreeCopier<Object>(make).copy(tree);
        Env<AttrContext> speculativeEnv = env.dup(newTree, env.info.dup(env.info.scope.dupUnshared()));
        speculativeEnv.info.scope.owner = env.info.scope.owner;
        Log.DeferredDiagnosticHandler deferredDiagnosticHandler =
                new Log.DeferredDiagnosticHandler(log, new Filter<JCDiagnostic>() {
                    public boolean accepts(final JCDiagnostic d) {
                        class PosScanner extends TreeScanner {
                            boolean found = false;

                            @Override
                            public void scan(JCTree tree) {
                                if (tree != null &&
                                        tree.pos() == d.getDiagnosticPosition()) {
                                    found = true;
                                }
                                super.scan(tree);
                            }
                        }
                        PosScanner posScanner = new PosScanner();
                        posScanner.scan(newTree);
                        return posScanner.found;
                    }
                });
        try {
            attr.attribTree(newTree, speculativeEnv, resultInfo);
            unenterScanner.scan(newTree);
            return newTree;
        } finally {
            unenterScanner.scan(newTree);
            log.popDiagnosticHandler(deferredDiagnosticHandler);
        }
    }

    boolean isDeferred(Env<AttrContext> env, JCExpression expr) {
        DeferredChecker dc = new DeferredChecker(env);
        dc.scan(expr);
        return dc.result.isPoly();
    }

    public enum AttrMode {

        SPECULATIVE,

        CHECK
    }

    enum ArgumentExpressionKind {

        POLY,

        NO_POLY,

        PRIMITIVE;

        static ArgumentExpressionKind standaloneKind(Type type, Types types) {
            return types.unboxedTypeOrType(type).isPrimitive() ?
                    ArgumentExpressionKind.PRIMITIVE :
                    ArgumentExpressionKind.NO_POLY;
        }

        static ArgumentExpressionKind methodKind(Symbol sym, Types types) {
            Type restype = sym.type.getReturnType();
            if (sym.type.hasTag(FORALL) &&
                    restype.containsAny(((ForAll) sym.type).tvars)) {
                return ArgumentExpressionKind.POLY;
            } else {
                return ArgumentExpressionKind.standaloneKind(restype, types);
            }
        }

        public final boolean isPoly() {
            return this == POLY;
        }

        public final boolean isPrimitive() {
            return this == PRIMITIVE;
        }
    }

    interface DeferredTypeCompleter {

        Type complete(DeferredType dt, ResultInfo resultInfo, DeferredAttrContext deferredAttrContext);
    }

    interface DeferredStuckPolicy {

        boolean isStuck();

        Set<Type> stuckVars();

        Set<Type> depVars();
    }

    abstract static class FilterScanner extends TreeScanner {
        final Filter<JCTree> treeFilter;

        FilterScanner(final Set<Tag> validTags) {
            this.treeFilter = new Filter<JCTree>() {
                public boolean accepts(JCTree t) {
                    return validTags.contains(t.getTag());
                }
            };
        }

        @Override
        public void scan(JCTree tree) {
            if (tree != null) {
                if (treeFilter.accepts(tree)) {
                    super.scan(tree);
                } else {
                    skip(tree);
                }
            }
        }

        abstract void skip(JCTree tree);
    }

    static class PolyScanner extends FilterScanner {
        PolyScanner() {
            super(EnumSet.of(CONDEXPR, PARENS, LAMBDA, REFERENCE));
        }

        @Override
        void skip(JCTree tree) {

        }
    }

    static class LambdaReturnScanner extends FilterScanner {
        LambdaReturnScanner() {
            super(EnumSet.of(BLOCK, CASE, CATCH, DOLOOP, FOREACHLOOP,
                    FORLOOP, RETURN, SYNCHRONIZED, SWITCH, TRY, WHILELOOP));
        }

        @Override
        void skip(JCTree tree) {

        }
    }

    public class DeferredType extends Type {
        public JCExpression tree;
        Env<AttrContext> env;
        AttrMode mode;
        SpeculativeCache speculativeCache;

        DeferredType(JCExpression tree, Env<AttrContext> env) {
            super(null);
            this.tree = tree;
            this.env = attr.copyEnv(env);
            this.speculativeCache = new SpeculativeCache();
        }

        @Override
        public TypeTag getTag() {
            return DEFERRED;
        }

        Type speculativeType(Symbol msym, MethodResolutionPhase phase) {
            SpeculativeCache.Entry e = speculativeCache.get(msym, phase);
            return e != null ? e.speculativeTree.type : Type.noType;
        }

        Type check(ResultInfo resultInfo) {
            DeferredStuckPolicy deferredStuckPolicy;
            if (resultInfo.pt.hasTag(NONE) || resultInfo.pt.isErroneous()) {
                deferredStuckPolicy = dummyStuckPolicy;
            } else if (resultInfo.checkContext.deferredAttrContext().mode == AttrMode.SPECULATIVE) {
                deferredStuckPolicy = new OverloadStuckPolicy(resultInfo, this);
            } else {
                deferredStuckPolicy = new CheckStuckPolicy(resultInfo, this);
            }
            return check(resultInfo, deferredStuckPolicy, basicCompleter);
        }

        private Type check(ResultInfo resultInfo, DeferredStuckPolicy deferredStuckPolicy,
                           DeferredTypeCompleter deferredTypeCompleter) {
            DeferredAttrContext deferredAttrContext =
                    resultInfo.checkContext.deferredAttrContext();
            Assert.check(deferredAttrContext != emptyDeferredAttrContext);
            if (deferredStuckPolicy.isStuck()) {
                deferredAttrContext.addDeferredAttrNode(this, resultInfo, deferredStuckPolicy);
                return Type.noType;
            } else {
                try {
                    return deferredTypeCompleter.complete(this, resultInfo, deferredAttrContext);
                } finally {
                    mode = deferredAttrContext.mode;
                }
            }
        }

        class SpeculativeCache {
            private Map<Symbol, List<Entry>> cache =
                    new WeakHashMap<Symbol, List<Entry>>();

            Entry get(Symbol msym, MethodResolutionPhase phase) {
                List<Entry> entries = cache.get(msym);
                if (entries == null) return null;
                for (Entry e : entries) {
                    if (e.matches(phase)) return e;
                }
                return null;
            }

            void put(JCTree speculativeTree, ResultInfo resultInfo) {
                Symbol msym = resultInfo.checkContext.deferredAttrContext().msym;
                List<Entry> entries = cache.get(msym);
                if (entries == null) {
                    entries = List.nil();
                }
                cache.put(msym, entries.prepend(new Entry(speculativeTree, resultInfo)));
            }

            class Entry {
                JCTree speculativeTree;
                ResultInfo resultInfo;

                public Entry(JCTree speculativeTree, ResultInfo resultInfo) {
                    this.speculativeTree = speculativeTree;
                    this.resultInfo = resultInfo;
                }

                boolean matches(MethodResolutionPhase phase) {
                    return resultInfo.checkContext.deferredAttrContext().phase == phase;
                }
            }
        }
    }

    class DeferredAttrContext {

        final AttrMode mode;

        final Symbol msym;

        final MethodResolutionPhase phase;

        final InferenceContext inferenceContext;

        final DeferredAttrContext parent;

        final Warner warn;

        ArrayList<DeferredAttrNode> deferredAttrNodes = new ArrayList<DeferredAttrNode>();

        DeferredAttrContext(AttrMode mode, Symbol msym, MethodResolutionPhase phase,
                            InferenceContext inferenceContext, DeferredAttrContext parent, Warner warn) {
            this.mode = mode;
            this.msym = msym;
            this.phase = phase;
            this.parent = parent;
            this.warn = warn;
            this.inferenceContext = inferenceContext;
        }

        void addDeferredAttrNode(final DeferredType dt, ResultInfo resultInfo,
                                 DeferredStuckPolicy deferredStuckPolicy) {
            deferredAttrNodes.add(new DeferredAttrNode(dt, resultInfo, deferredStuckPolicy));
        }

        void complete() {
            while (!deferredAttrNodes.isEmpty()) {
                Map<Type, Set<Type>> depVarsMap = new LinkedHashMap<Type, Set<Type>>();
                List<Type> stuckVars = List.nil();
                boolean progress = false;


                for (DeferredAttrNode deferredAttrNode : List.from(deferredAttrNodes)) {
                    if (!deferredAttrNode.process(this)) {
                        List<Type> restStuckVars =
                                List.from(deferredAttrNode.deferredStuckPolicy.stuckVars())
                                        .intersect(inferenceContext.restvars());
                        stuckVars = stuckVars.prependList(restStuckVars);

                        for (Type t : List.from(deferredAttrNode.deferredStuckPolicy.depVars())
                                .intersect(inferenceContext.restvars())) {
                            Set<Type> prevDeps = depVarsMap.get(t);
                            if (prevDeps == null) {
                                prevDeps = new LinkedHashSet<Type>();
                                depVarsMap.put(t, prevDeps);
                            }
                            prevDeps.addAll(restStuckVars);
                        }
                    } else {
                        deferredAttrNodes.remove(deferredAttrNode);
                        progress = true;
                    }
                }
                if (!progress) {
                    DeferredAttrContext dac = this;
                    while (dac != emptyDeferredAttrContext) {
                        if (dac.mode == AttrMode.SPECULATIVE) {

                            break;
                        }
                        dac = dac.parent;
                    }


                    try {
                        inferenceContext.solveAny(stuckVars, depVarsMap, warn);
                        inferenceContext.notifyChange();
                    } catch (Infer.GraphStrategy.NodeNotFoundException ex) {


                        break;
                    }
                }
            }
        }
    }

    class DeferredAttrNode {

        DeferredType dt;

        ResultInfo resultInfo;

        DeferredStuckPolicy deferredStuckPolicy;

        DeferredAttrNode(DeferredType dt, ResultInfo resultInfo, DeferredStuckPolicy deferredStuckPolicy) {
            this.dt = dt;
            this.resultInfo = resultInfo;
            this.deferredStuckPolicy = deferredStuckPolicy;
        }

        @SuppressWarnings("fallthrough")
        boolean process(final DeferredAttrContext deferredAttrContext) {
            switch (deferredAttrContext.mode) {
                case SPECULATIVE:
                    if (deferredStuckPolicy.isStuck()) {
                        dt.check(resultInfo, dummyStuckPolicy, new StructuralStuckChecker());
                        return true;
                    } else {
                        Assert.error("Cannot get here");
                    }
                case CHECK:
                    if (deferredStuckPolicy.isStuck()) {

                        if (deferredAttrContext.parent != emptyDeferredAttrContext &&
                                Type.containsAny(deferredAttrContext.parent.inferenceContext.inferencevars,
                                        List.from(deferredStuckPolicy.stuckVars()))) {
                            deferredAttrContext.parent.addDeferredAttrNode(dt,
                                    resultInfo.dup(new Check.NestedCheckContext(resultInfo.checkContext) {
                                        @Override
                                        public InferenceContext inferenceContext() {
                                            return deferredAttrContext.parent.inferenceContext;
                                        }

                                        @Override
                                        public DeferredAttrContext deferredAttrContext() {
                                            return deferredAttrContext.parent;
                                        }
                                    }), deferredStuckPolicy);
                            dt.tree.type = Type.stuckType;
                            return true;
                        } else {
                            return false;
                        }
                    } else {
                        ResultInfo instResultInfo =
                                resultInfo.dup(deferredAttrContext.inferenceContext.asInstType(resultInfo.pt));
                        dt.check(instResultInfo, dummyStuckPolicy, basicCompleter);
                        return true;
                    }
                default:
                    throw new AssertionError("Bad mode");
            }
        }

        class StructuralStuckChecker extends TreeScanner implements DeferredTypeCompleter {
            ResultInfo resultInfo;
            InferenceContext inferenceContext;
            Env<AttrContext> env;

            public Type complete(DeferredType dt, ResultInfo resultInfo, DeferredAttrContext deferredAttrContext) {
                this.resultInfo = resultInfo;
                this.inferenceContext = deferredAttrContext.inferenceContext;
                this.env = dt.env;
                dt.tree.accept(this);
                dt.speculativeCache.put(stuckTree, resultInfo);
                return Type.noType;
            }

            @Override
            public void visitLambda(JCLambda tree) {
                Check.CheckContext checkContext = resultInfo.checkContext;
                Type pt = resultInfo.pt;
                if (inferenceContext.inferencevars.contains(pt)) {

                    return;
                } else {

                    try {
                        Type desc = types.findDescriptorType(pt);
                        if (desc.getParameterTypes().length() != tree.params.length()) {
                            checkContext.report(tree, diags.fragment("incompatible.arg.types.in.lambda"));
                        }
                    } catch (Types.FunctionDescriptorLookupError ex) {
                        checkContext.report(null, ex.getDiagnostic());
                    }
                }
            }

            @Override
            public void visitNewClass(JCNewClass tree) {

            }

            @Override
            public void visitApply(JCMethodInvocation tree) {

            }

            @Override
            public void visitReference(JCMemberReference tree) {
                Check.CheckContext checkContext = resultInfo.checkContext;
                Type pt = resultInfo.pt;
                if (inferenceContext.inferencevars.contains(pt)) {

                    return;
                } else {
                    try {
                        types.findDescriptorType(pt);
                    } catch (Types.FunctionDescriptorLookupError ex) {
                        checkContext.report(null, ex.getDiagnostic());
                    }
                    Env<AttrContext> localEnv = env.dup(tree);
                    JCExpression exprTree = (JCExpression) attribSpeculative(tree.getQualifierExpression(), localEnv,
                            attr.memberReferenceQualifierResult(tree));
                    ListBuffer<Type> argtypes = new ListBuffer<>();
                    for (Type t : types.findDescriptorType(pt).getParameterTypes()) {
                        argtypes.append(Type.noType);
                    }
                    JCMemberReference mref2 = new TreeCopier<Void>(make).copy(tree);
                    mref2.expr = exprTree;
                    Symbol lookupSym =
                            rs.resolveMemberReferenceByArity(localEnv, mref2, exprTree.type,
                                    tree.name, argtypes.toList(), inferenceContext);
                    switch (lookupSym.kind) {


                        case Kinds.ABSENT_MTH:
                        case Kinds.WRONG_MTH:
                        case Kinds.WRONG_MTHS:
                        case Kinds.WRONG_STATICNESS:
                            checkContext.report(tree, diags.fragment("incompatible.arg.types.in.mref"));
                    }
                }
            }
        }
    }

    class DeferredTypeMap extends Mapping {
        DeferredAttrContext deferredAttrContext;

        protected DeferredTypeMap(AttrMode mode, Symbol msym, MethodResolutionPhase phase) {
            super(String.format("deferredTypeMap[%s]", mode));
            this.deferredAttrContext = new DeferredAttrContext(mode, msym, phase,
                    infer.emptyContext, emptyDeferredAttrContext, types.noWarnings);
        }

        @Override
        public Type apply(Type t) {
            if (!t.hasTag(DEFERRED)) {
                return t.map(this);
            } else {
                DeferredType dt = (DeferredType) t;
                return typeOf(dt);
            }
        }

        protected Type typeOf(DeferredType dt) {
            switch (deferredAttrContext.mode) {
                case CHECK:
                    return dt.tree.type == null ? Type.noType : dt.tree.type;
                case SPECULATIVE:
                    return dt.speculativeType(deferredAttrContext.msym, deferredAttrContext.phase);
            }
            Assert.error();
            return null;
        }
    }

    public class RecoveryDeferredTypeMap extends DeferredTypeMap {
        public RecoveryDeferredTypeMap(AttrMode mode, Symbol msym, MethodResolutionPhase phase) {
            super(mode, msym, phase != null ? phase : MethodResolutionPhase.BOX);
        }

        @Override
        protected Type typeOf(DeferredType dt) {
            Type owntype = super.typeOf(dt);
            return owntype == Type.noType ?
                    recover(dt) : owntype;
        }

        private Type recover(DeferredType dt) {
            dt.check(attr.new RecoveryInfo(deferredAttrContext) {
                @Override
                protected Type check(DiagnosticPosition pos, Type found) {
                    return chk.checkNonVoid(pos, super.check(pos, found));
                }
            });
            return super.apply(dt);
        }
    }

    class CheckStuckPolicy extends PolyScanner implements DeferredStuckPolicy, Infer.FreeTypeListener {
        Type pt;
        InferenceContext inferenceContext;
        Set<Type> stuckVars = new LinkedHashSet<Type>();
        Set<Type> depVars = new LinkedHashSet<Type>();

        public CheckStuckPolicy(ResultInfo resultInfo, DeferredType dt) {
            this.pt = resultInfo.pt;
            this.inferenceContext = resultInfo.checkContext.inferenceContext();
            scan(dt.tree);
            if (!stuckVars.isEmpty()) {
                resultInfo.checkContext.inferenceContext()
                        .addFreeTypeListener(List.from(stuckVars), this);
            }
        }

        @Override
        public boolean isStuck() {
            return !stuckVars.isEmpty();
        }

        @Override
        public Set<Type> stuckVars() {
            return stuckVars;
        }

        @Override
        public Set<Type> depVars() {
            return depVars;
        }

        @Override
        public void typesInferred(InferenceContext inferenceContext) {
            stuckVars.clear();
        }

        @Override
        public void visitLambda(JCLambda tree) {
            if (inferenceContext.inferenceVars().contains(pt)) {
                stuckVars.add(pt);
            }
            if (!types.isFunctionalInterface(pt)) {
                return;
            }
            Type descType = types.findDescriptorType(pt);
            List<Type> freeArgVars = inferenceContext.freeVarsIn(descType.getParameterTypes());
            if (tree.paramKind == JCLambda.ParameterKind.IMPLICIT &&
                    freeArgVars.nonEmpty()) {
                stuckVars.addAll(freeArgVars);
                depVars.addAll(inferenceContext.freeVarsIn(descType.getReturnType()));
            }
            scanLambdaBody(tree, descType.getReturnType());
        }

        @Override
        public void visitReference(JCMemberReference tree) {
            scan(tree.expr);
            if (inferenceContext.inferenceVars().contains(pt)) {
                stuckVars.add(pt);
                return;
            }
            if (!types.isFunctionalInterface(pt)) {
                return;
            }
            Type descType = types.findDescriptorType(pt);
            List<Type> freeArgVars = inferenceContext.freeVarsIn(descType.getParameterTypes());
            if (freeArgVars.nonEmpty() &&
                    tree.overloadKind == JCMemberReference.OverloadKind.OVERLOADED) {
                stuckVars.addAll(freeArgVars);
                depVars.addAll(inferenceContext.freeVarsIn(descType.getReturnType()));
            }
        }

        void scanLambdaBody(JCLambda lambda, final Type pt) {
            if (lambda.getBodyKind() == JCLambda.BodyKind.EXPRESSION) {
                Type prevPt = this.pt;
                try {
                    this.pt = pt;
                    scan(lambda.body);
                } finally {
                    this.pt = prevPt;
                }
            } else {
                LambdaReturnScanner lambdaScanner = new LambdaReturnScanner() {
                    @Override
                    public void visitReturn(JCReturn tree) {
                        if (tree.expr != null) {
                            Type prevPt = CheckStuckPolicy.this.pt;
                            try {
                                CheckStuckPolicy.this.pt = pt;
                                CheckStuckPolicy.this.scan(tree.expr);
                            } finally {
                                CheckStuckPolicy.this.pt = prevPt;
                            }
                        }
                    }
                };
                lambdaScanner.scan(lambda.body);
            }
        }
    }

    class OverloadStuckPolicy extends CheckStuckPolicy implements DeferredStuckPolicy {
        boolean stuck;

        public OverloadStuckPolicy(ResultInfo resultInfo, DeferredType dt) {
            super(resultInfo, dt);
        }

        @Override
        public boolean isStuck() {
            return super.isStuck() || stuck;
        }

        @Override
        public void visitLambda(JCLambda tree) {
            super.visitLambda(tree);
            if (tree.paramKind == JCLambda.ParameterKind.IMPLICIT) {
                stuck = true;
            }
        }

        @Override
        public void visitReference(JCMemberReference tree) {
            super.visitReference(tree);
            if (tree.overloadKind == JCMemberReference.OverloadKind.OVERLOADED) {
                stuck = true;
            }
        }
    }

    final class DeferredChecker extends FilterScanner {
        Env<AttrContext> env;
        ArgumentExpressionKind result;

        public DeferredChecker(Env<AttrContext> env) {
            super(deferredCheckerTags);
            this.env = env;
        }

        @Override
        public void visitLambda(JCLambda tree) {

            result = ArgumentExpressionKind.POLY;
        }

        @Override
        public void visitReference(JCMemberReference tree) {

            Env<AttrContext> localEnv = env.dup(tree);
            JCExpression exprTree = (JCExpression) attribSpeculative(tree.getQualifierExpression(), localEnv,
                    attr.memberReferenceQualifierResult(tree));
            JCMemberReference mref2 = new TreeCopier<Void>(make).copy(tree);
            mref2.expr = exprTree;
            Symbol res =
                    rs.getMemberReference(tree, localEnv, mref2,
                            exprTree.type, tree.name);
            tree.sym = res;
            if (res.kind >= Kinds.ERRONEOUS ||
                    res.type.hasTag(FORALL) ||
                    (res.flags() & Flags.VARARGS) != 0 ||
                    (TreeInfo.isStaticSelector(exprTree, tree.name.table.names) &&
                            exprTree.type.isRaw())) {
                tree.overloadKind = JCMemberReference.OverloadKind.OVERLOADED;
            } else {
                tree.overloadKind = JCMemberReference.OverloadKind.UNOVERLOADED;
            }

            result = ArgumentExpressionKind.POLY;
        }

        @Override
        public void visitTypeCast(JCTypeCast tree) {

            result = ArgumentExpressionKind.NO_POLY;
        }

        @Override
        public void visitConditional(JCConditional tree) {
            scan(tree.truepart);
            if (!result.isPrimitive()) {
                result = ArgumentExpressionKind.POLY;
                return;
            }
            scan(tree.falsepart);
            result = reduce(ArgumentExpressionKind.PRIMITIVE);
        }

        @Override
        public void visitNewClass(JCNewClass tree) {
            result = (TreeInfo.isDiamond(tree) || attr.findDiamonds) ?
                    ArgumentExpressionKind.POLY : ArgumentExpressionKind.NO_POLY;
        }

        @Override
        public void visitApply(JCMethodInvocation tree) {
            Name name = TreeInfo.name(tree.meth);

            if (tree.typeargs.nonEmpty() ||
                    name == name.table.names._this ||
                    name == name.table.names._super) {
                result = ArgumentExpressionKind.NO_POLY;
                return;
            }

            final JCExpression rec = tree.meth.hasTag(SELECT) ?
                    ((JCFieldAccess) tree.meth).selected :
                    null;
            if (rec != null && !isSimpleReceiver(rec)) {

                result = ArgumentExpressionKind.POLY;
                return;
            }
            Type site = rec != null ?
                    attribSpeculative(rec, env, attr.unknownTypeExprInfo).type :
                    env.enclClass.sym.type;
            while (site.hasTag(TYPEVAR)) {
                site = site.getUpperBound();
            }
            List<Type> args = rs.dummyArgs(tree.args.length());
            Resolve.LookupHelper lh = rs.new LookupHelper(name, site, args, List.nil(), MethodResolutionPhase.VARARITY) {
                @Override
                Symbol lookup(Env<AttrContext> env, MethodResolutionPhase phase) {
                    return rec == null ?
                            rs.findFun(env, name, argtypes, typeargtypes, phase.isBoxingRequired(), phase.isVarargsRequired()) :
                            rs.findMethod(env, site, name, argtypes, typeargtypes, phase.isBoxingRequired(), phase.isVarargsRequired(), false);
                }

                @Override
                Symbol access(Env<AttrContext> env, DiagnosticPosition pos, Symbol location, Symbol sym) {
                    return sym;
                }
            };
            Symbol sym = rs.lookupMethod(env, tree, site.tsym, rs.arityMethodCheck, lh);
            if (sym.kind == Kinds.AMBIGUOUS) {
                Resolve.AmbiguityError err = (Resolve.AmbiguityError) sym.baseSymbol();
                result = ArgumentExpressionKind.PRIMITIVE;
                for (Symbol s : err.ambiguousSyms) {
                    if (result.isPoly()) break;
                    if (s.kind == Kinds.MTH) {
                        result = reduce(ArgumentExpressionKind.methodKind(s, types));
                    }
                }
            } else {
                result = (sym.kind == Kinds.MTH) ?
                        ArgumentExpressionKind.methodKind(sym, types) :
                        ArgumentExpressionKind.NO_POLY;
            }
        }

        private boolean isSimpleReceiver(JCTree rec) {
            switch (rec.getTag()) {
                case IDENT:
                    return true;
                case SELECT:
                    return isSimpleReceiver(((JCFieldAccess) rec).selected);
                case TYPEAPPLY:
                case TYPEARRAY:
                    return true;
                case ANNOTATED_TYPE:
                    return isSimpleReceiver(((JCAnnotatedType) rec).underlyingType);
                default:
                    return false;
            }
        }

        private ArgumentExpressionKind reduce(ArgumentExpressionKind kind) {
            switch (result) {
                case PRIMITIVE:
                    return kind;
                case NO_POLY:
                    return kind.isPoly() ? kind : result;
                case POLY:
                    return result;
                default:
                    Assert.error();
                    return null;
            }
        }

        @Override
        public void visitLiteral(JCLiteral tree) {
            Type litType = attr.litType(tree.typetag);
            result = ArgumentExpressionKind.standaloneKind(litType, types);
        }

        @Override
        void skip(JCTree tree) {
            result = ArgumentExpressionKind.NO_POLY;
        }
    }
}
