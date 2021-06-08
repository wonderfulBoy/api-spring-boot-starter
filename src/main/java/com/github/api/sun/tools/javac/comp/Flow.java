package com.github.api.sun.tools.javac.comp;

import com.github.api.sun.tools.javac.code.*;
import com.github.api.sun.tools.javac.code.Symbol.ClassSymbol;
import com.github.api.sun.tools.javac.code.Symbol.VarSymbol;
import com.github.api.sun.tools.javac.tree.JCTree;
import com.github.api.sun.tools.javac.tree.JCTree.*;
import com.github.api.sun.tools.javac.tree.TreeInfo;
import com.github.api.sun.tools.javac.tree.TreeMaker;
import com.github.api.sun.tools.javac.tree.TreeScanner;
import com.github.api.sun.tools.javac.util.*;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import java.util.HashMap;

import static com.github.api.sun.tools.javac.code.Flags.BLOCK;
import static com.github.api.sun.tools.javac.code.Flags.*;
import static com.github.api.sun.tools.javac.code.Kinds.*;
import static com.github.api.sun.tools.javac.code.TypeTag.BOOLEAN;
import static com.github.api.sun.tools.javac.code.TypeTag.VOID;
import static com.github.api.sun.tools.javac.tree.JCTree.Tag.*;

public class Flow {
    protected static final Context.Key<Flow> flowKey =
            new Context.Key<Flow>();
    private final Names names;
    private final Log log;
    private final Symtab syms;
    private final Types types;
    private final Check chk;
    private final Resolve rs;
    private final JCDiagnostic.Factory diags;
    private final boolean allowImprovedRethrowAnalysis;
    private final boolean allowImprovedCatchAnalysis;
    private final boolean allowEffectivelyFinalInInnerClasses;
    private TreeMaker make;
    private Env<AttrContext> attrEnv;
    private Lint lint;

    protected Flow(Context context) {
        context.put(flowKey, this);
        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        chk = Check.instance(context);
        lint = Lint.instance(context);
        rs = Resolve.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        Source source = Source.instance(context);
        allowImprovedRethrowAnalysis = source.allowImprovedRethrowAnalysis();
        allowImprovedCatchAnalysis = source.allowImprovedCatchAnalysis();
        allowEffectivelyFinalInInnerClasses = source.allowEffectivelyFinalInInnerClasses();
    }

    public static Flow instance(Context context) {
        Flow instance = context.get(flowKey);
        if (instance == null)
            instance = new Flow(context);
        return instance;
    }

    public void analyzeTree(Env<AttrContext> env, TreeMaker make) {
        new AliveAnalyzer().analyzeTree(env, make);
        new AssignAnalyzer(log, syms, lint, names).analyzeTree(env);
        new FlowAnalyzer().analyzeTree(env, make);
        new CaptureAnalyzer().analyzeTree(env, make);
    }

    public void analyzeLambda(Env<AttrContext> env, JCLambda that, TreeMaker make, boolean speculative) {
        Log.DiagnosticHandler diagHandler = null;


        if (!speculative) {
            diagHandler = new Log.DiscardDiagnosticHandler(log);
        }
        try {
            new AliveAnalyzer().analyzeTree(env, that, make);
        } finally {
            if (!speculative) {
                log.popDiagnosticHandler(diagHandler);
            }
        }
    }

    public List<Type> analyzeLambdaThrownTypes(Env<AttrContext> env, JCLambda that, TreeMaker make) {


        Log.DiagnosticHandler diagHandler = new Log.DiscardDiagnosticHandler(log);
        try {
            new AssignAnalyzer(log, syms, lint, names).analyzeTree(env);
            LambdaFlowAnalyzer flowAnalyzer = new LambdaFlowAnalyzer();
            flowAnalyzer.analyzeTree(env, that, make);
            return flowAnalyzer.inferredThrownTypes;
        } finally {
            log.popDiagnosticHandler(diagHandler);
        }
    }

    enum FlowKind {

        NORMAL("var.might.already.be.assigned", false),

        SPECULATIVE_LOOP("var.might.be.assigned.in.loop", true);
        final String errKey;
        final boolean isFinal;

        FlowKind(String errKey, boolean isFinal) {
            this.errKey = errKey;
            this.isFinal = isFinal;
        }

        boolean isFinal() {
            return isFinal;
        }
    }

    static class PendingExit {
        JCTree tree;

        PendingExit(JCTree tree) {
            this.tree = tree;
        }

        void resolveJump(JCTree tree) {

        }
    }

    static abstract class BaseAnalyzer<P extends PendingExit> extends TreeScanner {
        ListBuffer<P> pendingExits;

        abstract void markDead(JCTree tree);

        void recordExit(JCTree tree, P pe) {
            pendingExits.append(pe);
            markDead(tree);
        }

        private boolean resolveJump(JCTree tree,
                                    ListBuffer<P> oldPendingExits,
                                    JumpKind jk) {
            boolean resolved = false;
            List<P> exits = pendingExits.toList();
            pendingExits = oldPendingExits;
            for (; exits.nonEmpty(); exits = exits.tail) {
                P exit = exits.head;
                if (exit.tree.hasTag(jk.treeTag) &&
                        jk.getTarget(exit.tree) == tree) {
                    exit.resolveJump(tree);
                    resolved = true;
                } else {
                    pendingExits.append(exit);
                }
            }
            return resolved;
        }

        boolean resolveContinues(JCTree tree) {
            return resolveJump(tree, new ListBuffer<P>(), JumpKind.CONTINUE);
        }

        boolean resolveBreaks(JCTree tree, ListBuffer<P> oldPendingExits) {
            return resolveJump(tree, oldPendingExits, JumpKind.BREAK);
        }

        @Override
        public void scan(JCTree tree) {
            if (tree != null && (
                    tree.type == null ||
                            tree.type != Type.stuckType)) {
                super.scan(tree);
            }
        }

        enum JumpKind {
            BREAK(Tag.BREAK) {
                @Override
                JCTree getTarget(JCTree tree) {
                    return ((JCBreak) tree).target;
                }
            },
            CONTINUE(Tag.CONTINUE) {
                @Override
                JCTree getTarget(JCTree tree) {
                    return ((JCContinue) tree).target;
                }
            };
            final Tag treeTag;

            JumpKind(Tag treeTag) {
                this.treeTag = treeTag;
            }

            abstract JCTree getTarget(JCTree tree);
        }
    }

    public static class AbstractAssignPendingExit extends PendingExit {
        final Bits inits;
        final Bits uninits;
        final Bits exit_inits = new Bits(true);
        final Bits exit_uninits = new Bits(true);

        public AbstractAssignPendingExit(JCTree tree, final Bits inits, final Bits uninits) {
            super(tree);
            this.inits = inits;
            this.uninits = uninits;
            this.exit_inits.assign(inits);
            this.exit_uninits.assign(uninits);
        }

        @Override
        public void resolveJump(JCTree tree) {
            inits.andSet(exit_inits);
            uninits.andSet(exit_uninits);
        }
    }

    public abstract static class AbstractAssignAnalyzer<P extends AbstractAssignPendingExit>
            extends BaseAnalyzer<P> {

        protected final Bits inits;

        final Bits uninits;

        final Bits uninitsTry;

        final Bits initsWhenTrue;
        final Bits initsWhenFalse;
        final Bits uninitsWhenTrue;
        final Bits uninitsWhenFalse;
        final Symtab syms;
        protected JCVariableDecl[] vardecls;
        protected int nextadr;
        protected int returnadr;
        protected Names names;
        JCClassDecl classDef;
        int firstadr;
        Scope unrefdResources;
        FlowKind flowKind = FlowKind.NORMAL;
        int startPos;

        public AbstractAssignAnalyzer(Bits inits, Symtab syms, Names names) {
            this.inits = inits;
            uninits = new Bits();
            uninitsTry = new Bits();
            initsWhenTrue = new Bits(true);
            initsWhenFalse = new Bits(true);
            uninitsWhenTrue = new Bits(true);
            uninitsWhenFalse = new Bits(true);
            this.syms = syms;
            this.names = names;
        }

        @Override
        protected void markDead(JCTree tree) {
            inits.inclRange(returnadr, nextadr);
            uninits.inclRange(returnadr, nextadr);
        }


        protected boolean trackable(VarSymbol sym) {
            return
                    sym.pos >= startPos &&
                            ((sym.owner.kind == MTH ||
                                    ((sym.flags() & (FINAL | HASINIT | PARAMETER)) == FINAL &&
                                            classDef.sym.isEnclosedBy((ClassSymbol) sym.owner))));
        }

        void newVar(JCVariableDecl varDecl) {
            VarSymbol sym = varDecl.sym;
            vardecls = ArrayUtils.ensureCapacity(vardecls, nextadr);
            if ((sym.flags() & FINAL) == 0) {
                sym.flags_field |= EFFECTIVELY_FINAL;
            }
            sym.adr = nextadr;
            vardecls[nextadr] = varDecl;
            exclVarFromInits(varDecl, nextadr);
            uninits.incl(nextadr);
            nextadr++;
        }

        protected void exclVarFromInits(JCTree tree, int adr) {
            inits.excl(adr);
        }

        protected void assignToInits(JCTree tree, Bits bits) {
            inits.assign(bits);
        }

        protected void andSetInits(JCTree tree, Bits bits) {
            inits.andSet(bits);
        }

        protected void orSetInits(JCTree tree, Bits bits) {
            inits.orSet(bits);
        }

        void letInit(DiagnosticPosition pos, VarSymbol sym) {
            if (sym.adr >= firstadr && trackable(sym)) {
                if (uninits.isMember(sym.adr)) {
                    uninit(sym);
                }
                inits.incl(sym.adr);
            }
        }

        void uninit(VarSymbol sym) {
            if (!inits.isMember(sym.adr)) {

                uninits.excl(sym.adr);
                uninitsTry.excl(sym.adr);
            } else {

                uninits.excl(sym.adr);
            }
        }

        void letInit(JCTree tree) {
            tree = TreeInfo.skipParens(tree);
            if (tree.hasTag(IDENT) || tree.hasTag(SELECT)) {
                Symbol sym = TreeInfo.symbol(tree);
                if (sym.kind == VAR) {
                    letInit(tree.pos(), (VarSymbol) sym);
                }
            }
        }

        void checkInit(DiagnosticPosition pos, VarSymbol sym) {
            checkInit(pos, sym, "var.might.not.have.been.initialized");
        }

        void checkInit(DiagnosticPosition pos, VarSymbol sym, String errkey) {
        }

        private void resetBits(Bits... bits) {
            for (Bits b : bits) {
                b.reset();
            }
        }

        void split(boolean setToNull) {
            initsWhenFalse.assign(inits);
            uninitsWhenFalse.assign(uninits);
            initsWhenTrue.assign(inits);
            uninitsWhenTrue.assign(uninits);
            if (setToNull) {
                resetBits(inits, uninits);
            }
        }

        protected void merge(JCTree tree) {
            inits.assign(initsWhenFalse.andSet(initsWhenTrue));
            uninits.assign(uninitsWhenFalse.andSet(uninitsWhenTrue));
        }


        void scanExpr(JCTree tree) {
            if (tree != null) {
                scan(tree);
                if (inits.isReset()) {
                    merge(tree);
                }
            }
        }

        void scanExprs(List<? extends JCExpression> trees) {
            if (trees != null)
                for (List<? extends JCExpression> l = trees; l.nonEmpty(); l = l.tail)
                    scanExpr(l.head);
        }

        void scanCond(JCTree tree) {
            if (tree.type.isFalse()) {
                if (inits.isReset()) merge(tree);
                initsWhenTrue.assign(inits);
                initsWhenTrue.inclRange(firstadr, nextadr);
                uninitsWhenTrue.assign(uninits);
                uninitsWhenTrue.inclRange(firstadr, nextadr);
                initsWhenFalse.assign(inits);
                uninitsWhenFalse.assign(uninits);
            } else if (tree.type.isTrue()) {
                if (inits.isReset()) merge(tree);
                initsWhenFalse.assign(inits);
                initsWhenFalse.inclRange(firstadr, nextadr);
                uninitsWhenFalse.assign(uninits);
                uninitsWhenFalse.inclRange(firstadr, nextadr);
                initsWhenTrue.assign(inits);
                uninitsWhenTrue.assign(uninits);
            } else {
                scan(tree);
                if (!inits.isReset())
                    split(tree.type != syms.unknownType);
            }
            if (tree.type != syms.unknownType) {
                resetBits(inits, uninits);
            }
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            if (tree.sym == null) {
                return;
            }
            JCClassDecl classDefPrev = classDef;
            int firstadrPrev = firstadr;
            int nextadrPrev = nextadr;
            ListBuffer<P> pendingExitsPrev = pendingExits;
            pendingExits = new ListBuffer<P>();
            if (tree.name != names.empty) {
                firstadr = nextadr;
            }
            classDef = tree;
            try {

                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (l.head.hasTag(VARDEF)) {
                        JCVariableDecl def = (JCVariableDecl) l.head;
                        if ((def.mods.flags & STATIC) != 0) {
                            VarSymbol sym = def.sym;
                            if (trackable(sym)) {
                                newVar(def);
                            }
                        }
                    }
                }

                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (!l.head.hasTag(METHODDEF) &&
                            (TreeInfo.flags(l.head) & STATIC) != 0) {
                        scan(l.head);
                    }
                }

                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (l.head.hasTag(VARDEF)) {
                        JCVariableDecl def = (JCVariableDecl) l.head;
                        if ((def.mods.flags & STATIC) == 0) {
                            VarSymbol sym = def.sym;
                            if (trackable(sym)) {
                                newVar(def);
                            }
                        }
                    }
                }

                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (!l.head.hasTag(METHODDEF) &&
                            (TreeInfo.flags(l.head) & STATIC) == 0) {
                        scan(l.head);
                    }
                }

                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (l.head.hasTag(METHODDEF)) {
                        scan(l.head);
                    }
                }
            } finally {
                pendingExits = pendingExitsPrev;
                nextadr = nextadrPrev;
                firstadr = firstadrPrev;
                classDef = classDefPrev;
            }
        }

        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            if (tree.body == null) {
                return;
            }

            if ((tree.sym.flags() & (SYNTHETIC | LAMBDA_METHOD)) == SYNTHETIC) {
                return;
            }
            final Bits initsPrev = new Bits(inits);
            final Bits uninitsPrev = new Bits(uninits);
            int nextadrPrev = nextadr;
            int firstadrPrev = firstadr;
            int returnadrPrev = returnadr;
            Assert.check(pendingExits.isEmpty());
            try {
                boolean isInitialConstructor =
                        TreeInfo.isInitialConstructor(tree);
                if (!isInitialConstructor) {
                    firstadr = nextadr;
                }
                for (List<JCVariableDecl> l = tree.params; l.nonEmpty(); l = l.tail) {
                    JCVariableDecl def = l.head;
                    scan(def);
                    Assert.check((def.sym.flags() & PARAMETER) != 0, "Method parameter without PARAMETER flag");

                    initParam(def);
                }


                scan(tree.body);
                if (isInitialConstructor) {
                    boolean isSynthesized = (tree.sym.flags() &
                            GENERATEDCONSTR) != 0;
                    for (int i = firstadr; i < nextadr; i++) {
                        JCVariableDecl vardecl = vardecls[i];
                        VarSymbol var = vardecl.sym;
                        if (var.owner == classDef.sym) {


                            if (isSynthesized) {
                                checkInit(TreeInfo.diagnosticPositionFor(var, vardecl),
                                        var, "var.not.initialized.in.default.constructor");
                            } else {
                                checkInit(TreeInfo.diagEndPos(tree.body), var);
                            }
                        }
                    }
                }
                List<P> exits = pendingExits.toList();
                pendingExits = new ListBuffer<>();
                while (exits.nonEmpty()) {
                    P exit = exits.head;
                    exits = exits.tail;
                    Assert.check(exit.tree.hasTag(RETURN), exit.tree);
                    if (isInitialConstructor) {
                        assignToInits(exit.tree, exit.exit_inits);
                        for (int i = firstadr; i < nextadr; i++) {
                            checkInit(exit.tree.pos(), vardecls[i].sym);
                        }
                    }
                }
            } finally {
                assignToInits(tree, initsPrev);
                uninits.assign(uninitsPrev);
                nextadr = nextadrPrev;
                firstadr = firstadrPrev;
                returnadr = returnadrPrev;
            }
        }

        protected void initParam(JCVariableDecl def) {
            inits.incl(def.sym.adr);
            uninits.excl(def.sym.adr);
        }

        public void visitVarDef(JCVariableDecl tree) {
            boolean track = trackable(tree.sym);
            if (track && tree.sym.owner.kind == MTH) {
                newVar(tree);
            }
            if (tree.init != null) {
                scanExpr(tree.init);
                if (track) {
                    letInit(tree.pos(), tree.sym);
                }
            }
        }

        public void visitBlock(JCBlock tree) {
            int nextadrPrev = nextadr;
            scan(tree.stats);
            nextadr = nextadrPrev;
        }

        int getLogNumberOfErrors() {
            return 0;
        }

        public void visitDoLoop(JCDoWhileLoop tree) {
            ListBuffer<P> prevPendingExits = pendingExits;
            FlowKind prevFlowKind = flowKind;
            flowKind = FlowKind.NORMAL;
            final Bits initsSkip = new Bits(true);
            final Bits uninitsSkip = new Bits(true);
            pendingExits = new ListBuffer<P>();
            int prevErrors = getLogNumberOfErrors();
            do {
                final Bits uninitsEntry = new Bits(uninits);
                uninitsEntry.excludeFrom(nextadr);
                scan(tree.body);
                resolveContinues(tree);
                scanCond(tree.cond);
                if (!flowKind.isFinal()) {
                    initsSkip.assign(initsWhenFalse);
                    uninitsSkip.assign(uninitsWhenFalse);
                }
                if (getLogNumberOfErrors() != prevErrors ||
                        flowKind.isFinal() ||
                        new Bits(uninitsEntry).diffSet(uninitsWhenTrue).nextBit(firstadr) == -1)
                    break;
                assignToInits(tree.cond, initsWhenTrue);
                uninits.assign(uninitsEntry.andSet(uninitsWhenTrue));
                flowKind = FlowKind.SPECULATIVE_LOOP;
            } while (true);
            flowKind = prevFlowKind;
            assignToInits(tree, initsSkip);
            uninits.assign(uninitsSkip);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitWhileLoop(JCWhileLoop tree) {
            ListBuffer<P> prevPendingExits = pendingExits;
            FlowKind prevFlowKind = flowKind;
            flowKind = FlowKind.NORMAL;
            final Bits initsSkip = new Bits(true);
            final Bits uninitsSkip = new Bits(true);
            pendingExits = new ListBuffer<>();
            int prevErrors = getLogNumberOfErrors();
            final Bits uninitsEntry = new Bits(uninits);
            uninitsEntry.excludeFrom(nextadr);
            do {
                scanCond(tree.cond);
                if (!flowKind.isFinal()) {
                    initsSkip.assign(initsWhenFalse);
                    uninitsSkip.assign(uninitsWhenFalse);
                }
                assignToInits(tree, initsWhenTrue);
                uninits.assign(uninitsWhenTrue);
                scan(tree.body);
                resolveContinues(tree);
                if (getLogNumberOfErrors() != prevErrors ||
                        flowKind.isFinal() ||
                        new Bits(uninitsEntry).diffSet(uninits).nextBit(firstadr) == -1) {
                    break;
                }
                uninits.assign(uninitsEntry.andSet(uninits));
                flowKind = FlowKind.SPECULATIVE_LOOP;
            } while (true);
            flowKind = prevFlowKind;


            assignToInits(tree.body, initsSkip);
            uninits.assign(uninitsSkip);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitForLoop(JCForLoop tree) {
            ListBuffer<P> prevPendingExits = pendingExits;
            FlowKind prevFlowKind = flowKind;
            flowKind = FlowKind.NORMAL;
            int nextadrPrev = nextadr;
            scan(tree.init);
            final Bits initsSkip = new Bits(true);
            final Bits uninitsSkip = new Bits(true);
            pendingExits = new ListBuffer<P>();
            int prevErrors = getLogNumberOfErrors();
            do {
                final Bits uninitsEntry = new Bits(uninits);
                uninitsEntry.excludeFrom(nextadr);
                if (tree.cond != null) {
                    scanCond(tree.cond);
                    if (!flowKind.isFinal()) {
                        initsSkip.assign(initsWhenFalse);
                        uninitsSkip.assign(uninitsWhenFalse);
                    }
                    assignToInits(tree.body, initsWhenTrue);
                    uninits.assign(uninitsWhenTrue);
                } else if (!flowKind.isFinal()) {
                    initsSkip.assign(inits);
                    initsSkip.inclRange(firstadr, nextadr);
                    uninitsSkip.assign(uninits);
                    uninitsSkip.inclRange(firstadr, nextadr);
                }
                scan(tree.body);
                resolveContinues(tree);
                scan(tree.step);
                if (getLogNumberOfErrors() != prevErrors ||
                        flowKind.isFinal() ||
                        new Bits(uninitsEntry).diffSet(uninits).nextBit(firstadr) == -1)
                    break;
                uninits.assign(uninitsEntry.andSet(uninits));
                flowKind = FlowKind.SPECULATIVE_LOOP;
            } while (true);
            flowKind = prevFlowKind;


            assignToInits(tree.body, initsSkip);
            uninits.assign(uninitsSkip);
            resolveBreaks(tree, prevPendingExits);
            nextadr = nextadrPrev;
        }

        public void visitForeachLoop(JCEnhancedForLoop tree) {
            visitVarDef(tree.var);
            ListBuffer<P> prevPendingExits = pendingExits;
            FlowKind prevFlowKind = flowKind;
            flowKind = FlowKind.NORMAL;
            int nextadrPrev = nextadr;
            scan(tree.expr);
            final Bits initsStart = new Bits(inits);
            final Bits uninitsStart = new Bits(uninits);
            letInit(tree.pos(), tree.var.sym);
            pendingExits = new ListBuffer<P>();
            int prevErrors = getLogNumberOfErrors();
            do {
                final Bits uninitsEntry = new Bits(uninits);
                uninitsEntry.excludeFrom(nextadr);
                scan(tree.body);
                resolveContinues(tree);
                if (getLogNumberOfErrors() != prevErrors ||
                        flowKind.isFinal() ||
                        new Bits(uninitsEntry).diffSet(uninits).nextBit(firstadr) == -1)
                    break;
                uninits.assign(uninitsEntry.andSet(uninits));
                flowKind = FlowKind.SPECULATIVE_LOOP;
            } while (true);
            flowKind = prevFlowKind;
            assignToInits(tree.body, initsStart);
            uninits.assign(uninitsStart.andSet(uninits));
            resolveBreaks(tree, prevPendingExits);
            nextadr = nextadrPrev;
        }

        public void visitLabelled(JCLabeledStatement tree) {
            ListBuffer<P> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<P>();
            scan(tree.body);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitSwitch(JCSwitch tree) {
            ListBuffer<P> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            int nextadrPrev = nextadr;
            scanExpr(tree.selector);
            final Bits initsSwitch = new Bits(inits);
            final Bits uninitsSwitch = new Bits(uninits);
            boolean hasDefault = false;
            for (List<JCCase> l = tree.cases; l.nonEmpty(); l = l.tail) {
                assignToInits(l.head, initsSwitch);
                uninits.assign(uninits.andSet(uninitsSwitch));
                JCCase c = l.head;
                if (c.pat == null) {
                    hasDefault = true;
                } else {
                    scanExpr(c.pat);
                }
                if (hasDefault) {
                    assignToInits(null, initsSwitch);
                    uninits.assign(uninits.andSet(uninitsSwitch));
                }
                scan(c.stats);
                addVars(c.stats, initsSwitch, uninitsSwitch);
                if (!hasDefault) {
                    assignToInits(l.head.stats.last(), initsSwitch);
                    uninits.assign(uninits.andSet(uninitsSwitch));
                }

            }
            if (!hasDefault) {
                andSetInits(null, initsSwitch);
            }
            resolveBreaks(tree, prevPendingExits);
            nextadr = nextadrPrev;
        }


        private void addVars(List<JCStatement> stats, final Bits inits,
                             final Bits uninits) {
            for (; stats.nonEmpty(); stats = stats.tail) {
                JCTree stat = stats.head;
                if (stat.hasTag(VARDEF)) {
                    int adr = ((JCVariableDecl) stat).sym.adr;
                    inits.excl(adr);
                    uninits.incl(adr);
                }
            }
        }

        boolean isEnabled(Lint.LintCategory lc) {
            return false;
        }

        void reportWarning(Lint.LintCategory lc, DiagnosticPosition pos, String key, Object... args) {
        }

        public void visitTry(JCTry tree) {
            ListBuffer<JCVariableDecl> resourceVarDecls = new ListBuffer<>();
            final Bits uninitsTryPrev = new Bits(uninitsTry);
            ListBuffer<P> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<>();
            final Bits initsTry = new Bits(inits);
            uninitsTry.assign(uninits);
            for (JCTree resource : tree.resources) {
                if (resource instanceof JCVariableDecl) {
                    JCVariableDecl vdecl = (JCVariableDecl) resource;
                    visitVarDef(vdecl);
                    unrefdResources.enter(vdecl.sym);
                    resourceVarDecls.append(vdecl);
                } else if (resource instanceof JCExpression) {
                    scanExpr(resource);
                } else {
                    throw new AssertionError(tree);
                }
            }
            scan(tree.body);
            uninitsTry.andSet(uninits);
            final Bits initsEnd = new Bits(inits);
            final Bits uninitsEnd = new Bits(uninits);
            int nextadrCatch = nextadr;
            if (!resourceVarDecls.isEmpty() &&
                    isEnabled(Lint.LintCategory.TRY)) {
                for (JCVariableDecl resVar : resourceVarDecls) {
                    if (unrefdResources.includes(resVar.sym)) {
                        reportWarning(Lint.LintCategory.TRY, resVar.pos(),
                                "try.resource.not.referenced", resVar.sym);
                        unrefdResources.remove(resVar.sym);
                    }
                }
            }

            final Bits initsCatchPrev = new Bits(initsTry);
            final Bits uninitsCatchPrev = new Bits(uninitsTry);
            for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
                JCVariableDecl param = l.head.param;
                assignToInits(tree.body, initsCatchPrev);
                uninits.assign(uninitsCatchPrev);
                scan(param);

                initParam(param);
                scan(l.head.body);
                initsEnd.andSet(inits);
                uninitsEnd.andSet(uninits);
                nextadr = nextadrCatch;
            }
            if (tree.finalizer != null) {
                assignToInits(tree.finalizer, initsTry);
                uninits.assign(uninitsTry);
                ListBuffer<P> exits = pendingExits;
                pendingExits = prevPendingExits;
                scan(tree.finalizer);
                if (!tree.finallyCanCompleteNormally) {

                } else {
                    uninits.andSet(uninitsEnd);


                    while (exits.nonEmpty()) {
                        P exit = exits.next();
                        if (exit.exit_inits != null) {
                            exit.exit_inits.orSet(inits);
                            exit.exit_uninits.andSet(uninits);
                        }
                        pendingExits.append(exit);
                    }
                    orSetInits(tree, initsEnd);
                }
            } else {
                assignToInits(tree, initsEnd);
                uninits.assign(uninitsEnd);
                ListBuffer<P> exits = pendingExits;
                pendingExits = prevPendingExits;
                while (exits.nonEmpty()) pendingExits.append(exits.next());
            }
            uninitsTry.andSet(uninitsTryPrev).andSet(uninits);
        }

        public void visitConditional(JCConditional tree) {
            scanCond(tree.cond);
            final Bits initsBeforeElse = new Bits(initsWhenFalse);
            final Bits uninitsBeforeElse = new Bits(uninitsWhenFalse);
            assignToInits(tree.cond, initsWhenTrue);
            uninits.assign(uninitsWhenTrue);
            if (tree.truepart.type.hasTag(BOOLEAN) &&
                    tree.falsepart.type.hasTag(BOOLEAN)) {


                scanCond(tree.truepart);
                final Bits initsAfterThenWhenTrue = new Bits(initsWhenTrue);
                final Bits initsAfterThenWhenFalse = new Bits(initsWhenFalse);
                final Bits uninitsAfterThenWhenTrue = new Bits(uninitsWhenTrue);
                final Bits uninitsAfterThenWhenFalse = new Bits(uninitsWhenFalse);
                assignToInits(tree.truepart, initsBeforeElse);
                uninits.assign(uninitsBeforeElse);
                scanCond(tree.falsepart);
                initsWhenTrue.andSet(initsAfterThenWhenTrue);
                initsWhenFalse.andSet(initsAfterThenWhenFalse);
                uninitsWhenTrue.andSet(uninitsAfterThenWhenTrue);
                uninitsWhenFalse.andSet(uninitsAfterThenWhenFalse);
            } else {
                scanExpr(tree.truepart);
                final Bits initsAfterThen = new Bits(inits);
                final Bits uninitsAfterThen = new Bits(uninits);
                assignToInits(tree.truepart, initsBeforeElse);
                uninits.assign(uninitsBeforeElse);
                scanExpr(tree.falsepart);
                andSetInits(tree.falsepart, initsAfterThen);
                uninits.andSet(uninitsAfterThen);
            }
        }

        public void visitIf(JCIf tree) {
            scanCond(tree.cond);
            final Bits initsBeforeElse = new Bits(initsWhenFalse);
            final Bits uninitsBeforeElse = new Bits(uninitsWhenFalse);
            assignToInits(tree.cond, initsWhenTrue);
            uninits.assign(uninitsWhenTrue);
            scan(tree.thenpart);
            if (tree.elsepart != null) {
                final Bits initsAfterThen = new Bits(inits);
                final Bits uninitsAfterThen = new Bits(uninits);
                assignToInits(tree.thenpart, initsBeforeElse);
                uninits.assign(uninitsBeforeElse);
                scan(tree.elsepart);
                andSetInits(tree.elsepart, initsAfterThen);
                uninits.andSet(uninitsAfterThen);
            } else {
                andSetInits(tree.thenpart, initsBeforeElse);
                uninits.andSet(uninitsBeforeElse);
            }
        }

        protected P createNewPendingExit(JCTree tree, Bits inits, Bits uninits) {
            return null;
        }

        @Override
        public void visitBreak(JCBreak tree) {
            recordExit(tree, createNewPendingExit(tree, inits, uninits));
        }

        @Override
        public void visitContinue(JCContinue tree) {
            recordExit(tree, createNewPendingExit(tree, inits, uninits));
        }

        @Override
        public void visitReturn(JCReturn tree) {
            scanExpr(tree.expr);
            recordExit(tree, createNewPendingExit(tree, inits, uninits));
        }

        public void visitThrow(JCThrow tree) {
            scanExpr(tree.expr);
            markDead(tree.expr);
        }

        public void visitApply(JCMethodInvocation tree) {
            scanExpr(tree.meth);
            scanExprs(tree.args);
        }

        public void visitNewClass(JCNewClass tree) {
            scanExpr(tree.encl);
            scanExprs(tree.args);
            scan(tree.def);
        }

        @Override
        public void visitLambda(JCLambda tree) {
            final Bits prevUninits = new Bits(uninits);
            final Bits prevInits = new Bits(inits);
            int returnadrPrev = returnadr;
            ListBuffer<P> prevPending = pendingExits;
            try {
                returnadr = nextadr;
                pendingExits = new ListBuffer<P>();
                for (List<JCVariableDecl> l = tree.params; l.nonEmpty(); l = l.tail) {
                    JCVariableDecl def = l.head;
                    scan(def);
                    inits.incl(def.sym.adr);
                    uninits.excl(def.sym.adr);
                }
                if (tree.getBodyKind() == JCLambda.BodyKind.EXPRESSION) {
                    scanExpr(tree.body);
                } else {
                    scan(tree.body);
                }
            } finally {
                returnadr = returnadrPrev;
                uninits.assign(prevUninits);
                assignToInits(tree, prevInits);
                pendingExits = prevPending;
            }
        }

        public void visitNewArray(JCNewArray tree) {
            scanExprs(tree.dims);
            scanExprs(tree.elems);
        }

        public void visitAssert(JCAssert tree) {
            final Bits initsExit = new Bits(inits);
            final Bits uninitsExit = new Bits(uninits);
            scanCond(tree.cond);
            uninitsExit.andSet(uninitsWhenTrue);
            if (tree.detail != null) {
                assignToInits(tree, initsWhenFalse);
                uninits.assign(uninitsWhenFalse);
                scanExpr(tree.detail);
            }
            assignToInits(tree, initsExit);
            uninits.assign(uninitsExit);
        }

        public void visitAssign(JCAssign tree) {
            JCTree lhs = TreeInfo.skipParens(tree.lhs);
            if (!(lhs instanceof JCIdent)) {
                scanExpr(lhs);
            }
            scanExpr(tree.rhs);
            letInit(lhs);
        }

        public void visitAssignop(JCAssignOp tree) {
            scanExpr(tree.lhs);
            scanExpr(tree.rhs);
            letInit(tree.lhs);
        }

        public void visitUnary(JCUnary tree) {
            switch (tree.getTag()) {
                case NOT:
                    scanCond(tree.arg);
                    final Bits t = new Bits(initsWhenFalse);
                    initsWhenFalse.assign(initsWhenTrue);
                    initsWhenTrue.assign(t);
                    t.assign(uninitsWhenFalse);
                    uninitsWhenFalse.assign(uninitsWhenTrue);
                    uninitsWhenTrue.assign(t);
                    break;
                case PREINC:
                case POSTINC:
                case PREDEC:
                case POSTDEC:
                    scanExpr(tree.arg);
                    letInit(tree.arg);
                    break;
                default:
                    scanExpr(tree.arg);
            }
        }

        public void visitBinary(JCBinary tree) {
            switch (tree.getTag()) {
                case AND:
                    scanCond(tree.lhs);
                    final Bits initsWhenFalseLeft = new Bits(initsWhenFalse);
                    final Bits uninitsWhenFalseLeft = new Bits(uninitsWhenFalse);
                    assignToInits(tree.lhs, initsWhenTrue);
                    uninits.assign(uninitsWhenTrue);
                    scanCond(tree.rhs);
                    initsWhenFalse.andSet(initsWhenFalseLeft);
                    uninitsWhenFalse.andSet(uninitsWhenFalseLeft);
                    break;
                case OR:
                    scanCond(tree.lhs);
                    final Bits initsWhenTrueLeft = new Bits(initsWhenTrue);
                    final Bits uninitsWhenTrueLeft = new Bits(uninitsWhenTrue);
                    assignToInits(tree.lhs, initsWhenFalse);
                    uninits.assign(uninitsWhenFalse);
                    scanCond(tree.rhs);
                    initsWhenTrue.andSet(initsWhenTrueLeft);
                    uninitsWhenTrue.andSet(uninitsWhenTrueLeft);
                    break;
                default:
                    scanExpr(tree.lhs);
                    scanExpr(tree.rhs);
            }
        }

        public void visitIdent(JCIdent tree) {
            if (tree.sym.kind == VAR) {
                checkInit(tree.pos(), (VarSymbol) tree.sym);
                referenced(tree.sym);
            }
        }

        void referenced(Symbol sym) {
            unrefdResources.remove(sym);
        }

        public void visitAnnotatedType(JCAnnotatedType tree) {

            tree.underlyingType.accept(this);
        }

        public void visitTopLevel(JCCompilationUnit tree) {

        }


        public void analyzeTree(Env<?> env) {
            analyzeTree(env, env.tree);
        }

        public void analyzeTree(Env<?> env, JCTree tree) {
            try {
                startPos = tree.pos().getStartPosition();
                if (vardecls == null)
                    vardecls = new JCVariableDecl[32];
                else
                    for (int i = 0; i < vardecls.length; i++)
                        vardecls[i] = null;
                firstadr = 0;
                nextadr = 0;
                pendingExits = new ListBuffer<>();
                this.classDef = null;
                unrefdResources = new Scope(env.enclClass.sym);
                scan(tree);
            } finally {

                startPos = -1;
                resetBits(inits, uninits, uninitsTry, initsWhenTrue,
                        initsWhenFalse, uninitsWhenTrue, uninitsWhenFalse);
                if (vardecls != null) {
                    for (int i = 0; i < vardecls.length; i++)
                        vardecls[i] = null;
                }
                firstadr = 0;
                nextadr = 0;
                pendingExits = null;
                this.classDef = null;
                unrefdResources = null;
            }
        }
    }

    public static class AssignPendingExit
            extends AbstractAssignPendingExit {
        public AssignPendingExit(JCTree tree, final Bits inits, final Bits uninits) {
            super(tree, inits, uninits);
        }
    }

    public static class AssignAnalyzer
            extends AbstractAssignAnalyzer<AssignPendingExit> {
        Log log;
        Lint lint;

        public AssignAnalyzer(Log log, Symtab syms, Lint lint, Names names) {
            super(new Bits(), syms, names);
            this.log = log;
            this.lint = lint;
        }

        @Override
        protected AssignPendingExit createNewPendingExit(JCTree tree,
                                                         Bits inits, Bits uninits) {
            return new AssignPendingExit(tree, inits, uninits);
        }

        @Override
        void letInit(DiagnosticPosition pos, VarSymbol sym) {
            if (sym.adr >= firstadr && trackable(sym)) {
                if ((sym.flags() & EFFECTIVELY_FINAL) != 0) {
                    if (!uninits.isMember(sym.adr)) {


                        sym.flags_field &= ~EFFECTIVELY_FINAL;
                    } else {
                        uninit(sym);
                    }
                } else if ((sym.flags() & FINAL) != 0) {
                    if ((sym.flags() & PARAMETER) != 0) {
                        if ((sym.flags() & UNION) != 0) {
                            log.error(pos, "multicatch.parameter.may.not.be.assigned", sym);
                        } else {
                            log.error(pos, "final.parameter.may.not.be.assigned",
                                    sym);
                        }
                    } else if (!uninits.isMember(sym.adr)) {
                        log.error(pos, flowKind.errKey, sym);
                    } else {
                        uninit(sym);
                    }
                }
                inits.incl(sym.adr);
            } else if ((sym.flags() & FINAL) != 0) {
                log.error(pos, "var.might.already.be.assigned", sym);
            }
        }

        @Override
        void checkInit(DiagnosticPosition pos, VarSymbol sym, String errkey) {
            if ((sym.adr >= firstadr || sym.owner.kind != TYP) &&
                    trackable(sym) &&
                    !inits.isMember(sym.adr)) {
                log.error(pos, errkey, sym);
                inits.incl(sym.adr);
            }
        }

        @Override
        void reportWarning(Lint.LintCategory lc, DiagnosticPosition pos,
                           String key, Object... args) {
            log.warning(lc, pos, key, args);
        }

        @Override
        int getLogNumberOfErrors() {
            return log.nerrors;
        }

        @Override
        boolean isEnabled(Lint.LintCategory lc) {
            return lint.isEnabled(lc);
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            if (tree.sym == null) {
                return;
            }
            Lint lintPrev = lint;
            lint = lint.augment(tree.sym);
            try {
                super.visitClassDef(tree);
            } finally {
                lint = lintPrev;
            }
        }

        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            if (tree.body == null) {
                return;
            }

            if ((tree.sym.flags() & SYNTHETIC) != 0) {
                return;
            }
            Lint lintPrev = lint;
            lint = lint.augment(tree.sym);
            try {
                super.visitMethodDef(tree);
            } finally {
                lint = lintPrev;
            }
        }

        @Override
        public void visitVarDef(JCVariableDecl tree) {
            if (tree.init == null) {
                super.visitVarDef(tree);
            } else {
                Lint lintPrev = lint;
                lint = lint.augment(tree.sym);
                try {
                    super.visitVarDef(tree);
                } finally {
                    lint = lintPrev;
                }
            }
        }
    }

    class AliveAnalyzer extends BaseAnalyzer<PendingExit> {

        private boolean alive;

        @Override
        void markDead(JCTree tree) {
            alive = false;
        }


        void scanDef(JCTree tree) {
            scanStat(tree);
            if (tree != null && tree.hasTag(Tag.BLOCK) && !alive) {
                log.error(tree.pos(),
                        "initializer.must.be.able.to.complete.normally");
            }
        }

        void scanStat(JCTree tree) {
            if (!alive && tree != null) {
                log.error(tree.pos(), "unreachable.stmt");
                if (!tree.hasTag(SKIP)) alive = true;
            }
            scan(tree);
        }

        void scanStats(List<? extends JCStatement> trees) {
            if (trees != null)
                for (List<? extends JCStatement> l = trees; l.nonEmpty(); l = l.tail)
                    scanStat(l.head);
        }

        public void visitClassDef(JCClassDecl tree) {
            if (tree.sym == null) return;
            boolean alivePrev = alive;
            ListBuffer<PendingExit> pendingExitsPrev = pendingExits;
            Lint lintPrev = lint;
            pendingExits = new ListBuffer<PendingExit>();
            lint = lint.augment(tree.sym);
            try {

                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (!l.head.hasTag(METHODDEF) &&
                            (TreeInfo.flags(l.head) & STATIC) != 0) {
                        scanDef(l.head);
                    }
                }

                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (!l.head.hasTag(METHODDEF) &&
                            (TreeInfo.flags(l.head) & STATIC) == 0) {
                        scanDef(l.head);
                    }
                }

                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (l.head.hasTag(METHODDEF)) {
                        scan(l.head);
                    }
                }
            } finally {
                pendingExits = pendingExitsPrev;
                alive = alivePrev;
                lint = lintPrev;
            }
        }

        public void visitMethodDef(JCMethodDecl tree) {
            if (tree.body == null) return;
            Lint lintPrev = lint;
            lint = lint.augment(tree.sym);
            Assert.check(pendingExits.isEmpty());
            try {
                alive = true;
                scanStat(tree.body);
                if (alive && !tree.sym.type.getReturnType().hasTag(VOID))
                    log.error(TreeInfo.diagEndPos(tree.body), "missing.ret.stmt");
                List<PendingExit> exits = pendingExits.toList();
                pendingExits = new ListBuffer<PendingExit>();
                while (exits.nonEmpty()) {
                    PendingExit exit = exits.head;
                    exits = exits.tail;
                    Assert.check(exit.tree.hasTag(RETURN));
                }
            } finally {
                lint = lintPrev;
            }
        }

        public void visitVarDef(JCVariableDecl tree) {
            if (tree.init != null) {
                Lint lintPrev = lint;
                lint = lint.augment(tree.sym);
                try {
                    scan(tree.init);
                } finally {
                    lint = lintPrev;
                }
            }
        }

        public void visitBlock(JCBlock tree) {
            scanStats(tree.stats);
        }

        public void visitDoLoop(JCDoWhileLoop tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<PendingExit>();
            scanStat(tree.body);
            alive |= resolveContinues(tree);
            scan(tree.cond);
            alive = alive && !tree.cond.type.isTrue();
            alive |= resolveBreaks(tree, prevPendingExits);
        }

        public void visitWhileLoop(JCWhileLoop tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<PendingExit>();
            scan(tree.cond);
            alive = !tree.cond.type.isFalse();
            scanStat(tree.body);
            alive |= resolveContinues(tree);
            alive = resolveBreaks(tree, prevPendingExits) ||
                    !tree.cond.type.isTrue();
        }

        public void visitForLoop(JCForLoop tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            scanStats(tree.init);
            pendingExits = new ListBuffer<PendingExit>();
            if (tree.cond != null) {
                scan(tree.cond);
                alive = !tree.cond.type.isFalse();
            } else {
                alive = true;
            }
            scanStat(tree.body);
            alive |= resolveContinues(tree);
            scan(tree.step);
            alive = resolveBreaks(tree, prevPendingExits) ||
                    tree.cond != null && !tree.cond.type.isTrue();
        }

        public void visitForeachLoop(JCEnhancedForLoop tree) {
            visitVarDef(tree.var);
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            scan(tree.expr);
            pendingExits = new ListBuffer<PendingExit>();
            scanStat(tree.body);
            alive |= resolveContinues(tree);
            resolveBreaks(tree, prevPendingExits);
            alive = true;
        }

        public void visitLabelled(JCLabeledStatement tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<PendingExit>();
            scanStat(tree.body);
            alive |= resolveBreaks(tree, prevPendingExits);
        }

        public void visitSwitch(JCSwitch tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<PendingExit>();
            scan(tree.selector);
            boolean hasDefault = false;
            for (List<JCCase> l = tree.cases; l.nonEmpty(); l = l.tail) {
                alive = true;
                JCCase c = l.head;
                if (c.pat == null)
                    hasDefault = true;
                else
                    scan(c.pat);
                scanStats(c.stats);

                if (alive &&
                        lint.isEnabled(Lint.LintCategory.FALLTHROUGH) &&
                        c.stats.nonEmpty() && l.tail.nonEmpty())
                    log.warning(Lint.LintCategory.FALLTHROUGH,
                            l.tail.head.pos(),
                            "possible.fall-through.into.case");
            }
            if (!hasDefault) {
                alive = true;
            }
            alive |= resolveBreaks(tree, prevPendingExits);
        }

        public void visitTry(JCTry tree) {
            ListBuffer<PendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<PendingExit>();
            for (JCTree resource : tree.resources) {
                if (resource instanceof JCVariableDecl) {
                    JCVariableDecl vdecl = (JCVariableDecl) resource;
                    visitVarDef(vdecl);
                } else if (resource instanceof JCExpression) {
                    scan(resource);
                } else {
                    throw new AssertionError(tree);
                }
            }
            scanStat(tree.body);
            boolean aliveEnd = alive;
            for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
                alive = true;
                JCVariableDecl param = l.head.param;
                scan(param);
                scanStat(l.head.body);
                aliveEnd |= alive;
            }
            if (tree.finalizer != null) {
                ListBuffer<PendingExit> exits = pendingExits;
                pendingExits = prevPendingExits;
                alive = true;
                scanStat(tree.finalizer);
                tree.finallyCanCompleteNormally = alive;
                if (!alive) {
                    if (lint.isEnabled(Lint.LintCategory.FINALLY)) {
                        log.warning(Lint.LintCategory.FINALLY,
                                TreeInfo.diagEndPos(tree.finalizer),
                                "finally.cannot.complete");
                    }
                } else {
                    while (exits.nonEmpty()) {
                        pendingExits.append(exits.next());
                    }
                    alive = aliveEnd;
                }
            } else {
                alive = aliveEnd;
                ListBuffer<PendingExit> exits = pendingExits;
                pendingExits = prevPendingExits;
                while (exits.nonEmpty()) pendingExits.append(exits.next());
            }
        }

        @Override
        public void visitIf(JCIf tree) {
            scan(tree.cond);
            scanStat(tree.thenpart);
            if (tree.elsepart != null) {
                boolean aliveAfterThen = alive;
                alive = true;
                scanStat(tree.elsepart);
                alive = alive | aliveAfterThen;
            } else {
                alive = true;
            }
        }

        public void visitBreak(JCBreak tree) {
            recordExit(tree, new PendingExit(tree));
        }

        public void visitContinue(JCContinue tree) {
            recordExit(tree, new PendingExit(tree));
        }

        public void visitReturn(JCReturn tree) {
            scan(tree.expr);
            recordExit(tree, new PendingExit(tree));
        }

        public void visitThrow(JCThrow tree) {
            scan(tree.expr);
            markDead(tree);
        }

        public void visitApply(JCMethodInvocation tree) {
            scan(tree.meth);
            scan(tree.args);
        }

        public void visitNewClass(JCNewClass tree) {
            scan(tree.encl);
            scan(tree.args);
            if (tree.def != null) {
                scan(tree.def);
            }
        }

        @Override
        public void visitLambda(JCLambda tree) {
            if (tree.type != null &&
                    tree.type.isErroneous()) {
                return;
            }
            ListBuffer<PendingExit> prevPending = pendingExits;
            boolean prevAlive = alive;
            try {
                pendingExits = new ListBuffer<>();
                alive = true;
                scanStat(tree.body);
                tree.canCompleteNormally = alive;
            } finally {
                pendingExits = prevPending;
                alive = prevAlive;
            }
        }

        public void visitTopLevel(JCCompilationUnit tree) {

        }


        public void analyzeTree(Env<AttrContext> env, TreeMaker make) {
            analyzeTree(env, env.tree, make);
        }

        public void analyzeTree(Env<AttrContext> env, JCTree tree, TreeMaker make) {
            try {
                attrEnv = env;
                Flow.this.make = make;
                pendingExits = new ListBuffer<PendingExit>();
                alive = true;
                scan(tree);
            } finally {
                pendingExits = null;
                Flow.this.make = null;
            }
        }
    }

    class FlowPendingExit extends PendingExit {
        Type thrown;

        FlowPendingExit(JCTree tree, Type thrown) {
            super(tree);
            this.thrown = thrown;
        }
    }

    class FlowAnalyzer extends BaseAnalyzer<FlowPendingExit> {

        HashMap<Symbol, List<Type>> preciseRethrowTypes;

        JCClassDecl classDef;

        List<Type> thrown;

        List<Type> caught;

        @Override
        void markDead(JCTree tree) {

        }


        void errorUncaught() {
            for (FlowPendingExit exit = pendingExits.next();
                 exit != null;
                 exit = pendingExits.next()) {
                if (classDef != null &&
                        classDef.pos == exit.tree.pos) {
                    log.error(exit.tree.pos(),
                            "unreported.exception.default.constructor",
                            exit.thrown);
                } else if (exit.tree.hasTag(VARDEF) &&
                        ((JCVariableDecl) exit.tree).sym.isResourceVariable()) {
                    log.error(exit.tree.pos(),
                            "unreported.exception.implicit.close",
                            exit.thrown,
                            ((JCVariableDecl) exit.tree).sym.name);
                } else {
                    log.error(exit.tree.pos(),
                            "unreported.exception.need.to.catch.or.throw",
                            exit.thrown);
                }
            }
        }

        void markThrown(JCTree tree, Type exc) {
            if (!chk.isUnchecked(tree.pos(), exc)) {
                if (!chk.isHandled(exc, caught)) {
                    pendingExits.append(new FlowPendingExit(tree, exc));
                }
                thrown = chk.incl(exc, thrown);
            }
        }


        public void visitClassDef(JCClassDecl tree) {
            if (tree.sym == null) return;
            JCClassDecl classDefPrev = classDef;
            List<Type> thrownPrev = thrown;
            List<Type> caughtPrev = caught;
            ListBuffer<FlowPendingExit> pendingExitsPrev = pendingExits;
            Lint lintPrev = lint;
            pendingExits = new ListBuffer<FlowPendingExit>();
            if (tree.name != names.empty) {
                caught = List.nil();
            }
            classDef = tree;
            thrown = List.nil();
            lint = lint.augment(tree.sym);
            try {

                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (!l.head.hasTag(METHODDEF) &&
                            (TreeInfo.flags(l.head) & STATIC) != 0) {
                        scan(l.head);
                        errorUncaught();
                    }
                }


                if (tree.name != names.empty) {
                    boolean firstConstructor = true;
                    for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                        if (TreeInfo.isInitialConstructor(l.head)) {
                            List<Type> mthrown =
                                    ((JCMethodDecl) l.head).sym.type.getThrownTypes();
                            if (firstConstructor) {
                                caught = mthrown;
                                firstConstructor = false;
                            } else {
                                caught = chk.intersect(mthrown, caught);
                            }
                        }
                    }
                }

                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (!l.head.hasTag(METHODDEF) &&
                            (TreeInfo.flags(l.head) & STATIC) == 0) {
                        scan(l.head);
                        errorUncaught();
                    }
                }


                if (tree.name == names.empty) {
                    for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                        if (TreeInfo.isInitialConstructor(l.head)) {
                            JCMethodDecl mdef = (JCMethodDecl) l.head;
                            mdef.thrown = make.Types(thrown);
                            mdef.sym.type = types.createMethodTypeWithThrown(mdef.sym.type, thrown);
                        }
                    }
                    thrownPrev = chk.union(thrown, thrownPrev);
                }

                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (l.head.hasTag(METHODDEF)) {
                        scan(l.head);
                        errorUncaught();
                    }
                }
                thrown = thrownPrev;
            } finally {
                pendingExits = pendingExitsPrev;
                caught = caughtPrev;
                classDef = classDefPrev;
                lint = lintPrev;
            }
        }

        public void visitMethodDef(JCMethodDecl tree) {
            if (tree.body == null) return;
            List<Type> caughtPrev = caught;
            List<Type> mthrown = tree.sym.type.getThrownTypes();
            Lint lintPrev = lint;
            lint = lint.augment(tree.sym);
            Assert.check(pendingExits.isEmpty());
            try {
                for (List<JCVariableDecl> l = tree.params; l.nonEmpty(); l = l.tail) {
                    JCVariableDecl def = l.head;
                    scan(def);
                }
                if (TreeInfo.isInitialConstructor(tree))
                    caught = chk.union(caught, mthrown);
                else if ((tree.sym.flags() & (BLOCK | STATIC)) != BLOCK)
                    caught = mthrown;


                scan(tree.body);
                List<FlowPendingExit> exits = pendingExits.toList();
                pendingExits = new ListBuffer<FlowPendingExit>();
                while (exits.nonEmpty()) {
                    FlowPendingExit exit = exits.head;
                    exits = exits.tail;
                    if (exit.thrown == null) {
                        Assert.check(exit.tree.hasTag(RETURN));
                    } else {

                        pendingExits.append(exit);
                    }
                }
            } finally {
                caught = caughtPrev;
                lint = lintPrev;
            }
        }

        public void visitVarDef(JCVariableDecl tree) {
            if (tree.init != null) {
                Lint lintPrev = lint;
                lint = lint.augment(tree.sym);
                try {
                    scan(tree.init);
                } finally {
                    lint = lintPrev;
                }
            }
        }

        public void visitBlock(JCBlock tree) {
            scan(tree.stats);
        }

        public void visitDoLoop(JCDoWhileLoop tree) {
            ListBuffer<FlowPendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<FlowPendingExit>();
            scan(tree.body);
            resolveContinues(tree);
            scan(tree.cond);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitWhileLoop(JCWhileLoop tree) {
            ListBuffer<FlowPendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<FlowPendingExit>();
            scan(tree.cond);
            scan(tree.body);
            resolveContinues(tree);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitForLoop(JCForLoop tree) {
            ListBuffer<FlowPendingExit> prevPendingExits = pendingExits;
            scan(tree.init);
            pendingExits = new ListBuffer<FlowPendingExit>();
            if (tree.cond != null) {
                scan(tree.cond);
            }
            scan(tree.body);
            resolveContinues(tree);
            scan(tree.step);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitForeachLoop(JCEnhancedForLoop tree) {
            visitVarDef(tree.var);
            ListBuffer<FlowPendingExit> prevPendingExits = pendingExits;
            scan(tree.expr);
            pendingExits = new ListBuffer<FlowPendingExit>();
            scan(tree.body);
            resolveContinues(tree);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitLabelled(JCLabeledStatement tree) {
            ListBuffer<FlowPendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<FlowPendingExit>();
            scan(tree.body);
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitSwitch(JCSwitch tree) {
            ListBuffer<FlowPendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<FlowPendingExit>();
            scan(tree.selector);
            for (List<JCCase> l = tree.cases; l.nonEmpty(); l = l.tail) {
                JCCase c = l.head;
                if (c.pat != null) {
                    scan(c.pat);
                }
                scan(c.stats);
            }
            resolveBreaks(tree, prevPendingExits);
        }

        public void visitTry(JCTry tree) {
            List<Type> caughtPrev = caught;
            List<Type> thrownPrev = thrown;
            thrown = List.nil();
            for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
                List<JCExpression> subClauses = TreeInfo.isMultiCatch(l.head) ?
                        ((JCTypeUnion) l.head.param.vartype).alternatives :
                        List.of(l.head.param.vartype);
                for (JCExpression ct : subClauses) {
                    caught = chk.incl(ct.type, caught);
                }
            }
            ListBuffer<FlowPendingExit> prevPendingExits = pendingExits;
            pendingExits = new ListBuffer<FlowPendingExit>();
            for (JCTree resource : tree.resources) {
                if (resource instanceof JCVariableDecl) {
                    JCVariableDecl vdecl = (JCVariableDecl) resource;
                    visitVarDef(vdecl);
                } else if (resource instanceof JCExpression) {
                    scan(resource);
                } else {
                    throw new AssertionError(tree);
                }
            }
            for (JCTree resource : tree.resources) {
                List<Type> closeableSupertypes = resource.type.isCompound() ?
                        types.interfaces(resource.type).prepend(types.supertype(resource.type)) :
                        List.of(resource.type);
                for (Type sup : closeableSupertypes) {
                    if (types.asSuper(sup, syms.autoCloseableType.tsym) != null) {
                        Symbol closeMethod = rs.resolveQualifiedMethod(tree,
                                attrEnv,
                                sup,
                                names.close,
                                List.nil(),
                                List.nil());
                        Type mt = types.memberType(resource.type, closeMethod);
                        if (closeMethod.kind == MTH) {
                            for (Type t : mt.getThrownTypes()) {
                                markThrown(resource, t);
                            }
                        }
                    }
                }
            }
            scan(tree.body);
            List<Type> thrownInTry = allowImprovedCatchAnalysis ?
                    chk.union(thrown, List.of(syms.runtimeExceptionType, syms.errorType)) :
                    thrown;
            thrown = thrownPrev;
            caught = caughtPrev;
            List<Type> caughtInTry = List.nil();
            for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
                JCVariableDecl param = l.head.param;
                List<JCExpression> subClauses = TreeInfo.isMultiCatch(l.head) ?
                        ((JCTypeUnion) l.head.param.vartype).alternatives :
                        List.of(l.head.param.vartype);
                List<Type> ctypes = List.nil();
                List<Type> rethrownTypes = chk.diff(thrownInTry, caughtInTry);
                for (JCExpression ct : subClauses) {
                    Type exc = ct.type;
                    if (exc != syms.unknownType) {
                        ctypes = ctypes.append(exc);
                        if (types.isSameType(exc, syms.objectType))
                            continue;
                        checkCaughtType(l.head.pos(), exc, thrownInTry, caughtInTry);
                        caughtInTry = chk.incl(exc, caughtInTry);
                    }
                }
                scan(param);
                preciseRethrowTypes.put(param.sym, chk.intersect(ctypes, rethrownTypes));
                scan(l.head.body);
                preciseRethrowTypes.remove(param.sym);
            }
            if (tree.finalizer != null) {
                List<Type> savedThrown = thrown;
                thrown = List.nil();
                ListBuffer<FlowPendingExit> exits = pendingExits;
                pendingExits = prevPendingExits;
                scan(tree.finalizer);
                if (!tree.finallyCanCompleteNormally) {

                    thrown = chk.union(thrown, thrownPrev);
                } else {
                    thrown = chk.union(thrown, chk.diff(thrownInTry, caughtInTry));
                    thrown = chk.union(thrown, savedThrown);


                    while (exits.nonEmpty()) {
                        pendingExits.append(exits.next());
                    }
                }
            } else {
                thrown = chk.union(thrown, chk.diff(thrownInTry, caughtInTry));
                ListBuffer<FlowPendingExit> exits = pendingExits;
                pendingExits = prevPendingExits;
                while (exits.nonEmpty()) pendingExits.append(exits.next());
            }
        }

        @Override
        public void visitIf(JCIf tree) {
            scan(tree.cond);
            scan(tree.thenpart);
            if (tree.elsepart != null) {
                scan(tree.elsepart);
            }
        }

        void checkCaughtType(DiagnosticPosition pos, Type exc, List<Type> thrownInTry, List<Type> caughtInTry) {
            if (chk.subset(exc, caughtInTry)) {
                log.error(pos, "except.already.caught", exc);
            } else if (!chk.isUnchecked(pos, exc) &&
                    !isExceptionOrThrowable(exc) &&
                    !chk.intersects(exc, thrownInTry)) {
                log.error(pos, "except.never.thrown.in.try", exc);
            } else if (allowImprovedCatchAnalysis) {
                List<Type> catchableThrownTypes = chk.intersect(List.of(exc), thrownInTry);


                if (chk.diff(catchableThrownTypes, caughtInTry).isEmpty() &&
                        !isExceptionOrThrowable(exc)) {
                    String key = catchableThrownTypes.length() == 1 ?
                            "unreachable.catch" :
                            "unreachable.catch.1";
                    log.warning(pos, key, catchableThrownTypes);
                }
            }
        }

        private boolean isExceptionOrThrowable(Type exc) {
            return exc.tsym == syms.throwableType.tsym ||
                    exc.tsym == syms.exceptionType.tsym;
        }

        public void visitBreak(JCBreak tree) {
            recordExit(tree, new FlowPendingExit(tree, null));
        }

        public void visitContinue(JCContinue tree) {
            recordExit(tree, new FlowPendingExit(tree, null));
        }

        public void visitReturn(JCReturn tree) {
            scan(tree.expr);
            recordExit(tree, new FlowPendingExit(tree, null));
        }

        public void visitThrow(JCThrow tree) {
            scan(tree.expr);
            Symbol sym = TreeInfo.symbol(tree.expr);
            if (sym != null &&
                    sym.kind == VAR &&
                    (sym.flags() & (FINAL | EFFECTIVELY_FINAL)) != 0 &&
                    preciseRethrowTypes.get(sym) != null &&
                    allowImprovedRethrowAnalysis) {
                for (Type t : preciseRethrowTypes.get(sym)) {
                    markThrown(tree, t);
                }
            } else {
                markThrown(tree, tree.expr.type);
            }
            markDead(tree);
        }

        public void visitApply(JCMethodInvocation tree) {
            scan(tree.meth);
            scan(tree.args);
            for (List<Type> l = tree.meth.type.getThrownTypes(); l.nonEmpty(); l = l.tail)
                markThrown(tree, l.head);
        }

        public void visitNewClass(JCNewClass tree) {
            scan(tree.encl);
            scan(tree.args);

            for (List<Type> l = tree.constructorType.getThrownTypes();
                 l.nonEmpty();
                 l = l.tail) {
                markThrown(tree, l.head);
            }
            List<Type> caughtPrev = caught;
            try {


                if (tree.def != null)
                    for (List<Type> l = tree.constructor.type.getThrownTypes();
                         l.nonEmpty();
                         l = l.tail) {
                        caught = chk.incl(l.head, caught);
                    }
                scan(tree.def);
            } finally {
                caught = caughtPrev;
            }
        }

        @Override
        public void visitLambda(JCLambda tree) {
            if (tree.type != null &&
                    tree.type.isErroneous()) {
                return;
            }
            List<Type> prevCaught = caught;
            List<Type> prevThrown = thrown;
            ListBuffer<FlowPendingExit> prevPending = pendingExits;
            try {
                pendingExits = new ListBuffer<>();
                caught = tree.getDescriptorType(types).getThrownTypes();
                thrown = List.nil();
                scan(tree.body);
                List<FlowPendingExit> exits = pendingExits.toList();
                pendingExits = new ListBuffer<FlowPendingExit>();
                while (exits.nonEmpty()) {
                    FlowPendingExit exit = exits.head;
                    exits = exits.tail;
                    if (exit.thrown == null) {
                        Assert.check(exit.tree.hasTag(RETURN));
                    } else {

                        pendingExits.append(exit);
                    }
                }
                errorUncaught();
            } finally {
                pendingExits = prevPending;
                caught = prevCaught;
                thrown = prevThrown;
            }
        }

        public void visitTopLevel(JCCompilationUnit tree) {

        }


        public void analyzeTree(Env<AttrContext> env, TreeMaker make) {
            analyzeTree(env, env.tree, make);
        }

        public void analyzeTree(Env<AttrContext> env, JCTree tree, TreeMaker make) {
            try {
                attrEnv = env;
                Flow.this.make = make;
                pendingExits = new ListBuffer<FlowPendingExit>();
                preciseRethrowTypes = new HashMap<Symbol, List<Type>>();
                this.thrown = this.caught = null;
                this.classDef = null;
                scan(tree);
            } finally {
                pendingExits = null;
                Flow.this.make = null;
                this.thrown = this.caught = null;
                this.classDef = null;
            }
        }
    }

    class LambdaFlowAnalyzer extends FlowAnalyzer {
        List<Type> inferredThrownTypes;
        boolean inLambda;

        @Override
        public void visitLambda(JCLambda tree) {
            if ((tree.type != null &&
                    tree.type.isErroneous()) || inLambda) {
                return;
            }
            List<Type> prevCaught = caught;
            List<Type> prevThrown = thrown;
            ListBuffer<FlowPendingExit> prevPending = pendingExits;
            inLambda = true;
            try {
                pendingExits = new ListBuffer<>();
                caught = List.of(syms.throwableType);
                thrown = List.nil();
                scan(tree.body);
                inferredThrownTypes = thrown;
            } finally {
                pendingExits = prevPending;
                caught = prevCaught;
                thrown = prevThrown;
                inLambda = false;
            }
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {

        }
    }

    class CaptureAnalyzer extends BaseAnalyzer<PendingExit> {
        JCTree currentTree;

        @Override
        void markDead(JCTree tree) {

        }

        @SuppressWarnings("fallthrough")
        void checkEffectivelyFinal(DiagnosticPosition pos, VarSymbol sym) {
            if (currentTree != null &&
                    sym.owner.kind == MTH &&
                    sym.pos < currentTree.getStartPosition()) {
                switch (currentTree.getTag()) {
                    case CLASSDEF:
                        if (!allowEffectivelyFinalInInnerClasses) {
                            if ((sym.flags() & FINAL) == 0) {
                                reportInnerClsNeedsFinalError(pos, sym);
                            }
                            break;
                        }
                    case LAMBDA:
                        if ((sym.flags() & (EFFECTIVELY_FINAL | FINAL)) == 0) {
                            reportEffectivelyFinalError(pos, sym);
                        }
                }
            }
        }

        @SuppressWarnings("fallthrough")
        void letInit(JCTree tree) {
            tree = TreeInfo.skipParens(tree);
            if (tree.hasTag(IDENT) || tree.hasTag(SELECT)) {
                Symbol sym = TreeInfo.symbol(tree);
                if (currentTree != null &&
                        sym.kind == VAR &&
                        sym.owner.kind == MTH &&
                        ((VarSymbol) sym).pos < currentTree.getStartPosition()) {
                    switch (currentTree.getTag()) {
                        case CLASSDEF:
                            if (!allowEffectivelyFinalInInnerClasses) {
                                reportInnerClsNeedsFinalError(tree, sym);
                                break;
                            }
                        case LAMBDA:
                            reportEffectivelyFinalError(tree, sym);
                    }
                }
            }
        }

        void reportEffectivelyFinalError(DiagnosticPosition pos, Symbol sym) {
            String subKey = currentTree.hasTag(LAMBDA) ?
                    "lambda" : "inner.cls";
            log.error(pos, "cant.ref.non.effectively.final.var", sym, diags.fragment(subKey));
        }

        void reportInnerClsNeedsFinalError(DiagnosticPosition pos, Symbol sym) {
            log.error(pos,
                    "local.var.accessed.from.icls.needs.final",
                    sym);
        }


        public void visitClassDef(JCClassDecl tree) {
            JCTree prevTree = currentTree;
            try {
                currentTree = tree.sym.isLocal() ? tree : null;
                super.visitClassDef(tree);
            } finally {
                currentTree = prevTree;
            }
        }

        @Override
        public void visitLambda(JCLambda tree) {
            JCTree prevTree = currentTree;
            try {
                currentTree = tree;
                super.visitLambda(tree);
            } finally {
                currentTree = prevTree;
            }
        }

        @Override
        public void visitIdent(JCIdent tree) {
            if (tree.sym.kind == VAR) {
                checkEffectivelyFinal(tree, (VarSymbol) tree.sym);
            }
        }

        public void visitAssign(JCAssign tree) {
            JCTree lhs = TreeInfo.skipParens(tree.lhs);
            if (!(lhs instanceof JCIdent)) {
                scan(lhs);
            }
            scan(tree.rhs);
            letInit(lhs);
        }

        public void visitAssignop(JCAssignOp tree) {
            scan(tree.lhs);
            scan(tree.rhs);
            letInit(tree.lhs);
        }

        public void visitUnary(JCUnary tree) {
            switch (tree.getTag()) {
                case PREINC:
                case POSTINC:
                case PREDEC:
                case POSTDEC:
                    scan(tree.arg);
                    letInit(tree.arg);
                    break;
                default:
                    scan(tree.arg);
            }
        }

        public void visitTopLevel(JCCompilationUnit tree) {

        }


        public void analyzeTree(Env<AttrContext> env, TreeMaker make) {
            analyzeTree(env, env.tree, make);
        }

        public void analyzeTree(Env<AttrContext> env, JCTree tree, TreeMaker make) {
            try {
                attrEnv = env;
                Flow.this.make = make;
                pendingExits = new ListBuffer<PendingExit>();
                scan(tree);
            } finally {
                pendingExits = null;
                Flow.this.make = null;
            }
        }
    }
}
