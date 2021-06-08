package com.sun.tools.javac.comp;

import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.JCTree.JCMemberReference.ReferenceKind;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.DynamicMethodSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.MethodType;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.LambdaToMethod.LambdaAnalyzerPreprocessor.*;
import com.sun.tools.javac.comp.Lower.BasicFreeVarCollector;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.source.tree.MemberReferenceTree.ReferenceMode;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.sun.tools.javac.comp.LambdaToMethod.LambdaSymbolKind.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTag.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

public class LambdaToMethod extends TreeTranslator {
    public static final int FLAG_SERIALIZABLE = 1 << 0;
    public static final int FLAG_MARKERS = 1 << 1;
    public static final int FLAG_BRIDGES = 1 << 2;
    protected static final Context.Key<LambdaToMethod> unlambdaKey =
            new Context.Key<LambdaToMethod>();
    private Attr attr;
    private JCDiagnostic.Factory diags;
    private Log log;
    private Lower lower;
    private Names names;
    private Symtab syms;
    private Resolve rs;
    private TreeMaker make;
    private Types types;
    private TransTypes transTypes;
    private Env<AttrContext> attrEnv;
    private LambdaAnalyzerPreprocessor analyzer;
    private Map<JCTree, TranslationContext<?>> contextMap;
    private TranslationContext<?> context;
    private KlassInfo kInfo;
    private boolean dumpLambdaToMethodStats;

    private LambdaToMethod(Context context) {
        context.put(unlambdaKey, this);
        diags = JCDiagnostic.Factory.instance(context);
        log = Log.instance(context);
        lower = Lower.instance(context);
        names = Names.instance(context);
        syms = Symtab.instance(context);
        rs = Resolve.instance(context);
        make = TreeMaker.instance(context);
        types = Types.instance(context);
        transTypes = TransTypes.instance(context);
        analyzer = new LambdaAnalyzerPreprocessor();
        Options options = Options.instance(context);
        dumpLambdaToMethodStats = options.isSet("dumpLambdaToMethodStats");
        attr = Attr.instance(context);
    }

    public static LambdaToMethod instance(Context context) {
        LambdaToMethod instance = context.get(unlambdaKey);
        if (instance == null) {
            instance = new LambdaToMethod(context);
        }
        return instance;
    }

    @Override
    public <T extends JCTree> T translate(T tree) {
        TranslationContext<?> newContext = contextMap.get(tree);
        return translate(tree, newContext != null ? newContext : context);
    }

    <T extends JCTree> T translate(T tree, TranslationContext<?> newContext) {
        TranslationContext<?> prevContext = context;
        try {
            context = newContext;
            return super.translate(tree);
        } finally {
            context = prevContext;
        }
    }

    <T extends JCTree> List<T> translate(List<T> trees, TranslationContext<?> newContext) {
        ListBuffer<T> buf = new ListBuffer<>();
        for (T tree : trees) {
            buf.append(translate(tree, newContext));
        }
        return buf.toList();
    }

    public JCTree translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        this.make = make;
        this.attrEnv = env;
        this.context = null;
        this.contextMap = new HashMap<JCTree, TranslationContext<?>>();
        return translate(cdef);
    }

    @Override
    public void visitClassDef(JCClassDecl tree) {
        if (tree.sym.owner.kind == PCK) {

            tree = analyzer.analyzeAndPreprocessClass(tree);
        }
        KlassInfo prevKlassInfo = kInfo;
        try {
            kInfo = new KlassInfo(tree);
            super.visitClassDef(tree);
            if (!kInfo.deserializeCases.isEmpty()) {
                int prevPos = make.pos;
                try {
                    make.at(tree);
                    kInfo.addMethod(makeDeserializeMethod(tree.sym));
                } finally {
                    make.at(prevPos);
                }
            }

            List<JCTree> newMethods = kInfo.appendedMethodList.toList();
            tree.defs = tree.defs.appendList(newMethods);
            for (JCTree lambda : newMethods) {
                tree.sym.members().enter(((JCMethodDecl) lambda).sym);
            }
            result = tree;
        } finally {
            kInfo = prevKlassInfo;
        }
    }

    @Override
    public void visitLambda(JCLambda tree) {
        LambdaTranslationContext localContext = (LambdaTranslationContext) context;
        MethodSymbol sym = (MethodSymbol) localContext.translatedSym;
        MethodType lambdaType = (MethodType) sym.type;
        {
            Symbol owner = localContext.owner;
            ListBuffer<Attribute.TypeCompound> ownerTypeAnnos = new ListBuffer<Attribute.TypeCompound>();
            ListBuffer<Attribute.TypeCompound> lambdaTypeAnnos = new ListBuffer<Attribute.TypeCompound>();
            for (Attribute.TypeCompound tc : owner.getRawTypeAttributes()) {
                if (tc.position.onLambda == tree) {
                    lambdaTypeAnnos.append(tc);
                } else {
                    ownerTypeAnnos.append(tc);
                }
            }
            if (lambdaTypeAnnos.nonEmpty()) {
                owner.setTypeAttributes(ownerTypeAnnos.toList());
                sym.setTypeAttributes(lambdaTypeAnnos.toList());
            }
        }

        JCMethodDecl lambdaDecl = make.MethodDef(make.Modifiers(sym.flags_field),
                sym.name,
                make.QualIdent(lambdaType.getReturnType().tsym),
                List.nil(),
                localContext.syntheticParams,
                lambdaType.getThrownTypes() == null ?
                        List.nil() :
                        make.Types(lambdaType.getThrownTypes()),
                null,
                null);
        lambdaDecl.sym = sym;
        lambdaDecl.type = lambdaType;


        lambdaDecl.body = translate(makeLambdaBody(tree, lambdaDecl));

        kInfo.addMethod(lambdaDecl);


        ListBuffer<JCExpression> syntheticInits = new ListBuffer<>();
        if (!sym.isStatic()) {
            syntheticInits.append(makeThis(
                    sym.owner.enclClass().asType(),
                    localContext.owner.enclClass()));
        }

        for (Symbol fv : localContext.getSymbolMap(CAPTURED_VAR).keySet()) {
            if (fv != localContext.self) {
                JCTree captured_local = make.Ident(fv).setType(fv.type);
                syntheticInits.append((JCExpression) captured_local);
            }
        }

        List<JCExpression> indy_args = translate(syntheticInits.toList(), localContext.prev);

        int refKind = referenceKind(sym);

        result = makeMetafactoryIndyCall(context, refKind, sym, indy_args);
    }

    private JCIdent makeThis(Type type, Symbol owner) {
        VarSymbol _this = new VarSymbol(PARAMETER | FINAL | SYNTHETIC,
                names._this,
                type,
                owner);
        return make.Ident(_this);
    }

    @Override
    public void visitReference(JCMemberReference tree) {
        ReferenceTranslationContext localContext = (ReferenceTranslationContext) context;


        Symbol refSym = localContext.needsBridge()
                ? localContext.bridgeSym
                : localContext.isSignaturePolymorphic()
                ? localContext.sigPolySym
                : tree.sym;

        if (localContext.needsBridge()) {
            bridgeMemberReference(tree, localContext);
        }

        JCExpression init;
        switch (tree.kind) {
            case IMPLICIT_INNER:
            case SUPER:
                init = makeThis(
                        localContext.owner.enclClass().asType(),
                        localContext.owner.enclClass());
                break;
            case BOUND:
                init = tree.getQualifierExpression();
                init = attr.makeNullCheck(init);
                break;
            case UNBOUND:
            case STATIC:
            case TOPLEVEL:
            case ARRAY_CTOR:
                init = null;
                break;
            default:
                throw new InternalError("Should not have an invalid kind");
        }
        List<JCExpression> indy_args = init == null ? List.nil() : translate(List.of(init), localContext.prev);

        result = makeMetafactoryIndyCall(localContext, localContext.referenceKind(), refSym, indy_args);
    }

    @Override
    public void visitIdent(JCIdent tree) {
        if (context == null || !analyzer.lambdaIdentSymbolFilter(tree.sym)) {
            super.visitIdent(tree);
        } else {
            int prevPos = make.pos;
            try {
                make.at(tree);
                LambdaTranslationContext lambdaContext = (LambdaTranslationContext) context;
                JCTree ltree = lambdaContext.translate(tree);
                if (ltree != null) {
                    result = ltree;
                } else {


                    super.visitIdent(tree);
                }
            } finally {
                make.at(prevPos);
            }
        }
    }

    @Override
    public void visitVarDef(JCVariableDecl tree) {
        LambdaTranslationContext lambdaContext = (LambdaTranslationContext) context;
        if (context != null && lambdaContext.getSymbolMap(LOCAL_VAR).containsKey(tree.sym)) {
            JCExpression init = translate(tree.init);
            int prevPos = make.pos;
            try {
                result = make.at(tree).VarDef((VarSymbol) lambdaContext.getSymbolMap(LOCAL_VAR).get(tree.sym), init);
            } finally {
                make.at(prevPos);
            }
        } else if (context != null && lambdaContext.getSymbolMap(TYPE_VAR).containsKey(tree.sym)) {
            JCExpression init = translate(tree.init);
            VarSymbol xsym = (VarSymbol) lambdaContext.getSymbolMap(TYPE_VAR).get(tree.sym);
            int prevPos = make.pos;
            try {
                result = make.at(tree).VarDef(xsym, init);
            } finally {
                make.at(prevPos);
            }

            Scope sc = tree.sym.owner.members();
            if (sc != null) {
                sc.remove(tree.sym);
                sc.enter(xsym);
            }
        } else {
            super.visitVarDef(tree);
        }
    }

    private JCBlock makeLambdaBody(JCLambda tree, JCMethodDecl lambdaMethodDecl) {
        return tree.getBodyKind() == JCLambda.BodyKind.EXPRESSION ?
                makeLambdaExpressionBody((JCExpression) tree.body, lambdaMethodDecl) :
                makeLambdaStatementBody((JCBlock) tree.body, lambdaMethodDecl, tree.canCompleteNormally);
    }

    private JCBlock makeLambdaExpressionBody(JCExpression expr, JCMethodDecl lambdaMethodDecl) {
        Type restype = lambdaMethodDecl.type.getReturnType();
        boolean isLambda_void = expr.type.hasTag(VOID);
        boolean isTarget_void = restype.hasTag(VOID);
        boolean isTarget_Void = types.isSameType(restype, types.boxedClass(syms.voidType).type);
        int prevPos = make.pos;
        try {
            if (isTarget_void) {


                JCStatement stat = make.at(expr).Exec(expr);
                return make.Block(0, List.of(stat));
            } else if (isLambda_void && isTarget_Void) {


                ListBuffer<JCStatement> stats = new ListBuffer<>();
                stats.append(make.at(expr).Exec(expr));
                stats.append(make.Return(make.Literal(BOT, null).setType(syms.botType)));
                return make.Block(0, stats.toList());
            } else {


                JCExpression retExpr = transTypes.coerce(attrEnv, expr, restype);
                return make.at(retExpr).Block(0, List.of(make.Return(retExpr)));
            }
        } finally {
            make.at(prevPos);
        }
    }

    private JCBlock makeLambdaStatementBody(JCBlock block, final JCMethodDecl lambdaMethodDecl, boolean completeNormally) {
        final Type restype = lambdaMethodDecl.type.getReturnType();
        final boolean isTarget_void = restype.hasTag(VOID);
        boolean isTarget_Void = types.isSameType(restype, types.boxedClass(syms.voidType).type);
        class LambdaBodyTranslator extends TreeTranslator {
            @Override
            public void visitClassDef(JCClassDecl tree) {

                result = tree;
            }

            @Override
            public void visitLambda(JCLambda tree) {

                result = tree;
            }

            @Override
            public void visitReturn(JCReturn tree) {
                boolean isLambda_void = tree.expr == null;
                if (isTarget_void && !isLambda_void) {


                    VarSymbol loc = makeSyntheticVar(0, names.fromString("$loc"), tree.expr.type, lambdaMethodDecl.sym);
                    JCVariableDecl varDef = make.VarDef(loc, tree.expr);
                    result = make.Block(0, List.of(varDef, make.Return(null)));
                } else if (!isTarget_void || !isLambda_void) {


                    tree.expr = transTypes.coerce(attrEnv, tree.expr, restype);
                    result = tree;
                } else {
                    result = tree;
                }
            }
        }
        JCBlock trans_block = new LambdaBodyTranslator().translate(block);
        if (completeNormally && isTarget_Void) {


            trans_block.stats = trans_block.stats.append(make.Return(make.Literal(BOT, null).setType(syms.botType)));
        }
        return trans_block;
    }

    private JCMethodDecl makeDeserializeMethod(Symbol kSym) {
        ListBuffer<JCCase> cases = new ListBuffer<>();
        ListBuffer<JCBreak> breaks = new ListBuffer<>();
        for (Map.Entry<String, ListBuffer<JCStatement>> entry : kInfo.deserializeCases.entrySet()) {
            JCBreak br = make.Break(null);
            breaks.add(br);
            List<JCStatement> stmts = entry.getValue().append(br).toList();
            cases.add(make.Case(make.Literal(entry.getKey()), stmts));
        }
        JCSwitch sw = make.Switch(deserGetter("getImplMethodName", syms.stringType), cases.toList());
        for (JCBreak br : breaks) {
            br.target = sw;
        }
        JCBlock body = make.Block(0L, List.of(
                sw,
                make.Throw(makeNewClass(
                        syms.illegalArgumentExceptionType,
                        List.of(make.Literal("Invalid lambda deserialization"))))));
        JCMethodDecl deser = make.MethodDef(make.Modifiers(kInfo.deserMethodSym.flags()),
                names.deserializeLambda,
                make.QualIdent(kInfo.deserMethodSym.getReturnType().tsym),
                List.nil(),
                List.of(make.VarDef(kInfo.deserParamSym, null)),
                List.nil(),
                body,
                null);
        deser.sym = kInfo.deserMethodSym;
        deser.type = kInfo.deserMethodSym.type;

        return deser;
    }

    JCNewClass makeNewClass(Type ctype, List<JCExpression> args, Symbol cons) {
        JCNewClass tree = make.NewClass(null,
                null, make.QualIdent(ctype.tsym), args, null);
        tree.constructor = cons;
        tree.type = ctype;
        return tree;
    }

    JCNewClass makeNewClass(Type ctype, List<JCExpression> args) {
        return makeNewClass(ctype, args,
                rs.resolveConstructor(null, attrEnv, ctype, TreeInfo.types(args), List.nil()));
    }

    private void addDeserializationCase(int implMethodKind, Symbol refSym, Type targetType, MethodSymbol samSym,
                                        DiagnosticPosition pos, List<Object> staticArgs, MethodType indyType) {
        String functionalInterfaceClass = classSig(targetType);
        String functionalInterfaceMethodName = samSym.getSimpleName().toString();
        String functionalInterfaceMethodSignature = typeSig(types.erasure(samSym.type));
        String implClass = classSig(types.erasure(refSym.owner.type));
        String implMethodName = refSym.getQualifiedName().toString();
        String implMethodSignature = typeSig(types.erasure(refSym.type));
        JCExpression kindTest = eqTest(syms.intType, deserGetter("getImplMethodKind", syms.intType), make.Literal(implMethodKind));
        ListBuffer<JCExpression> serArgs = new ListBuffer<>();
        int i = 0;
        for (Type t : indyType.getParameterTypes()) {
            List<JCExpression> indexAsArg = new ListBuffer<JCExpression>().append(make.Literal(i)).toList();
            List<Type> argTypes = new ListBuffer<Type>().append(syms.intType).toList();
            serArgs.add(make.TypeCast(types.erasure(t), deserGetter("getCapturedArg", syms.objectType, argTypes, indexAsArg)));
            ++i;
        }
        JCStatement stmt = make.If(
                deserTest(deserTest(deserTest(deserTest(deserTest(
                        kindTest,
                        "getFunctionalInterfaceClass", functionalInterfaceClass),
                        "getFunctionalInterfaceMethodName", functionalInterfaceMethodName),
                        "getFunctionalInterfaceMethodSignature", functionalInterfaceMethodSignature),
                        "getImplClass", implClass),
                        "getImplMethodSignature", implMethodSignature),
                make.Return(makeIndyCall(
                        pos,
                        syms.lambdaMetafactory,
                        names.altMetafactory,
                        staticArgs, indyType, serArgs.toList(), samSym.name)),
                null);
        ListBuffer<JCStatement> stmts = kInfo.deserializeCases.get(implMethodName);
        if (stmts == null) {
            stmts = new ListBuffer<>();
            kInfo.deserializeCases.put(implMethodName, stmts);
        }

        stmts.append(stmt);
    }

    private JCExpression eqTest(Type argType, JCExpression arg1, JCExpression arg2) {
        JCBinary testExpr = make.Binary(Tag.EQ, arg1, arg2);
        testExpr.operator = rs.resolveBinaryOperator(null, Tag.EQ, attrEnv, argType, argType);
        testExpr.setType(syms.booleanType);
        return testExpr;
    }

    private JCExpression deserTest(JCExpression prev, String func, String lit) {
        MethodType eqmt = new MethodType(List.of(syms.objectType), syms.booleanType, List.nil(), syms.methodClass);
        Symbol eqsym = rs.resolveQualifiedMethod(null, attrEnv, syms.objectType, names.equals, List.of(syms.objectType), List.nil());
        JCMethodInvocation eqtest = make.Apply(
                List.nil(),
                make.Select(deserGetter(func, syms.stringType), eqsym).setType(eqmt),
                List.of(make.Literal(lit)));
        eqtest.setType(syms.booleanType);
        JCBinary compound = make.Binary(Tag.AND, prev, eqtest);
        compound.operator = rs.resolveBinaryOperator(null, Tag.AND, attrEnv, syms.booleanType, syms.booleanType);
        compound.setType(syms.booleanType);
        return compound;
    }

    private JCExpression deserGetter(String func, Type type) {
        return deserGetter(func, type, List.nil(), List.nil());
    }

    private JCExpression deserGetter(String func, Type type, List<Type> argTypes, List<JCExpression> args) {
        MethodType getmt = new MethodType(argTypes, type, List.nil(), syms.methodClass);
        Symbol getsym = rs.resolveQualifiedMethod(null, attrEnv, syms.serializedLambdaType, names.fromString(func), argTypes, List.nil());
        return make.Apply(
                List.nil(),
                make.Select(make.Ident(kInfo.deserParamSym).setType(syms.serializedLambdaType), getsym).setType(getmt),
                args).setType(type);
    }

    private MethodSymbol makePrivateSyntheticMethod(long flags, Name name, Type type, Symbol owner) {
        return new MethodSymbol(flags | SYNTHETIC | PRIVATE, name, type, owner);
    }

    private VarSymbol makeSyntheticVar(long flags, String name, Type type, Symbol owner) {
        return makeSyntheticVar(flags, names.fromString(name), type, owner);
    }

    private VarSymbol makeSyntheticVar(long flags, Name name, Type type, Symbol owner) {
        return new VarSymbol(flags | SYNTHETIC, name, type, owner);
    }

    private void setVarargsIfNeeded(JCTree tree, Type varargsElement) {
        if (varargsElement != null) {
            switch (tree.getTag()) {
                case APPLY:
                    ((JCMethodInvocation) tree).varargsElement = varargsElement;
                    break;
                case NEWCLASS:
                    ((JCNewClass) tree).varargsElement = varargsElement;
                    break;
                default:
                    throw new AssertionError();
            }
        }
    }

    private List<JCExpression> convertArgs(Symbol meth, List<JCExpression> args, Type varargsElement) {
        Assert.check(meth.kind == Kinds.MTH);
        List<Type> formals = types.erasure(meth.type).getParameterTypes();
        if (varargsElement != null) {
            Assert.check((meth.flags() & VARARGS) != 0);
        }
        return transTypes.translateArgs(args, formals, varargsElement, attrEnv);
    }

    private void bridgeMemberReference(JCMemberReference tree, ReferenceTranslationContext localContext) {
        kInfo.addMethod(new MemberReferenceBridger(tree, localContext).bridge());
    }

    private MethodType typeToMethodType(Type mt) {
        Type type = types.erasure(mt);
        return new MethodType(type.getParameterTypes(),
                type.getReturnType(),
                type.getThrownTypes(),
                syms.methodClass);
    }

    private JCExpression makeMetafactoryIndyCall(TranslationContext<?> context,
                                                 int refKind, Symbol refSym, List<JCExpression> indy_args) {
        JCFunctionalExpression tree = context.tree;

        MethodSymbol samSym = (MethodSymbol) types.findDescriptorSymbol(tree.type.tsym);
        List<Object> staticArgs = List.of(
                typeToMethodType(samSym.type),
                new Pool.MethodHandle(refKind, refSym, types),
                typeToMethodType(tree.getDescriptorType(types)));

        ListBuffer<Type> indy_args_types = new ListBuffer<>();
        for (JCExpression arg : indy_args) {
            indy_args_types.append(arg.type);
        }

        MethodType indyType = new MethodType(indy_args_types.toList(),
                tree.type,
                List.nil(),
                syms.methodClass);
        Name metafactoryName = context.needsAltMetafactory() ?
                names.altMetafactory : names.metafactory;
        if (context.needsAltMetafactory()) {
            ListBuffer<Object> markers = new ListBuffer<>();
            for (Type t : tree.targets.tail) {
                if (t.tsym != syms.serializableType.tsym) {
                    markers.append(t.tsym);
                }
            }
            int flags = context.isSerializable() ? FLAG_SERIALIZABLE : 0;
            boolean hasMarkers = markers.nonEmpty();
            boolean hasBridges = context.bridges.nonEmpty();
            if (hasMarkers) {
                flags |= FLAG_MARKERS;
            }
            if (hasBridges) {
                flags |= FLAG_BRIDGES;
            }
            staticArgs = staticArgs.append(flags);
            if (hasMarkers) {
                staticArgs = staticArgs.append(markers.length());
                staticArgs = staticArgs.appendList(markers.toList());
            }
            if (hasBridges) {
                staticArgs = staticArgs.append(context.bridges.length() - 1);
                for (Symbol s : context.bridges) {
                    Type s_erasure = s.erasure(types);
                    if (!types.isSameType(s_erasure, samSym.erasure(types))) {
                        staticArgs = staticArgs.append(s.erasure(types));
                    }
                }
            }
            if (context.isSerializable()) {
                int prevPos = make.pos;
                try {
                    make.at(kInfo.clazz);
                    addDeserializationCase(refKind, refSym, tree.type, samSym,
                            tree, staticArgs, indyType);
                } finally {
                    make.at(prevPos);
                }
            }
        }
        return makeIndyCall(tree, syms.lambdaMetafactory, metafactoryName, staticArgs, indyType, indy_args, samSym.name);
    }

    private JCExpression makeIndyCall(DiagnosticPosition pos, Type site, Name bsmName,
                                      List<Object> staticArgs, MethodType indyType, List<JCExpression> indyArgs,
                                      Name methName) {
        int prevPos = make.pos;
        try {
            make.at(pos);
            List<Type> bsm_staticArgs = List.of(syms.methodHandleLookupType,
                    syms.stringType,
                    syms.methodTypeType).appendList(bsmStaticArgToTypes(staticArgs));
            Symbol bsm = rs.resolveInternalMethod(pos, attrEnv, site,
                    bsmName, bsm_staticArgs, List.nil());
            DynamicMethodSymbol dynSym =
                    new DynamicMethodSymbol(methName,
                            syms.noSymbol,
                            bsm.isStatic() ?
                                    ClassFile.REF_invokeStatic :
                                    ClassFile.REF_invokeVirtual,
                            (MethodSymbol) bsm,
                            indyType,
                            staticArgs.toArray());
            JCFieldAccess qualifier = make.Select(make.QualIdent(site.tsym), bsmName);
            qualifier.sym = dynSym;
            qualifier.type = indyType.getReturnType();
            JCMethodInvocation proxyCall = make.Apply(List.nil(), qualifier, indyArgs);
            proxyCall.type = indyType.getReturnType();
            return proxyCall;
        } finally {
            make.at(prevPos);
        }
    }

    private List<Type> bsmStaticArgToTypes(List<Object> args) {
        ListBuffer<Type> argtypes = new ListBuffer<>();
        for (Object arg : args) {
            argtypes.append(bsmStaticArgToType(arg));
        }
        return argtypes.toList();
    }

    private Type bsmStaticArgToType(Object arg) {
        Assert.checkNonNull(arg);
        if (arg instanceof ClassSymbol) {
            return syms.classType;
        } else if (arg instanceof Integer) {
            return syms.intType;
        } else if (arg instanceof Long) {
            return syms.longType;
        } else if (arg instanceof Float) {
            return syms.floatType;
        } else if (arg instanceof Double) {
            return syms.doubleType;
        } else if (arg instanceof String) {
            return syms.stringType;
        } else if (arg instanceof Pool.MethodHandle) {
            return syms.methodHandleType;
        } else if (arg instanceof MethodType) {
            return syms.methodTypeType;
        } else {
            Assert.error("bad static arg " + arg.getClass());
            return null;
        }
    }

    private int referenceKind(Symbol refSym) {
        if (refSym.isConstructor()) {
            return ClassFile.REF_newInvokeSpecial;
        } else {
            if (refSym.isStatic()) {
                return ClassFile.REF_invokeStatic;
            } else if ((refSym.flags() & PRIVATE) != 0) {
                return ClassFile.REF_invokeSpecial;
            } else if (refSym.enclClass().isInterface()) {
                return ClassFile.REF_invokeInterface;
            } else {
                return ClassFile.REF_invokeVirtual;
            }
        }
    }

    private String typeSig(Type type) {
        L2MSignatureGenerator sg = new L2MSignatureGenerator();
        sg.assembleSig(type);
        return sg.toString();
    }

    private String classSig(Type type) {
        L2MSignatureGenerator sg = new L2MSignatureGenerator();
        sg.assembleClassSig(type);
        return sg.toString();
    }


    enum LambdaSymbolKind {
        PARAM,
        LOCAL_VAR,
        CAPTURED_VAR,
        CAPTURED_THIS,
        TYPE_VAR
    }

    private class KlassInfo {

        private final Map<String, ListBuffer<JCStatement>> deserializeCases;
        private final MethodSymbol deserMethodSym;
        private final VarSymbol deserParamSym;
        private final JCClassDecl clazz;
        private ListBuffer<JCTree> appendedMethodList;

        private KlassInfo(JCClassDecl clazz) {
            this.clazz = clazz;
            appendedMethodList = new ListBuffer<>();
            deserializeCases = new HashMap<String, ListBuffer<JCStatement>>();
            MethodType type = new MethodType(List.of(syms.serializedLambdaType), syms.objectType,
                    List.nil(), syms.methodClass);
            deserMethodSym = makePrivateSyntheticMethod(STATIC, names.deserializeLambda, type, clazz.sym);
            deserParamSym = new VarSymbol(FINAL, names.fromString("lambda"),
                    syms.serializedLambdaType, deserMethodSym);
        }

        private void addMethod(JCTree decl) {
            appendedMethodList = appendedMethodList.prepend(decl);
        }
    }

    private class MemberReferenceBridger {
        private final JCMemberReference tree;
        private final ReferenceTranslationContext localContext;
        private final ListBuffer<JCExpression> args = new ListBuffer<>();
        private final ListBuffer<JCVariableDecl> params = new ListBuffer<>();

        MemberReferenceBridger(JCMemberReference tree, ReferenceTranslationContext localContext) {
            this.tree = tree;
            this.localContext = localContext;
        }

        JCMethodDecl bridge() {
            int prevPos = make.pos;
            try {
                make.at(tree);
                Type samDesc = localContext.bridgedRefSig();
                List<Type> samPTypes = samDesc.getParameterTypes();


                Type recType = null;
                switch (tree.kind) {
                    case IMPLICIT_INNER:
                        recType = tree.sym.owner.type.getEnclosingType();
                        break;
                    case BOUND:
                        recType = tree.getQualifierExpression().type;
                        break;
                    case UNBOUND:
                        recType = samPTypes.head;
                        samPTypes = samPTypes.tail;
                        break;
                }


                VarSymbol rcvr = (recType == null)
                        ? null
                        : addParameter("rec$", recType, false);
                List<Type> refPTypes = tree.sym.type.getParameterTypes();
                int refSize = refPTypes.size();
                int samSize = samPTypes.size();

                int last = localContext.needsVarArgsConversion() ? refSize - 1 : refSize;
                List<Type> l = refPTypes;

                for (int i = 0; l.nonEmpty() && i < last; ++i) {
                    addParameter("x$" + i, l.head, true);
                    l = l.tail;
                }

                for (int i = last; i < samSize; ++i) {
                    addParameter("xva$" + i, tree.varargsElement, true);
                }

                JCMethodDecl bridgeDecl = make.MethodDef(make.Modifiers(localContext.bridgeSym.flags()),
                        localContext.bridgeSym.name,
                        make.QualIdent(samDesc.getReturnType().tsym),
                        List.nil(),
                        params.toList(),
                        tree.sym.type.getThrownTypes() == null
                                ? List.nil()
                                : make.Types(tree.sym.type.getThrownTypes()),
                        null,
                        null);
                bridgeDecl.sym = (MethodSymbol) localContext.bridgeSym;
                bridgeDecl.type = localContext.bridgeSym.type =
                        types.createMethodTypeWithParameters(samDesc, TreeInfo.types(params.toList()));


                JCExpression bridgeExpr = (tree.getMode() == ReferenceMode.INVOKE)
                        ? bridgeExpressionInvoke(makeReceiver(rcvr))
                        : bridgeExpressionNew();


                bridgeDecl.body = makeLambdaExpressionBody(bridgeExpr, bridgeDecl);
                return bridgeDecl;
            } finally {
                make.at(prevPos);
            }
        }

        private JCExpression makeReceiver(VarSymbol rcvr) {
            if (rcvr == null) return null;
            JCExpression rcvrExpr = make.Ident(rcvr);
            Type rcvrType = tree.sym.enclClass().type;
            if (!rcvr.type.tsym.isSubClass(rcvrType.tsym, types)) {
                rcvrExpr = make.TypeCast(make.Type(rcvrType), rcvrExpr).setType(rcvrType);
            }
            return rcvrExpr;
        }

        private JCExpression bridgeExpressionInvoke(JCExpression rcvr) {
            JCExpression qualifier =
                    tree.sym.isStatic() ?
                            make.Type(tree.sym.owner.type) :
                            (rcvr != null) ?
                                    rcvr :
                                    tree.getQualifierExpression();

            JCFieldAccess select = make.Select(qualifier, tree.sym.name);
            select.sym = tree.sym;
            select.type = tree.sym.erasure(types);

            JCExpression apply = make.Apply(List.nil(), select,
                    convertArgs(tree.sym, args.toList(), tree.varargsElement)).
                    setType(tree.sym.erasure(types).getReturnType());
            apply = transTypes.coerce(apply, localContext.generatedRefSig().getReturnType());
            setVarargsIfNeeded(apply, tree.varargsElement);
            return apply;
        }

        private JCExpression bridgeExpressionNew() {
            if (tree.kind == ReferenceKind.ARRAY_CTOR) {

                JCNewArray newArr = make.NewArray(
                        make.Type(types.elemtype(tree.getQualifierExpression().type)),
                        List.of(make.Ident(params.first())),
                        null);
                newArr.type = tree.getQualifierExpression().type;
                return newArr;
            } else {
                JCExpression encl = null;
                switch (tree.kind) {
                    case UNBOUND:
                    case IMPLICIT_INNER:
                        encl = make.Ident(params.first());
                }

                JCNewClass newClass = make.NewClass(encl,
                        List.nil(),
                        make.Type(tree.getQualifierExpression().type),
                        convertArgs(tree.sym, args.toList(), tree.varargsElement),
                        null);
                newClass.constructor = tree.sym;
                newClass.constructorType = tree.sym.erasure(types);
                newClass.type = tree.getQualifierExpression().type;
                setVarargsIfNeeded(newClass, tree.varargsElement);
                return newClass;
            }
        }

        private VarSymbol addParameter(String name, Type p, boolean genArg) {
            VarSymbol vsym = new VarSymbol(0, names.fromString(name), p, localContext.bridgeSym);
            params.append(make.VarDef(vsym, null));
            if (genArg) {
                args.append(make.Ident(vsym));
            }
            return vsym;
        }
    }

    class LambdaAnalyzerPreprocessor extends TreeTranslator {

        private List<Frame> frameStack;

        private int lambdaCount = 0;
        private SyntheticMethodNameCounter syntheticMethodNameCounts =
                new SyntheticMethodNameCounter();
        private Map<Symbol, JCClassDecl> localClassDefs;
        private Map<ClassSymbol, Symbol> clinits =
                new HashMap<ClassSymbol, Symbol>();

        private JCClassDecl analyzeAndPreprocessClass(JCClassDecl tree) {
            frameStack = List.nil();
            localClassDefs = new HashMap<Symbol, JCClassDecl>();
            return translate(tree);
        }

        @Override
        public void visitBlock(JCBlock tree) {
            List<Frame> prevStack = frameStack;
            try {
                if (frameStack.nonEmpty() && frameStack.head.tree.hasTag(CLASSDEF)) {
                    frameStack = frameStack.prepend(new Frame(tree));
                }
                super.visitBlock(tree);
            } finally {
                frameStack = prevStack;
            }
        }

        @Override
        public void visitClassDef(JCClassDecl tree) {
            List<Frame> prevStack = frameStack;
            SyntheticMethodNameCounter prevSyntheticMethodNameCounts =
                    syntheticMethodNameCounts;
            Map<ClassSymbol, Symbol> prevClinits = clinits;
            DiagnosticSource prevSource = log.currentSource();
            try {
                log.useSource(tree.sym.sourcefile);
                syntheticMethodNameCounts = new SyntheticMethodNameCounter();
                prevClinits = new HashMap<ClassSymbol, Symbol>();
                if (tree.sym.owner.kind == MTH) {
                    localClassDefs.put(tree.sym, tree);
                }
                if (directlyEnclosingLambda() != null) {
                    tree.sym.owner = owner();
                    if (tree.sym.hasOuterInstance()) {


                        TranslationContext<?> localContext = context();
                        while (localContext != null) {
                            if (localContext.tree.getTag() == LAMBDA) {
                                ((LambdaTranslationContext) localContext)
                                        .addSymbol(tree.sym.type.getEnclosingType().tsym, CAPTURED_THIS);
                            }
                            localContext = localContext.prev;
                        }
                    }
                }
                frameStack = frameStack.prepend(new Frame(tree));
                super.visitClassDef(tree);
            } finally {
                log.useSource(prevSource.getFile());
                frameStack = prevStack;
                syntheticMethodNameCounts = prevSyntheticMethodNameCounts;
                clinits = prevClinits;
            }
        }

        @Override
        public void visitIdent(JCIdent tree) {
            if (context() != null && lambdaIdentSymbolFilter(tree.sym)) {
                if (tree.sym.kind == VAR &&
                        tree.sym.owner.kind == MTH &&
                        tree.type.constValue() == null) {
                    TranslationContext<?> localContext = context();
                    while (localContext != null) {
                        if (localContext.tree.getTag() == LAMBDA) {
                            JCTree block = capturedDecl(localContext.depth, tree.sym);
                            if (block == null) break;
                            ((LambdaTranslationContext) localContext)
                                    .addSymbol(tree.sym, CAPTURED_VAR);
                        }
                        localContext = localContext.prev;
                    }
                } else if (tree.sym.owner.kind == TYP) {
                    TranslationContext<?> localContext = context();
                    while (localContext != null) {
                        if (localContext.tree.hasTag(LAMBDA)) {
                            JCTree block = capturedDecl(localContext.depth, tree.sym);
                            if (block == null) break;
                            switch (block.getTag()) {
                                case CLASSDEF:
                                    JCClassDecl cdecl = (JCClassDecl) block;
                                    ((LambdaTranslationContext) localContext)
                                            .addSymbol(cdecl.sym, CAPTURED_THIS);
                                    break;
                                default:
                                    Assert.error("bad block kind");
                            }
                        }
                        localContext = localContext.prev;
                    }
                }
            }
            super.visitIdent(tree);
        }

        @Override
        public void visitLambda(JCLambda tree) {
            List<Frame> prevStack = frameStack;
            try {
                LambdaTranslationContext context = (LambdaTranslationContext) makeLambdaContext(tree);
                frameStack = frameStack.prepend(new Frame(tree));
                for (JCVariableDecl param : tree.params) {
                    context.addSymbol(param.sym, PARAM);
                    frameStack.head.addLocal(param.sym);
                }
                contextMap.put(tree, context);
                super.visitLambda(tree);
                context.complete();
            } finally {
                frameStack = prevStack;
            }
        }

        @Override
        public void visitMethodDef(JCMethodDecl tree) {
            List<Frame> prevStack = frameStack;
            try {
                frameStack = frameStack.prepend(new Frame(tree));
                super.visitMethodDef(tree);
            } finally {
                frameStack = prevStack;
            }
        }

        @Override
        public void visitNewClass(JCNewClass tree) {
            if (lambdaNewClassFilter(context(), tree)) {
                TranslationContext<?> localContext = context();
                while (localContext != null) {
                    if (localContext.tree.getTag() == LAMBDA) {
                        ((LambdaTranslationContext) localContext)
                                .addSymbol(tree.type.getEnclosingType().tsym, CAPTURED_THIS);
                    }
                    localContext = localContext.prev;
                }
            }
            if (context() != null && tree.type.tsym.owner.kind == MTH) {
                LambdaTranslationContext lambdaContext = (LambdaTranslationContext) context();
                captureLocalClassDefs(tree.type.tsym, lambdaContext);
            }
            super.visitNewClass(tree);
        }

        void captureLocalClassDefs(Symbol csym, final LambdaTranslationContext lambdaContext) {
            JCClassDecl localCDef = localClassDefs.get(csym);
            if (localCDef != null && localCDef.pos < lambdaContext.tree.pos) {
                BasicFreeVarCollector fvc = lower.new BasicFreeVarCollector() {
                    @Override
                    void addFreeVars(ClassSymbol c) {
                        captureLocalClassDefs(c, lambdaContext);
                    }

                    @Override
                    void visitSymbol(Symbol sym) {
                        if (sym.kind == VAR &&
                                sym.owner.kind == MTH &&
                                ((VarSymbol) sym).getConstValue() == null) {
                            TranslationContext<?> localContext = context();
                            while (localContext != null) {
                                if (localContext.tree.getTag() == LAMBDA) {
                                    JCTree block = capturedDecl(localContext.depth, sym);
                                    if (block == null) break;
                                    ((LambdaTranslationContext) localContext).addSymbol(sym, CAPTURED_VAR);
                                }
                                localContext = localContext.prev;
                            }
                        }
                    }
                };
                fvc.scan(localCDef);
            }
        }

        @Override
        public void visitReference(JCMemberReference tree) {
            if (tree.getMode() == ReferenceMode.NEW
                    && tree.kind != ReferenceKind.ARRAY_CTOR
                    && tree.sym.owner.isLocal()) {
                MethodSymbol consSym = (MethodSymbol) tree.sym;
                List<Type> ptypes = consSym.type.getParameterTypes();
                Type classType = consSym.owner.type;


                Symbol owner = owner();
                ListBuffer<JCVariableDecl> paramBuff = new ListBuffer<JCVariableDecl>();
                int i = 0;
                for (List<Type> l = ptypes; l.nonEmpty(); l = l.tail) {
                    JCVariableDecl param = make.Param(make.paramName(i++), l.head, owner);
                    param.sym.pos = tree.pos;
                    paramBuff.append(param);
                }
                List<JCVariableDecl> params = paramBuff.toList();

                JCNewClass nc = makeNewClass(classType, make.Idents(params));
                nc.pos = tree.pos;

                JCLambda slam = make.Lambda(params, nc);
                slam.targets = tree.targets;
                slam.type = tree.type;
                slam.pos = tree.pos;

                visitLambda(slam);
            } else {
                super.visitReference(tree);
                contextMap.put(tree, makeReferenceContext(tree));
            }
        }

        @Override
        public void visitSelect(JCFieldAccess tree) {
            if (context() != null && tree.sym.kind == VAR &&
                    (tree.sym.name == names._this ||
                            tree.sym.name == names._super)) {


                TranslationContext<?> localContext = context();
                while (localContext != null) {
                    if (localContext.tree.hasTag(LAMBDA)) {
                        JCClassDecl clazz = (JCClassDecl) capturedDecl(localContext.depth, tree.sym);
                        if (clazz == null) break;
                        ((LambdaTranslationContext) localContext).addSymbol(clazz.sym, CAPTURED_THIS);
                    }
                    localContext = localContext.prev;
                }
            }
            super.visitSelect(tree);
        }

        @Override
        public void visitVarDef(JCVariableDecl tree) {
            TranslationContext<?> context = context();
            LambdaTranslationContext ltc = (context != null && context instanceof LambdaTranslationContext) ?
                    (LambdaTranslationContext) context :
                    null;
            if (ltc != null) {
                if (frameStack.head.tree.hasTag(LAMBDA)) {
                    ltc.addSymbol(tree.sym, LOCAL_VAR);
                }


                Type type = tree.sym.asType();
                if (inClassWithinLambda() && !types.isSameType(types.erasure(type), type)) {
                    ltc.addSymbol(tree.sym, TYPE_VAR);
                }
            }
            List<Frame> prevStack = frameStack;
            try {
                if (tree.sym.owner.kind == MTH) {
                    frameStack.head.addLocal(tree.sym);
                }
                frameStack = frameStack.prepend(new Frame(tree));
                super.visitVarDef(tree);
            } finally {
                frameStack = prevStack;
            }
        }

        private Symbol owner() {
            return owner(false);
        }

        @SuppressWarnings("fallthrough")
        private Symbol owner(boolean skipLambda) {
            List<Frame> frameStack2 = frameStack;
            while (frameStack2.nonEmpty()) {
                switch (frameStack2.head.tree.getTag()) {
                    case VARDEF:
                        if (((JCVariableDecl) frameStack2.head.tree).sym.isLocal()) {
                            frameStack2 = frameStack2.tail;
                            break;
                        }
                        JCClassDecl cdecl = (JCClassDecl) frameStack2.tail.head.tree;
                        return initSym(cdecl.sym,
                                ((JCVariableDecl) frameStack2.head.tree).sym.flags() & STATIC);
                    case BLOCK:
                        JCClassDecl cdecl2 = (JCClassDecl) frameStack2.tail.head.tree;
                        return initSym(cdecl2.sym,
                                ((JCBlock) frameStack2.head.tree).flags & STATIC);
                    case CLASSDEF:
                        return ((JCClassDecl) frameStack2.head.tree).sym;
                    case METHODDEF:
                        return ((JCMethodDecl) frameStack2.head.tree).sym;
                    case LAMBDA:
                        if (!skipLambda)
                            return ((LambdaTranslationContext) contextMap
                                    .get(frameStack2.head.tree)).translatedSym;
                    default:
                        frameStack2 = frameStack2.tail;
                }
            }
            Assert.error();
            return null;
        }

        private Symbol initSym(ClassSymbol csym, long flags) {
            boolean isStatic = (flags & STATIC) != 0;
            if (isStatic) {

                MethodSymbol clinit = attr.removeClinit(csym);
                if (clinit != null) {
                    clinits.put(csym, clinit);
                    return clinit;
                }

                clinit = (MethodSymbol) clinits.get(csym);
                if (clinit == null) {

                    clinit = makePrivateSyntheticMethod(STATIC,
                            names.clinit,
                            new MethodType(List.nil(), syms.voidType,
                                    List.nil(), syms.methodClass),
                            csym);
                    clinits.put(csym, clinit);
                }
                return clinit;
            } else {

                for (Symbol s : csym.members_field.getElementsByName(names.init)) {
                    return s;
                }
            }
            Assert.error("init not found");
            return null;
        }

        private JCTree directlyEnclosingLambda() {
            if (frameStack.isEmpty()) {
                return null;
            }
            List<Frame> frameStack2 = frameStack;
            while (frameStack2.nonEmpty()) {
                switch (frameStack2.head.tree.getTag()) {
                    case CLASSDEF:
                    case METHODDEF:
                        return null;
                    case LAMBDA:
                        return frameStack2.head.tree;
                    default:
                        frameStack2 = frameStack2.tail;
                }
            }
            Assert.error();
            return null;
        }

        private boolean inClassWithinLambda() {
            if (frameStack.isEmpty()) {
                return false;
            }
            List<Frame> frameStack2 = frameStack;
            boolean classFound = false;
            while (frameStack2.nonEmpty()) {
                switch (frameStack2.head.tree.getTag()) {
                    case LAMBDA:
                        return classFound;
                    case CLASSDEF:
                        classFound = true;
                        frameStack2 = frameStack2.tail;
                        break;
                    default:
                        frameStack2 = frameStack2.tail;
                }
            }

            return false;
        }

        private JCTree capturedDecl(int depth, Symbol sym) {
            int currentDepth = frameStack.size() - 1;
            for (Frame block : frameStack) {
                switch (block.tree.getTag()) {
                    case CLASSDEF:
                        ClassSymbol clazz = ((JCClassDecl) block.tree).sym;
                        if (sym.isMemberOf(clazz, types)) {
                            return currentDepth > depth ? null : block.tree;
                        }
                        break;
                    case VARDEF:
                        if (((JCVariableDecl) block.tree).sym == sym &&
                                sym.owner.kind == MTH) {
                            return currentDepth > depth ? null : block.tree;
                        }
                        break;
                    case BLOCK:
                    case METHODDEF:
                    case LAMBDA:
                        if (block.locals != null && block.locals.contains(sym)) {
                            return currentDepth > depth ? null : block.tree;
                        }
                        break;
                    default:
                        Assert.error("bad decl kind " + block.tree.getTag());
                }
                currentDepth--;
            }
            return null;
        }

        private TranslationContext<?> context() {
            for (Frame frame : frameStack) {
                TranslationContext<?> context = contextMap.get(frame.tree);
                if (context != null) {
                    return context;
                }
            }
            return null;
        }

        private boolean lambdaIdentSymbolFilter(Symbol sym) {
            return (sym.kind == VAR || sym.kind == MTH)
                    && !sym.isStatic()
                    && sym.name != names.init;
        }

        private boolean lambdaNewClassFilter(TranslationContext<?> context, JCNewClass tree) {
            if (context != null
                    && tree.encl == null
                    && tree.def == null
                    && !tree.type.getEnclosingType().hasTag(NONE)) {
                Type encl = tree.type.getEnclosingType();
                Type current = context.owner.enclClass().type;
                while (!current.hasTag(NONE)) {
                    if (current.tsym.isSubClass(encl.tsym, types)) {
                        return true;
                    }
                    current = current.getEnclosingType();
                }
                return false;
            } else {
                return false;
            }
        }

        private TranslationContext<JCLambda> makeLambdaContext(JCLambda tree) {
            return new LambdaTranslationContext(tree);
        }

        private TranslationContext<JCMemberReference> makeReferenceContext(JCMemberReference tree) {
            return new ReferenceTranslationContext(tree);
        }

        private class SyntheticMethodNameCounter {
            private Map<String, Integer> map = new HashMap<>();

            int getIndex(StringBuilder buf) {
                String temp = buf.toString();
                Integer count = map.get(temp);
                if (count == null) {
                    count = 0;
                }
                ++count;
                map.put(temp, count);
                return count;
            }
        }

        private class Frame {
            final JCTree tree;
            List<Symbol> locals;

            public Frame(JCTree tree) {
                this.tree = tree;
            }

            void addLocal(Symbol sym) {
                if (locals == null) {
                    locals = List.nil();
                }
                locals = locals.prepend(sym);
            }
        }

        private abstract class TranslationContext<T extends JCFunctionalExpression> {

            final T tree;

            final Symbol owner;

            final int depth;

            final TranslationContext<?> prev;

            final List<Symbol> bridges;

            TranslationContext(T tree) {
                this.tree = tree;
                this.owner = owner();
                this.depth = frameStack.size() - 1;
                this.prev = context();
                ClassSymbol csym =
                        types.makeFunctionalInterfaceClass(attrEnv, names.empty, tree.targets, ABSTRACT | INTERFACE);
                this.bridges = types.functionalInterfaceBridges(csym);
            }

            boolean needsAltMetafactory() {
                return tree.targets.length() > 1 ||
                        isSerializable() ||
                        bridges.length() > 1;
            }

            boolean isSerializable() {
                for (Type target : tree.targets) {
                    if (types.asSuper(target, syms.serializableType.tsym) != null) {
                        return true;
                    }
                }
                return false;
            }

            String enclosingMethodName() {
                return syntheticMethodNameComponent(owner.name);
            }

            String syntheticMethodNameComponent(Name name) {
                if (name == null) {
                    return "null";
                }
                String methodName = name.toString();
                if (methodName.equals("<clinit>")) {
                    methodName = "static";
                } else if (methodName.equals("<init>")) {
                    methodName = "new";
                }
                return methodName;
            }
        }

        private class LambdaTranslationContext extends TranslationContext<JCLambda> {

            final Symbol self;

            final Symbol assignedTo;
            Map<LambdaSymbolKind, Map<Symbol, Symbol>> translatedSymbols;

            Symbol translatedSym;
            List<JCVariableDecl> syntheticParams;

            LambdaTranslationContext(JCLambda tree) {
                super(tree);
                Frame frame = frameStack.head;
                switch (frame.tree.getTag()) {
                    case VARDEF:
                        assignedTo = self = ((JCVariableDecl) frame.tree).sym;
                        break;
                    case ASSIGN:
                        self = null;
                        assignedTo = TreeInfo.symbol(((JCAssign) frame.tree).getVariable());
                        break;
                    default:
                        assignedTo = self = null;
                        break;
                }

                this.translatedSym = makePrivateSyntheticMethod(0, null, null, owner.enclClass());
                if (dumpLambdaToMethodStats) {
                    log.note(tree, "lambda.stat", needsAltMetafactory(), translatedSym);
                }
                translatedSymbols = new EnumMap<>(LambdaSymbolKind.class);
                translatedSymbols.put(PARAM, new LinkedHashMap<Symbol, Symbol>());
                translatedSymbols.put(LOCAL_VAR, new LinkedHashMap<Symbol, Symbol>());
                translatedSymbols.put(CAPTURED_VAR, new LinkedHashMap<Symbol, Symbol>());
                translatedSymbols.put(CAPTURED_THIS, new LinkedHashMap<Symbol, Symbol>());
                translatedSymbols.put(TYPE_VAR, new LinkedHashMap<Symbol, Symbol>());
            }

            private String serializedLambdaDisambiguation() {
                StringBuilder buf = new StringBuilder();


                Assert.check(
                        owner.type != null ||
                                directlyEnclosingLambda() != null);
                if (owner.type != null) {
                    buf.append(typeSig(owner.type));
                    buf.append(":");
                }

                buf.append(types.findDescriptorSymbol(tree.type.tsym).owner.flatName());
                buf.append(" ");

                if (assignedTo != null) {
                    buf.append(assignedTo.flatName());
                    buf.append("=");
                }

                for (Symbol fv : getSymbolMap(CAPTURED_VAR).keySet()) {
                    if (fv != self) {
                        buf.append(typeSig(fv.type));
                        buf.append(" ");
                        buf.append(fv.flatName());
                        buf.append(",");
                    }
                }
                return buf.toString();
            }

            private Name lambdaName() {
                return names.lambda.append(names.fromString(enclosingMethodName() + "$" + lambdaCount++));
            }

            private Name serializedLambdaName() {
                StringBuilder buf = new StringBuilder();
                buf.append(names.lambda);

                buf.append(enclosingMethodName());
                buf.append('$');


                String disam = serializedLambdaDisambiguation();
                buf.append(Integer.toHexString(disam.hashCode()));
                buf.append('$');


                buf.append(syntheticMethodNameCounts.getIndex(buf));
                String result = buf.toString();

                return names.fromString(result);
            }

            Symbol translate(Name name, final Symbol sym, LambdaSymbolKind skind) {
                Symbol ret;
                switch (skind) {
                    case CAPTURED_THIS:
                        ret = sym;
                        break;
                    case TYPE_VAR:

                        ret = new VarSymbol(sym.flags(), name,
                                types.erasure(sym.type), sym.owner);

                        ((VarSymbol) ret).pos = ((VarSymbol) sym).pos;
                        break;
                    case CAPTURED_VAR:
                        ret = new VarSymbol(SYNTHETIC | FINAL | PARAMETER, name, types.erasure(sym.type), translatedSym) {
                            @Override
                            public Symbol baseSymbol() {

                                return sym;
                            }
                        };
                        break;
                    case LOCAL_VAR:
                        ret = new VarSymbol(FINAL, name, types.erasure(sym.type), translatedSym);
                        ((VarSymbol) ret).pos = ((VarSymbol) sym).pos;
                        break;
                    case PARAM:
                        ret = new VarSymbol(FINAL | PARAMETER, name, types.erasure(sym.type), translatedSym);
                        ((VarSymbol) ret).pos = ((VarSymbol) sym).pos;
                        break;
                    default:
                        ret = makeSyntheticVar(FINAL, name, types.erasure(sym.type), translatedSym);
                        ((VarSymbol) ret).pos = ((VarSymbol) sym).pos;
                }
                if (ret != sym) {
                    ret.setDeclarationAttributes(sym.getRawAttributes());
                    ret.setTypeAttributes(sym.getRawTypeAttributes());
                }
                return ret;
            }

            void addSymbol(Symbol sym, LambdaSymbolKind skind) {
                Map<Symbol, Symbol> transMap = getSymbolMap(skind);
                Name preferredName;
                switch (skind) {
                    case CAPTURED_THIS:
                        preferredName = names.fromString("encl$" + transMap.size());
                        break;
                    case CAPTURED_VAR:
                        preferredName = names.fromString("cap$" + transMap.size());
                        break;
                    case LOCAL_VAR:
                        preferredName = sym.name;
                        break;
                    case PARAM:
                        preferredName = sym.name;
                        break;
                    case TYPE_VAR:
                        preferredName = sym.name;
                        break;
                    default:
                        throw new AssertionError();
                }
                if (!transMap.containsKey(sym)) {
                    transMap.put(sym, translate(preferredName, sym, skind));
                }
            }

            Map<Symbol, Symbol> getSymbolMap(LambdaSymbolKind skind) {
                Map<Symbol, Symbol> m = translatedSymbols.get(skind);
                Assert.checkNonNull(m);
                return m;
            }

            JCTree translate(JCIdent lambdaIdent) {
                for (Map<Symbol, Symbol> m : translatedSymbols.values()) {
                    if (m.containsKey(lambdaIdent.sym)) {
                        Symbol tSym = m.get(lambdaIdent.sym);
                        JCTree t = make.Ident(tSym).setType(lambdaIdent.type);
                        tSym.setTypeAttributes(lambdaIdent.sym.getRawTypeAttributes());
                        return t;
                    }
                }
                return null;
            }

            void complete() {
                if (syntheticParams != null) {
                    return;
                }
                boolean inInterface = translatedSym.owner.isInterface();
                boolean thisReferenced = !getSymbolMap(CAPTURED_THIS).isEmpty();


                translatedSym.flags_field = SYNTHETIC | LAMBDA_METHOD |
                        PRIVATE |
                        (thisReferenced ? (inInterface ? DEFAULT : 0) : STATIC);

                ListBuffer<JCVariableDecl> params = new ListBuffer<>();


                for (Symbol thisSym : getSymbolMap(CAPTURED_VAR).values()) {
                    params.append(make.VarDef((VarSymbol) thisSym, null));
                }
                for (Symbol thisSym : getSymbolMap(PARAM).values()) {
                    params.append(make.VarDef((VarSymbol) thisSym, null));
                }
                syntheticParams = params.toList();

                translatedSym.name = isSerializable()
                        ? serializedLambdaName()
                        : lambdaName();

                translatedSym.type = types.createMethodTypeWithParameters(
                        generatedLambdaSig(),
                        TreeInfo.types(syntheticParams));
            }

            Type generatedLambdaSig() {
                return types.erasure(tree.getDescriptorType(types));
            }
        }

        private class ReferenceTranslationContext extends TranslationContext<JCMemberReference> {
            final boolean isSuper;
            final Symbol bridgeSym;
            final Symbol sigPolySym;

            ReferenceTranslationContext(JCMemberReference tree) {
                super(tree);
                this.isSuper = tree.hasKind(ReferenceKind.SUPER);
                this.bridgeSym = needsBridge()
                        ? makePrivateSyntheticMethod(isSuper ? 0 : STATIC,
                        referenceBridgeName(), null,
                        owner.enclClass())
                        : null;
                this.sigPolySym = isSignaturePolymorphic()
                        ? makePrivateSyntheticMethod(tree.sym.flags(),
                        tree.sym.name,
                        bridgedRefSig(),
                        tree.sym.enclClass())
                        : null;
                if (dumpLambdaToMethodStats) {
                    String key = bridgeSym == null ?
                            "mref.stat" : "mref.stat.1";
                    log.note(tree, key, needsAltMetafactory(), bridgeSym);
                }
            }

            int referenceKind() {
                return LambdaToMethod.this.referenceKind(needsBridge()
                        ? bridgeSym
                        : tree.sym);
            }

            boolean needsVarArgsConversion() {
                return tree.varargsElement != null;
            }

            private String referenceBridgeDisambiguation() {
                StringBuilder buf = new StringBuilder();


                if (owner.type != null) {
                    buf.append(typeSig(owner.type));
                    buf.append(":");
                }

                buf.append(classSig(tree.sym.owner.type));

                buf.append(tree.sym.isStatic() ? " S " : " I ");

                buf.append(typeSig(tree.sym.erasure(types)));
                return buf.toString();
            }

            private Name referenceBridgeName() {
                StringBuilder buf = new StringBuilder();

                buf.append(names.lambda);

                buf.append("MR$");

                buf.append(enclosingMethodName());
                buf.append('$');

                buf.append(syntheticMethodNameComponent(tree.sym.name));
                buf.append('$');


                String disam = referenceBridgeDisambiguation();
                buf.append(Integer.toHexString(disam.hashCode()));
                buf.append('$');


                buf.append(syntheticMethodNameCounts.getIndex(buf));
                String result = buf.toString();
                return names.fromString(result);
            }

            boolean isArrayOp() {
                return tree.sym.owner == syms.arrayClass;
            }

            boolean receiverAccessible() {


                return tree.ownerAccessible;
            }

            boolean isPrivateInOtherClass() {
                return (tree.sym.flags() & PRIVATE) != 0 &&
                        !types.isSameType(
                                types.erasure(tree.sym.enclClass().asType()),
                                types.erasure(owner.enclClass().asType()));
            }

            final boolean isSignaturePolymorphic() {
                return tree.sym.kind == MTH &&
                        types.isSignaturePolymorphic((MethodSymbol) tree.sym);
            }

            final boolean needsBridge() {
                return isSuper || needsVarArgsConversion() || isArrayOp() ||
                        isPrivateInOtherClass() ||
                        !receiverAccessible();
            }

            Type generatedRefSig() {
                return types.erasure(tree.sym.type);
            }

            Type bridgedRefSig() {
                return types.erasure(types.findDescriptorSymbol(tree.targets.head.tsym).type);
            }
        }
    }

    private class L2MSignatureGenerator extends Types.SignatureGenerator {

        StringBuilder sb = new StringBuilder();

        L2MSignatureGenerator() {
            super(types);
        }

        @Override
        protected void append(char ch) {
            sb.append(ch);
        }

        @Override
        protected void append(byte[] ba) {
            sb.append(new String(ba));
        }

        @Override
        protected void append(Name name) {
            sb.append(name.toString());
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }
}
