package com.sun.tools.javac.comp;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import java.util.HashMap;
import java.util.Map;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTag.*;
import static com.sun.tools.javac.comp.CompileStates.CompileState;

public class TransTypes extends TreeTranslator {

    protected static final Context.Key<TransTypes> transTypesKey =
            new Context.Key<TransTypes>();
    private static final String statePreviousToFlowAssertMsg =
            "The current compile state [%s] of class %s is previous to FLOW";
    private final Resolve resolve;
    private final boolean addBridges;
    private final CompileStates compileStates;
    Map<MethodSymbol, MethodSymbol> overridden;
    JCTree currentMethod = null;
    private Names names;
    private Log log;
    private Symtab syms;
    private TreeMaker make;
    private Enter enter;
    private boolean allowEnums;
    private boolean allowInterfaceBridges;
    private Types types;
    private Filter<Symbol> overrideBridgeFilter = new Filter<Symbol>() {
        public boolean accepts(Symbol s) {
            return (s.flags() & (SYNTHETIC | OVERRIDE_BRIDGE)) != SYNTHETIC;
        }
    };
    private Type pt;
    private Env<AttrContext> env;

    protected TransTypes(Context context) {
        context.put(transTypesKey, this);
        compileStates = CompileStates.instance(context);
        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        enter = Enter.instance(context);
        overridden = new HashMap<MethodSymbol, MethodSymbol>();
        Source source = Source.instance(context);
        allowEnums = source.allowEnums();
        addBridges = source.addBridges();
        allowInterfaceBridges = source.allowDefaultMethods();
        types = Types.instance(context);
        make = TreeMaker.instance(context);
        resolve = Resolve.instance(context);
    }

    public static TransTypes instance(Context context) {
        TransTypes instance = context.get(transTypesKey);
        if (instance == null)
            instance = new TransTypes(context);
        return instance;
    }

    JCExpression cast(JCExpression tree, Type target) {
        int oldpos = make.pos;
        make.at(tree.pos);
        if (!types.isSameType(tree.type, target)) {
            if (!resolve.isAccessible(env, target.tsym))
                resolve.logAccessErrorInternal(env, tree, target);
            tree = make.TypeCast(make.Type(target), tree).setType(target);
        }
        make.pos = oldpos;
        return tree;
    }

    public JCExpression coerce(Env<AttrContext> env, JCExpression tree, Type target) {
        Env<AttrContext> prevEnv = this.env;
        try {
            this.env = env;
            return coerce(tree, target);
        } finally {
            this.env = prevEnv;
        }
    }

    JCExpression coerce(JCExpression tree, Type target) {
        Type btarget = target.baseType();
        if (tree.type.isPrimitive() == target.isPrimitive()) {
            return types.isAssignable(tree.type, btarget, types.noWarnings)
                    ? tree
                    : cast(tree, btarget);
        }
        return tree;
    }

    JCExpression retype(JCExpression tree, Type erasedType, Type target) {
        if (!erasedType.isPrimitive()) {
            if (target != null && target.isPrimitive()) {
                target = erasure(tree.type);
            }
            tree.type = erasedType;
            if (target != null) {
                return coerce(tree, target);
            }
        }
        return tree;
    }

    <T extends JCTree> List<T> translateArgs(List<T> _args,
                                             List<Type> parameters,
                                             Type varargsElement) {
        if (parameters.isEmpty()) return _args;
        List<T> args = _args;
        while (parameters.tail.nonEmpty()) {
            args.head = translate(args.head, parameters.head);
            args = args.tail;
            parameters = parameters.tail;
        }
        Type parameter = parameters.head;
        Assert.check(varargsElement != null || args.length() == 1);
        if (varargsElement != null) {
            while (args.nonEmpty()) {
                args.head = translate(args.head, varargsElement);
                args = args.tail;
            }
        } else {
            args.head = translate(args.head, parameter);
        }
        return _args;
    }

    public <T extends JCTree> List<T> translateArgs(List<T> _args,
                                                    List<Type> parameters,
                                                    Type varargsElement,
                                                    Env<AttrContext> localEnv) {
        Env<AttrContext> prevEnv = env;
        try {
            env = localEnv;
            return translateArgs(_args, parameters, varargsElement);
        } finally {
            env = prevEnv;
        }
    }

    void addBridge(DiagnosticPosition pos,
                   MethodSymbol meth,
                   MethodSymbol impl,
                   ClassSymbol origin,
                   boolean hypothetical,
                   ListBuffer<JCTree> bridges) {
        make.at(pos);
        Type origType = types.memberType(origin.type, meth);
        Type origErasure = erasure(origType);

        Type bridgeType = meth.erasure(types);
        long flags = impl.flags() & AccessFlags | SYNTHETIC | BRIDGE |
                (origin.isInterface() ? DEFAULT : 0);
        if (hypothetical) flags |= HYPOTHETICAL;
        MethodSymbol bridge = new MethodSymbol(flags,
                meth.name,
                bridgeType,
                origin);

        bridge.params = createBridgeParams(impl, bridge, bridgeType);
        bridge.setAttributes(impl);
        if (!hypothetical) {
            JCMethodDecl md = make.MethodDef(bridge, null);


            JCExpression receiver = (impl.owner == origin)
                    ? make.This(origin.erasure(types))
                    : make.Super(types.supertype(origin.type).tsym.erasure(types), origin);

            Type calltype = erasure(impl.type.getReturnType());


            JCExpression call =
                    make.Apply(
                            null,
                            make.Select(receiver, impl).setType(calltype),
                            translateArgs(make.Idents(md.params), origErasure.getParameterTypes(), null))
                            .setType(calltype);
            JCStatement stat = (origErasure.getReturnType().hasTag(VOID))
                    ? make.Exec(call)
                    : make.Return(coerce(call, bridgeType.getReturnType()));
            md.body = make.Block(0, List.of(stat));

            bridges.append(md);
        }

        origin.members().enter(bridge);
        overridden.put(bridge, meth);
    }

    private List<VarSymbol> createBridgeParams(MethodSymbol impl, MethodSymbol bridge,
                                               Type bridgeType) {
        List<VarSymbol> bridgeParams = null;
        if (impl.params != null) {
            bridgeParams = List.nil();
            List<VarSymbol> implParams = impl.params;
            Type.MethodType mType = (Type.MethodType) bridgeType;
            List<Type> argTypes = mType.argtypes;
            while (implParams.nonEmpty() && argTypes.nonEmpty()) {
                VarSymbol param = new VarSymbol(implParams.head.flags() | SYNTHETIC | PARAMETER,
                        implParams.head.name, argTypes.head, bridge);
                param.setAttributes(implParams.head);
                bridgeParams = bridgeParams.append(param);
                implParams = implParams.tail;
                argTypes = argTypes.tail;
            }
        }
        return bridgeParams;
    }

    void addBridgeIfNeeded(DiagnosticPosition pos,
                           Symbol sym,
                           ClassSymbol origin,
                           ListBuffer<JCTree> bridges) {
        if (sym.kind == MTH &&
                sym.name != names.init &&
                (sym.flags() & (PRIVATE | STATIC)) == 0 &&
                (sym.flags() & (SYNTHETIC | OVERRIDE_BRIDGE)) != SYNTHETIC &&
                sym.isMemberOf(origin, types)) {
            MethodSymbol meth = (MethodSymbol) sym;
            MethodSymbol bridge = meth.binaryImplementation(origin, types);
            MethodSymbol impl = meth.implementation(origin, types, true, overrideBridgeFilter);
            if (bridge == null ||
                    bridge == meth ||
                    (impl != null && !bridge.owner.isSubClass(impl.owner, types))) {

                if (impl != null && isBridgeNeeded(meth, impl, origin.type)) {
                    addBridge(pos, meth, impl, origin, bridge == impl, bridges);
                } else if (impl == meth
                        && impl.owner != origin
                        && (impl.flags() & FINAL) == 0
                        && (meth.flags() & (ABSTRACT | PUBLIC)) == PUBLIC
                        && (origin.flags() & PUBLIC) > (impl.owner.flags() & PUBLIC)) {


                    addBridge(pos, meth, impl, origin, false, bridges);
                }
            } else if ((bridge.flags() & (SYNTHETIC | OVERRIDE_BRIDGE)) == SYNTHETIC) {
                MethodSymbol other = overridden.get(bridge);
                if (other != null && other != meth) {
                    if (impl == null || !impl.overrides(other, origin, types, true)) {

                        log.error(pos, "name.clash.same.erasure.no.override",
                                other, other.location(origin.type, types),
                                meth, meth.location(origin.type, types));
                    }
                }
            } else if (!bridge.overrides(meth, origin, types, true)) {

                if (bridge.owner == origin ||
                        types.asSuper(bridge.owner.type, meth.owner) == null)


                    log.error(pos, "name.clash.same.erasure.no.override",
                            bridge, bridge.location(origin.type, types),
                            meth, meth.location(origin.type, types));
            }
        }
    }

    private boolean isBridgeNeeded(MethodSymbol method,
                                   MethodSymbol impl,
                                   Type dest) {
        if (impl != method) {


            Type method_erasure = method.erasure(types);
            if (!isSameMemberWhenErased(dest, method, method_erasure))
                return true;
            Type impl_erasure = impl.erasure(types);
            if (!isSameMemberWhenErased(dest, impl, impl_erasure))
                return true;


            return !types.isSameType(impl_erasure.getReturnType(),
                    method_erasure.getReturnType());
        } else {

            if ((method.flags() & ABSTRACT) != 0) {


                return false;
            }


            return !isSameMemberWhenErased(dest, method, method.erasure(types));
        }
    }

    private boolean isSameMemberWhenErased(Type type,
                                           MethodSymbol method,
                                           Type erasure) {
        return types.isSameType(erasure(types.memberType(type, method)),
                erasure);
    }

    void addBridges(DiagnosticPosition pos,
                    TypeSymbol i,
                    ClassSymbol origin,
                    ListBuffer<JCTree> bridges) {
        for (Scope.Entry e = i.members().elems; e != null; e = e.sibling)
            addBridgeIfNeeded(pos, e.sym, origin, bridges);
        for (List<Type> l = types.interfaces(i.type); l.nonEmpty(); l = l.tail)
            addBridges(pos, l.head.tsym, origin, bridges);
    }

    void addBridges(DiagnosticPosition pos, ClassSymbol origin, ListBuffer<JCTree> bridges) {
        Type st = types.supertype(origin.type);
        while (st.hasTag(CLASS)) {
            addBridges(pos, st.tsym, origin, bridges);
            st = types.supertype(st);
        }
        for (List<Type> l = types.interfaces(origin.type); l.nonEmpty(); l = l.tail)
            addBridges(pos, l.head.tsym, origin, bridges);
    }

    public <T extends JCTree> T translate(T tree, Type pt) {
        Type prevPt = this.pt;
        try {
            this.pt = pt;
            return translate(tree);
        } finally {
            this.pt = prevPt;
        }
    }

    public <T extends JCTree> List<T> translate(List<T> trees, Type pt) {
        Type prevPt = this.pt;
        List<T> res;
        try {
            this.pt = pt;
            res = translate(trees);
        } finally {
            this.pt = prevPt;
        }
        return res;
    }

    public void visitClassDef(JCClassDecl tree) {
        translateClass(tree.sym);
        result = tree;
    }

    public void visitMethodDef(JCMethodDecl tree) {
        JCTree previousMethod = currentMethod;
        try {
            currentMethod = tree;
            tree.restype = translate(tree.restype, null);
            tree.typarams = List.nil();
            tree.params = translateVarDefs(tree.params);
            tree.recvparam = translate(tree.recvparam, null);
            tree.thrown = translate(tree.thrown, null);
            tree.body = translate(tree.body, tree.sym.erasure(types).getReturnType());
            tree.type = erasure(tree.type);
            result = tree;
        } finally {
            currentMethod = previousMethod;
        }

        for (Scope.Entry e = tree.sym.owner.members().lookup(tree.name);
             e.sym != null;
             e = e.next()) {
            if (e.sym != tree.sym &&
                    types.isSameType(erasure(e.sym.type), tree.type)) {
                log.error(tree.pos(),
                        "name.clash.same.erasure", tree.sym,
                        e.sym);
                return;
            }
        }
    }

    public void visitVarDef(JCVariableDecl tree) {
        tree.vartype = translate(tree.vartype, null);
        tree.init = translate(tree.init, tree.sym.erasure(types));
        tree.type = erasure(tree.type);
        result = tree;
    }

    public void visitDoLoop(JCDoWhileLoop tree) {
        tree.body = translate(tree.body);
        tree.cond = translate(tree.cond, syms.booleanType);
        result = tree;
    }

    public void visitWhileLoop(JCWhileLoop tree) {
        tree.cond = translate(tree.cond, syms.booleanType);
        tree.body = translate(tree.body);
        result = tree;
    }

    public void visitForLoop(JCForLoop tree) {
        tree.init = translate(tree.init, null);
        if (tree.cond != null)
            tree.cond = translate(tree.cond, syms.booleanType);
        tree.step = translate(tree.step, null);
        tree.body = translate(tree.body);
        result = tree;
    }

    public void visitForeachLoop(JCEnhancedForLoop tree) {
        tree.var = translate(tree.var, null);
        Type iterableType = tree.expr.type;
        tree.expr = translate(tree.expr, erasure(tree.expr.type));
        if (types.elemtype(tree.expr.type) == null)
            tree.expr.type = iterableType;
        tree.body = translate(tree.body);
        result = tree;
    }

    public void visitLambda(JCLambda tree) {
        JCTree prevMethod = currentMethod;
        try {
            currentMethod = null;
            tree.params = translate(tree.params);
            tree.body = translate(tree.body, tree.body.type == null ? null : erasure(tree.body.type));
            tree.type = erasure(tree.type);
            result = tree;
        } finally {
            currentMethod = prevMethod;
        }
    }

    public void visitSwitch(JCSwitch tree) {
        Type selsuper = types.supertype(tree.selector.type);
        boolean enumSwitch = selsuper != null &&
                selsuper.tsym == syms.enumSym;
        Type target = enumSwitch ? erasure(tree.selector.type) : syms.intType;
        tree.selector = translate(tree.selector, target);
        tree.cases = translateCases(tree.cases);
        result = tree;
    }

    public void visitCase(JCCase tree) {
        tree.pat = translate(tree.pat, null);
        tree.stats = translate(tree.stats);
        result = tree;
    }

    public void visitSynchronized(JCSynchronized tree) {
        tree.lock = translate(tree.lock, erasure(tree.lock.type));
        tree.body = translate(tree.body);
        result = tree;
    }

    public void visitTry(JCTry tree) {
        tree.resources = translate(tree.resources, syms.autoCloseableType);
        tree.body = translate(tree.body);
        tree.catchers = translateCatchers(tree.catchers);
        tree.finalizer = translate(tree.finalizer);
        result = tree;
    }

    public void visitConditional(JCConditional tree) {
        tree.cond = translate(tree.cond, syms.booleanType);
        tree.truepart = translate(tree.truepart, erasure(tree.type));
        tree.falsepart = translate(tree.falsepart, erasure(tree.type));
        tree.type = erasure(tree.type);
        result = retype(tree, tree.type, pt);
    }

    public void visitIf(JCIf tree) {
        tree.cond = translate(tree.cond, syms.booleanType);
        tree.thenpart = translate(tree.thenpart);
        tree.elsepart = translate(tree.elsepart);
        result = tree;
    }

    public void visitExec(JCExpressionStatement tree) {
        tree.expr = translate(tree.expr, null);
        result = tree;
    }

    public void visitReturn(JCReturn tree) {
        tree.expr = translate(tree.expr, currentMethod != null ? types.erasure(currentMethod.type).getReturnType() : null);
        result = tree;
    }

    public void visitThrow(JCThrow tree) {
        tree.expr = translate(tree.expr, erasure(tree.expr.type));
        result = tree;
    }

    public void visitAssert(JCAssert tree) {
        tree.cond = translate(tree.cond, syms.booleanType);
        if (tree.detail != null)
            tree.detail = translate(tree.detail, erasure(tree.detail.type));
        result = tree;
    }

    public void visitApply(JCMethodInvocation tree) {
        tree.meth = translate(tree.meth, null);
        Symbol meth = TreeInfo.symbol(tree.meth);
        Type mt = meth.erasure(types);
        List<Type> argtypes = mt.getParameterTypes();
        if (allowEnums &&
                meth.name == names.init &&
                meth.owner == syms.enumSym)
            argtypes = argtypes.tail.tail;
        if (tree.varargsElement != null)
            tree.varargsElement = types.erasure(tree.varargsElement);
        else if (tree.args.length() != argtypes.length()) {
            log.error(tree.pos(),
                    "method.invoked.with.incorrect.number.arguments",
                    tree.args.length(), argtypes.length());
        }
        tree.args = translateArgs(tree.args, argtypes, tree.varargsElement);
        tree.type = types.erasure(tree.type);

        result = retype(tree, mt.getReturnType(), pt);
    }

    public void visitNewClass(JCNewClass tree) {
        if (tree.encl != null)
            tree.encl = translate(tree.encl, erasure(tree.encl.type));
        tree.clazz = translate(tree.clazz, null);
        if (tree.varargsElement != null)
            tree.varargsElement = types.erasure(tree.varargsElement);
        tree.args = translateArgs(
                tree.args, tree.constructor.erasure(types).getParameterTypes(), tree.varargsElement);
        tree.def = translate(tree.def, null);
        if (tree.constructorType != null)
            tree.constructorType = erasure(tree.constructorType);
        tree.type = erasure(tree.type);
        result = tree;
    }

    public void visitNewArray(JCNewArray tree) {
        tree.elemtype = translate(tree.elemtype, null);
        translate(tree.dims, syms.intType);
        if (tree.type != null) {
            tree.elems = translate(tree.elems, erasure(types.elemtype(tree.type)));
            tree.type = erasure(tree.type);
        } else {
            tree.elems = translate(tree.elems, null);
        }
        result = tree;
    }

    public void visitParens(JCParens tree) {
        tree.expr = translate(tree.expr, pt);
        tree.type = erasure(tree.type);
        result = tree;
    }

    public void visitAssign(JCAssign tree) {
        tree.lhs = translate(tree.lhs, null);
        tree.rhs = translate(tree.rhs, erasure(tree.lhs.type));
        tree.type = erasure(tree.lhs.type);
        result = retype(tree, tree.type, pt);
    }

    public void visitAssignop(JCAssignOp tree) {
        tree.lhs = translate(tree.lhs, null);
        tree.rhs = translate(tree.rhs, tree.operator.type.getParameterTypes().tail.head);
        tree.type = erasure(tree.type);
        result = tree;
    }

    public void visitUnary(JCUnary tree) {
        tree.arg = translate(tree.arg, tree.operator.type.getParameterTypes().head);
        result = tree;
    }

    public void visitBinary(JCBinary tree) {
        tree.lhs = translate(tree.lhs, tree.operator.type.getParameterTypes().head);
        tree.rhs = translate(tree.rhs, tree.operator.type.getParameterTypes().tail.head);
        result = tree;
    }

    public void visitTypeCast(JCTypeCast tree) {
        tree.clazz = translate(tree.clazz, null);
        Type originalTarget = tree.type;
        tree.type = erasure(tree.type);
        tree.expr = translate(tree.expr, tree.type);
        if (originalTarget.isCompound()) {
            Type.IntersectionClassType ict = (Type.IntersectionClassType) originalTarget;
            for (Type c : ict.getExplicitComponents()) {
                Type ec = erasure(c);
                if (!types.isSameType(ec, tree.type)) {
                    tree.expr = coerce(tree.expr, ec);
                }
            }
        }
        result = tree;
    }

    public void visitTypeTest(JCInstanceOf tree) {
        tree.expr = translate(tree.expr, null);
        tree.clazz = translate(tree.clazz, null);
        result = tree;
    }

    public void visitIndexed(JCArrayAccess tree) {
        tree.indexed = translate(tree.indexed, erasure(tree.indexed.type));
        tree.index = translate(tree.index, syms.intType);

        result = retype(tree, types.elemtype(tree.indexed.type), pt);
    }

    public void visitAnnotation(JCAnnotation tree) {
        result = tree;
    }

    public void visitIdent(JCIdent tree) {
        Type et = tree.sym.erasure(types);

        if (tree.sym.kind == TYP && tree.sym.type.hasTag(TYPEVAR)) {
            result = make.at(tree.pos).Type(et);
        } else if (tree.type.constValue() != null) {
            result = tree;
        } else if (tree.sym.kind == VAR) {
            result = retype(tree, et, pt);
        } else {
            tree.type = erasure(tree.type);
            result = tree;
        }
    }

    public void visitSelect(JCFieldAccess tree) {
        Type t = tree.selected.type;
        while (t.hasTag(TYPEVAR))
            t = t.getUpperBound();
        if (t.isCompound()) {
            if ((tree.sym.flags() & IPROXY) != 0) {
                tree.sym = ((MethodSymbol) tree.sym).
                        implemented((TypeSymbol) tree.sym.owner, types);
            }
            tree.selected = coerce(
                    translate(tree.selected, erasure(tree.selected.type)),
                    erasure(tree.sym.owner.type));
        } else
            tree.selected = translate(tree.selected, erasure(t));

        if (tree.type.constValue() != null) {
            result = tree;
        } else if (tree.sym.kind == VAR) {
            result = retype(tree, tree.sym.erasure(types), pt);
        } else {
            tree.type = erasure(tree.type);
            result = tree;
        }
    }

    public void visitReference(JCMemberReference tree) {
        tree.expr = translate(tree.expr, erasure(tree.expr.type));
        tree.type = erasure(tree.type);
        result = tree;
    }

    public void visitTypeArray(JCArrayTypeTree tree) {
        tree.elemtype = translate(tree.elemtype, null);
        tree.type = erasure(tree.type);
        result = tree;
    }

    public void visitTypeApply(JCTypeApply tree) {
        JCTree clazz = translate(tree.clazz, null);
        result = clazz;
    }

    public void visitTypeIntersection(JCTypeIntersection tree) {
        tree.bounds = translate(tree.bounds, null);
        tree.type = erasure(tree.type);
        result = tree;
    }

    private Type erasure(Type t) {
        return types.erasure(t);
    }

    private boolean boundsRestricted(ClassSymbol c) {
        Type st = types.supertype(c.type);
        if (st.isParameterized()) {
            List<Type> actuals = st.allparams();
            List<Type> formals = st.tsym.type.allparams();
            while (!actuals.isEmpty() && !formals.isEmpty()) {
                Type actual = actuals.head;
                Type formal = formals.head;
                if (!types.isSameType(types.erasure(actual),
                        types.erasure(formal)))
                    return true;
                actuals = actuals.tail;
                formals = formals.tail;
            }
        }
        return false;
    }

    private List<JCTree> addOverrideBridgesIfNeeded(DiagnosticPosition pos,
                                                    final ClassSymbol c) {
        ListBuffer<JCTree> buf = new ListBuffer<>();
        if (c.isInterface() || !boundsRestricted(c))
            return buf.toList();
        Type t = types.supertype(c.type);
        Scope s = t.tsym.members();
        if (s.elems != null) {
            for (Symbol sym : s.getElements(new NeedsOverridBridgeFilter(c))) {
                MethodSymbol m = (MethodSymbol) sym;
                MethodSymbol member = (MethodSymbol) m.asMemberOf(c.type, types);
                MethodSymbol impl = m.implementation(c, types, false);
                if ((impl == null || impl.owner != c) &&
                        !types.isSameType(member.erasure(types), m.erasure(types))) {
                    addOverrideBridges(pos, m, member, c, buf);
                }
            }
        }
        return buf.toList();
    }

    private void addOverrideBridges(DiagnosticPosition pos,
                                    MethodSymbol impl,
                                    MethodSymbol member,
                                    ClassSymbol c,
                                    ListBuffer<JCTree> bridges) {
        Type implErasure = impl.erasure(types);
        long flags = (impl.flags() & AccessFlags) | SYNTHETIC | BRIDGE | OVERRIDE_BRIDGE;
        member = new MethodSymbol(flags, member.name, member.type, c);
        JCMethodDecl md = make.MethodDef(member, null);
        JCExpression receiver = make.Super(types.supertype(c.type).tsym.erasure(types), c);
        Type calltype = erasure(impl.type.getReturnType());
        JCExpression call =
                make.Apply(null,
                        make.Select(receiver, impl).setType(calltype),
                        translateArgs(make.Idents(md.params),
                                implErasure.getParameterTypes(), null))
                        .setType(calltype);
        JCStatement stat = (member.getReturnType().hasTag(VOID))
                ? make.Exec(call)
                : make.Return(coerce(call, member.erasure(types).getReturnType()));
        md.body = make.Block(0, List.of(stat));
        c.members().enter(member);
        bridges.append(md);
    }

    void translateClass(ClassSymbol c) {
        Type st = types.supertype(c.type);

        if (st.hasTag(CLASS)) {
            translateClass((ClassSymbol) st.tsym);
        }
        Env<AttrContext> myEnv = enter.typeEnvs.remove(c);
        if (myEnv == null) {
            return;
        }

        boolean envHasCompState = compileStates.get(myEnv) != null;
        if (!envHasCompState && c.outermostClass() == c) {
            Assert.error("No info for outermost class: " + myEnv.enclClass.sym);
        }
        if (envHasCompState &&
                CompileState.FLOW.isAfter(compileStates.get(myEnv))) {
            Assert.error(String.format(statePreviousToFlowAssertMsg,
                    compileStates.get(myEnv), myEnv.enclClass.sym));
        }
        Env<AttrContext> oldEnv = env;
        try {
            env = myEnv;

            TreeMaker savedMake = make;
            Type savedPt = pt;
            make = make.forToplevel(env.toplevel);
            pt = null;
            try {
                JCClassDecl tree = (JCClassDecl) env.tree;
                tree.typarams = List.nil();
                super.visitClassDef(tree);
                make.at(tree.pos);
                if (addBridges) {
                    ListBuffer<JCTree> bridges = new ListBuffer<JCTree>();
                    if (false)
                        bridges.appendList(addOverrideBridgesIfNeeded(tree, c));
                    if (allowInterfaceBridges || (tree.sym.flags() & INTERFACE) == 0) {
                        addBridges(tree.pos(), c, bridges);
                    }
                    tree.defs = bridges.toList().prependList(tree.defs);
                }
                tree.type = erasure(tree.type);
            } finally {
                make = savedMake;
                pt = savedPt;
            }
        } finally {
            env = oldEnv;
        }
    }

    public JCTree translateTopLevelClass(JCTree cdef, TreeMaker make) {

        this.make = make;
        pt = null;
        return translate(cdef, null);
    }

    class NeedsOverridBridgeFilter implements Filter<Symbol> {
        ClassSymbol c;

        NeedsOverridBridgeFilter(ClassSymbol c) {
            this.c = c;
        }

        public boolean accepts(Symbol s) {
            return s.kind == MTH &&
                    !s.isConstructor() &&
                    s.isInheritedIn(c, types) &&
                    (s.flags() & FINAL) == 0 &&
                    (s.flags() & (SYNTHETIC | OVERRIDE_BRIDGE)) != SYNTHETIC;
        }
    }
}
