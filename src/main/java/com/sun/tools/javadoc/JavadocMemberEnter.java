package com.sun.tools.javadoc;

import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.Context;

import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.code.Flags.INTERFACE;

public class JavadocMemberEnter extends MemberEnter {
    final DocEnv docenv;

    protected JavadocMemberEnter(Context context) {
        super(context);
        docenv = DocEnv.instance(context);
    }

    public static JavadocMemberEnter instance0(Context context) {
        MemberEnter instance = context.get(memberEnterKey);
        if (instance == null)
            instance = new JavadocMemberEnter(context);
        return (JavadocMemberEnter) instance;
    }

    public static void preRegister(Context context) {
        context.put(memberEnterKey, new Context.Factory<MemberEnter>() {
            public MemberEnter make(Context c) {
                return new JavadocMemberEnter(c);
            }
        });
    }

    private static boolean isAnnotationTypeElement(MethodSymbol meth) {
        return ClassDocImpl.isAnnotationType(meth.enclClass());
    }

    private static boolean isParameter(VarSymbol var) {
        return (var.flags() & Flags.PARAMETER) != 0;
    }

    private static boolean containsNonConstantExpression(JCExpression tree) {
        return new MaybeConstantExpressionScanner().containsNonConstantExpression(tree);
    }

    @Override
    public void visitMethodDef(JCMethodDecl tree) {
        super.visitMethodDef(tree);
        MethodSymbol meth = tree.sym;
        if (meth == null || meth.kind != Kinds.MTH) return;
        TreePath treePath = docenv.getTreePath(env.toplevel, env.enclClass, tree);
        if (meth.isConstructor())
            docenv.makeConstructorDoc(meth, treePath);
        else if (isAnnotationTypeElement(meth))
            docenv.makeAnnotationTypeElementDoc(meth, treePath);
        else
            docenv.makeMethodDoc(meth, treePath);
        tree.body = null;
    }

    @Override
    public void visitVarDef(JCVariableDecl tree) {
        if (tree.init != null) {
            boolean isFinal = (tree.mods.flags & FINAL) != 0
                    || (env.enclClass.mods.flags & INTERFACE) != 0;
            if (!isFinal || containsNonConstantExpression(tree.init)) {
                tree.init = null;
            }
        }
        super.visitVarDef(tree);
        if (tree.sym != null &&
                tree.sym.kind == Kinds.VAR &&
                !isParameter(tree.sym)) {
            docenv.makeFieldDoc(tree.sym, docenv.getTreePath(env.toplevel, env.enclClass, tree));
        }
    }

    private static class MaybeConstantExpressionScanner extends JCTree.Visitor {
        boolean maybeConstantExpr = true;

        public boolean containsNonConstantExpression(JCExpression tree) {
            scan(tree);
            return !maybeConstantExpr;
        }

        public void scan(JCTree tree) {
            if (maybeConstantExpr && tree != null)
                tree.accept(this);
        }

        @Override
        public void visitTree(JCTree tree) {
            maybeConstantExpr = false;
        }

        @Override
        public void visitBinary(JCBinary tree) {
            switch (tree.getTag()) {
                case MUL:
                case DIV:
                case MOD:
                case PLUS:
                case MINUS:
                case SL:
                case SR:
                case USR:
                case LT:
                case LE:
                case GT:
                case GE:
                case EQ:
                case NE:
                case BITAND:
                case BITXOR:
                case BITOR:
                case AND:
                case OR:
                    break;
                default:
                    maybeConstantExpr = false;
            }
        }

        @Override
        public void visitConditional(JCConditional tree) {
            scan(tree.cond);
            scan(tree.truepart);
            scan(tree.falsepart);
        }

        @Override
        public void visitIdent(JCIdent tree) {
        }

        @Override
        public void visitLiteral(JCLiteral tree) {
        }

        @Override
        public void visitParens(JCParens tree) {
            scan(tree.expr);
        }

        @Override
        public void visitSelect(JCFieldAccess tree) {
            scan(tree.selected);
        }

        @Override
        public void visitTypeCast(JCTypeCast tree) {
            scan(tree.clazz);
            scan(tree.expr);
        }

        @Override
        public void visitTypeIdent(JCPrimitiveTypeTree tree) {
        }

        @Override
        public void visitUnary(JCUnary tree) {
            switch (tree.getTag()) {
                case POS:
                case NEG:
                case COMPL:
                case NOT:
                    break;
                default:
                    maybeConstantExpr = false;
            }
        }
    }
}
