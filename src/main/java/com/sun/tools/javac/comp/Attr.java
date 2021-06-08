package com.sun.tools.javac.comp;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberReferenceTree.ReferenceMode;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.TreeVisitor;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.comp.Check.CheckContext;
import com.sun.tools.javac.comp.DeferredAttr.AttrMode;
import com.sun.tools.javac.comp.Infer.FreeTypeListener;
import com.sun.tools.javac.comp.Infer.InferenceContext;
import com.sun.tools.javac.jvm.ByteCodes;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree.JCPolyExpression.PolyKind;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import javax.lang.model.element.ElementKind;
import javax.tools.JavaFileObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.sun.tools.javac.code.Flags.ANNOTATION;
import static com.sun.tools.javac.code.Flags.BLOCK;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.ERRONEOUS;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTag.WILDCARD;
import static com.sun.tools.javac.code.TypeTag.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

public class Attr extends JCTree.Visitor {
    public static final Filter<Symbol> anyNonAbstractOrDefaultMethod = new Filter<Symbol>() {
        @Override
        public boolean accepts(Symbol s) {
            return s.kind == Kinds.MTH &&
                    (s.flags() & (DEFAULT | ABSTRACT)) != ABSTRACT;
        }
    };
    protected static final Context.Key<Attr> attrKey = new Context.Key<Attr>();
    static final boolean allowDiamondFinder = true;
    final static TypeTag[] primitiveTags = new TypeTag[]{
            BYTE,
            CHAR,
            SHORT,
            INT,
            LONG,
            FLOAT,
            DOUBLE,
            BOOLEAN,
    };
    final Names names;
    final Log log;
    final Symtab syms;
    final Resolve rs;
    final Infer infer;
    final DeferredAttr deferredAttr;
    final Check chk;
    final Flow flow;
    final MemberEnter memberEnter;
    final TreeMaker make;
    final ConstFold cfolder;
    final Enter enter;
    final Target target;
    final Types types;
    final JCDiagnostic.Factory diags;
    final Annotate annotate;
    final TypeAnnotations typeAnnotations;
    final DeferredLintHandler deferredLintHandler;
    final ResultInfo statInfo;
    final ResultInfo varInfo;
    final ResultInfo unknownAnyPolyInfo;
    final ResultInfo unknownExprInfo;
    final ResultInfo unknownTypeInfo;
    final ResultInfo unknownTypeExprInfo;
    final ResultInfo recoveryInfo;
    boolean relax;
    boolean allowPoly;
    boolean allowTypeAnnos;
    boolean allowGenerics;
    boolean allowVarargs;
    boolean allowEnums;
    boolean allowBoxing;
    boolean allowCovariantReturns;
    boolean allowLambda;
    boolean allowDefaultMethods;
    boolean allowAnonOuterThis;
    boolean findDiamonds;
    boolean useBeforeDeclarationWarning;
    boolean identifyLambdaCandidate;
    boolean allowStringsInSwitch;
    String sourceName;
    Env<AttrContext> env;
    ResultInfo resultInfo;
    Type result;

    TreeTranslator removeClassParams = new TreeTranslator() {
        @Override
        public void visitTypeApply(JCTypeApply tree) {
            result = translate(tree.clazz);
        }
    };
    Types.MapVisitor<DiagnosticPosition> targetChecker = new Types.MapVisitor<DiagnosticPosition>() {
        @Override
        public Type visitClassType(ClassType t, DiagnosticPosition pos) {
            return t.isCompound() ?
                    visitIntersectionClassType((IntersectionClassType) t, pos) : t;
        }

        public Type visitIntersectionClassType(IntersectionClassType ict, DiagnosticPosition pos) {
            Symbol desc = types.findDescriptorSymbol(makeNotionalInterface(ict));
            Type target = null;
            for (Type bound : ict.getExplicitComponents()) {
                TypeSymbol boundSym = bound.tsym;
                if (types.isFunctionalInterface(boundSym) &&
                        types.findDescriptorSymbol(boundSym) == desc) {
                    target = bound;
                } else if (!boundSym.isInterface() || (boundSym.flags() & ANNOTATION) != 0) {

                    reportIntersectionError(pos, "not.an.intf.component", boundSym);
                }
            }
            return target != null ?
                    target :
                    ict.getExplicitComponents().head;
        }

        private TypeSymbol makeNotionalInterface(IntersectionClassType ict) {
            ListBuffer<Type> targs = new ListBuffer<>();
            ListBuffer<Type> supertypes = new ListBuffer<>();
            for (Type i : ict.interfaces_field) {
                if (i.isParameterized()) {
                    targs.appendList(i.tsym.type.allparams());
                }
                supertypes.append(i.tsym.type);
            }
            IntersectionClassType notionalIntf =
                    (IntersectionClassType) types.makeCompoundType(supertypes.toList());
            notionalIntf.allparams_field = targs.toList();
            notionalIntf.tsym.flags_field |= INTERFACE;
            return notionalIntf.tsym;
        }

        private void reportIntersectionError(DiagnosticPosition pos, String key, Object... args) {
            resultInfo.checkContext.report(pos, diags.fragment("bad.intersection.target.for.functional.expr",
                    diags.fragment(key, args)));
        }
    };
    Warner noteWarner = new Warner();
    private TreeVisitor<Symbol, Env<AttrContext>> identAttributer = new IdentAttributer();
    private JCTree breakTree = null;

    private Map<ClassSymbol, MethodSymbol> clinits = new HashMap<>();

    protected Attr(Context context) {
        context.put(attrKey, this);
        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        rs = Resolve.instance(context);
        chk = Check.instance(context);
        flow = Flow.instance(context);
        memberEnter = MemberEnter.instance(context);
        make = TreeMaker.instance(context);
        enter = Enter.instance(context);
        infer = Infer.instance(context);
        deferredAttr = DeferredAttr.instance(context);
        cfolder = ConstFold.instance(context);
        target = Target.instance(context);
        types = Types.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        annotate = Annotate.instance(context);
        typeAnnotations = TypeAnnotations.instance(context);
        deferredLintHandler = DeferredLintHandler.instance(context);
        Options options = Options.instance(context);
        Source source = Source.instance(context);
        allowGenerics = source.allowGenerics();
        allowVarargs = source.allowVarargs();
        allowEnums = source.allowEnums();
        allowBoxing = source.allowBoxing();
        allowCovariantReturns = source.allowCovariantReturns();
        allowAnonOuterThis = source.allowAnonOuterThis();
        allowStringsInSwitch = source.allowStringsInSwitch();
        allowPoly = source.allowPoly();
        allowTypeAnnos = source.allowTypeAnnotations();
        allowLambda = source.allowLambda();
        allowDefaultMethods = source.allowDefaultMethods();
        sourceName = source.name;
        relax = (options.isSet("-retrofit") ||
                options.isSet("-relax"));
        findDiamonds = options.get("findDiamond") != null &&
                source.allowDiamond();
        useBeforeDeclarationWarning = options.isSet("useBeforeDeclarationWarning");
        identifyLambdaCandidate = options.getBoolean("identifyLambdaCandidate", false);
        statInfo = new ResultInfo(NIL, Type.noType);
        varInfo = new ResultInfo(VAR, Type.noType);
        unknownExprInfo = new ResultInfo(VAL, Type.noType);
        unknownAnyPolyInfo = new ResultInfo(VAL, Infer.anyPoly);
        unknownTypeInfo = new ResultInfo(TYP, Type.noType);
        unknownTypeExprInfo = new ResultInfo(Kinds.TYP | Kinds.VAL, Type.noType);
        recoveryInfo = new RecoveryInfo(deferredAttr.emptyDeferredAttrContext);
    }

    public static Attr instance(Context context) {
        Attr instance = context.get(attrKey);
        if (instance == null)
            instance = new Attr(context);
        return instance;
    }

    static boolean isType(Symbol sym) {
        return sym != null && sym.kind == TYP;
    }

    private static void addVars(List<JCStatement> stats, Scope switchScope) {
        for (; stats.nonEmpty(); stats = stats.tail) {
            JCTree stat = stats.head;
            if (stat.hasTag(VARDEF))
                switchScope.enter(((JCVariableDecl) stat).sym);
        }
    }

    private static List<Attribute.TypeCompound> fromAnnotations(List<JCAnnotation> annotations) {
        if (annotations.isEmpty()) {
            return List.nil();
        }
        ListBuffer<Attribute.TypeCompound> buf = new ListBuffer<>();
        for (JCAnnotation anno : annotations) {
            if (anno.attribute != null) {


                buf.append((Attribute.TypeCompound) anno.attribute);
            }


        }
        return buf.toList();
    }

    Type check(final JCTree tree, final Type found, final int ownkind, final ResultInfo resultInfo) {
        InferenceContext inferenceContext = resultInfo.checkContext.inferenceContext();
        Type owntype = found;
        if (!owntype.hasTag(ERROR) && !resultInfo.pt.hasTag(METHOD) && !resultInfo.pt.hasTag(FORALL)) {
            if (allowPoly && inferenceContext.free(found)) {
                if ((ownkind & ~resultInfo.pkind) == 0) {
                    owntype = resultInfo.check(tree, inferenceContext.asFree(owntype));
                } else {
                    log.error(tree.pos(), "unexpected.type",
                            kindNames(resultInfo.pkind),
                            kindName(ownkind));
                    owntype = types.createErrorType(owntype);
                }
                inferenceContext.addFreeTypeListener(List.of(found, resultInfo.pt), new FreeTypeListener() {
                    @Override
                    public void typesInferred(InferenceContext inferenceContext) {
                        ResultInfo pendingResult =
                                resultInfo.dup(inferenceContext.asInstType(resultInfo.pt));
                        check(tree, inferenceContext.asInstType(found), ownkind, pendingResult);
                    }
                });
                return tree.type = resultInfo.pt;
            } else {
                if ((ownkind & ~resultInfo.pkind) == 0) {
                    owntype = resultInfo.check(tree, owntype);
                } else {
                    log.error(tree.pos(), "unexpected.type",
                            kindNames(resultInfo.pkind),
                            kindName(ownkind));
                    owntype = types.createErrorType(owntype);
                }
            }
        }
        tree.type = owntype;
        return owntype;
    }

    boolean isAssignableAsBlankFinal(VarSymbol v, Env<AttrContext> env) {
        Symbol owner = owner(env);
        return v.owner == owner || ((owner.name == names.init || owner.kind == VAR ||
                (owner.flags() & BLOCK) != 0) && v.owner == owner.owner
                && ((v.flags() & STATIC) != 0) == Resolve.isStatic(env));
    }

    Symbol owner(Env<AttrContext> env) {
        while (true) {
            switch (env.tree.getTag()) {
                case VARDEF:
                    VarSymbol vsym = ((JCVariableDecl) env.tree).sym;
                    if (vsym.owner.kind == TYP) {
                        return vsym;
                    }
                    break;
                case METHODDEF:
                    return ((JCMethodDecl) env.tree).sym;
                case CLASSDEF:
                    return ((JCClassDecl) env.tree).sym;
                case BLOCK:
                    Symbol blockSym = env.info.scope.owner;
                    if ((blockSym.flags() & BLOCK) != 0) {
                        return blockSym;
                    }
                    break;
                case TOPLEVEL:
                    return env.info.scope.owner;
            }
            Assert.checkNonNull(env.next);
            env = env.next;
        }
    }

    void checkAssignable(DiagnosticPosition pos, VarSymbol v, JCTree base, Env<AttrContext> env) {
        if ((v.flags() & FINAL) != 0 &&
                ((v.flags() & HASINIT) != 0
                        ||
                        !((base == null ||
                                (base.hasTag(IDENT) && TreeInfo.name(base) == names._this)) &&
                                isAssignableAsBlankFinal(v, env)))) {
            if (v.isResourceVariable()) {
                log.error(pos, "try.resource.may.not.be.assigned", v);
            } else {
                log.error(pos, "cant.assign.val.to.final.var", v);
            }
        }
    }

    boolean isStaticReference(JCTree tree) {
        if (tree.hasTag(SELECT)) {
            Symbol lsym = TreeInfo.symbol(((JCFieldAccess) tree).selected);
            return lsym != null && lsym.kind == TYP;
        }
        return true;
    }

    Symbol thisSym(DiagnosticPosition pos, Env<AttrContext> env) {
        return rs.resolveSelf(pos, env, env.enclClass.sym, names._this);
    }

    public Symbol attribIdent(JCTree tree, JCCompilationUnit topLevel) {
        Env<AttrContext> localEnv = enter.topLevelEnv(topLevel);
        localEnv.enclClass = make.ClassDef(make.Modifiers(0),
                syms.errSymbol.name,
                null, null, null, null);
        localEnv.enclClass.sym = syms.errSymbol;
        return tree.accept(identAttributer, localEnv);
    }

    public Type coerce(Type etype, Type ttype) {
        return cfolder.coerce(etype, ttype);
    }

    public Type attribType(JCTree node, TypeSymbol sym) {
        Env<AttrContext> env = enter.typeEnvs.get(sym);
        Env<AttrContext> localEnv = env.dup(node, env.info.dup());
        return attribTree(node, localEnv, unknownTypeInfo);
    }

    public Type attribImportQualifier(JCImport tree, Env<AttrContext> env) {
        JCFieldAccess s = (JCFieldAccess) tree.qualid;
        return attribTree(s.selected,
                env,
                new ResultInfo(tree.staticImport ? TYP : (TYP | PCK),
                        Type.noType));
    }

    public Env<AttrContext> attribExprToTree(JCTree expr, Env<AttrContext> env, JCTree tree) {
        breakTree = tree;
        JavaFileObject prev = log.useSource(env.toplevel.sourcefile);
        try {
            attribExpr(expr, env);
        } catch (BreakAttr b) {
            return b.env;
        } catch (AssertionError ae) {
            if (ae.getCause() instanceof BreakAttr) {
                return ((BreakAttr) (ae.getCause())).env;
            } else {
                throw ae;
            }
        } finally {
            breakTree = null;
            log.useSource(prev);
        }
        return env;
    }

    public Env<AttrContext> attribStatToTree(JCTree stmt, Env<AttrContext> env, JCTree tree) {
        breakTree = tree;
        JavaFileObject prev = log.useSource(env.toplevel.sourcefile);
        try {
            attribStat(stmt, env);
        } catch (BreakAttr b) {
            return b.env;
        } catch (AssertionError ae) {
            if (ae.getCause() instanceof BreakAttr) {
                return ((BreakAttr) (ae.getCause())).env;
            } else {
                throw ae;
            }
        } finally {
            breakTree = null;
            log.useSource(prev);
        }
        return env;
    }

    Type pt() {
        return resultInfo.pt;
    }

    int pkind() {
        return resultInfo.pkind;
    }

    Type attribTree(JCTree tree, Env<AttrContext> env, ResultInfo resultInfo) {
        Env<AttrContext> prevEnv = this.env;
        ResultInfo prevResult = this.resultInfo;
        try {
            this.env = env;
            this.resultInfo = resultInfo;
            tree.accept(this);
            if (tree == breakTree &&
                    resultInfo.checkContext.deferredAttrContext().mode == AttrMode.CHECK) {
                throw new BreakAttr(copyEnv(env));
            }
            return result;
        } catch (CompletionFailure ex) {
            tree.type = syms.errType;
            return chk.completionError(tree.pos(), ex);
        } finally {
            this.env = prevEnv;
            this.resultInfo = prevResult;
        }
    }

    Env<AttrContext> copyEnv(Env<AttrContext> env) {
        Env<AttrContext> newEnv =
                env.dup(env.tree, env.info.dup(copyScope(env.info.scope)));
        if (newEnv.outer != null) {
            newEnv.outer = copyEnv(newEnv.outer);
        }
        return newEnv;
    }

    Scope copyScope(Scope sc) {
        Scope newScope = new Scope(sc.owner);
        List<Symbol> elemsList = List.nil();
        while (sc != null) {
            for (Scope.Entry e = sc.elems; e != null; e = e.sibling) {
                elemsList = elemsList.prepend(e.sym);
            }
            sc = sc.next;
        }
        for (Symbol s : elemsList) {
            newScope.enter(s);
        }
        return newScope;
    }

    public Type attribExpr(JCTree tree, Env<AttrContext> env, Type pt) {
        return attribTree(tree, env, new ResultInfo(VAL, !pt.hasTag(ERROR) ? pt : Type.noType));
    }

    public Type attribExpr(JCTree tree, Env<AttrContext> env) {
        return attribTree(tree, env, unknownExprInfo);
    }

    public Type attribType(JCTree tree, Env<AttrContext> env) {
        Type result = attribType(tree, env, Type.noType);
        return result;
    }

    Type attribType(JCTree tree, Env<AttrContext> env, Type pt) {
        Type result = attribTree(tree, env, new ResultInfo(TYP, pt));
        return result;
    }

    public Type attribStat(JCTree tree, Env<AttrContext> env) {
        return attribTree(tree, env, statInfo);
    }

    List<Type> attribExprs(List<JCExpression> trees, Env<AttrContext> env, Type pt) {
        ListBuffer<Type> ts = new ListBuffer<Type>();
        for (List<JCExpression> l = trees; l.nonEmpty(); l = l.tail)
            ts.append(attribExpr(l.head, env, pt));
        return ts.toList();
    }

    <T extends JCTree> void attribStats(List<T> trees, Env<AttrContext> env) {
        for (List<T> l = trees; l.nonEmpty(); l = l.tail)
            attribStat(l.head, env);
    }

    int attribArgs(List<JCExpression> trees, Env<AttrContext> env, ListBuffer<Type> argtypes) {
        int kind = VAL;
        for (JCExpression arg : trees) {
            Type argtype;
            if (allowPoly && deferredAttr.isDeferred(env, arg)) {
                argtype = deferredAttr.new DeferredType(arg, env);
                kind |= POLY;
            } else {
                argtype = chk.checkNonVoid(arg, attribTree(arg, env, unknownAnyPolyInfo));
            }
            argtypes.append(argtype);
        }
        return kind;
    }

    List<Type> attribAnyTypes(List<JCExpression> trees, Env<AttrContext> env) {
        ListBuffer<Type> argtypes = new ListBuffer<Type>();
        for (List<JCExpression> l = trees; l.nonEmpty(); l = l.tail)
            argtypes.append(attribType(l.head, env));
        return argtypes.toList();
    }

    List<Type> attribTypes(List<JCExpression> trees, Env<AttrContext> env) {
        List<Type> types = attribAnyTypes(trees, env);
        return chk.checkRefTypes(trees, types);
    }

    void attribTypeVariables(List<JCTypeParameter> typarams, Env<AttrContext> env) {
        for (JCTypeParameter tvar : typarams) {
            TypeVar a = (TypeVar) tvar.type;
            a.tsym.flags_field |= UNATTRIBUTED;
            a.bound = Type.noType;
            if (!tvar.bounds.isEmpty()) {
                List<Type> bounds = List.of(attribType(tvar.bounds.head, env));
                for (JCExpression bound : tvar.bounds.tail)
                    bounds = bounds.prepend(attribType(bound, env));
                types.setBounds(a, bounds.reverse());
            } else {
                types.setBounds(a, List.of(syms.objectType));
            }
            a.tsym.flags_field &= ~UNATTRIBUTED;
        }
        for (JCTypeParameter tvar : typarams) {
            chk.checkNonCyclic(tvar.pos(), (TypeVar) tvar.type);
        }
    }

    void attribAnnotationTypes(List<JCAnnotation> annotations,
                               Env<AttrContext> env) {
        for (List<JCAnnotation> al = annotations; al.nonEmpty(); al = al.tail) {
            JCAnnotation a = al.head;
            attribType(a.annotationType, env);
        }
    }

    public Object attribLazyConstantValue(Env<AttrContext> env,
                                          JCVariableDecl variable,
                                          Type type) {
        DiagnosticPosition prevLintPos
                = deferredLintHandler.setPos(variable.pos());
        try {
            memberEnter.typeAnnotate(variable.init, env, null, variable.pos());
            annotate.flush();
            Type itype = attribExpr(variable.init, env, type);
            if (itype.constValue() != null) {
                return coerce(itype, type).constValue();
            } else {
                return null;
            }
        } finally {
            deferredLintHandler.setPos(prevLintPos);
        }
    }

    Type attribBase(JCTree tree,
                    Env<AttrContext> env,
                    boolean classExpected,
                    boolean interfaceExpected,
                    boolean checkExtensible) {
        Type t = tree.type != null ?
                tree.type :
                attribType(tree, env);
        return checkBase(t, tree, env, classExpected, interfaceExpected, checkExtensible);
    }

    Type checkBase(Type t,
                   JCTree tree,
                   Env<AttrContext> env,
                   boolean classExpected,
                   boolean interfaceExpected,
                   boolean checkExtensible) {
        if (t.isErroneous())
            return t;
        if (t.hasTag(TYPEVAR) && !classExpected && !interfaceExpected) {
            if (t.getUpperBound() == null) {
                log.error(tree.pos(), "illegal.forward.ref");
                return types.createErrorType(t);
            }
        } else {
            t = chk.checkClassType(tree.pos(), t, checkExtensible | !allowGenerics);
        }
        if (interfaceExpected && (t.tsym.flags() & INTERFACE) == 0) {
            log.error(tree.pos(), "intf.expected.here");
            return types.createErrorType(t);
        } else if (checkExtensible &&
                classExpected &&
                (t.tsym.flags() & INTERFACE) != 0) {
            log.error(tree.pos(), "no.intf.expected.here");
            return types.createErrorType(t);
        }
        if (checkExtensible &&
                ((t.tsym.flags() & FINAL) != 0)) {
            log.error(tree.pos(),
                    "cant.inherit.from.final", t.tsym);
        }
        chk.checkNonCyclic(tree.pos(), t);
        return t;
    }

    Type attribIdentAsEnumType(Env<AttrContext> env, JCIdent id) {
        Assert.check((env.enclClass.sym.flags() & ENUM) != 0);
        id.type = env.info.scope.owner.type;
        id.sym = env.info.scope.owner;
        return id.type;
    }

    public void visitClassDef(JCClassDecl tree) {
        if ((env.info.scope.owner.kind & (VAR | MTH)) != 0)
            enter.classEnter(tree, env);
        ClassSymbol c = tree.sym;
        if (c == null) {
            result = null;
        } else {
            c.complete();
            if (env.info.isSelfCall &&
                    env.tree.hasTag(NEWCLASS) &&
                    ((JCNewClass) env.tree).encl == null) {
                c.flags_field |= NOOUTERTHIS;
            }
            attribClass(tree.pos(), c);
            result = tree.type = c.type;
        }
    }

    public void visitMethodDef(JCMethodDecl tree) {
        MethodSymbol m = tree.sym;
        boolean isDefaultMethod = (m.flags() & DEFAULT) != 0;
        Lint lint = env.info.lint.augment(m);
        Lint prevLint = chk.setLint(lint);
        MethodSymbol prevMethod = chk.setMethod(m);
        try {
            deferredLintHandler.flush(tree.pos());
            chk.checkDeprecatedAnnotation(tree.pos(), m);
            Env<AttrContext> localEnv = memberEnter.methodEnv(tree, env);
            localEnv.info.lint = lint;
            attribStats(tree.typarams, localEnv);
            if (m.isStatic()) {
                chk.checkHideClashes(tree.pos(), env.enclClass.type, m);
            } else {
                chk.checkOverrideClashes(tree.pos(), env.enclClass.type, m);
            }
            chk.checkOverride(tree, m);
            if (isDefaultMethod && types.overridesObjectMethod(m.enclClass(), m)) {
                log.error(tree, "default.overrides.object.member", m.name, Kinds.kindName(m.location()), m.location());
            }
            for (List<JCTypeParameter> l = tree.typarams; l.nonEmpty(); l = l.tail)
                localEnv.info.scope.enterIfAbsent(l.head.type.tsym);
            ClassSymbol owner = env.enclClass.sym;
            if ((owner.flags() & ANNOTATION) != 0 &&
                    tree.params.nonEmpty())
                log.error(tree.params.head.pos(),
                        "intf.annotation.members.cant.have.params");
            for (List<JCVariableDecl> l = tree.params; l.nonEmpty(); l = l.tail) {
                attribStat(l.head, localEnv);
            }
            chk.checkVarargsMethodDecl(localEnv, tree);
            chk.validate(tree.typarams, localEnv);
            if (tree.restype != null && !tree.restype.type.hasTag(VOID))
                chk.validate(tree.restype, localEnv);
            if (tree.recvparam != null) {
                Env<AttrContext> newEnv = memberEnter.methodEnv(tree, env);
                attribType(tree.recvparam, newEnv);
                chk.validate(tree.recvparam, newEnv);
            }
            if ((owner.flags() & ANNOTATION) != 0) {
                if (tree.thrown.nonEmpty()) {
                    log.error(tree.thrown.head.pos(),
                            "throws.not.allowed.in.intf.annotation");
                }
                if (tree.typarams.nonEmpty()) {
                    log.error(tree.typarams.head.pos(),
                            "intf.annotation.members.cant.have.type.params");
                }
                chk.validateAnnotationType(tree.restype);
                chk.validateAnnotationMethod(tree.pos(), m);
            }
            for (List<JCExpression> l = tree.thrown; l.nonEmpty(); l = l.tail)
                chk.checkType(l.head.pos(), l.head.type, syms.throwableType);
            if (tree.body == null) {
                if (isDefaultMethod || (tree.sym.flags() & (ABSTRACT | NATIVE)) == 0 &&
                        !relax)
                    log.error(tree.pos(), "missing.meth.body.or.decl.abstract");
                if (tree.defaultValue != null) {
                    if ((owner.flags() & ANNOTATION) == 0)
                        log.error(tree.pos(),
                                "default.allowed.in.intf.annotation.member");
                }
            } else if ((tree.sym.flags() & ABSTRACT) != 0 && !isDefaultMethod) {
                if ((owner.flags() & INTERFACE) != 0) {
                    log.error(tree.body.pos(), "intf.meth.cant.have.body");
                } else {
                    log.error(tree.pos(), "abstract.meth.cant.have.body");
                }
            } else if ((tree.mods.flags & NATIVE) != 0) {
                log.error(tree.pos(), "native.meth.cant.have.body");
            } else {
                if (tree.name == names.init && owner.type != syms.objectType) {
                    JCBlock body = tree.body;
                    if (body.stats.isEmpty() ||
                            !TreeInfo.isSelfCall(body.stats.head)) {
                        body.stats = body.stats.
                                prepend(memberEnter.SuperCall(make.at(body.pos),
                                        List.nil(),
                                        List.nil(),
                                        false));
                    } else if ((env.enclClass.sym.flags() & ENUM) != 0 &&
                            (tree.mods.flags & GENERATEDCONSTR) == 0 &&
                            TreeInfo.isSuperCall(body.stats.head)) {
                        log.error(tree.body.stats.head.pos(),
                                "call.to.super.not.allowed.in.enum.ctor",
                                env.enclClass.sym);
                    }
                }
                memberEnter.typeAnnotate(tree.body, localEnv, m, null);
                annotate.flush();
                attribStat(tree.body, localEnv);
            }
            localEnv.info.scope.leave();
            result = tree.type = m.type;
        } finally {
            chk.setLint(prevLint);
            chk.setMethod(prevMethod);
        }
    }

    public void visitVarDef(JCVariableDecl tree) {
        if (env.info.scope.owner.kind == MTH) {
            if (tree.sym != null) {
                env.info.scope.enter(tree.sym);
            } else {
                memberEnter.memberEnter(tree, env);
                annotate.flush();
            }
        } else {
            if (tree.init != null) {
                memberEnter.typeAnnotate(tree.init, env, tree.sym, tree.pos());
                annotate.flush();
            }
        }
        VarSymbol v = tree.sym;
        Lint lint = env.info.lint.augment(v);
        Lint prevLint = chk.setLint(lint);
        boolean isImplicitLambdaParameter = env.tree.hasTag(LAMBDA) &&
                ((JCLambda) env.tree).paramKind == JCLambda.ParameterKind.IMPLICIT &&
                (tree.sym.flags() & PARAMETER) != 0;
        chk.validate(tree.vartype, env, !isImplicitLambdaParameter);
        try {
            v.getConstValue();
            deferredLintHandler.flush(tree.pos());
            chk.checkDeprecatedAnnotation(tree.pos(), v);
            if (tree.init != null) {
                if ((v.flags_field & FINAL) == 0 ||
                        !memberEnter.needsLazyConstValue(tree.init)) {
                    Env<AttrContext> initEnv = memberEnter.initEnv(tree, env);
                    initEnv.info.lint = lint;
                    initEnv.info.enclVar = v;
                    attribExpr(tree.init, initEnv, v.type);
                }
            }
            result = tree.type = v.type;
        } finally {
            chk.setLint(prevLint);
        }
    }

    public void visitSkip(JCSkip tree) {
        result = null;
    }

    public void visitBlock(JCBlock tree) {
        if (env.info.scope.owner.kind == TYP) {
            Env<AttrContext> localEnv =
                    env.dup(tree, env.info.dup(env.info.scope.dupUnshared()));
            localEnv.info.scope.owner =
                    new MethodSymbol(tree.flags | BLOCK |
                            env.info.scope.owner.flags() & STRICTFP, names.empty, null,
                            env.info.scope.owner);
            if ((tree.flags & STATIC) != 0) localEnv.info.staticLevel++;
            memberEnter.typeAnnotate(tree, localEnv, localEnv.info.scope.owner, null);
            annotate.flush();
            {
                ClassSymbol cs = (ClassSymbol) env.info.scope.owner;
                List<Attribute.TypeCompound> tas = localEnv.info.scope.owner.getRawTypeAttributes();
                if ((tree.flags & STATIC) != 0) {
                    cs.appendClassInitTypeAttributes(tas);
                } else {
                    cs.appendInitTypeAttributes(tas);
                }
            }
            attribStats(tree.stats, localEnv);
        } else {
            Env<AttrContext> localEnv =
                    env.dup(tree, env.info.dup(env.info.scope.dup()));
            try {
                attribStats(tree.stats, localEnv);
            } finally {
                localEnv.info.scope.leave();
            }
        }
        result = null;
    }

    public void visitDoLoop(JCDoWhileLoop tree) {
        attribStat(tree.body, env.dup(tree));
        attribExpr(tree.cond, env, syms.booleanType);
        result = null;
    }

    public void visitWhileLoop(JCWhileLoop tree) {
        attribExpr(tree.cond, env, syms.booleanType);
        attribStat(tree.body, env.dup(tree));
        result = null;
    }

    public void visitForLoop(JCForLoop tree) {
        Env<AttrContext> loopEnv =
                env.dup(env.tree, env.info.dup(env.info.scope.dup()));
        try {
            attribStats(tree.init, loopEnv);
            if (tree.cond != null) attribExpr(tree.cond, loopEnv, syms.booleanType);
            loopEnv.tree = tree;
            attribStats(tree.step, loopEnv);
            attribStat(tree.body, loopEnv);
            result = null;
        } finally {
            loopEnv.info.scope.leave();
        }
    }

    public void visitForeachLoop(JCEnhancedForLoop tree) {
        Env<AttrContext> loopEnv =
                env.dup(env.tree, env.info.dup(env.info.scope.dup()));
        try {
            Type exprType = types.upperBound(attribExpr(tree.expr, loopEnv));
            attribStat(tree.var, loopEnv);
            chk.checkNonVoid(tree.pos(), exprType);
            Type elemtype = types.elemtype(exprType);
            if (elemtype == null) {
                Type base = types.asSuper(exprType, syms.iterableType.tsym);
                if (base == null) {
                    log.error(tree.expr.pos(),
                            "foreach.not.applicable.to.type",
                            exprType,
                            diags.fragment("type.req.array.or.iterable"));
                    elemtype = types.createErrorType(exprType);
                } else {
                    List<Type> iterableParams = base.allparams();
                    elemtype = iterableParams.isEmpty()
                            ? syms.objectType
                            : types.upperBound(iterableParams.head);
                }
            }
            chk.checkType(tree.expr.pos(), elemtype, tree.var.sym.type);
            loopEnv.tree = tree;
            attribStat(tree.body, loopEnv);
            result = null;
        } finally {
            loopEnv.info.scope.leave();
        }
    }

    public void visitLabelled(JCLabeledStatement tree) {
        Env<AttrContext> env1 = env;
        while (env1 != null && !env1.tree.hasTag(CLASSDEF)) {
            if (env1.tree.hasTag(LABELLED) &&
                    ((JCLabeledStatement) env1.tree).label == tree.label) {
                log.error(tree.pos(), "label.already.in.use",
                        tree.label);
                break;
            }
            env1 = env1.next;
        }
        attribStat(tree.body, env.dup(tree));
        result = null;
    }

    public void visitSwitch(JCSwitch tree) {
        Type seltype = attribExpr(tree.selector, env);
        Env<AttrContext> switchEnv =
                env.dup(tree, env.info.dup(env.info.scope.dup()));
        try {
            boolean enumSwitch =
                    allowEnums &&
                            (seltype.tsym.flags() & Flags.ENUM) != 0;
            boolean stringSwitch = false;
            if (types.isSameType(seltype, syms.stringType)) {
                if (allowStringsInSwitch) {
                    stringSwitch = true;
                } else {
                    log.error(tree.selector.pos(), "string.switch.not.supported.in.source", sourceName);
                }
            }
            if (!enumSwitch && !stringSwitch)
                seltype = chk.checkType(tree.selector.pos(), seltype, syms.intType);
            Set<Object> labels = new HashSet<Object>();
            boolean hasDefault = false;
            for (List<JCCase> l = tree.cases; l.nonEmpty(); l = l.tail) {
                JCCase c = l.head;
                Env<AttrContext> caseEnv =
                        switchEnv.dup(c, env.info.dup(switchEnv.info.scope.dup()));
                try {
                    if (c.pat != null) {
                        if (enumSwitch) {
                            Symbol sym = enumConstant(c.pat, seltype);
                            if (sym == null) {
                                log.error(c.pat.pos(), "enum.label.must.be.unqualified.enum");
                            } else if (!labels.add(sym)) {
                                log.error(c.pos(), "duplicate.case.label");
                            }
                        } else {
                            Type pattype = attribExpr(c.pat, switchEnv, seltype);
                            if (!pattype.hasTag(ERROR)) {
                                if (pattype.constValue() == null) {
                                    log.error(c.pat.pos(),
                                            (stringSwitch ? "string.const.req" : "const.expr.req"));
                                } else if (labels.contains(pattype.constValue())) {
                                    log.error(c.pos(), "duplicate.case.label");
                                } else {
                                    labels.add(pattype.constValue());
                                }
                            }
                        }
                    } else if (hasDefault) {
                        log.error(c.pos(), "duplicate.default.label");
                    } else {
                        hasDefault = true;
                    }
                    attribStats(c.stats, caseEnv);
                } finally {
                    caseEnv.info.scope.leave();
                    addVars(c.stats, switchEnv.info.scope);
                }
            }
            result = null;
        } finally {
            switchEnv.info.scope.leave();
        }
    }

    private Symbol enumConstant(JCTree tree, Type enumType) {
        if (!tree.hasTag(IDENT)) {
            log.error(tree.pos(), "enum.label.must.be.unqualified.enum");
            return syms.errSymbol;
        }
        JCIdent ident = (JCIdent) tree;
        Name name = ident.name;
        for (Scope.Entry e = enumType.tsym.members().lookup(name);
             e.scope != null; e = e.next()) {
            if (e.sym.kind == VAR) {
                Symbol s = ident.sym = e.sym;
                ((VarSymbol) s).getConstValue();
                ident.type = s.type;
                return ((s.flags_field & Flags.ENUM) == 0)
                        ? null : s;
            }
        }
        return null;
    }

    public void visitSynchronized(JCSynchronized tree) {
        chk.checkRefType(tree.pos(), attribExpr(tree.lock, env));
        attribStat(tree.body, env);
        result = null;
    }

    public void visitTry(JCTry tree) {
        Env<AttrContext> localEnv = env.dup(tree, env.info.dup(env.info.scope.dup()));
        try {
            boolean isTryWithResource = tree.resources.nonEmpty();
            Env<AttrContext> tryEnv = isTryWithResource ?
                    env.dup(tree, localEnv.info.dup(localEnv.info.scope.dup())) :
                    localEnv;
            try {
                for (JCTree resource : tree.resources) {
                    CheckContext twrContext = new Check.NestedCheckContext(resultInfo.checkContext) {
                        @Override
                        public void report(DiagnosticPosition pos, JCDiagnostic details) {
                            chk.basicHandler.report(pos, diags.fragment("try.not.applicable.to.type", details));
                        }
                    };
                    ResultInfo twrResult = new ResultInfo(VAL, syms.autoCloseableType, twrContext);
                    if (resource.hasTag(VARDEF)) {
                        attribStat(resource, tryEnv);
                        twrResult.check(resource, resource.type);
                        checkAutoCloseable(resource.pos(), localEnv, resource.type);
                        VarSymbol var = ((JCVariableDecl) resource).sym;
                        var.setData(ElementKind.RESOURCE_VARIABLE);
                    } else {
                        attribTree(resource, tryEnv, twrResult);
                    }
                }
                attribStat(tree.body, tryEnv);
            } finally {
                if (isTryWithResource)
                    tryEnv.info.scope.leave();
            }
            for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
                JCCatch c = l.head;
                Env<AttrContext> catchEnv =
                        localEnv.dup(c, localEnv.info.dup(localEnv.info.scope.dup()));
                try {
                    Type ctype = attribStat(c.param, catchEnv);
                    if (TreeInfo.isMultiCatch(c)) {
                        c.param.sym.flags_field |= FINAL | UNION;
                    }
                    if (c.param.sym.kind == Kinds.VAR) {
                        c.param.sym.setData(ElementKind.EXCEPTION_PARAMETER);
                    }
                    chk.checkType(c.param.vartype.pos(),
                            chk.checkClassType(c.param.vartype.pos(), ctype),
                            syms.throwableType);
                    attribStat(c.body, catchEnv);
                } finally {
                    catchEnv.info.scope.leave();
                }
            }
            if (tree.finalizer != null) attribStat(tree.finalizer, localEnv);
            result = null;
        } finally {
            localEnv.info.scope.leave();
        }
    }

    void checkAutoCloseable(DiagnosticPosition pos, Env<AttrContext> env, Type resource) {
        if (!resource.isErroneous() &&
                types.asSuper(resource, syms.autoCloseableType.tsym) != null &&
                !types.isSameType(resource, syms.autoCloseableType)) {
            Symbol close = syms.noSymbol;
            Log.DiagnosticHandler discardHandler = new Log.DiscardDiagnosticHandler(log);
            try {
                close = rs.resolveQualifiedMethod(pos,
                        env,
                        resource,
                        names.close,
                        List.nil(),
                        List.nil());
            } finally {
                log.popDiagnosticHandler(discardHandler);
            }
            if (close.kind == MTH &&
                    close.overrides(syms.autoCloseableClose, resource.tsym, types, true) &&
                    chk.isHandled(syms.interruptedExceptionType, types.memberType(resource, close).getThrownTypes()) &&
                    env.info.lint.isEnabled(LintCategory.TRY)) {
                log.warning(LintCategory.TRY, pos, "try.resource.throws.interrupted.exc", resource);
            }
        }
    }

    public void visitConditional(JCConditional tree) {
        Type condtype = attribExpr(tree.cond, env, syms.booleanType);
        tree.polyKind = (!allowPoly ||
                pt().hasTag(NONE) && pt() != Type.recoveryType ||
                isBooleanOrNumeric(env, tree)) ?
                PolyKind.STANDALONE : PolyKind.POLY;
        if (tree.polyKind == PolyKind.POLY && resultInfo.pt.hasTag(VOID)) {
            resultInfo.checkContext.report(tree, diags.fragment("conditional.target.cant.be.void"));
            result = tree.type = types.createErrorType(resultInfo.pt);
            return;
        }
        ResultInfo condInfo = tree.polyKind == PolyKind.STANDALONE ?
                unknownExprInfo :
                resultInfo.dup(new Check.NestedCheckContext(resultInfo.checkContext) {
                    @Override
                    public void report(DiagnosticPosition pos, JCDiagnostic details) {
                        enclosingContext.report(pos, diags.fragment("incompatible.type.in.conditional", details));
                    }
                });
        Type truetype = attribTree(tree.truepart, env, condInfo);
        Type falsetype = attribTree(tree.falsepart, env, condInfo);
        Type owntype = (tree.polyKind == PolyKind.STANDALONE) ? condType(tree, truetype, falsetype) : pt();
        if (condtype.constValue() != null &&
                truetype.constValue() != null &&
                falsetype.constValue() != null &&
                !owntype.hasTag(NONE)) {
            owntype = cfolder.coerce(condtype.isTrue() ? truetype : falsetype, owntype);
        }
        result = check(tree, owntype, VAL, resultInfo);
    }

    private boolean isBooleanOrNumeric(Env<AttrContext> env, JCExpression tree) {
        switch (tree.getTag()) {
            case LITERAL:
                return ((JCLiteral) tree).typetag.isSubRangeOf(DOUBLE) ||
                        ((JCLiteral) tree).typetag == BOOLEAN ||
                        ((JCLiteral) tree).typetag == BOT;
            case LAMBDA:
            case REFERENCE:
                return false;
            case PARENS:
                return isBooleanOrNumeric(env, ((JCParens) tree).expr);
            case CONDEXPR:
                JCConditional condTree = (JCConditional) tree;
                return isBooleanOrNumeric(env, condTree.truepart) &&
                        isBooleanOrNumeric(env, condTree.falsepart);
            case APPLY:
                JCMethodInvocation speculativeMethodTree =
                        (JCMethodInvocation) deferredAttr.attribSpeculative(tree, env, unknownExprInfo);
                Type owntype = TreeInfo.symbol(speculativeMethodTree.meth).type.getReturnType();
                return types.unboxedTypeOrType(owntype).isPrimitive();
            case NEWCLASS:
                JCExpression className =
                        removeClassParams.translate(((JCNewClass) tree).clazz);
                JCExpression speculativeNewClassTree =
                        (JCExpression) deferredAttr.attribSpeculative(className, env, unknownTypeInfo);
                return types.unboxedTypeOrType(speculativeNewClassTree.type).isPrimitive();
            default:
                Type speculativeType = deferredAttr.attribSpeculative(tree, env, unknownExprInfo).type;
                speculativeType = types.unboxedTypeOrType(speculativeType);
                return speculativeType.isPrimitive();
        }
    }

    private Type condType(DiagnosticPosition pos,
                          Type thentype, Type elsetype) {
        if (types.isSameType(thentype, elsetype))
            return thentype.baseType();
        Type thenUnboxed = (!allowBoxing || thentype.isPrimitive())
                ? thentype : types.unboxedType(thentype);
        Type elseUnboxed = (!allowBoxing || elsetype.isPrimitive())
                ? elsetype : types.unboxedType(elsetype);
        if (thenUnboxed.isPrimitive() && elseUnboxed.isPrimitive()) {
            if (thenUnboxed.getTag().isStrictSubRangeOf(INT) &&
                    elseUnboxed.hasTag(INT) &&
                    types.isAssignable(elseUnboxed, thenUnboxed)) {
                return thenUnboxed.baseType();
            }
            if (elseUnboxed.getTag().isStrictSubRangeOf(INT) &&
                    thenUnboxed.hasTag(INT) &&
                    types.isAssignable(thenUnboxed, elseUnboxed)) {
                return elseUnboxed.baseType();
            }
            for (TypeTag tag : primitiveTags) {
                Type candidate = syms.typeOfTag[tag.ordinal()];
                if (types.isSubtype(thenUnboxed, candidate) &&
                        types.isSubtype(elseUnboxed, candidate)) {
                    return candidate;
                }
            }
        }
        if (allowBoxing) {
            if (thentype.isPrimitive())
                thentype = types.boxedClass(thentype).type;
            if (elsetype.isPrimitive())
                elsetype = types.boxedClass(elsetype).type;
        }
        if (types.isSubtype(thentype, elsetype))
            return elsetype.baseType();
        if (types.isSubtype(elsetype, thentype))
            return thentype.baseType();
        if (!allowBoxing || thentype.hasTag(VOID) || elsetype.hasTag(VOID)) {
            log.error(pos, "neither.conditional.subtype",
                    thentype, elsetype);
            return thentype.baseType();
        }
        return types.lub(thentype.baseType(), elsetype.baseType());
    }

    public void visitIf(JCIf tree) {
        attribExpr(tree.cond, env, syms.booleanType);
        attribStat(tree.thenpart, env);
        if (tree.elsepart != null)
            attribStat(tree.elsepart, env);
        chk.checkEmptyIf(tree);
        result = null;
    }

    public void visitExec(JCExpressionStatement tree) {
        Env<AttrContext> localEnv = env.dup(tree);
        attribExpr(tree.expr, localEnv);
        result = null;
    }

    public void visitBreak(JCBreak tree) {
        tree.target = findJumpTarget(tree.pos(), tree.getTag(), tree.label, env);
        result = null;
    }

    public void visitContinue(JCContinue tree) {
        tree.target = findJumpTarget(tree.pos(), tree.getTag(), tree.label, env);
        result = null;
    }

    private JCTree findJumpTarget(DiagnosticPosition pos,
                                  Tag tag,
                                  Name label,
                                  Env<AttrContext> env) {
        Env<AttrContext> env1 = env;
        LOOP:
        while (env1 != null) {
            switch (env1.tree.getTag()) {
                case LABELLED:
                    JCLabeledStatement labelled = (JCLabeledStatement) env1.tree;
                    if (label == labelled.label) {
                        if (tag == CONTINUE) {
                            if (!labelled.body.hasTag(DOLOOP) &&
                                    !labelled.body.hasTag(WHILELOOP) &&
                                    !labelled.body.hasTag(FORLOOP) &&
                                    !labelled.body.hasTag(FOREACHLOOP))
                                log.error(pos, "not.loop.label", label);
                            return TreeInfo.referencedStatement(labelled);
                        } else {
                            return labelled;
                        }
                    }
                    break;
                case DOLOOP:
                case WHILELOOP:
                case FORLOOP:
                case FOREACHLOOP:
                    if (label == null) return env1.tree;
                    break;
                case SWITCH:
                    if (label == null && tag == BREAK) return env1.tree;
                    break;
                case LAMBDA:
                case METHODDEF:
                case CLASSDEF:
                    break LOOP;
                default:
            }
            env1 = env1.next;
        }
        if (label != null)
            log.error(pos, "undef.label", label);
        else if (tag == CONTINUE)
            log.error(pos, "cont.outside.loop");
        else
            log.error(pos, "break.outside.switch.loop");
        return null;
    }

    public void visitReturn(JCReturn tree) {
        if (env.info.returnResult == null) {
            log.error(tree.pos(), "ret.outside.meth");
        } else {
            if (tree.expr != null) {
                if (env.info.returnResult.pt.hasTag(VOID)) {
                    env.info.returnResult.checkContext.report(tree.expr.pos(),
                            diags.fragment("unexpected.ret.val"));
                }
                attribTree(tree.expr, env, env.info.returnResult);
            } else if (!env.info.returnResult.pt.hasTag(VOID) &&
                    !env.info.returnResult.pt.hasTag(NONE)) {
                env.info.returnResult.checkContext.report(tree.pos(),
                        diags.fragment("missing.ret.val"));
            }
        }
        result = null;
    }

    public void visitThrow(JCThrow tree) {
        Type owntype = attribExpr(tree.expr, env, allowPoly ? Type.noType : syms.throwableType);
        if (allowPoly) {
            chk.checkType(tree, owntype, syms.throwableType);
        }
        result = null;
    }

    public void visitAssert(JCAssert tree) {
        attribExpr(tree.cond, env, syms.booleanType);
        if (tree.detail != null) {
            chk.checkNonVoid(tree.detail.pos(), attribExpr(tree.detail, env));
        }
        result = null;
    }

    public void visitApply(JCMethodInvocation tree) {
        Env<AttrContext> localEnv = env.dup(tree, env.info.dup());
        List<Type> argtypes;
        List<Type> typeargtypes = null;
        Name methName = TreeInfo.name(tree.meth);
        boolean isConstructorCall =
                methName == names._this || methName == names._super;
        ListBuffer<Type> argtypesBuf = new ListBuffer<>();
        if (isConstructorCall) {
            if (checkFirstConstructorStat(tree, env)) {
                localEnv.info.isSelfCall = true;
                attribArgs(tree.args, localEnv, argtypesBuf);
                argtypes = argtypesBuf.toList();
                typeargtypes = attribTypes(tree.typeargs, localEnv);
                Type site = env.enclClass.sym.type;
                if (methName == names._super) {
                    if (site == syms.objectType) {
                        log.error(tree.meth.pos(), "no.superclass", site);
                        site = types.createErrorType(syms.objectType);
                    } else {
                        site = types.supertype(site);
                    }
                }
                if (site.hasTag(CLASS)) {
                    Type encl = site.getEnclosingType();
                    while (encl != null && encl.hasTag(TYPEVAR))
                        encl = encl.getUpperBound();
                    if (encl.hasTag(CLASS)) {
                        if (tree.meth.hasTag(SELECT)) {
                            JCTree qualifier = ((JCFieldAccess) tree.meth).selected;
                            chk.checkRefType(qualifier.pos(),
                                    attribExpr(qualifier, localEnv,
                                            encl));
                        } else if (methName == names._super) {
                            rs.resolveImplicitThis(tree.meth.pos(),
                                    localEnv, site, true);
                        }
                    } else if (tree.meth.hasTag(SELECT)) {
                        log.error(tree.meth.pos(), "illegal.qual.not.icls",
                                site.tsym);
                    }
                    if (site.tsym == syms.enumSym && allowEnums)
                        argtypes = argtypes.prepend(syms.intType).prepend(syms.stringType);
                    boolean selectSuperPrev = localEnv.info.selectSuper;
                    localEnv.info.selectSuper = true;
                    localEnv.info.pendingResolutionPhase = null;
                    Symbol sym = rs.resolveConstructor(
                            tree.meth.pos(), localEnv, site, argtypes, typeargtypes);
                    localEnv.info.selectSuper = selectSuperPrev;
                    TreeInfo.setSymbol(tree.meth, sym);
                    Type mpt = newMethodTemplate(resultInfo.pt, argtypes, typeargtypes);
                    checkId(tree.meth, site, sym, localEnv, new ResultInfo(MTH, mpt));
                }
            }
            result = tree.type = syms.voidType;
        } else {
            int kind = attribArgs(tree.args, localEnv, argtypesBuf);
            argtypes = argtypesBuf.toList();
            typeargtypes = attribAnyTypes(tree.typeargs, localEnv);
            Type mpt = newMethodTemplate(resultInfo.pt, argtypes, typeargtypes);
            localEnv.info.pendingResolutionPhase = null;
            Type mtype = attribTree(tree.meth, localEnv, new ResultInfo(kind, mpt, resultInfo.checkContext));
            Type restype = mtype.getReturnType();
            if (restype.hasTag(WILDCARD))
                throw new AssertionError(mtype);
            Type qualifier = (tree.meth.hasTag(SELECT))
                    ? ((JCFieldAccess) tree.meth).selected.type
                    : env.enclClass.sym.type;
            restype = adjustMethodReturnType(qualifier, methName, argtypes, restype);
            chk.checkRefTypes(tree.typeargs, typeargtypes);
            result = check(tree, capture(restype), VAL, resultInfo);
        }
        chk.validate(tree.typeargs, localEnv);
    }

    Type adjustMethodReturnType(Type qualifierType, Name methodName, List<Type> argtypes, Type restype) {
        if (allowCovariantReturns &&
                methodName == names.clone &&
                types.isArray(qualifierType)) {
            return qualifierType;
        } else if (allowGenerics &&
                methodName == names.getClass &&
                argtypes.isEmpty()) {
            return new ClassType(restype.getEnclosingType(),
                    List.of(new WildcardType(types.erasure(qualifierType),
                            BoundKind.EXTENDS,
                            syms.boundClass)),
                    restype.tsym);
        } else {
            return restype;
        }
    }

    boolean checkFirstConstructorStat(JCMethodInvocation tree, Env<AttrContext> env) {
        JCMethodDecl enclMethod = env.enclMethod;
        if (enclMethod != null && enclMethod.name == names.init) {
            JCBlock body = enclMethod.body;
            if (body.stats.head.hasTag(EXEC) &&
                    ((JCExpressionStatement) body.stats.head).expr == tree)
                return true;
        }
        log.error(tree.pos(), "call.must.be.first.stmt.in.ctor",
                TreeInfo.name(tree.meth));
        return false;
    }

    Type newMethodTemplate(Type restype, List<Type> argtypes, List<Type> typeargtypes) {
        MethodType mt = new MethodType(argtypes, restype, List.nil(), syms.methodClass);
        return (typeargtypes == null) ? mt : new ForAll(typeargtypes, mt);
    }

    public void visitNewClass(final JCNewClass tree) {
        Type owntype = types.createErrorType(tree.type);
        Env<AttrContext> localEnv = env.dup(tree, env.info.dup());
        JCClassDecl cdef = tree.def;
        JCExpression clazz = tree.clazz;
        JCExpression clazzid;
        JCAnnotatedType annoclazzid;
        annoclazzid = null;
        if (clazz.hasTag(TYPEAPPLY)) {
            clazzid = ((JCTypeApply) clazz).clazz;
            if (clazzid.hasTag(ANNOTATED_TYPE)) {
                annoclazzid = (JCAnnotatedType) clazzid;
                clazzid = annoclazzid.underlyingType;
            }
        } else {
            if (clazz.hasTag(ANNOTATED_TYPE)) {
                annoclazzid = (JCAnnotatedType) clazz;
                clazzid = annoclazzid.underlyingType;
            } else {
                clazzid = clazz;
            }
        }
        JCExpression clazzid1 = clazzid;
        if (tree.encl != null) {
            Type encltype = chk.checkRefType(tree.encl.pos(),
                    attribExpr(tree.encl, env));
            clazzid1 = make.at(clazz.pos).Select(make.Type(encltype),
                    ((JCIdent) clazzid).name);
            EndPosTable endPosTable = this.env.toplevel.endPositions;
            endPosTable.storeEnd(clazzid1, tree.getEndPosition(endPosTable));
            if (clazz.hasTag(ANNOTATED_TYPE)) {
                JCAnnotatedType annoType = (JCAnnotatedType) clazz;
                List<JCAnnotation> annos = annoType.annotations;
                if (annoType.underlyingType.hasTag(TYPEAPPLY)) {
                    clazzid1 = make.at(tree.pos).
                            TypeApply(clazzid1,
                                    ((JCTypeApply) clazz).arguments);
                }
                clazzid1 = make.at(tree.pos).
                        AnnotatedType(annos, clazzid1);
            } else if (clazz.hasTag(TYPEAPPLY)) {
                clazzid1 = make.at(tree.pos).
                        TypeApply(clazzid1,
                                ((JCTypeApply) clazz).arguments);
            }
            clazz = clazzid1;
        }
        Type clazztype = TreeInfo.isEnumInit(env.tree) ?
                attribIdentAsEnumType(env, (JCIdent) clazz) :
                attribType(clazz, env);
        clazztype = chk.checkDiamond(tree, clazztype);
        chk.validate(clazz, localEnv);
        if (tree.encl != null) {
            tree.clazz.type = clazztype;
            TreeInfo.setSymbol(clazzid, TreeInfo.symbol(clazzid1));
            clazzid.type = ((JCIdent) clazzid).sym.type;
            if (annoclazzid != null) {
                annoclazzid.type = clazzid.type;
            }
            if (!clazztype.isErroneous()) {
                if (cdef != null && clazztype.tsym.isInterface()) {
                    log.error(tree.encl.pos(), "anon.class.impl.intf.no.qual.for.new");
                } else if (clazztype.tsym.isStatic()) {
                    log.error(tree.encl.pos(), "qualified.new.of.static.class", clazztype.tsym);
                }
            }
        } else if (!clazztype.tsym.isInterface() &&
                clazztype.getEnclosingType().hasTag(CLASS)) {
            rs.resolveImplicitThis(tree.pos(), env, clazztype);
        }
        ListBuffer<Type> argtypesBuf = new ListBuffer<>();
        int pkind = attribArgs(tree.args, localEnv, argtypesBuf);
        List<Type> argtypes = argtypesBuf.toList();
        List<Type> typeargtypes = attribTypes(tree.typeargs, localEnv);
        if (clazztype.hasTag(CLASS)) {
            if (allowEnums &&
                    (clazztype.tsym.flags_field & Flags.ENUM) != 0 &&
                    (!env.tree.hasTag(VARDEF) ||
                            (((JCVariableDecl) env.tree).mods.flags & Flags.ENUM) == 0 ||
                            ((JCVariableDecl) env.tree).init != tree))
                log.error(tree.pos(), "enum.cant.be.instantiated");
            if (cdef == null &&
                    (clazztype.tsym.flags() & (ABSTRACT | INTERFACE)) != 0) {
                log.error(tree.pos(), "abstract.cant.be.instantiated",
                        clazztype.tsym);
            } else if (cdef != null && clazztype.tsym.isInterface()) {
                if (!argtypes.isEmpty())
                    log.error(tree.args.head.pos(), "anon.class.impl.intf.no.args");
                if (!typeargtypes.isEmpty())
                    log.error(tree.typeargs.head.pos(), "anon.class.impl.intf.no.typeargs");
                argtypes = List.nil();
                typeargtypes = List.nil();
            } else if (TreeInfo.isDiamond(tree)) {
                ClassType site = new ClassType(clazztype.getEnclosingType(),
                        clazztype.tsym.type.getTypeArguments(),
                        clazztype.tsym);
                Env<AttrContext> diamondEnv = localEnv.dup(tree);
                diamondEnv.info.selectSuper = cdef != null;
                diamondEnv.info.pendingResolutionPhase = null;
                Symbol constructor = rs.resolveDiamond(tree.pos(),
                        diamondEnv,
                        site,
                        argtypes,
                        typeargtypes);
                tree.constructor = constructor.baseSymbol();
                final TypeSymbol csym = clazztype.tsym;
                ResultInfo diamondResult = new ResultInfo(MTH, newMethodTemplate(resultInfo.pt, argtypes, typeargtypes), new Check.NestedCheckContext(resultInfo.checkContext) {
                    @Override
                    public void report(DiagnosticPosition _unused, JCDiagnostic details) {
                        enclosingContext.report(tree.clazz,
                                diags.fragment("cant.apply.diamond.1", diags.fragment("diamond", csym), details));
                    }
                });
                Type constructorType = tree.constructorType = types.createErrorType(clazztype);
                constructorType = checkId(tree, site,
                        constructor,
                        diamondEnv,
                        diamondResult);
                tree.clazz.type = types.createErrorType(clazztype);
                if (!constructorType.isErroneous()) {
                    tree.clazz.type = clazztype = constructorType.getReturnType();
                    tree.constructorType = types.createMethodTypeWithReturn(constructorType, syms.voidType);
                }
                clazztype = chk.checkClassType(tree.clazz, tree.clazz.type, true);
            } else {
                Env<AttrContext> rsEnv = localEnv.dup(tree);
                rsEnv.info.selectSuper = cdef != null;
                rsEnv.info.pendingResolutionPhase = null;
                tree.constructor = rs.resolveConstructor(
                        tree.pos(), rsEnv, clazztype, argtypes, typeargtypes);
                if (cdef == null) {
                    tree.constructorType = checkId(tree,
                            clazztype,
                            tree.constructor,
                            rsEnv,
                            new ResultInfo(pkind, newMethodTemplate(syms.voidType, argtypes, typeargtypes)));
                    if (rsEnv.info.lastResolveVarargs())
                        Assert.check(tree.constructorType.isErroneous() || tree.varargsElement != null);
                }
                if (cdef == null &&
                        !clazztype.isErroneous() &&
                        clazztype.getTypeArguments().nonEmpty() &&
                        findDiamonds) {
                    findDiamond(localEnv, tree, clazztype);
                }
            }
            if (cdef != null) {
                if (Resolve.isStatic(env)) cdef.mods.flags |= STATIC;
                if (clazztype.tsym.isInterface()) {
                    cdef.implementing = List.of(clazz);
                } else {
                    cdef.extending = clazz;
                }
                attribStat(cdef, localEnv);
                checkLambdaCandidate(tree, cdef.sym, clazztype);
                if (tree.encl != null && !clazztype.tsym.isInterface()) {
                    tree.args = tree.args.prepend(makeNullCheck(tree.encl));
                    argtypes = argtypes.prepend(tree.encl.type);
                    tree.encl = null;
                }
                clazztype = cdef.sym.type;
                Symbol sym = tree.constructor = rs.resolveConstructor(
                        tree.pos(), localEnv, clazztype, argtypes, typeargtypes);
                Assert.check(sym.kind < AMBIGUOUS);
                tree.constructor = sym;
                tree.constructorType = checkId(tree,
                        clazztype,
                        tree.constructor,
                        localEnv,
                        new ResultInfo(pkind, newMethodTemplate(syms.voidType, argtypes, typeargtypes)));
            }
            if (tree.constructor != null && tree.constructor.kind == MTH)
                owntype = clazztype;
        }
        result = check(tree, owntype, VAL, resultInfo);
        chk.validate(tree.typeargs, localEnv);
    }

    void findDiamond(Env<AttrContext> env, JCNewClass tree, Type clazztype) {
        JCTypeApply ta = (JCTypeApply) tree.clazz;
        List<JCExpression> prevTypeargs = ta.arguments;
        try {
            ta.arguments = List.nil();
            ResultInfo findDiamondResult = new ResultInfo(VAL,
                    resultInfo.checkContext.inferenceContext().free(resultInfo.pt) ? Type.noType : pt());
            Type inferred = deferredAttr.attribSpeculative(tree, env, findDiamondResult).type;
            Type polyPt = allowPoly ?
                    syms.objectType :
                    clazztype;
            if (!inferred.isErroneous() &&
                    (allowPoly && pt() == Infer.anyPoly ?
                            types.isSameType(inferred, clazztype) :
                            types.isAssignable(inferred, pt().hasTag(NONE) ? polyPt : pt(), types.noWarnings))) {
                String key = types.isSameType(clazztype, inferred) ?
                        "diamond.redundant.args" :
                        "diamond.redundant.args.1";
                log.warning(tree.clazz.pos(), key, clazztype, inferred);
            }
        } finally {
            ta.arguments = prevTypeargs;
        }
    }

    private void checkLambdaCandidate(JCNewClass tree, ClassSymbol csym, Type clazztype) {
        if (allowLambda &&
                identifyLambdaCandidate &&
                clazztype.hasTag(CLASS) &&
                !pt().hasTag(NONE) &&
                types.isFunctionalInterface(clazztype.tsym)) {
            Symbol descriptor = types.findDescriptorSymbol(clazztype.tsym);
            int count = 0;
            boolean found = false;
            for (Symbol sym : csym.members().getElements()) {
                if ((sym.flags() & SYNTHETIC) != 0 ||
                        sym.isConstructor()) continue;
                count++;
                if (sym.kind != MTH ||
                        !sym.name.equals(descriptor.name)) continue;
                Type mtype = types.memberType(clazztype, sym);
                if (types.overrideEquivalent(mtype, types.memberType(clazztype, descriptor))) {
                    found = true;
                }
            }
            if (found && count == 1) {
                log.note(tree.def, "potential.lambda.found");
            }
        }
    }

    public JCExpression makeNullCheck(JCExpression arg) {
        Name name = TreeInfo.name(arg);
        if (name == names._this || name == names._super) return arg;
        Tag optag = NULLCHK;
        JCUnary tree = make.at(arg.pos).Unary(optag, arg);
        tree.operator = syms.nullcheck;
        tree.type = arg.type;
        return tree;
    }

    public void visitNewArray(JCNewArray tree) {
        Type owntype = types.createErrorType(tree.type);
        Env<AttrContext> localEnv = env.dup(tree);
        Type elemtype;
        if (tree.elemtype != null) {
            elemtype = attribType(tree.elemtype, localEnv);
            chk.validate(tree.elemtype, localEnv);
            owntype = elemtype;
            for (List<JCExpression> l = tree.dims; l.nonEmpty(); l = l.tail) {
                attribExpr(l.head, localEnv, syms.intType);
                owntype = new ArrayType(owntype, syms.arrayClass);
            }
        } else {
            if (pt().hasTag(ARRAY)) {
                elemtype = types.elemtype(pt());
            } else {
                if (!pt().hasTag(ERROR)) {
                    log.error(tree.pos(), "illegal.initializer.for.type",
                            pt());
                }
                elemtype = types.createErrorType(pt());
            }
        }
        if (tree.elems != null) {
            attribExprs(tree.elems, localEnv, elemtype);
            owntype = new ArrayType(elemtype, syms.arrayClass);
        }
        if (!types.isReifiable(elemtype))
            log.error(tree.pos(), "generic.array.creation");
        result = check(tree, owntype, VAL, resultInfo);
    }

    @Override
    public void visitLambda(final JCLambda that) {
        if (pt().isErroneous() || (pt().hasTag(NONE) && pt() != Type.recoveryType)) {
            if (pt().hasTag(NONE)) {
                log.error(that.pos(), "unexpected.lambda");
            }
            result = that.type = types.createErrorType(pt());
            return;
        }
        final Env<AttrContext> localEnv = lambdaEnv(that, env);
        boolean needsRecovery =
                resultInfo.checkContext.deferredAttrContext().mode == AttrMode.CHECK;
        try {
            Type currentTarget = pt();
            List<Type> explicitParamTypes = null;
            if (that.paramKind == JCLambda.ParameterKind.EXPLICIT) {
                attribStats(that.params, localEnv);
                explicitParamTypes = TreeInfo.types(that.params);
            }
            Type lambdaType;
            if (pt() != Type.recoveryType) {
                currentTarget = targetChecker.visit(currentTarget, that);
                if (explicitParamTypes != null) {
                    currentTarget = infer.instantiateFunctionalInterface(that,
                            currentTarget, explicitParamTypes, resultInfo.checkContext);
                }
                lambdaType = types.findDescriptorType(currentTarget);
            } else {
                currentTarget = Type.recoveryType;
                lambdaType = fallbackDescriptorType(that);
            }
            setFunctionalInfo(localEnv, that, pt(), lambdaType, currentTarget, resultInfo.checkContext);
            if (lambdaType.hasTag(FORALL)) {
                resultInfo.checkContext.report(that, diags.fragment("invalid.generic.lambda.target",
                        lambdaType, kindName(currentTarget.tsym), currentTarget.tsym));
                result = that.type = types.createErrorType(pt());
                return;
            }
            if (that.paramKind == JCLambda.ParameterKind.IMPLICIT) {
                List<Type> actuals = lambdaType.getParameterTypes();
                List<JCVariableDecl> params = that.params;
                boolean arityMismatch = false;
                while (params.nonEmpty()) {
                    if (actuals.isEmpty()) {
                        arityMismatch = true;
                    }
                    Type argType = arityMismatch ?
                            syms.errType :
                            actuals.head;
                    params.head.vartype = make.at(params.head).Type(argType);
                    params.head.sym = null;
                    actuals = actuals.isEmpty() ?
                            actuals :
                            actuals.tail;
                    params = params.tail;
                }
                attribStats(that.params, localEnv);
                if (arityMismatch) {
                    resultInfo.checkContext.report(that, diags.fragment("incompatible.arg.types.in.lambda"));
                    result = that.type = types.createErrorType(currentTarget);
                    return;
                }
            }
            needsRecovery = false;
            FunctionalReturnContext funcContext = that.getBodyKind() == JCLambda.BodyKind.EXPRESSION ?
                    new ExpressionLambdaReturnContext((JCExpression) that.getBody(), resultInfo.checkContext) :
                    new FunctionalReturnContext(resultInfo.checkContext);
            ResultInfo bodyResultInfo = lambdaType.getReturnType() == Type.recoveryType ?
                    recoveryInfo :
                    new ResultInfo(VAL, lambdaType.getReturnType(), funcContext);
            localEnv.info.returnResult = bodyResultInfo;
            if (that.getBodyKind() == JCLambda.BodyKind.EXPRESSION) {
                attribTree(that.getBody(), localEnv, bodyResultInfo);
            } else {
                JCBlock body = (JCBlock) that.body;
                attribStats(body.stats, localEnv);
            }
            result = check(that, currentTarget, VAL, resultInfo);
            boolean isSpeculativeRound =
                    resultInfo.checkContext.deferredAttrContext().mode == AttrMode.SPECULATIVE;
            preFlow(that);
            flow.analyzeLambda(env, that, make, isSpeculativeRound);
            checkLambdaCompatible(that, lambdaType, resultInfo.checkContext);
            if (!isSpeculativeRound) {
                if (resultInfo.checkContext.inferenceContext().free(lambdaType.getThrownTypes())) {
                    List<Type> inferredThrownTypes = flow.analyzeLambdaThrownTypes(env, that, make);
                    List<Type> thrownTypes = resultInfo.checkContext.inferenceContext().asFree(lambdaType.getThrownTypes());
                    chk.unhandled(inferredThrownTypes, thrownTypes);
                }
                checkAccessibleTypes(that, localEnv, resultInfo.checkContext.inferenceContext(), lambdaType, currentTarget);
            }
            result = check(that, currentTarget, VAL, resultInfo);
        } catch (Types.FunctionDescriptorLookupError ex) {
            JCDiagnostic cause = ex.getDiagnostic();
            resultInfo.checkContext.report(that, cause);
            result = that.type = types.createErrorType(pt());
            return;
        } finally {
            localEnv.info.scope.leave();
            if (needsRecovery) {
                attribTree(that, env, recoveryInfo);
            }
        }
    }

    void preFlow(JCLambda tree) {
        new PostAttrAnalyzer() {
            @Override
            public void scan(JCTree tree) {
                if (tree == null || (tree.type != null && tree.type == Type.stuckType)) {
                    return;
                }
                super.scan(tree);
            }
        }.scan(tree);
    }

    private Type fallbackDescriptorType(JCExpression tree) {
        switch (tree.getTag()) {
            case LAMBDA:
                JCLambda lambda = (JCLambda) tree;
                List<Type> argtypes = List.nil();
                for (JCVariableDecl param : lambda.params) {
                    argtypes = param.vartype != null ?
                            argtypes.append(param.vartype.type) :
                            argtypes.append(syms.errType);
                }
                return new MethodType(argtypes, Type.recoveryType,
                        List.of(syms.throwableType), syms.methodClass);
            case REFERENCE:
                return new MethodType(List.nil(), Type.recoveryType,
                        List.of(syms.throwableType), syms.methodClass);
            default:
                Assert.error("Cannot get here!");
        }
        return null;
    }

    private void checkAccessibleTypes(final DiagnosticPosition pos, final Env<AttrContext> env,
                                      final InferenceContext inferenceContext, final Type... ts) {
        checkAccessibleTypes(pos, env, inferenceContext, List.from(ts));
    }

    private void checkAccessibleTypes(final DiagnosticPosition pos, final Env<AttrContext> env,
                                      final InferenceContext inferenceContext, final List<Type> ts) {
        if (inferenceContext.free(ts)) {
            inferenceContext.addFreeTypeListener(ts, new FreeTypeListener() {
                @Override
                public void typesInferred(InferenceContext inferenceContext) {
                    checkAccessibleTypes(pos, env, inferenceContext, inferenceContext.asInstTypes(ts));
                }
            });
        } else {
            for (Type t : ts) {
                rs.checkAccessibleType(env, t);
            }
        }
    }

    private void checkLambdaCompatible(JCLambda tree, Type descriptor, CheckContext checkContext) {
        Type returnType = checkContext.inferenceContext().asFree(descriptor.getReturnType());
        if (tree.getBodyKind() == JCLambda.BodyKind.STATEMENT && tree.canCompleteNormally &&
                !returnType.hasTag(VOID) && returnType != Type.recoveryType) {
            checkContext.report(tree, diags.fragment("incompatible.ret.type.in.lambda",
                    diags.fragment("missing.ret.val", returnType)));
        }
        List<Type> argTypes = checkContext.inferenceContext().asFree(descriptor.getParameterTypes());
        if (!types.isSameTypes(argTypes, TreeInfo.types(tree.params))) {
            checkContext.report(tree, diags.fragment("incompatible.arg.types.in.lambda"));
        }
    }

    public MethodSymbol removeClinit(ClassSymbol sym) {
        return clinits.remove(sym);
    }

    private Env<AttrContext> lambdaEnv(JCLambda that, Env<AttrContext> env) {
        Env<AttrContext> lambdaEnv;
        Symbol owner = env.info.scope.owner;
        if (owner.kind == VAR && owner.owner.kind == TYP) {
            lambdaEnv = env.dup(that, env.info.dup(env.info.scope.dupUnshared()));
            ClassSymbol enclClass = owner.enclClass();
            if ((owner.flags() & STATIC) == 0) {
                for (Symbol s : enclClass.members_field.getElementsByName(names.init)) {
                    lambdaEnv.info.scope.owner = s;
                    break;
                }
            } else {
                MethodSymbol clinit = clinits.get(enclClass);
                if (clinit == null) {
                    Type clinitType = new MethodType(List.nil(),
                            syms.voidType, List.nil(), syms.methodClass);
                    clinit = new MethodSymbol(STATIC | SYNTHETIC | PRIVATE,
                            names.clinit, clinitType, enclClass);
                    clinit.params = List.nil();
                    clinits.put(enclClass, clinit);
                }
                lambdaEnv.info.scope.owner = clinit;
            }
        } else {
            lambdaEnv = env.dup(that, env.info.dup(env.info.scope.dup()));
        }
        return lambdaEnv;
    }

    @Override
    public void visitReference(final JCMemberReference that) {
        if (pt().isErroneous() || (pt().hasTag(NONE) && pt() != Type.recoveryType)) {
            if (pt().hasTag(NONE)) {
                log.error(that.pos(), "unexpected.mref");
            }
            result = that.type = types.createErrorType(pt());
            return;
        }
        final Env<AttrContext> localEnv = env.dup(that);
        try {
            Type exprType = attribTree(that.expr, env, memberReferenceQualifierResult(that));
            if (that.getMode() == ReferenceMode.NEW) {
                exprType = chk.checkConstructorRefType(that.expr, exprType);
                if (!exprType.isErroneous() &&
                        exprType.isRaw() &&
                        that.typeargs != null) {
                    log.error(that.expr.pos(), "invalid.mref", Kinds.kindName(that.getMode()),
                            diags.fragment("mref.infer.and.explicit.params"));
                    exprType = types.createErrorType(exprType);
                }
            }
            if (exprType.isErroneous()) {
                result = that.type = exprType;
                return;
            }
            if (TreeInfo.isStaticSelector(that.expr, names)) {
                chk.validate(that.expr, env, false);
            }
            List<Type> typeargtypes = List.nil();
            if (that.typeargs != null) {
                typeargtypes = attribTypes(that.typeargs, localEnv);
            }
            Type target;
            Type desc;
            if (pt() != Type.recoveryType) {
                target = targetChecker.visit(pt(), that);
                desc = types.findDescriptorType(target);
            } else {
                target = Type.recoveryType;
                desc = fallbackDescriptorType(that);
            }
            setFunctionalInfo(localEnv, that, pt(), desc, target, resultInfo.checkContext);
            List<Type> argtypes = desc.getParameterTypes();
            Resolve.MethodCheck referenceCheck = rs.resolveMethodCheck;
            if (resultInfo.checkContext.inferenceContext().free(argtypes)) {
                referenceCheck = rs.new MethodReferenceCheck(resultInfo.checkContext.inferenceContext());
            }
            Pair<Symbol, Resolve.ReferenceLookupHelper> refResult = null;
            List<Type> saved_undet = resultInfo.checkContext.inferenceContext().save();
            try {
                refResult = rs.resolveMemberReference(localEnv, that, that.expr.type,
                        that.name, argtypes, typeargtypes, referenceCheck,
                        resultInfo.checkContext.inferenceContext(),
                        resultInfo.checkContext.deferredAttrContext().mode);
            } finally {
                resultInfo.checkContext.inferenceContext().rollback(saved_undet);
            }
            Symbol refSym = refResult.fst;
            Resolve.ReferenceLookupHelper lookupHelper = refResult.snd;
            if (refSym.kind != MTH) {
                boolean targetError;
                switch (refSym.kind) {
                    case ABSENT_MTH:
                        targetError = false;
                        break;
                    case WRONG_MTH:
                    case WRONG_MTHS:
                    case AMBIGUOUS:
                    case HIDDEN:
                    case STATICERR:
                    case MISSING_ENCL:
                    case WRONG_STATICNESS:
                        targetError = true;
                        break;
                    default:
                        Assert.error("unexpected result kind " + refSym.kind);
                        targetError = false;
                }
                JCDiagnostic detailsDiag = ((Resolve.ResolveError) refSym).getDiagnostic(JCDiagnostic.DiagnosticType.FRAGMENT,
                        that, exprType.tsym, exprType, that.name, argtypes, typeargtypes);
                JCDiagnostic.DiagnosticType diagKind = targetError ?
                        JCDiagnostic.DiagnosticType.FRAGMENT : JCDiagnostic.DiagnosticType.ERROR;
                JCDiagnostic diag = diags.create(diagKind, log.currentSource(), that,
                        "invalid.mref", Kinds.kindName(that.getMode()), detailsDiag);
                if (targetError && target == Type.recoveryType) {
                    result = that.type = target;
                    return;
                } else {
                    if (targetError) {
                        resultInfo.checkContext.report(that, diag);
                    } else {
                        log.report(diag);
                    }
                    result = that.type = types.createErrorType(target);
                    return;
                }
            }
            that.sym = refSym.baseSymbol();
            that.kind = lookupHelper.referenceKind(that.sym);
            that.ownerAccessible = rs.isAccessible(localEnv, that.sym.enclClass());
            if (desc.getReturnType() == Type.recoveryType) {
                result = that.type = target;
                return;
            }
            if (resultInfo.checkContext.deferredAttrContext().mode == AttrMode.CHECK) {
                if (that.getMode() == ReferenceMode.INVOKE &&
                        TreeInfo.isStaticSelector(that.expr, names) &&
                        that.kind.isUnbound() &&
                        !desc.getParameterTypes().head.isParameterized()) {
                    chk.checkRaw(that.expr, localEnv);
                }
                if (that.sym.isStatic() && TreeInfo.isStaticSelector(that.expr, names) &&
                        exprType.getTypeArguments().nonEmpty()) {
                    log.error(that.expr.pos(), "invalid.mref", Kinds.kindName(that.getMode()),
                            diags.fragment("static.mref.with.targs"));
                    result = that.type = types.createErrorType(target);
                    return;
                }
                if (that.sym.isStatic() && !TreeInfo.isStaticSelector(that.expr, names) &&
                        !that.kind.isUnbound()) {
                    log.error(that.expr.pos(), "invalid.mref", Kinds.kindName(that.getMode()),
                            diags.fragment("static.bound.mref"));
                    result = that.type = types.createErrorType(target);
                    return;
                }
                if (!refSym.isStatic() && that.kind == JCMemberReference.ReferenceKind.SUPER) {
                    rs.checkNonAbstract(that.pos(), that.sym);
                }
            }
            ResultInfo checkInfo =
                    resultInfo.dup(newMethodTemplate(
                            desc.getReturnType().hasTag(VOID) ? Type.noType : desc.getReturnType(),
                            that.kind.isUnbound() ? argtypes.tail : argtypes, typeargtypes));
            Type refType = checkId(that, lookupHelper.site, refSym, localEnv, checkInfo);
            if (that.kind.isUnbound() &&
                    resultInfo.checkContext.inferenceContext().free(argtypes.head)) {
                if (!types.isSubtype(resultInfo.checkContext.inferenceContext().asFree(argtypes.head), exprType)) {
                    Assert.error("Can't get here");
                }
            }
            if (!refType.isErroneous()) {
                refType = types.createMethodTypeWithReturn(refType,
                        adjustMethodReturnType(lookupHelper.site, that.name, checkInfo.pt.getParameterTypes(), refType.getReturnType()));
            }
            boolean isSpeculativeRound =
                    resultInfo.checkContext.deferredAttrContext().mode == AttrMode.SPECULATIVE;
            checkReferenceCompatible(that, desc, refType, resultInfo.checkContext, isSpeculativeRound);
            if (!isSpeculativeRound) {
                checkAccessibleTypes(that, localEnv, resultInfo.checkContext.inferenceContext(), desc, target);
            }
            result = check(that, target, VAL, resultInfo);
        } catch (Types.FunctionDescriptorLookupError ex) {
            JCDiagnostic cause = ex.getDiagnostic();
            resultInfo.checkContext.report(that, cause);
            result = that.type = types.createErrorType(pt());
            return;
        }
    }

    ResultInfo memberReferenceQualifierResult(JCMemberReference tree) {
        return new ResultInfo(tree.getMode() == ReferenceMode.INVOKE ? VAL | TYP : TYP, Type.noType);
    }

    @SuppressWarnings("fallthrough")
    void checkReferenceCompatible(JCMemberReference tree, Type descriptor, Type refType, CheckContext checkContext, boolean speculativeAttr) {
        Type returnType = checkContext.inferenceContext().asFree(descriptor.getReturnType());
        Type resType;
        switch (tree.getMode()) {
            case NEW:
                if (!tree.expr.type.isRaw()) {
                    resType = tree.expr.type;
                    break;
                }
            default:
                resType = refType.getReturnType();
        }
        Type incompatibleReturnType = resType;
        if (returnType.hasTag(VOID)) {
            incompatibleReturnType = null;
        }
        if (!returnType.hasTag(VOID) && !resType.hasTag(VOID)) {
            if (resType.isErroneous() ||
                    new FunctionalReturnContext(checkContext).compatible(resType, returnType, types.noWarnings)) {
                incompatibleReturnType = null;
            }
        }
        if (incompatibleReturnType != null) {
            checkContext.report(tree, diags.fragment("incompatible.ret.type.in.mref",
                    diags.fragment("inconvertible.types", resType, descriptor.getReturnType())));
        }
        if (!speculativeAttr) {
            List<Type> thrownTypes = checkContext.inferenceContext().asFree(descriptor.getThrownTypes());
            if (chk.unhandled(refType.getThrownTypes(), thrownTypes).nonEmpty()) {
                log.error(tree, "incompatible.thrown.types.in.mref", refType.getThrownTypes());
            }
        }
    }

    private void setFunctionalInfo(final Env<AttrContext> env, final JCFunctionalExpression fExpr,
                                   final Type pt, final Type descriptorType, final Type primaryTarget, final CheckContext checkContext) {
        if (checkContext.inferenceContext().free(descriptorType)) {
            checkContext.inferenceContext().addFreeTypeListener(List.of(pt, descriptorType), new FreeTypeListener() {
                public void typesInferred(InferenceContext inferenceContext) {
                    setFunctionalInfo(env, fExpr, pt, inferenceContext.asInstType(descriptorType),
                            inferenceContext.asInstType(primaryTarget), checkContext);
                }
            });
        } else {
            ListBuffer<Type> targets = new ListBuffer<>();
            if (pt.hasTag(CLASS)) {
                if (pt.isCompound()) {
                    targets.append(types.removeWildcards(primaryTarget));
                    for (Type t : ((IntersectionClassType) pt()).interfaces_field) {
                        if (t != primaryTarget) {
                            targets.append(types.removeWildcards(t));
                        }
                    }
                } else {
                    targets.append(types.removeWildcards(primaryTarget));
                }
            }
            fExpr.targets = targets.toList();
            if (checkContext.deferredAttrContext().mode == AttrMode.CHECK &&
                    pt != Type.recoveryType) {
                ClassSymbol csym = types.makeFunctionalInterfaceClass(env,
                        names.empty, List.of(fExpr.targets.head), ABSTRACT);
                if (csym != null) {
                    chk.checkImplementations(env.tree, csym, csym);
                }
            }
        }
    }

    public void visitParens(JCParens tree) {
        Type owntype = attribTree(tree.expr, env, resultInfo);
        result = check(tree, owntype, pkind(), resultInfo);
        Symbol sym = TreeInfo.symbol(tree);
        if (sym != null && (sym.kind & (TYP | PCK)) != 0)
            log.error(tree.pos(), "illegal.start.of.type");
    }

    public void visitAssign(JCAssign tree) {
        Type owntype = attribTree(tree.lhs, env.dup(tree), varInfo);
        Type capturedType = capture(owntype);
        attribExpr(tree.rhs, env, owntype);
        result = check(tree, capturedType, VAL, resultInfo);
    }

    public void visitAssignop(JCAssignOp tree) {
        Type owntype = attribTree(tree.lhs, env, varInfo);
        Type operand = attribExpr(tree.rhs, env);
        Symbol operator = tree.operator = rs.resolveBinaryOperator(
                tree.pos(), tree.getTag().noAssignOp(), env,
                owntype, operand);
        if (operator.kind == MTH &&
                !owntype.isErroneous() &&
                !operand.isErroneous()) {
            chk.checkOperator(tree.pos(),
                    (OperatorSymbol) operator,
                    tree.getTag().noAssignOp(),
                    owntype,
                    operand);
            chk.checkDivZero(tree.rhs.pos(), operator, operand);
            chk.checkCastable(tree.rhs.pos(),
                    operator.type.getReturnType(),
                    owntype);
        }
        result = check(tree, owntype, VAL, resultInfo);
    }

    public void visitUnary(JCUnary tree) {
        Type argtype = (tree.getTag().isIncOrDecUnaryOp())
                ? attribTree(tree.arg, env, varInfo)
                : chk.checkNonVoid(tree.arg.pos(), attribExpr(tree.arg, env));
        Symbol operator = tree.operator =
                rs.resolveUnaryOperator(tree.pos(), tree.getTag(), env, argtype);
        Type owntype = types.createErrorType(tree.type);
        if (operator.kind == MTH &&
                !argtype.isErroneous()) {
            owntype = (tree.getTag().isIncOrDecUnaryOp())
                    ? tree.arg.type
                    : operator.type.getReturnType();
            int opc = ((OperatorSymbol) operator).opcode;
            if (argtype.constValue() != null) {
                Type ctype = cfolder.fold1(opc, argtype);
                if (ctype != null) {
                    owntype = cfolder.coerce(ctype, owntype);
                    if (tree.arg.type.tsym == syms.stringType.tsym) {
                        tree.arg.type = syms.stringType;
                    }
                }
            }
        }
        result = check(tree, owntype, VAL, resultInfo);
    }

    public void visitBinary(JCBinary tree) {
        Type left = chk.checkNonVoid(tree.lhs.pos(), attribExpr(tree.lhs, env));
        Type right = chk.checkNonVoid(tree.lhs.pos(), attribExpr(tree.rhs, env));
        Symbol operator = tree.operator =
                rs.resolveBinaryOperator(tree.pos(), tree.getTag(), env, left, right);
        Type owntype = types.createErrorType(tree.type);
        if (operator.kind == MTH &&
                !left.isErroneous() &&
                !right.isErroneous()) {
            owntype = operator.type.getReturnType();
            int opc = chk.checkOperator(tree.lhs.pos(),
                    (OperatorSymbol) operator,
                    tree.getTag(),
                    left,
                    right);
            if (left.constValue() != null && right.constValue() != null) {
                Type ctype = cfolder.fold2(opc, left, right);
                if (ctype != null) {
                    owntype = cfolder.coerce(ctype, owntype);
                    if (tree.lhs.type.tsym == syms.stringType.tsym) {
                        tree.lhs.type = syms.stringType;
                    }
                    if (tree.rhs.type.tsym == syms.stringType.tsym) {
                        tree.rhs.type = syms.stringType;
                    }
                }
            }
            if ((opc == ByteCodes.if_acmpeq || opc == ByteCodes.if_acmpne)) {
                if (!types.isEqualityComparable(left, right,
                        new Warner(tree.pos()))) {
                    log.error(tree.pos(), "incomparable.types", left, right);
                }
            }
            chk.checkDivZero(tree.rhs.pos(), operator, right);
        }
        result = check(tree, owntype, VAL, resultInfo);
    }

    public void visitTypeCast(final JCTypeCast tree) {
        Type clazztype = attribType(tree.clazz, env);
        chk.validate(tree.clazz, env, false);
        Env<AttrContext> localEnv = env.dup(tree);
        final ResultInfo castInfo;
        JCExpression expr = TreeInfo.skipParens(tree.expr);
        boolean isPoly = allowPoly && (expr.hasTag(LAMBDA) || expr.hasTag(REFERENCE));
        if (isPoly) {
            castInfo = new ResultInfo(VAL, clazztype, new Check.NestedCheckContext(resultInfo.checkContext) {
                @Override
                public boolean compatible(Type found, Type req, Warner warn) {
                    return types.isCastable(found, req, warn);
                }
            });
        } else {
            castInfo = unknownExprInfo;
        }
        Type exprtype = attribTree(tree.expr, localEnv, castInfo);
        Type owntype = isPoly ? clazztype : chk.checkCastable(tree.expr.pos(), exprtype, clazztype);
        if (exprtype.constValue() != null)
            owntype = cfolder.coerce(exprtype, owntype);
        result = check(tree, capture(owntype), VAL, resultInfo);
        if (!isPoly)
            chk.checkRedundantCast(localEnv, tree);
    }

    public void visitTypeTest(JCInstanceOf tree) {
        Type exprtype = chk.checkNullOrRefType(
                tree.expr.pos(), attribExpr(tree.expr, env));
        Type clazztype = attribType(tree.clazz, env);
        if (!clazztype.hasTag(TYPEVAR)) {
            clazztype = chk.checkClassOrArrayType(tree.clazz.pos(), clazztype);
        }
        if (!clazztype.isErroneous() && !types.isReifiable(clazztype)) {
            log.error(tree.clazz.pos(), "illegal.generic.type.for.instof");
            clazztype = types.createErrorType(clazztype);
        }
        chk.validate(tree.clazz, env, false);
        chk.checkCastable(tree.expr.pos(), exprtype, clazztype);
        result = check(tree, syms.booleanType, VAL, resultInfo);
    }

    public void visitIndexed(JCArrayAccess tree) {
        Type owntype = types.createErrorType(tree.type);
        Type atype = attribExpr(tree.indexed, env);
        attribExpr(tree.index, env, syms.intType);
        if (types.isArray(atype))
            owntype = types.elemtype(atype);
        else if (!atype.hasTag(ERROR))
            log.error(tree.pos(), "array.req.but.found", atype);
        if ((pkind() & VAR) == 0) owntype = capture(owntype);
        result = check(tree, owntype, VAR, resultInfo);
    }

    public void visitIdent(JCIdent tree) {
        Symbol sym;
        if (pt().hasTag(METHOD) || pt().hasTag(FORALL)) {
            env.info.pendingResolutionPhase = null;
            sym = rs.resolveMethod(tree.pos(), env, tree.name, pt().getParameterTypes(), pt().getTypeArguments());
        } else if (tree.sym != null && tree.sym.kind != VAR) {
            sym = tree.sym;
        } else {
            sym = rs.resolveIdent(tree.pos(), env, tree.name, pkind());
        }
        tree.sym = sym;
        Env<AttrContext> symEnv = env;
        boolean noOuterThisPath = false;
        if (env.enclClass.sym.owner.kind != PCK &&
                (sym.kind & (VAR | MTH | TYP)) != 0 &&
                sym.owner.kind == TYP &&
                tree.name != names._this && tree.name != names._super) {
            while (symEnv.outer != null &&
                    !sym.isMemberOf(symEnv.enclClass.sym, types)) {
                if ((symEnv.enclClass.sym.flags() & NOOUTERTHIS) != 0)
                    noOuterThisPath = !allowAnonOuterThis;
                symEnv = symEnv.outer;
            }
        }
        if (sym.kind == VAR) {
            VarSymbol v = (VarSymbol) sym;
            checkInit(tree, env, v, false);
            if (pkind() == VAR)
                checkAssignable(tree.pos(), v, null, env);
        }
        if ((symEnv.info.isSelfCall || noOuterThisPath) &&
                (sym.kind & (VAR | MTH)) != 0 &&
                sym.owner.kind == TYP &&
                (sym.flags() & STATIC) == 0) {
            chk.earlyRefError(tree.pos(), sym.kind == VAR ? sym : thisSym(tree.pos(), env));
        }
        Env<AttrContext> env1 = env;
        if (sym.kind != ERR && sym.kind != TYP && sym.owner != null && sym.owner != env1.enclClass.sym) {
            while (env1.outer != null && !rs.isAccessible(env, env1.enclClass.sym.type, sym))
                env1 = env1.outer;
        }
        result = checkId(tree, env1.enclClass.sym.type, sym, env, resultInfo);
    }

    public void visitSelect(JCFieldAccess tree) {
        int skind = 0;
        if (tree.name == names._this || tree.name == names._super ||
                tree.name == names._class) {
            skind = TYP;
        } else {
            if ((pkind() & PCK) != 0) skind = skind | PCK;
            if ((pkind() & TYP) != 0) skind = skind | TYP | PCK;
            if ((pkind() & (VAL | MTH)) != 0) skind = skind | VAL | TYP;
        }
        Type site = attribTree(tree.selected, env, new ResultInfo(skind, Infer.anyPoly));
        if ((pkind() & (PCK | TYP)) == 0)
            site = capture(site);
        if (skind == TYP) {
            Type elt = site;
            while (elt.hasTag(ARRAY))
                elt = ((ArrayType) elt.unannotatedType()).elemtype;
            if (elt.hasTag(TYPEVAR)) {
                log.error(tree.pos(), "type.var.cant.be.deref");
                result = types.createErrorType(tree.type);
                return;
            }
        }
        Symbol sitesym = TreeInfo.symbol(tree.selected);
        boolean selectSuperPrev = env.info.selectSuper;
        env.info.selectSuper =
                sitesym != null &&
                        sitesym.name == names._super;
        env.info.pendingResolutionPhase = null;
        Symbol sym = selectSym(tree, sitesym, site, env, resultInfo);
        if (sym.exists() && !isType(sym) && (pkind() & (PCK | TYP)) != 0) {
            site = capture(site);
            sym = selectSym(tree, sitesym, site, env, resultInfo);
        }
        boolean varArgs = env.info.lastResolveVarargs();
        tree.sym = sym;
        if (site.hasTag(TYPEVAR) && !isType(sym) && sym.kind != ERR) {
            while (site.hasTag(TYPEVAR)) site = site.getUpperBound();
            site = capture(site);
        }
        if (sym.kind == VAR) {
            VarSymbol v = (VarSymbol) sym;
            checkInit(tree, env, v, true);
            if (pkind() == VAR)
                checkAssignable(tree.pos(), v, tree.selected, env);
        }
        if (sitesym != null &&
                sitesym.kind == VAR &&
                ((VarSymbol) sitesym).isResourceVariable() &&
                sym.kind == MTH &&
                sym.name.equals(names.close) &&
                sym.overrides(syms.autoCloseableClose, sitesym.type.tsym, types, true) &&
                env.info.lint.isEnabled(LintCategory.TRY)) {
            log.warning(LintCategory.TRY, tree, "try.explicit.close.call");
        }
        if (isType(sym) && (sitesym == null || (sitesym.kind & (TYP | PCK)) == 0)) {
            tree.type = check(tree.selected, pt(),
                    sitesym == null ? VAL : sitesym.kind, new ResultInfo(TYP | PCK, pt()));
        }
        if (isType(sitesym)) {
            if (sym.name == names._this) {
                if (env.info.isSelfCall &&
                        site.tsym == env.enclClass.sym) {
                    chk.earlyRefError(tree.pos(), sym);
                }
            } else {
                if ((sym.flags() & STATIC) == 0 &&
                        !env.next.tree.hasTag(REFERENCE) &&
                        sym.name != names._super &&
                        (sym.kind == VAR || sym.kind == MTH)) {
                    rs.accessBase(rs.new StaticError(sym),
                            tree.pos(), site, sym.name, true);
                }
            }
        } else if (sym.kind != ERR && (sym.flags() & STATIC) != 0 && sym.name != names._class) {
            chk.warnStatic(tree, "static.not.qualified.by.type", Kinds.kindName(sym.kind), sym.owner);
        }
        if (env.info.selectSuper && (sym.flags() & STATIC) == 0) {
            rs.checkNonAbstract(tree.pos(), sym);
            if (site.isRaw()) {
                Type site1 = types.asSuper(env.enclClass.sym.type, site.tsym);
                if (site1 != null) site = site1;
            }
        }
        env.info.selectSuper = selectSuperPrev;
        result = checkId(tree, site, sym, env, resultInfo);
    }

    private Symbol selectSym(JCFieldAccess tree,
                             Symbol location,
                             Type site,
                             Env<AttrContext> env,
                             ResultInfo resultInfo) {
        DiagnosticPosition pos = tree.pos();
        Name name = tree.name;
        switch (site.getTag()) {
            case PACKAGE:
                return rs.accessBase(
                        rs.findIdentInPackage(env, site.tsym, name, resultInfo.pkind),
                        pos, location, site, name, true);
            case ARRAY:
            case CLASS:
                if (resultInfo.pt.hasTag(METHOD) || resultInfo.pt.hasTag(FORALL)) {
                    return rs.resolveQualifiedMethod(
                            pos, env, location, site, name, resultInfo.pt.getParameterTypes(), resultInfo.pt.getTypeArguments());
                } else if (name == names._this || name == names._super) {
                    return rs.resolveSelf(pos, env, site.tsym, name);
                } else if (name == names._class) {
                    Type t = syms.classType;
                    List<Type> typeargs = allowGenerics
                            ? List.of(types.erasure(site))
                            : List.nil();
                    t = new ClassType(t.getEnclosingType(), typeargs, t.tsym);
                    return new VarSymbol(
                            STATIC | PUBLIC | FINAL, names._class, t, site.tsym);
                } else {
                    Symbol sym = rs.findIdentInType(env, site, name, resultInfo.pkind);
                    if ((resultInfo.pkind & ERRONEOUS) == 0)
                        sym = rs.accessBase(sym, pos, location, site, name, true);
                    return sym;
                }
            case WILDCARD:
                throw new AssertionError(tree);
            case TYPEVAR:
                Symbol sym = (site.getUpperBound() != null)
                        ? selectSym(tree, location, capture(site.getUpperBound()), env, resultInfo)
                        : null;
                if (sym == null) {
                    log.error(pos, "type.var.cant.be.deref");
                    return syms.errSymbol;
                } else {
                    Symbol sym2 = (sym.flags() & Flags.PRIVATE) != 0 ?
                            rs.new AccessError(env, site, sym) :
                            sym;
                    rs.accessBase(sym2, pos, location, site, name, true);
                    return sym;
                }
            case ERROR:
                return types.createErrorType(name, site.tsym, site).tsym;
            default:
                if (name == names._class) {
                    Type t = syms.classType;
                    Type arg = types.boxedClass(site).type;
                    t = new ClassType(t.getEnclosingType(), List.of(arg), t.tsym);
                    return new VarSymbol(
                            STATIC | PUBLIC | FINAL, names._class, t, site.tsym);
                } else {
                    log.error(pos, "cant.deref", site);
                    return syms.errSymbol;
                }
        }
    }

    Type checkId(JCTree tree,
                 Type site,
                 Symbol sym,
                 Env<AttrContext> env,
                 ResultInfo resultInfo) {
        return (resultInfo.pt.hasTag(FORALL) || resultInfo.pt.hasTag(METHOD)) ?
                checkMethodId(tree, site, sym, env, resultInfo) :
                checkIdInternal(tree, site, sym, resultInfo.pt, env, resultInfo);
    }

    Type checkMethodId(JCTree tree,
                       Type site,
                       Symbol sym,
                       Env<AttrContext> env,
                       ResultInfo resultInfo) {
        boolean isPolymorhicSignature =
                (sym.baseSymbol().flags() & SIGNATURE_POLYMORPHIC) != 0;
        return isPolymorhicSignature ?
                checkSigPolyMethodId(tree, site, sym, env, resultInfo) :
                checkMethodIdInternal(tree, site, sym, env, resultInfo);
    }

    Type checkSigPolyMethodId(JCTree tree,
                              Type site,
                              Symbol sym,
                              Env<AttrContext> env,
                              ResultInfo resultInfo) {
        checkMethodIdInternal(tree, site, sym.baseSymbol(), env, resultInfo);
        env.info.pendingResolutionPhase = Resolve.MethodResolutionPhase.BASIC;
        return sym.type;
    }

    Type checkMethodIdInternal(JCTree tree,
                               Type site,
                               Symbol sym,
                               Env<AttrContext> env,
                               ResultInfo resultInfo) {
        if ((resultInfo.pkind & POLY) != 0) {
            Type pt = resultInfo.pt.map(deferredAttr.new RecoveryDeferredTypeMap(AttrMode.SPECULATIVE, sym, env.info.pendingResolutionPhase));
            Type owntype = checkIdInternal(tree, site, sym, pt, env, resultInfo);
            resultInfo.pt.map(deferredAttr.new RecoveryDeferredTypeMap(AttrMode.CHECK, sym, env.info.pendingResolutionPhase));
            return owntype;
        } else {
            return checkIdInternal(tree, site, sym, resultInfo.pt, env, resultInfo);
        }
    }

    Type checkIdInternal(JCTree tree,
                         Type site,
                         Symbol sym,
                         Type pt,
                         Env<AttrContext> env,
                         ResultInfo resultInfo) {
        if (pt.isErroneous()) {
            return types.createErrorType(site);
        }
        Type owntype;
        switch (sym.kind) {
            case TYP:
                owntype = sym.type;
                if (owntype.hasTag(CLASS)) {
                    chk.checkForBadAuxiliaryClassAccess(tree.pos(), env, (ClassSymbol) sym);
                    Type ownOuter = owntype.getEnclosingType();
                    if (owntype.tsym.type.getTypeArguments().nonEmpty()) {
                        owntype = types.erasure(owntype);
                    } else if (ownOuter.hasTag(CLASS) && site != ownOuter) {
                        Type normOuter = site;
                        if (normOuter.hasTag(CLASS)) {
                            normOuter = types.asEnclosingSuper(site, ownOuter.tsym);
                        }
                        if (normOuter == null)
                            normOuter = types.erasure(ownOuter);
                        if (normOuter != ownOuter)
                            owntype = new ClassType(
                                    normOuter, List.nil(), owntype.tsym);
                    }
                }
                break;
            case VAR:
                VarSymbol v = (VarSymbol) sym;
                if (allowGenerics &&
                        resultInfo.pkind == VAR &&
                        v.owner.kind == TYP &&
                        (v.flags() & STATIC) == 0 &&
                        (site.hasTag(CLASS) || site.hasTag(TYPEVAR))) {
                    Type s = types.asOuterSuper(site, v.owner);
                    if (s != null &&
                            s.isRaw() &&
                            !types.isSameType(v.type, v.erasure(types))) {
                        chk.warnUnchecked(tree.pos(),
                                "unchecked.assign.to.var",
                                v, s);
                    }
                }
                owntype = (sym.owner.kind == TYP &&
                        sym.name != names._this && sym.name != names._super)
                        ? types.memberType(site, sym)
                        : sym.type;
                if (v.getConstValue() != null && isStaticReference(tree))
                    owntype = owntype.constType(v.getConstValue());
                if (resultInfo.pkind == VAL) {
                    owntype = capture(owntype);
                }
                break;
            case MTH: {
                owntype = checkMethod(site, sym,
                        new ResultInfo(resultInfo.pkind, resultInfo.pt.getReturnType(), resultInfo.checkContext),
                        env, TreeInfo.args(env.tree), resultInfo.pt.getParameterTypes(),
                        resultInfo.pt.getTypeArguments());
                break;
            }
            case PCK:
            case ERR:
                owntype = sym.type;
                break;
            default:
                throw new AssertionError("unexpected kind: " + sym.kind +
                        " in tree " + tree);
        }
        if (sym.name != names.init) {
            chk.checkDeprecated(tree.pos(), env.info.scope.owner, sym);
            chk.checkSunAPI(tree.pos(), sym);
            chk.checkProfile(tree.pos(), sym);
        }
        return check(tree, owntype, sym.kind, resultInfo);
    }

    private void checkInit(JCTree tree, Env<AttrContext> env, VarSymbol v, boolean onlyWarning) {
        if ((env.info.enclVar == v || v.pos > tree.pos) &&
                v.owner.kind == TYP &&
                canOwnInitializer(owner(env)) &&
                v.owner == env.info.scope.owner.enclClass() &&
                ((v.flags() & STATIC) != 0) == Resolve.isStatic(env) &&
                (!env.tree.hasTag(ASSIGN) ||
                        TreeInfo.skipParens(((JCAssign) env.tree).lhs) != tree)) {
            String suffix = (env.info.enclVar == v) ?
                    "self.ref" : "forward.ref";
            if (!onlyWarning || isStaticEnumField(v)) {
                log.error(tree.pos(), "illegal." + suffix);
            } else if (useBeforeDeclarationWarning) {
                log.warning(tree.pos(), suffix, v);
            }
        }
        v.getConstValue();
        checkEnumInitializer(tree, env, v);
    }

    private void checkEnumInitializer(JCTree tree, Env<AttrContext> env, VarSymbol v) {
        if (isStaticEnumField(v)) {
            ClassSymbol enclClass = env.info.scope.owner.enclClass();
            if (enclClass == null || enclClass.owner == null)
                return;
            if (v.owner != enclClass && !types.isSubtype(enclClass.type, v.owner.type))
                return;
            if (!Resolve.isInitializer(env))
                return;
            log.error(tree.pos(), "illegal.enum.static.ref");
        }
    }

    private boolean isStaticEnumField(VarSymbol v) {
        return Flags.isEnum(v.owner) &&
                Flags.isStatic(v) &&
                !Flags.isConstant(v) &&
                v.name != names._class;
    }

    private boolean canOwnInitializer(Symbol sym) {
        return
                (sym.kind & (VAR | TYP)) != 0 ||
                        (sym.kind == MTH && (sym.flags() & BLOCK) != 0);
    }

    public Type checkMethod(Type site, final Symbol sym, ResultInfo resultInfo, Env<AttrContext> env,
                            final List<JCExpression> argtrees, List<Type> argtypes, List<Type> typeargtypes) {
        if (allowGenerics &&
                (sym.flags() & STATIC) == 0 &&
                (site.hasTag(CLASS) || site.hasTag(TYPEVAR))) {
            Type s = types.asOuterSuper(site, sym.owner);
            if (s != null && s.isRaw() &&
                    !types.isSameTypes(sym.type.getParameterTypes(),
                            sym.erasure(types).getParameterTypes())) {
                chk.warnUnchecked(env.tree.pos(),
                        "unchecked.call.mbr.of.raw.type",
                        sym, s);
            }
        }
        if (env.info.defaultSuperCallSite != null) {
            for (Type sup : types.interfaces(env.enclClass.type).prepend(types.supertype((env.enclClass.type)))) {
                if (!sup.tsym.isSubClass(sym.enclClass(), types) ||
                        types.isSameType(sup, env.info.defaultSuperCallSite)) continue;
                List<MethodSymbol> icand_sup =
                        types.interfaceCandidates(sup, (MethodSymbol) sym);
                if (icand_sup.nonEmpty() &&
                        icand_sup.head != sym &&
                        icand_sup.head.overrides(sym, icand_sup.head.enclClass(), types, true)) {
                    log.error(env.tree.pos(), "illegal.default.super.call", env.info.defaultSuperCallSite,
                            diags.fragment("overridden.default", sym, sup));
                    break;
                }
            }
            env.info.defaultSuperCallSite = null;
        }
        if (sym.isStatic() && site.isInterface() && env.tree.hasTag(APPLY)) {
            JCMethodInvocation app = (JCMethodInvocation) env.tree;
            if (app.meth.hasTag(SELECT) &&
                    !TreeInfo.isStaticSelector(((JCFieldAccess) app.meth).selected, names)) {
                log.error(env.tree.pos(), "illegal.static.intf.meth.call", site);
            }
        }
        noteWarner.clear();
        try {
            Type owntype = rs.checkMethod(env, site, sym, resultInfo,
                    argtypes, typeargtypes, noteWarner);
            DeferredAttr.DeferredTypeMap checkDeferredMap =
                    deferredAttr.new DeferredTypeMap(AttrMode.CHECK, sym, env.info.pendingResolutionPhase);
            argtypes = Type.map(argtypes, checkDeferredMap);
            if (noteWarner.hasNonSilentLint(LintCategory.UNCHECKED)) {
                chk.warnUnchecked(env.tree.pos(),
                        "unchecked.meth.invocation.applied",
                        kindName(sym),
                        sym.name,
                        rs.methodArguments(sym.type.getParameterTypes()),
                        rs.methodArguments(Type.map(argtypes, checkDeferredMap)),
                        kindName(sym.location()),
                        sym.location());
                owntype = new MethodType(owntype.getParameterTypes(),
                        types.erasure(owntype.getReturnType()),
                        types.erasure(owntype.getThrownTypes()),
                        syms.methodClass);
            }
            return chk.checkMethod(owntype, sym, env, argtrees, argtypes, env.info.lastResolveVarargs(),
                    resultInfo.checkContext.inferenceContext());
        } catch (Infer.InferenceException ex) {
            resultInfo.checkContext.report(env.tree.pos(), ex.getDiagnostic());
            return types.createErrorType(site);
        } catch (Resolve.InapplicableMethodException ex) {
            final JCDiagnostic diag = ex.getDiagnostic();
            Resolve.InapplicableSymbolError errSym = rs.new InapplicableSymbolError(null) {
                @Override
                protected Pair<Symbol, JCDiagnostic> errCandidate() {
                    return new Pair<Symbol, JCDiagnostic>(sym, diag);
                }
            };
            List<Type> argtypes2 = Type.map(argtypes,
                    rs.new ResolveDeferredRecoveryMap(AttrMode.CHECK, sym, env.info.pendingResolutionPhase));
            JCDiagnostic errDiag = errSym.getDiagnostic(JCDiagnostic.DiagnosticType.ERROR,
                    env.tree, sym, site, sym.name, argtypes2, typeargtypes);
            log.report(errDiag);
            return types.createErrorType(site);
        }
    }

    public void visitLiteral(JCLiteral tree) {
        result = check(
                tree, litType(tree.typetag).constType(tree.value), VAL, resultInfo);
    }

    Type litType(TypeTag tag) {
        return (tag == CLASS) ? syms.stringType : syms.typeOfTag[tag.ordinal()];
    }

    public void visitTypeIdent(JCPrimitiveTypeTree tree) {
        result = check(tree, syms.typeOfTag[tree.typetag.ordinal()], TYP, resultInfo);
    }

    public void visitTypeArray(JCArrayTypeTree tree) {
        Type etype = attribType(tree.elemtype, env);
        Type type = new ArrayType(etype, syms.arrayClass);
        result = check(tree, type, TYP, resultInfo);
    }

    public void visitTypeApply(JCTypeApply tree) {
        Type owntype = types.createErrorType(tree.type);
        Type clazztype = chk.checkClassType(tree.clazz.pos(), attribType(tree.clazz, env));
        List<Type> actuals = attribTypes(tree.arguments, env);
        if (clazztype.hasTag(CLASS)) {
            List<Type> formals = clazztype.tsym.type.getTypeArguments();
            if (actuals.isEmpty())
                actuals = formals;
            if (actuals.length() == formals.length()) {
                List<Type> a = actuals;
                List<Type> f = formals;
                while (a.nonEmpty()) {
                    a.head = a.head.withTypeVar(f.head);
                    a = a.tail;
                    f = f.tail;
                }
                Type clazzOuter = clazztype.getEnclosingType();
                if (clazzOuter.hasTag(CLASS)) {
                    Type site;
                    JCExpression clazz = TreeInfo.typeIn(tree.clazz);
                    if (clazz.hasTag(IDENT)) {
                        site = env.enclClass.sym.type;
                    } else if (clazz.hasTag(SELECT)) {
                        site = ((JCFieldAccess) clazz).selected.type;
                    } else throw new AssertionError("" + tree);
                    if (clazzOuter.hasTag(CLASS) && site != clazzOuter) {
                        if (site.hasTag(CLASS))
                            site = types.asOuterSuper(site, clazzOuter.tsym);
                        if (site == null)
                            site = types.erasure(clazzOuter);
                        clazzOuter = site;
                    }
                }
                owntype = new ClassType(clazzOuter, actuals, clazztype.tsym);
            } else {
                if (formals.length() != 0) {
                    log.error(tree.pos(), "wrong.number.type.args",
                            Integer.toString(formals.length()));
                } else {
                    log.error(tree.pos(), "type.doesnt.take.params", clazztype.tsym);
                }
                owntype = types.createErrorType(tree.type);
            }
        }
        result = check(tree, owntype, TYP, resultInfo);
    }

    public void visitTypeUnion(JCTypeUnion tree) {
        ListBuffer<Type> multicatchTypes = new ListBuffer<>();
        ListBuffer<Type> all_multicatchTypes = null;
        for (JCExpression typeTree : tree.alternatives) {
            Type ctype = attribType(typeTree, env);
            ctype = chk.checkType(typeTree.pos(),
                    chk.checkClassType(typeTree.pos(), ctype),
                    syms.throwableType);
            if (!ctype.isErroneous()) {
                if (chk.intersects(ctype, multicatchTypes.toList())) {
                    for (Type t : multicatchTypes) {
                        boolean sub = types.isSubtype(ctype, t);
                        boolean sup = types.isSubtype(t, ctype);
                        if (sub || sup) {
                            Type a = sub ? ctype : t;
                            Type b = sub ? t : ctype;
                            log.error(typeTree.pos(), "multicatch.types.must.be.disjoint", a, b);
                        }
                    }
                }
                multicatchTypes.append(ctype);
                if (all_multicatchTypes != null)
                    all_multicatchTypes.append(ctype);
            } else {
                if (all_multicatchTypes == null) {
                    all_multicatchTypes = new ListBuffer<>();
                    all_multicatchTypes.appendList(multicatchTypes);
                }
                all_multicatchTypes.append(ctype);
            }
        }
        Type t = check(tree, types.lub(multicatchTypes.toList()), TYP, resultInfo);
        if (t.hasTag(CLASS)) {
            List<Type> alternatives =
                    ((all_multicatchTypes == null) ? multicatchTypes : all_multicatchTypes).toList();
            t = new UnionClassType((ClassType) t, alternatives);
        }
        tree.type = result = t;
    }

    public void visitTypeIntersection(JCTypeIntersection tree) {
        attribTypes(tree.bounds, env);
        tree.type = result = checkIntersection(tree, tree.bounds);
    }

    public void visitTypeParameter(JCTypeParameter tree) {
        TypeVar typeVar = (TypeVar) tree.type;
        if (tree.annotations != null && tree.annotations.nonEmpty()) {
            annotateType(tree, tree.annotations);
        }
        if (!typeVar.bound.isErroneous()) {
            typeVar.bound = checkIntersection(tree, tree.bounds);
        }
    }

    Type checkIntersection(JCTree tree, List<JCExpression> bounds) {
        Set<Type> boundSet = new HashSet<Type>();
        if (bounds.nonEmpty()) {
            bounds.head.type = checkBase(bounds.head.type, bounds.head, env, false, false, false);
            boundSet.add(types.erasure(bounds.head.type));
            if (bounds.head.type.isErroneous()) {
                return bounds.head.type;
            } else if (bounds.head.type.hasTag(TYPEVAR)) {
                if (bounds.tail.nonEmpty()) {
                    log.error(bounds.tail.head.pos(),
                            "type.var.may.not.be.followed.by.other.bounds");
                    return bounds.head.type;
                }
            } else {
                for (JCExpression bound : bounds.tail) {
                    bound.type = checkBase(bound.type, bound, env, false, true, false);
                    if (bound.type.isErroneous()) {
                        bounds = List.of(bound);
                    } else if (bound.type.hasTag(CLASS)) {
                        chk.checkNotRepeated(bound.pos(), types.erasure(bound.type), boundSet);
                    }
                }
            }
        }
        if (bounds.length() == 0) {
            return syms.objectType;
        } else if (bounds.length() == 1) {
            return bounds.head.type;
        } else {
            Type owntype = types.makeCompoundType(TreeInfo.types(bounds));
            JCExpression extending;
            List<JCExpression> implementing;
            if (!bounds.head.type.isInterface()) {
                extending = bounds.head;
                implementing = bounds.tail;
            } else {
                extending = null;
                implementing = bounds;
            }
            JCClassDecl cd = make.at(tree).ClassDef(
                    make.Modifiers(PUBLIC | ABSTRACT),
                    names.empty, List.nil(),
                    extending, implementing, List.nil());
            ClassSymbol c = (ClassSymbol) owntype.tsym;
            Assert.check((c.flags() & COMPOUND) != 0);
            cd.sym = c;
            c.sourcefile = env.toplevel.sourcefile;
            c.flags_field |= UNATTRIBUTED;
            Env<AttrContext> cenv = enter.classEnv(cd, env);
            enter.typeEnvs.put(c, cenv);
            attribClass(c);
            return owntype;
        }
    }

    public void visitWildcard(JCWildcard tree) {
        Type type = (tree.kind.kind == BoundKind.UNBOUND)
                ? syms.objectType
                : attribType(tree.inner, env);
        result = check(tree, new WildcardType(chk.checkRefType(tree.pos(), type),
                        tree.kind.kind,
                        syms.boundClass),
                TYP, resultInfo);
    }

    public void visitAnnotation(JCAnnotation tree) {
        Assert.error("should be handled in Annotate");
    }

    public void visitAnnotatedType(JCAnnotatedType tree) {
        Type underlyingType = attribType(tree.getUnderlyingType(), env);
        this.attribAnnotationTypes(tree.annotations, env);
        annotateType(tree, tree.annotations);
        result = tree.type = underlyingType;
    }

    public void annotateType(final JCTree tree, final List<JCAnnotation> annotations) {
        annotate.typeAnnotation(new Annotate.Worker() {
            @Override
            public String toString() {
                return "annotate " + annotations + " onto " + tree;
            }

            @Override
            public void run() {
                List<Attribute.TypeCompound> compounds = fromAnnotations(annotations);
                if (annotations.size() == compounds.size()) {
                    tree.type = tree.type.unannotatedType().annotatedType(compounds);
                }
            }
        });
    }

    public void visitErroneous(JCErroneous tree) {
        if (tree.errs != null)
            for (JCTree err : tree.errs)
                attribTree(err, env, new ResultInfo(ERR, pt()));
        result = tree.type = syms.errType;
    }

    public void visitTree(JCTree tree) {
        throw new AssertionError();
    }

    public void attrib(Env<AttrContext> env) {
        if (env.tree.hasTag(TOPLEVEL))
            attribTopLevel(env);
        else
            attribClass(env.tree.pos(), env.enclClass.sym);
    }

    public void attribTopLevel(Env<AttrContext> env) {
        JCCompilationUnit toplevel = env.toplevel;
        try {
            annotate.flush();
        } catch (CompletionFailure ex) {
            chk.completionError(toplevel.pos(), ex);
        }
    }

    public void attribClass(DiagnosticPosition pos, ClassSymbol c) {
        try {
            annotate.flush();
            attribClass(c);
        } catch (CompletionFailure ex) {
            chk.completionError(pos, ex);
        }
    }

    void attribClass(ClassSymbol c) throws CompletionFailure {
        if (c.type.hasTag(ERROR)) return;
        chk.checkNonCyclic(null, c.type);
        Type st = types.supertype(c.type);
        if ((c.flags_field & Flags.COMPOUND) == 0) {
            if (st.hasTag(CLASS)) attribClass((ClassSymbol) st.tsym);
            if (c.owner.kind == TYP && c.owner.type.hasTag(CLASS))
                attribClass((ClassSymbol) c.owner);
        }
        if ((c.flags_field & UNATTRIBUTED) != 0) {
            c.flags_field &= ~UNATTRIBUTED;
            Env<AttrContext> env = enter.typeEnvs.get(c);
            Env<AttrContext> lintEnv = env;
            while (lintEnv.info.lint == null)
                lintEnv = lintEnv.next;
            env.info.lint = lintEnv.info.lint.augment(c);
            Lint prevLint = chk.setLint(env.info.lint);
            JavaFileObject prev = log.useSource(c.sourcefile);
            ResultInfo prevReturnRes = env.info.returnResult;
            try {
                deferredLintHandler.flush(env.tree);
                env.info.returnResult = null;
                if (st.tsym == syms.enumSym &&
                        ((c.flags_field & (Flags.ENUM | Flags.COMPOUND)) == 0))
                    log.error(env.tree.pos(), "enum.no.subclassing");
                if (st.tsym != null &&
                        ((st.tsym.flags_field & Flags.ENUM) != 0) &&
                        ((c.flags_field & (Flags.ENUM | Flags.COMPOUND)) == 0)) {
                    log.error(env.tree.pos(), "enum.types.not.extensible");
                }
                attribClassBody(env, c);
                chk.checkDeprecatedAnnotation(env.tree.pos(), c);
                chk.checkClassOverrideEqualsAndHashIfNeeded(env.tree.pos(), c);
                chk.checkFunctionalInterface((JCClassDecl) env.tree, c);
            } finally {
                env.info.returnResult = prevReturnRes;
                log.useSource(prev);
                chk.setLint(prevLint);
            }
        }
    }

    public void visitImport(JCImport tree) {
    }

    private void attribClassBody(Env<AttrContext> env, ClassSymbol c) {
        JCClassDecl tree = (JCClassDecl) env.tree;
        Assert.check(c == tree.sym);
        attribStats(tree.typarams, env);
        if (!c.isAnonymous()) {
            chk.validate(tree.typarams, env);
            chk.validate(tree.extending, env);
            chk.validate(tree.implementing, env);
        }
        if ((c.flags() & (ABSTRACT | INTERFACE)) == 0) {
            if (!relax)
                chk.checkAllDefined(tree.pos(), c);
        }
        if ((c.flags() & ANNOTATION) != 0) {
            if (tree.implementing.nonEmpty())
                log.error(tree.implementing.head.pos(), "cant.extend.intf.annotation");
            if (tree.typarams.nonEmpty())
                log.error(tree.typarams.head.pos(), "intf.annotation.cant.have.type.params");
            Attribute.Compound repeatable = c.attribute(syms.repeatableType.tsym);
            if (repeatable != null) {
                DiagnosticPosition cbPos = getDiagnosticPosition(tree, repeatable.type);
                Assert.checkNonNull(cbPos);
                chk.validateRepeatable(c, repeatable, cbPos);
            }
        } else {
            chk.checkCompatibleSupertypes(tree.pos(), c.type);
            if (allowDefaultMethods) {
                chk.checkDefaultMethodClashes(tree.pos(), c.type);
            }
        }
        chk.checkClassBounds(tree.pos(), c.type);
        tree.type = c.type;
        for (List<JCTypeParameter> l = tree.typarams;
             l.nonEmpty(); l = l.tail) {
            Assert.checkNonNull(env.info.scope.lookup(l.head.name).scope);
        }
        if (!c.type.allparams().isEmpty() && types.isSubtype(c.type, syms.throwableType))
            log.error(tree.extending.pos(), "generic.throwable");
        chk.checkImplementations(tree);
        checkAutoCloseable(tree.pos(), env, c.type);
        for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
            attribStat(l.head, env);
            if (c.owner.kind != PCK &&
                    ((c.flags() & STATIC) == 0 || c.name == names.empty) &&
                    (TreeInfo.flags(l.head) & (STATIC | INTERFACE)) != 0) {
                Symbol sym = null;
                if (l.head.hasTag(VARDEF)) sym = ((JCVariableDecl) l.head).sym;
                if (sym == null ||
                        sym.kind != VAR ||
                        ((VarSymbol) sym).getConstValue() == null)
                    log.error(l.head.pos(), "icls.cant.have.static.decl", c);
            }
        }
        chk.checkCyclicConstructors(tree);
        chk.checkNonCyclicElements(tree);
        if (env.info.lint.isEnabled(LintCategory.SERIAL) &&
                isSerializable(c) &&
                (c.flags() & Flags.ENUM) == 0 &&
                checkForSerial(c)) {
            checkSerialVersionUID(tree, c);
        }
        if (allowTypeAnnos) {
            typeAnnotations.organizeTypeAnnotationsBodies(tree);
            validateTypeAnnotations(tree, false);
        }
    }

    boolean checkForSerial(ClassSymbol c) {
        if ((c.flags() & ABSTRACT) == 0) {
            return true;
        } else {
            return c.members().anyMatch(anyNonAbstractOrDefaultMethod);
        }
    }

    private DiagnosticPosition getDiagnosticPosition(JCClassDecl tree, Type t) {
        for (List<JCAnnotation> al = tree.mods.annotations; !al.isEmpty(); al = al.tail) {
            if (types.isSameType(al.head.annotationType.type, t))
                return al.head.pos();
        }
        return null;
    }

    private boolean isSerializable(ClassSymbol c) {
        try {
            syms.serializableType.complete();
        } catch (CompletionFailure e) {
            return false;
        }
        return types.isSubtype(c.type, syms.serializableType);
    }

    private void checkSerialVersionUID(JCClassDecl tree, ClassSymbol c) {
        Scope.Entry e = c.members().lookup(names.serialVersionUID);
        while (e.scope != null && e.sym.kind != VAR) e = e.next();
        if (e.scope == null) {
            log.warning(LintCategory.SERIAL,
                    tree.pos(), "missing.SVUID", c);
            return;
        }
        VarSymbol svuid = (VarSymbol) e.sym;
        if ((svuid.flags() & (STATIC | FINAL)) !=
                (STATIC | FINAL))
            log.warning(LintCategory.SERIAL,
                    TreeInfo.diagnosticPositionFor(svuid, tree), "improper.SVUID", c);
        else if (!svuid.type.hasTag(LONG))
            log.warning(LintCategory.SERIAL,
                    TreeInfo.diagnosticPositionFor(svuid, tree), "long.SVUID", c);
        else if (svuid.getConstValue() == null)
            log.warning(LintCategory.SERIAL,
                    TreeInfo.diagnosticPositionFor(svuid, tree), "constant.SVUID", c);
    }

    private Type capture(Type type) {
        return types.capture(type);
    }

    public void validateTypeAnnotations(JCTree tree, boolean sigOnly) {
        tree.accept(new TypeAnnotationsValidator(sigOnly));
    }

    public void postAttr(JCTree tree) {
        new PostAttrAnalyzer().scan(tree);
    }

    private static class BreakAttr extends RuntimeException {
        static final long serialVersionUID = -6924771130405446405L;
        private Env<AttrContext> env;

        private BreakAttr(Env<AttrContext> env) {
            this.env = env;
        }
    }

    private class IdentAttributer extends SimpleTreeVisitor<Symbol, Env<AttrContext>> {
        @Override
        public Symbol visitMemberSelect(MemberSelectTree node, Env<AttrContext> env) {
            Symbol site = visit(node.getExpression(), env);
            if (site.kind == ERR || site.kind == ABSENT_TYP)
                return site;
            Name name = (Name) node.getIdentifier();
            if (site.kind == PCK) {
                env.toplevel.packge = (PackageSymbol) site;
                return rs.findIdentInPackage(env, (TypeSymbol) site, name, TYP | PCK);
            } else {
                env.enclClass.sym = (ClassSymbol) site;
                return rs.findMemberType(env, site.asType(), name, (TypeSymbol) site);
            }
        }

        @Override
        public Symbol visitIdentifier(IdentifierTree node, Env<AttrContext> env) {
            return rs.findIdent(env, (Name) node.getName(), TYP | PCK);
        }
    }

    class ResultInfo {
        final int pkind;
        final Type pt;
        final CheckContext checkContext;

        ResultInfo(int pkind, Type pt) {
            this(pkind, pt, chk.basicHandler);
        }

        protected ResultInfo(int pkind, Type pt, CheckContext checkContext) {
            this.pkind = pkind;
            this.pt = pt;
            this.checkContext = checkContext;
        }

        protected Type check(final DiagnosticPosition pos, final Type found) {
            return chk.checkType(pos, found, pt, checkContext);
        }

        protected ResultInfo dup(Type newPt) {
            return new ResultInfo(pkind, newPt, checkContext);
        }

        protected ResultInfo dup(CheckContext newContext) {
            return new ResultInfo(pkind, pt, newContext);
        }

        @Override
        public String toString() {
            if (pt != null) {
                return pt.toString();
            } else {
                return "";
            }
        }
    }

    class RecoveryInfo extends ResultInfo {
        public RecoveryInfo(final DeferredAttr.DeferredAttrContext deferredAttrContext) {
            super(Kinds.VAL, Type.recoveryType, new Check.NestedCheckContext(chk.basicHandler) {
                @Override
                public DeferredAttr.DeferredAttrContext deferredAttrContext() {
                    return deferredAttrContext;
                }

                @Override
                public boolean compatible(Type found, Type req, Warner warn) {
                    return true;
                }

                @Override
                public void report(DiagnosticPosition pos, JCDiagnostic details) {
                    chk.basicHandler.report(pos, details);
                }
            });
        }
    }

    class FunctionalReturnContext extends Check.NestedCheckContext {
        FunctionalReturnContext(CheckContext enclosingContext) {
            super(enclosingContext);
        }

        @Override
        public boolean compatible(Type found, Type req, Warner warn) {
            return chk.basicHandler.compatible(found, inferenceContext().asFree(req), warn);
        }

        @Override
        public void report(DiagnosticPosition pos, JCDiagnostic details) {
            enclosingContext.report(pos, diags.fragment("incompatible.ret.type.in.lambda", details));
        }
    }

    class ExpressionLambdaReturnContext extends FunctionalReturnContext {
        JCExpression expr;

        ExpressionLambdaReturnContext(JCExpression expr, CheckContext enclosingContext) {
            super(enclosingContext);
            this.expr = expr;
        }

        @Override
        public boolean compatible(Type found, Type req, Warner warn) {
            return TreeInfo.isExpressionStatement(expr) && req.hasTag(VOID) ||
                    super.compatible(found, req, warn);
        }
    }

    private final class TypeAnnotationsValidator extends TreeScanner {
        private final boolean sigOnly;

        public TypeAnnotationsValidator(boolean sigOnly) {
            this.sigOnly = sigOnly;
        }

        public void visitAnnotation(JCAnnotation tree) {
            chk.validateTypeAnnotation(tree, false);
            super.visitAnnotation(tree);
        }

        public void visitAnnotatedType(JCAnnotatedType tree) {
            if (!tree.underlyingType.type.isErroneous()) {
                super.visitAnnotatedType(tree);
            }
        }

        public void visitTypeParameter(JCTypeParameter tree) {
            chk.validateTypeAnnotations(tree.annotations, true);
            scan(tree.bounds);
        }

        public void visitMethodDef(JCMethodDecl tree) {
            if (tree.recvparam != null &&
                    !tree.recvparam.vartype.type.isErroneous()) {
                checkForDeclarationAnnotations(tree.recvparam.mods.annotations,
                        tree.recvparam.vartype.type.tsym);
            }
            if (tree.restype != null && tree.restype.type != null) {
                validateAnnotatedType(tree.restype, tree.restype.type);
            }
            if (sigOnly) {
                scan(tree.mods);
                scan(tree.restype);
                scan(tree.typarams);
                scan(tree.recvparam);
                scan(tree.params);
                scan(tree.thrown);
            } else {
                scan(tree.defaultValue);
                scan(tree.body);
            }
        }

        public void visitVarDef(final JCVariableDecl tree) {
            if (tree.sym != null && tree.sym.type != null)
                validateAnnotatedType(tree.vartype, tree.sym.type);
            scan(tree.mods);
            scan(tree.vartype);
            if (!sigOnly) {
                scan(tree.init);
            }
        }

        public void visitTypeCast(JCTypeCast tree) {
            if (tree.clazz != null && tree.clazz.type != null)
                validateAnnotatedType(tree.clazz, tree.clazz.type);
            super.visitTypeCast(tree);
        }

        public void visitTypeTest(JCInstanceOf tree) {
            if (tree.clazz != null && tree.clazz.type != null)
                validateAnnotatedType(tree.clazz, tree.clazz.type);
            super.visitTypeTest(tree);
        }

        public void visitNewClass(JCNewClass tree) {
            if (tree.clazz.hasTag(ANNOTATED_TYPE)) {
                checkForDeclarationAnnotations(((JCAnnotatedType) tree.clazz).annotations,
                        tree.clazz.type.tsym);
            }
            if (tree.def != null) {
                checkForDeclarationAnnotations(tree.def.mods.annotations, tree.clazz.type.tsym);
            }
            if (tree.clazz.type != null) {
                validateAnnotatedType(tree.clazz, tree.clazz.type);
            }
            super.visitNewClass(tree);
        }

        public void visitNewArray(JCNewArray tree) {
            if (tree.elemtype != null && tree.elemtype.type != null) {
                if (tree.elemtype.hasTag(ANNOTATED_TYPE)) {
                    checkForDeclarationAnnotations(((JCAnnotatedType) tree.elemtype).annotations,
                            tree.elemtype.type.tsym);
                }
                validateAnnotatedType(tree.elemtype, tree.elemtype.type);
            }
            super.visitNewArray(tree);
        }

        public void visitClassDef(JCClassDecl tree) {
            if (sigOnly) {
                scan(tree.mods);
                scan(tree.typarams);
                scan(tree.extending);
                scan(tree.implementing);
            }
            for (JCTree member : tree.defs) {
                if (member.hasTag(Tag.CLASSDEF)) {
                    continue;
                }
                scan(member);
            }
        }

        public void visitBlock(JCBlock tree) {
            if (!sigOnly) {
                scan(tree.stats);
            }
        }

        private void validateAnnotatedType(final JCTree errtree, final Type type) {
            if (type.isPrimitiveOrVoid()) {
                return;
            }
            JCTree enclTr = errtree;
            Type enclTy = type;
            boolean repeat = true;
            while (repeat) {
                if (enclTr.hasTag(TYPEAPPLY)) {
                    List<Type> tyargs = enclTy.getTypeArguments();
                    List<JCExpression> trargs = ((JCTypeApply) enclTr).getTypeArguments();
                    if (trargs.length() > 0) {
                        if (tyargs.length() == trargs.length()) {
                            for (int i = 0; i < tyargs.length(); ++i) {
                                validateAnnotatedType(trargs.get(i), tyargs.get(i));
                            }
                        }
                    }
                    enclTr = ((JCTypeApply) enclTr).clazz;
                }
                if (enclTr.hasTag(SELECT)) {
                    enclTr = ((JCFieldAccess) enclTr).getExpression();
                    if (enclTy != null &&
                            !enclTy.hasTag(NONE)) {
                        enclTy = enclTy.getEnclosingType();
                    }
                } else if (enclTr.hasTag(ANNOTATED_TYPE)) {
                    JCAnnotatedType at = (JCAnnotatedType) enclTr;
                    if (enclTy == null ||
                            enclTy.hasTag(NONE)) {
                        if (at.getAnnotations().size() == 1) {
                            log.error(at.underlyingType.pos(), "cant.type.annotate.scoping.1", at.getAnnotations().head.attribute);
                        } else {
                            ListBuffer<Attribute.Compound> comps = new ListBuffer<Attribute.Compound>();
                            for (JCAnnotation an : at.getAnnotations()) {
                                comps.add(an.attribute);
                            }
                            log.error(at.underlyingType.pos(), "cant.type.annotate.scoping", comps.toList());
                        }
                        repeat = false;
                    }
                    enclTr = at.underlyingType;
                } else if (enclTr.hasTag(IDENT)) {
                    repeat = false;
                } else if (enclTr.hasTag(Tag.WILDCARD)) {
                    JCWildcard wc = (JCWildcard) enclTr;
                    if (wc.getKind() == JCTree.Kind.EXTENDS_WILDCARD) {
                        validateAnnotatedType(wc.getBound(), ((WildcardType) enclTy.unannotatedType()).getExtendsBound());
                    } else if (wc.getKind() == JCTree.Kind.SUPER_WILDCARD) {
                        validateAnnotatedType(wc.getBound(), ((WildcardType) enclTy.unannotatedType()).getSuperBound());
                    } else {
                    }
                    repeat = false;
                } else if (enclTr.hasTag(TYPEARRAY)) {
                    JCArrayTypeTree art = (JCArrayTypeTree) enclTr;
                    validateAnnotatedType(art.getType(), ((ArrayType) enclTy.unannotatedType()).getComponentType());
                    repeat = false;
                } else if (enclTr.hasTag(TYPEUNION)) {
                    JCTypeUnion ut = (JCTypeUnion) enclTr;
                    for (JCTree t : ut.getTypeAlternatives()) {
                        validateAnnotatedType(t, t.type);
                    }
                    repeat = false;
                } else if (enclTr.hasTag(TYPEINTERSECTION)) {
                    JCTypeIntersection it = (JCTypeIntersection) enclTr;
                    for (JCTree t : it.getBounds()) {
                        validateAnnotatedType(t, t.type);
                    }
                    repeat = false;
                } else if (enclTr.getKind() == JCTree.Kind.PRIMITIVE_TYPE ||
                        enclTr.getKind() == JCTree.Kind.ERRONEOUS) {
                    repeat = false;
                } else {
                    Assert.error("Unexpected tree: " + enclTr + " with kind: " + enclTr.getKind() +
                            " within: " + errtree + " with kind: " + errtree.getKind());
                }
            }
        }

        private void checkForDeclarationAnnotations(List<? extends JCAnnotation> annotations, Symbol sym) {
            for (JCAnnotation ai : annotations) {
                if (!ai.type.isErroneous() &&
                        typeAnnotations.annotationType(ai.attribute, sym) == TypeAnnotations.AnnotationType.DECLARATION) {
                    log.error(ai.pos(), "annotation.type.not.applicable");
                }
            }
        }
    }

    class PostAttrAnalyzer extends TreeScanner {
        private void initTypeIfNeeded(JCTree that) {
            if (that.type == null) {
                that.type = syms.unknownType;
            }
        }

        @Override
        public void scan(JCTree tree) {
            if (tree == null) return;
            if (tree instanceof JCExpression) {
                initTypeIfNeeded(tree);
            }
            super.scan(tree);
        }

        @Override
        public void visitIdent(JCIdent that) {
            if (that.sym == null) {
                that.sym = syms.unknownSymbol;
            }
        }

        @Override
        public void visitSelect(JCFieldAccess that) {
            if (that.sym == null) {
                that.sym = syms.unknownSymbol;
            }
            super.visitSelect(that);
        }

        @Override
        public void visitClassDef(JCClassDecl that) {
            initTypeIfNeeded(that);
            if (that.sym == null) {
                that.sym = new ClassSymbol(0, that.name, that.type, syms.noSymbol);
            }
            super.visitClassDef(that);
        }

        @Override
        public void visitMethodDef(JCMethodDecl that) {
            initTypeIfNeeded(that);
            if (that.sym == null) {
                that.sym = new MethodSymbol(0, that.name, that.type, syms.noSymbol);
            }
            super.visitMethodDef(that);
        }

        @Override
        public void visitVarDef(JCVariableDecl that) {
            initTypeIfNeeded(that);
            if (that.sym == null) {
                that.sym = new VarSymbol(0, that.name, that.type, syms.noSymbol);
                that.sym.adr = 0;
            }
            super.visitVarDef(that);
        }

        @Override
        public void visitNewClass(JCNewClass that) {
            if (that.constructor == null) {
                that.constructor = new MethodSymbol(0, names.init, syms.unknownType, syms.noSymbol);
            }
            if (that.constructorType == null) {
                that.constructorType = syms.unknownType;
            }
            super.visitNewClass(that);
        }

        @Override
        public void visitAssignop(JCAssignOp that) {
            if (that.operator == null)
                that.operator = new OperatorSymbol(names.empty, syms.unknownType, -1, syms.noSymbol);
            super.visitAssignop(that);
        }

        @Override
        public void visitBinary(JCBinary that) {
            if (that.operator == null)
                that.operator = new OperatorSymbol(names.empty, syms.unknownType, -1, syms.noSymbol);
            super.visitBinary(that);
        }

        @Override
        public void visitUnary(JCUnary that) {
            if (that.operator == null)
                that.operator = new OperatorSymbol(names.empty, syms.unknownType, -1, syms.noSymbol);
            super.visitUnary(that);
        }

        @Override
        public void visitLambda(JCLambda that) {
            super.visitLambda(that);
            if (that.targets == null) {
                that.targets = List.nil();
            }
        }

        @Override
        public void visitReference(JCMemberReference that) {
            super.visitReference(that);
            if (that.sym == null) {
                that.sym = new MethodSymbol(0, names.empty, syms.unknownType, syms.noSymbol);
            }
            if (that.targets == null) {
                that.targets = List.nil();
            }
        }
    }
}
