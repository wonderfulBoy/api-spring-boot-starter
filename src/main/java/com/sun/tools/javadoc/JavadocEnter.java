package com.sun.tools.javadoc;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;

import javax.tools.JavaFileObject;

public class JavadocEnter extends Enter {
    final Messager messager;
    final DocEnv docenv;

    protected JavadocEnter(Context context) {
        super(context);
        messager = Messager.instance0(context);
        docenv = DocEnv.instance(context);
    }

    public static JavadocEnter instance0(Context context) {
        Enter instance = context.get(enterKey);
        if (instance == null)
            instance = new JavadocEnter(context);
        return (JavadocEnter) instance;
    }

    public static void preRegister(Context context) {
        context.put(enterKey, new Context.Factory<Enter>() {
            public Enter make(Context c) {
                return new JavadocEnter(c);
            }
        });
    }

    @Override
    public void main(List<JCCompilationUnit> trees) {
        int nerrors = messager.nerrors;
        super.main(trees);
        messager.nwarnings += (messager.nerrors - nerrors);
        messager.nerrors = nerrors;
    }

    @Override
    public void visitTopLevel(JCCompilationUnit tree) {
        super.visitTopLevel(tree);
        if (tree.sourcefile.isNameCompatible("package-info", JavaFileObject.Kind.SOURCE)) {
            docenv.makePackageDoc(tree.packge, docenv.getTreePath(tree));
        }
    }

    @Override
    public void visitClassDef(JCClassDecl tree) {
        super.visitClassDef(tree);
        if (tree.sym == null) return;
        if (tree.sym.kind == Kinds.TYP || tree.sym.kind == Kinds.ERR) {
            ClassSymbol c = tree.sym;
            docenv.makeClassDoc(c, docenv.getTreePath(env.toplevel, tree));
        }
    }

    @Override
    protected void duplicateClass(DiagnosticPosition pos, ClassSymbol c) {
    }

}
