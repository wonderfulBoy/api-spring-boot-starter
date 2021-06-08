package com.github.api.sun.tools.javac.tree;

import com.github.api.sun.source.tree.Tree;
import com.github.api.sun.tools.javac.code.Flags;
import com.github.api.sun.tools.javac.code.Kinds;
import com.github.api.sun.tools.javac.code.Symbol;
import com.github.api.sun.tools.javac.code.Type;
import com.github.api.sun.tools.javac.comp.AttrContext;
import com.github.api.sun.tools.javac.comp.Env;
import com.github.api.sun.tools.javac.tree.JCTree.*;
import com.github.api.sun.tools.javac.tree.JCTree.JCPolyExpression.PolyKind;
import com.github.api.sun.tools.javac.util.*;
import com.github.api.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import static com.github.api.sun.tools.javac.code.Flags.*;
import static com.github.api.sun.tools.javac.code.TypeTag.BOT;
import static com.github.api.sun.tools.javac.tree.JCTree.Tag.BLOCK;
import static com.github.api.sun.tools.javac.tree.JCTree.Tag.SYNCHRONIZED;
import static com.github.api.sun.tools.javac.tree.JCTree.Tag.*;

public class TreeInfo {
    public static final int
            notExpression = -1,
            noPrec = 0,
            assignPrec = 1,
            assignopPrec = 2,
            condPrec = 3,
            orPrec = 4,
            andPrec = 5,
            bitorPrec = 6,
            bitxorPrec = 7,
            bitandPrec = 8,
            eqPrec = 9,
            ordPrec = 10,
            shiftPrec = 11,
            addPrec = 12,
            mulPrec = 13,
            prefixPrec = 14,
            postfixPrec = 15,
            precCount = 16;
    protected static final Context.Key<TreeInfo> treeInfoKey =
            new Context.Key<TreeInfo>();
    private Name[] opname = new Name[Tag.getNumberOfOperators()];

    private TreeInfo(Context context) {
        context.put(treeInfoKey, this);
        Names names = Names.instance(context);

        setOpname(POS, "+++", names);
        setOpname(NEG, "---", names);
        setOpname(NOT, "!", names);
        setOpname(COMPL, "~", names);
        setOpname(PREINC, "++", names);
        setOpname(PREDEC, "--", names);
        setOpname(POSTINC, "++", names);
        setOpname(POSTDEC, "--", names);
        setOpname(NULLCHK, "<*nullchk*>", names);
        setOpname(OR, "||", names);
        setOpname(AND, "&&", names);
        setOpname(EQ, "==", names);
        setOpname(NE, "!=", names);
        setOpname(LT, "<", names);
        setOpname(GT, ">", names);
        setOpname(LE, "<=", names);
        setOpname(GE, ">=", names);
        setOpname(BITOR, "|", names);
        setOpname(BITXOR, "^", names);
        setOpname(BITAND, "&", names);
        setOpname(SL, "<<", names);
        setOpname(SR, ">>", names);
        setOpname(USR, ">>>", names);
        setOpname(PLUS, "+", names);
        setOpname(MINUS, names.hyphen);
        setOpname(MUL, names.asterisk);
        setOpname(DIV, names.slash);
        setOpname(MOD, "%", names);
    }

    public static TreeInfo instance(Context context) {
        TreeInfo instance = context.get(treeInfoKey);
        if (instance == null)
            instance = new TreeInfo(context);
        return instance;
    }

    public static List<JCExpression> args(JCTree t) {
        switch (t.getTag()) {
            case APPLY:
                return ((JCMethodInvocation) t).args;
            case NEWCLASS:
                return ((JCNewClass) t).args;
            default:
                return null;
        }
    }

    public static boolean isConstructor(JCTree tree) {
        if (tree.hasTag(METHODDEF)) {
            Name name = ((JCMethodDecl) tree).name;
            return name == name.table.names.init;
        } else {
            return false;
        }
    }

    public static boolean hasConstructors(List<JCTree> trees) {
        for (List<JCTree> l = trees; l.nonEmpty(); l = l.tail)
            if (isConstructor(l.head)) return true;
        return false;
    }

    public static boolean isMultiCatch(JCCatch catchClause) {
        return catchClause.param.vartype.hasTag(TYPEUNION);
    }

    public static boolean isSyntheticInit(JCTree stat) {
        if (stat.hasTag(EXEC)) {
            JCExpressionStatement exec = (JCExpressionStatement) stat;
            if (exec.expr.hasTag(ASSIGN)) {
                JCAssign assign = (JCAssign) exec.expr;
                if (assign.lhs.hasTag(SELECT)) {
                    JCFieldAccess select = (JCFieldAccess) assign.lhs;
                    if (select.sym != null &&
                            (select.sym.flags() & SYNTHETIC) != 0) {
                        Name selected = name(select.selected);
                        return selected != null && selected == selected.table.names._this;
                    }
                }
            }
        }
        return false;
    }

    public static Name calledMethodName(JCTree tree) {
        if (tree.hasTag(EXEC)) {
            JCExpressionStatement exec = (JCExpressionStatement) tree;
            if (exec.expr.hasTag(APPLY)) {
                Name mname = TreeInfo.name(((JCMethodInvocation) exec.expr).meth);
                return mname;
            }
        }
        return null;
    }

    public static boolean isSelfCall(JCTree tree) {
        Name name = calledMethodName(tree);
        if (name != null) {
            Names names = name.table.names;
            return name == names._this || name == names._super;
        } else {
            return false;
        }
    }

    public static boolean isSuperCall(JCTree tree) {
        Name name = calledMethodName(tree);
        if (name != null) {
            Names names = name.table.names;
            return name == names._super;
        } else {
            return false;
        }
    }

    public static boolean isInitialConstructor(JCTree tree) {
        JCMethodInvocation app = firstConstructorCall(tree);
        if (app == null) return false;
        Name meth = name(app.meth);
        return meth == null || meth != meth.table.names._this;
    }

    public static JCMethodInvocation firstConstructorCall(JCTree tree) {
        if (!tree.hasTag(METHODDEF)) return null;
        JCMethodDecl md = (JCMethodDecl) tree;
        Names names = md.name.table.names;
        if (md.name != names.init) return null;
        if (md.body == null) return null;
        List<JCStatement> stats = md.body.stats;

        while (stats.nonEmpty() && isSyntheticInit(stats.head))
            stats = stats.tail;
        if (stats.isEmpty()) return null;
        if (!stats.head.hasTag(EXEC)) return null;
        JCExpressionStatement exec = (JCExpressionStatement) stats.head;
        if (!exec.expr.hasTag(APPLY)) return null;
        return (JCMethodInvocation) exec.expr;
    }

    public static boolean isDiamond(JCTree tree) {
        switch (tree.getTag()) {
            case TYPEAPPLY:
                return ((JCTypeApply) tree).getTypeArguments().isEmpty();
            case NEWCLASS:
                return isDiamond(((JCNewClass) tree).clazz);
            case ANNOTATED_TYPE:
                return isDiamond(((JCAnnotatedType) tree).underlyingType);
            default:
                return false;
        }
    }

    public static boolean isEnumInit(JCTree tree) {
        switch (tree.getTag()) {
            case VARDEF:
                return (((JCVariableDecl) tree).mods.flags & ENUM) != 0;
            default:
                return false;
        }
    }

    public static void setPolyKind(JCTree tree, PolyKind pkind) {
        switch (tree.getTag()) {
            case APPLY:
                ((JCMethodInvocation) tree).polyKind = pkind;
                break;
            case NEWCLASS:
                ((JCNewClass) tree).polyKind = pkind;
                break;
            case REFERENCE:
                ((JCMemberReference) tree).refPolyKind = pkind;
                break;
            default:
                throw new AssertionError("Unexpected tree: " + tree);
        }
    }

    public static void setVarargsElement(JCTree tree, Type varargsElement) {
        switch (tree.getTag()) {
            case APPLY:
                ((JCMethodInvocation) tree).varargsElement = varargsElement;
                break;
            case NEWCLASS:
                ((JCNewClass) tree).varargsElement = varargsElement;
                break;
            case REFERENCE:
                ((JCMemberReference) tree).varargsElement = varargsElement;
                break;
            default:
                throw new AssertionError("Unexpected tree: " + tree);
        }
    }

    public static boolean isExpressionStatement(JCExpression tree) {
        switch (tree.getTag()) {
            case PREINC:
            case PREDEC:
            case POSTINC:
            case POSTDEC:
            case ASSIGN:
            case BITOR_ASG:
            case BITXOR_ASG:
            case BITAND_ASG:
            case SL_ASG:
            case SR_ASG:
            case USR_ASG:
            case PLUS_ASG:
            case MINUS_ASG:
            case MUL_ASG:
            case DIV_ASG:
            case MOD_ASG:
            case APPLY:
            case NEWCLASS:
            case ERRONEOUS:
                return true;
            default:
                return false;
        }
    }

    public static boolean isStaticSelector(JCTree base, Names names) {
        if (base == null)
            return false;
        switch (base.getTag()) {
            case IDENT:
                JCIdent id = (JCIdent) base;
                return id.name != names._this &&
                        id.name != names._super &&
                        isStaticSym(base);
            case SELECT:
                return isStaticSym(base) &&
                        isStaticSelector(((JCFieldAccess) base).selected, names);
            case TYPEAPPLY:
            case TYPEARRAY:
                return true;
            case ANNOTATED_TYPE:
                return isStaticSelector(((JCAnnotatedType) base).underlyingType, names);
            default:
                return false;
        }
    }

    private static boolean isStaticSym(JCTree tree) {
        Symbol sym = symbol(tree);
        return (sym.kind == Kinds.TYP ||
                sym.kind == Kinds.PCK);
    }

    public static boolean isNull(JCTree tree) {
        if (!tree.hasTag(LITERAL))
            return false;
        JCLiteral lit = (JCLiteral) tree;
        return (lit.typetag == BOT);
    }

    public static String getCommentText(Env<?> env, JCTree tree) {
        DocCommentTable docComments = (tree.hasTag(Tag.TOPLEVEL))
                ? ((JCCompilationUnit) tree).docComments
                : env.toplevel.docComments;
        return (docComments == null) ? null : docComments.getCommentText(tree);
    }

    public static DCTree.DCDocComment getCommentTree(Env<?> env, JCTree tree) {
        DocCommentTable docComments = (tree.hasTag(Tag.TOPLEVEL))
                ? ((JCCompilationUnit) tree).docComments
                : env.toplevel.docComments;
        return (docComments == null) ? null : docComments.getCommentTree(tree);
    }

    public static int firstStatPos(JCTree tree) {
        if (tree.hasTag(BLOCK) && ((JCBlock) tree).stats.nonEmpty())
            return ((JCBlock) tree).stats.head.pos;
        else
            return tree.pos;
    }

    public static int endPos(JCTree tree) {
        if (tree.hasTag(BLOCK) && ((JCBlock) tree).endpos != Position.NOPOS)
            return ((JCBlock) tree).endpos;
        else if (tree.hasTag(SYNCHRONIZED))
            return endPos(((JCSynchronized) tree).body);
        else if (tree.hasTag(TRY)) {
            JCTry t = (JCTry) tree;
            return endPos((t.finalizer != null) ? t.finalizer
                    : (t.catchers.nonEmpty() ? t.catchers.last().body : t.body));
        } else
            return tree.pos;
    }

    public static int getStartPos(JCTree tree) {
        if (tree == null)
            return Position.NOPOS;
        switch (tree.getTag()) {
            case APPLY:
                return getStartPos(((JCMethodInvocation) tree).meth);
            case ASSIGN:
                return getStartPos(((JCAssign) tree).lhs);
            case BITOR_ASG:
            case BITXOR_ASG:
            case BITAND_ASG:
            case SL_ASG:
            case SR_ASG:
            case USR_ASG:
            case PLUS_ASG:
            case MINUS_ASG:
            case MUL_ASG:
            case DIV_ASG:
            case MOD_ASG:
                return getStartPos(((JCAssignOp) tree).lhs);
            case OR:
            case AND:
            case BITOR:
            case BITXOR:
            case BITAND:
            case EQ:
            case NE:
            case LT:
            case GT:
            case LE:
            case GE:
            case SL:
            case SR:
            case USR:
            case PLUS:
            case MINUS:
            case MUL:
            case DIV:
            case MOD:
                return getStartPos(((JCBinary) tree).lhs);
            case CLASSDEF: {
                JCClassDecl node = (JCClassDecl) tree;
                if (node.mods.pos != Position.NOPOS)
                    return node.mods.pos;
                break;
            }
            case CONDEXPR:
                return getStartPos(((JCConditional) tree).cond);
            case EXEC:
                return getStartPos(((JCExpressionStatement) tree).expr);
            case INDEXED:
                return getStartPos(((JCArrayAccess) tree).indexed);
            case METHODDEF: {
                JCMethodDecl node = (JCMethodDecl) tree;
                if (node.mods.pos != Position.NOPOS)
                    return node.mods.pos;
                if (node.typarams.nonEmpty())
                    return getStartPos(node.typarams.head);
                return node.restype == null ? node.pos : getStartPos(node.restype);
            }
            case SELECT:
                return getStartPos(((JCFieldAccess) tree).selected);
            case TYPEAPPLY:
                return getStartPos(((JCTypeApply) tree).clazz);
            case TYPEARRAY:
                return getStartPos(((JCArrayTypeTree) tree).elemtype);
            case TYPETEST:
                return getStartPos(((JCInstanceOf) tree).expr);
            case POSTINC:
            case POSTDEC:
                return getStartPos(((JCUnary) tree).arg);
            case ANNOTATED_TYPE: {
                JCAnnotatedType node = (JCAnnotatedType) tree;
                if (node.annotations.nonEmpty()) {
                    if (node.underlyingType.hasTag(TYPEARRAY) ||
                            node.underlyingType.hasTag(SELECT)) {
                        return getStartPos(node.underlyingType);
                    } else {
                        return getStartPos(node.annotations.head);
                    }
                } else {
                    return getStartPos(node.underlyingType);
                }
            }
            case NEWCLASS: {
                JCNewClass node = (JCNewClass) tree;
                if (node.encl != null)
                    return getStartPos(node.encl);
                break;
            }
            case VARDEF: {
                JCVariableDecl node = (JCVariableDecl) tree;
                if (node.mods.pos != Position.NOPOS) {
                    return node.mods.pos;
                } else if (node.vartype == null) {


                    return node.pos;
                } else {
                    return getStartPos(node.vartype);
                }
            }
            case ERRONEOUS: {
                JCErroneous node = (JCErroneous) tree;
                if (node.errs != null && node.errs.nonEmpty())
                    return getStartPos(node.errs.head);
            }
        }
        return tree.pos;
    }

    public static int getEndPos(JCTree tree, EndPosTable endPosTable) {
        if (tree == null)
            return Position.NOPOS;
        if (endPosTable == null) {

            return endPos(tree);
        }
        int mapPos = endPosTable.getEndPos(tree);
        if (mapPos != Position.NOPOS)
            return mapPos;
        switch (tree.getTag()) {
            case BITOR_ASG:
            case BITXOR_ASG:
            case BITAND_ASG:
            case SL_ASG:
            case SR_ASG:
            case USR_ASG:
            case PLUS_ASG:
            case MINUS_ASG:
            case MUL_ASG:
            case DIV_ASG:
            case MOD_ASG:
                return getEndPos(((JCAssignOp) tree).rhs, endPosTable);
            case OR:
            case AND:
            case BITOR:
            case BITXOR:
            case BITAND:
            case EQ:
            case NE:
            case LT:
            case GT:
            case LE:
            case GE:
            case SL:
            case SR:
            case USR:
            case PLUS:
            case MINUS:
            case MUL:
            case DIV:
            case MOD:
                return getEndPos(((JCBinary) tree).rhs, endPosTable);
            case CASE:
                return getEndPos(((JCCase) tree).stats.last(), endPosTable);
            case CATCH:
                return getEndPos(((JCCatch) tree).body, endPosTable);
            case CONDEXPR:
                return getEndPos(((JCConditional) tree).falsepart, endPosTable);
            case FORLOOP:
                return getEndPos(((JCForLoop) tree).body, endPosTable);
            case FOREACHLOOP:
                return getEndPos(((JCEnhancedForLoop) tree).body, endPosTable);
            case IF: {
                JCIf node = (JCIf) tree;
                if (node.elsepart == null) {
                    return getEndPos(node.thenpart, endPosTable);
                } else {
                    return getEndPos(node.elsepart, endPosTable);
                }
            }
            case LABELLED:
                return getEndPos(((JCLabeledStatement) tree).body, endPosTable);
            case MODIFIERS:
                return getEndPos(((JCModifiers) tree).annotations.last(), endPosTable);
            case SYNCHRONIZED:
                return getEndPos(((JCSynchronized) tree).body, endPosTable);
            case TOPLEVEL:
                return getEndPos(((JCCompilationUnit) tree).defs.last(), endPosTable);
            case TRY: {
                JCTry node = (JCTry) tree;
                if (node.finalizer != null) {
                    return getEndPos(node.finalizer, endPosTable);
                } else if (!node.catchers.isEmpty()) {
                    return getEndPos(node.catchers.last(), endPosTable);
                } else {
                    return getEndPos(node.body, endPosTable);
                }
            }
            case WILDCARD:
                return getEndPos(((JCWildcard) tree).inner, endPosTable);
            case TYPECAST:
                return getEndPos(((JCTypeCast) tree).expr, endPosTable);
            case TYPETEST:
                return getEndPos(((JCInstanceOf) tree).clazz, endPosTable);
            case POS:
            case NEG:
            case NOT:
            case COMPL:
            case PREINC:
            case PREDEC:
                return getEndPos(((JCUnary) tree).arg, endPosTable);
            case WHILELOOP:
                return getEndPos(((JCWhileLoop) tree).body, endPosTable);
            case ANNOTATED_TYPE:
                return getEndPos(((JCAnnotatedType) tree).underlyingType, endPosTable);
            case ERRONEOUS: {
                JCErroneous node = (JCErroneous) tree;
                if (node.errs != null && node.errs.nonEmpty())
                    return getEndPos(node.errs.last(), endPosTable);
            }
        }
        return Position.NOPOS;
    }

    public static DiagnosticPosition diagEndPos(final JCTree tree) {
        final int endPos = TreeInfo.endPos(tree);
        return new DiagnosticPosition() {
            public JCTree getTree() {
                return tree;
            }

            public int getStartPosition() {
                return TreeInfo.getStartPos(tree);
            }

            public int getPreferredPosition() {
                return endPos;
            }

            public int getEndPosition(EndPosTable endPosTable) {
                return TreeInfo.getEndPos(tree, endPosTable);
            }
        };
    }

    public static int finalizerPos(JCTree tree) {
        if (tree.hasTag(TRY)) {
            JCTry t = (JCTry) tree;
            Assert.checkNonNull(t.finalizer);
            return firstStatPos(t.finalizer);
        } else if (tree.hasTag(SYNCHRONIZED)) {
            return endPos(((JCSynchronized) tree).body);
        } else {
            throw new AssertionError();
        }
    }

    public static int positionFor(final Symbol sym, final JCTree tree) {
        JCTree decl = declarationFor(sym, tree);
        return ((decl != null) ? decl : tree).pos;
    }

    public static DiagnosticPosition diagnosticPositionFor(final Symbol sym, final JCTree tree) {
        JCTree decl = declarationFor(sym, tree);
        return ((decl != null) ? decl : tree).pos();
    }

    public static JCTree declarationFor(final Symbol sym, final JCTree tree) {
        class DeclScanner extends TreeScanner {
            JCTree result = null;

            public void scan(JCTree tree) {
                if (tree != null && result == null)
                    tree.accept(this);
            }

            public void visitTopLevel(JCCompilationUnit that) {
                if (that.packge == sym) result = that;
                else super.visitTopLevel(that);
            }

            public void visitClassDef(JCClassDecl that) {
                if (that.sym == sym) result = that;
                else super.visitClassDef(that);
            }

            public void visitMethodDef(JCMethodDecl that) {
                if (that.sym == sym) result = that;
                else super.visitMethodDef(that);
            }

            public void visitVarDef(JCVariableDecl that) {
                if (that.sym == sym) result = that;
                else super.visitVarDef(that);
            }

            public void visitTypeParameter(JCTypeParameter that) {
                if (that.type != null && that.type.tsym == sym) result = that;
                else super.visitTypeParameter(that);
            }
        }
        DeclScanner s = new DeclScanner();
        tree.accept(s);
        return s.result;
    }

    public static Env<AttrContext> scopeFor(JCTree node, JCCompilationUnit unit) {
        return scopeFor(pathFor(node, unit));
    }

    public static Env<AttrContext> scopeFor(List<JCTree> path) {

        throw new UnsupportedOperationException("not implemented yet");
    }

    public static List<JCTree> pathFor(final JCTree node, final JCCompilationUnit unit) {
        class Result extends Error {
            static final long serialVersionUID = -5942088234594905625L;
            List<JCTree> path;

            Result(List<JCTree> path) {
                this.path = path;
            }
        }
        class PathFinder extends TreeScanner {
            List<JCTree> path = List.nil();

            public void scan(JCTree tree) {
                if (tree != null) {
                    path = path.prepend(tree);
                    if (tree == node)
                        throw new Result(path);
                    super.scan(tree);
                    path = path.tail;
                }
            }
        }
        try {
            new PathFinder().scan(unit);
        } catch (Result result) {
            return result.path;
        }
        return List.nil();
    }

    public static JCTree referencedStatement(JCLabeledStatement tree) {
        JCTree t = tree;
        do t = ((JCLabeledStatement) t).body;
        while (t.hasTag(LABELLED));
        switch (t.getTag()) {
            case DOLOOP:
            case WHILELOOP:
            case FORLOOP:
            case FOREACHLOOP:
            case SWITCH:
                return t;
            default:
                return tree;
        }
    }

    public static JCExpression skipParens(JCExpression tree) {
        while (tree.hasTag(PARENS)) {
            tree = ((JCParens) tree).expr;
        }
        return tree;
    }

    public static JCTree skipParens(JCTree tree) {
        if (tree.hasTag(PARENS))
            return skipParens((JCParens) tree);
        else
            return tree;
    }

    public static List<Type> types(List<? extends JCTree> trees) {
        ListBuffer<Type> ts = new ListBuffer<Type>();
        for (List<? extends JCTree> l = trees; l.nonEmpty(); l = l.tail)
            ts.append(l.head.type);
        return ts.toList();
    }

    public static Name name(JCTree tree) {
        switch (tree.getTag()) {
            case IDENT:
                return ((JCIdent) tree).name;
            case SELECT:
                return ((JCFieldAccess) tree).name;
            case TYPEAPPLY:
                return name(((JCTypeApply) tree).clazz);
            default:
                return null;
        }
    }

    public static Name fullName(JCTree tree) {
        tree = skipParens(tree);
        switch (tree.getTag()) {
            case IDENT:
                return ((JCIdent) tree).name;
            case SELECT:
                Name sname = fullName(((JCFieldAccess) tree).selected);
                return sname == null ? null : sname.append('.', name(tree));
            default:
                return null;
        }
    }

    public static Symbol symbolFor(JCTree node) {
        Symbol sym = symbolForImpl(node);
        return sym != null ? sym.baseSymbol() : null;
    }

    private static Symbol symbolForImpl(JCTree node) {
        node = skipParens(node);
        switch (node.getTag()) {
            case TOPLEVEL:
                return ((JCCompilationUnit) node).packge;
            case CLASSDEF:
                return ((JCClassDecl) node).sym;
            case METHODDEF:
                return ((JCMethodDecl) node).sym;
            case VARDEF:
                return ((JCVariableDecl) node).sym;
            case IDENT:
                return ((JCIdent) node).sym;
            case SELECT:
                return ((JCFieldAccess) node).sym;
            case REFERENCE:
                return ((JCMemberReference) node).sym;
            case NEWCLASS:
                return ((JCNewClass) node).constructor;
            case APPLY:
                return symbolFor(((JCMethodInvocation) node).meth);
            case TYPEAPPLY:
                return symbolFor(((JCTypeApply) node).clazz);
            case ANNOTATION:
            case TYPE_ANNOTATION:
            case TYPEPARAMETER:
                if (node.type != null)
                    return node.type.tsym;
                return null;
            default:
                return null;
        }
    }

    public static boolean isDeclaration(JCTree node) {
        node = skipParens(node);
        switch (node.getTag()) {
            case CLASSDEF:
            case METHODDEF:
            case VARDEF:
                return true;
            default:
                return false;
        }
    }

    public static Symbol symbol(JCTree tree) {
        tree = skipParens(tree);
        switch (tree.getTag()) {
            case IDENT:
                return ((JCIdent) tree).sym;
            case SELECT:
                return ((JCFieldAccess) tree).sym;
            case TYPEAPPLY:
                return symbol(((JCTypeApply) tree).clazz);
            case ANNOTATED_TYPE:
                return symbol(((JCAnnotatedType) tree).underlyingType);
            default:
                return null;
        }
    }

    public static boolean nonstaticSelect(JCTree tree) {
        tree = skipParens(tree);
        if (!tree.hasTag(SELECT)) return false;
        JCFieldAccess s = (JCFieldAccess) tree;
        Symbol e = symbol(s.selected);
        return e == null || (e.kind != Kinds.PCK && e.kind != Kinds.TYP);
    }

    public static void setSymbol(JCTree tree, Symbol sym) {
        tree = skipParens(tree);
        switch (tree.getTag()) {
            case IDENT:
                ((JCIdent) tree).sym = sym;
                break;
            case SELECT:
                ((JCFieldAccess) tree).sym = sym;
                break;
            default:
        }
    }

    public static long flags(JCTree tree) {
        switch (tree.getTag()) {
            case VARDEF:
                return ((JCVariableDecl) tree).mods.flags;
            case METHODDEF:
                return ((JCMethodDecl) tree).mods.flags;
            case CLASSDEF:
                return ((JCClassDecl) tree).mods.flags;
            case BLOCK:
                return ((JCBlock) tree).flags;
            default:
                return 0;
        }
    }

    public static long firstFlag(long flags) {
        long flag = 1;
        while ((flag & flags & ExtendedStandardFlags) == 0)
            flag = flag << 1;
        return flag;
    }

    public static String flagNames(long flags) {
        return Flags.toString(flags & ExtendedStandardFlags).trim();
    }

    public static int opPrec(Tag op) {
        switch (op) {
            case POS:
            case NEG:
            case NOT:
            case COMPL:
            case PREINC:
            case PREDEC:
                return prefixPrec;
            case POSTINC:
            case POSTDEC:
            case NULLCHK:
                return postfixPrec;
            case ASSIGN:
                return assignPrec;
            case BITOR_ASG:
            case BITXOR_ASG:
            case BITAND_ASG:
            case SL_ASG:
            case SR_ASG:
            case USR_ASG:
            case PLUS_ASG:
            case MINUS_ASG:
            case MUL_ASG:
            case DIV_ASG:
            case MOD_ASG:
                return assignopPrec;
            case OR:
                return orPrec;
            case AND:
                return andPrec;
            case EQ:
            case NE:
                return eqPrec;
            case LT:
            case GT:
            case LE:
            case GE:
                return ordPrec;
            case BITOR:
                return bitorPrec;
            case BITXOR:
                return bitxorPrec;
            case BITAND:
                return bitandPrec;
            case SL:
            case SR:
            case USR:
                return shiftPrec;
            case PLUS:
            case MINUS:
                return addPrec;
            case MUL:
            case DIV:
            case MOD:
                return mulPrec;
            case TYPETEST:
                return ordPrec;
            default:
                throw new AssertionError();
        }
    }

    static Tree.Kind tagToKind(Tag tag) {
        switch (tag) {

            case POSTINC:
                return Tree.Kind.POSTFIX_INCREMENT;
            case POSTDEC:
                return Tree.Kind.POSTFIX_DECREMENT;

            case PREINC:
                return Tree.Kind.PREFIX_INCREMENT;
            case PREDEC:
                return Tree.Kind.PREFIX_DECREMENT;
            case POS:
                return Tree.Kind.UNARY_PLUS;
            case NEG:
                return Tree.Kind.UNARY_MINUS;
            case COMPL:
                return Tree.Kind.BITWISE_COMPLEMENT;
            case NOT:
                return Tree.Kind.LOGICAL_COMPLEMENT;


            case MUL:
                return Tree.Kind.MULTIPLY;
            case DIV:
                return Tree.Kind.DIVIDE;
            case MOD:
                return Tree.Kind.REMAINDER;

            case PLUS:
                return Tree.Kind.PLUS;
            case MINUS:
                return Tree.Kind.MINUS;

            case SL:
                return Tree.Kind.LEFT_SHIFT;
            case SR:
                return Tree.Kind.RIGHT_SHIFT;
            case USR:
                return Tree.Kind.UNSIGNED_RIGHT_SHIFT;

            case LT:
                return Tree.Kind.LESS_THAN;
            case GT:
                return Tree.Kind.GREATER_THAN;
            case LE:
                return Tree.Kind.LESS_THAN_EQUAL;
            case GE:
                return Tree.Kind.GREATER_THAN_EQUAL;

            case EQ:
                return Tree.Kind.EQUAL_TO;
            case NE:
                return Tree.Kind.NOT_EQUAL_TO;

            case BITAND:
                return Tree.Kind.AND;
            case BITXOR:
                return Tree.Kind.XOR;
            case BITOR:
                return Tree.Kind.OR;

            case AND:
                return Tree.Kind.CONDITIONAL_AND;
            case OR:
                return Tree.Kind.CONDITIONAL_OR;

            case MUL_ASG:
                return Tree.Kind.MULTIPLY_ASSIGNMENT;
            case DIV_ASG:
                return Tree.Kind.DIVIDE_ASSIGNMENT;
            case MOD_ASG:
                return Tree.Kind.REMAINDER_ASSIGNMENT;
            case PLUS_ASG:
                return Tree.Kind.PLUS_ASSIGNMENT;
            case MINUS_ASG:
                return Tree.Kind.MINUS_ASSIGNMENT;
            case SL_ASG:
                return Tree.Kind.LEFT_SHIFT_ASSIGNMENT;
            case SR_ASG:
                return Tree.Kind.RIGHT_SHIFT_ASSIGNMENT;
            case USR_ASG:
                return Tree.Kind.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT;
            case BITAND_ASG:
                return Tree.Kind.AND_ASSIGNMENT;
            case BITXOR_ASG:
                return Tree.Kind.XOR_ASSIGNMENT;
            case BITOR_ASG:
                return Tree.Kind.OR_ASSIGNMENT;

            case NULLCHK:
                return Tree.Kind.OTHER;
            case ANNOTATION:
                return Tree.Kind.ANNOTATION;
            case TYPE_ANNOTATION:
                return Tree.Kind.TYPE_ANNOTATION;
            default:
                return null;
        }
    }

    public static JCExpression typeIn(JCExpression tree) {
        switch (tree.getTag()) {
            case ANNOTATED_TYPE:
                return ((JCAnnotatedType) tree).underlyingType;
            case IDENT:
            case TYPEIDENT:
            case SELECT:
            case TYPEARRAY:
            case WILDCARD:
            case TYPEPARAMETER:
            case TYPEAPPLY:
            case ERRONEOUS:
                return tree;
            default:
                throw new AssertionError("Unexpected type tree: " + tree);
        }
    }

    public static JCTree innermostType(JCTree type) {
        JCTree lastAnnotatedType = null;
        JCTree cur = type;
        loop:
        while (true) {
            switch (cur.getTag()) {
                case TYPEARRAY:
                    lastAnnotatedType = null;
                    cur = ((JCArrayTypeTree) cur).elemtype;
                    break;
                case WILDCARD:
                    lastAnnotatedType = null;
                    cur = ((JCWildcard) cur).inner;
                    break;
                case ANNOTATED_TYPE:
                    lastAnnotatedType = cur;
                    cur = ((JCAnnotatedType) cur).underlyingType;
                    break;
                default:
                    break loop;
            }
        }
        if (lastAnnotatedType != null) {
            return lastAnnotatedType;
        } else {
            return cur;
        }
    }

    public static boolean containsTypeAnnotation(JCTree e) {
        TypeAnnotationFinder finder = new TypeAnnotationFinder();
        finder.scan(e);
        return finder.foundTypeAnno;
    }

    private void setOpname(Tag tag, String name, Names names) {
        setOpname(tag, names.fromString(name));
    }

    private void setOpname(Tag tag, Name name) {
        opname[tag.operatorIndex()] = name;
    }

    public Name operatorName(Tag tag) {
        return opname[tag.operatorIndex()];
    }

    private static class TypeAnnotationFinder extends TreeScanner {
        public boolean foundTypeAnno = false;

        @Override
        public void scan(JCTree tree) {
            if (foundTypeAnno || tree == null)
                return;
            super.scan(tree);
        }

        public void visitAnnotation(JCAnnotation tree) {
            foundTypeAnno = foundTypeAnno || tree.hasTag(TYPE_ANNOTATION);
        }
    }
}
