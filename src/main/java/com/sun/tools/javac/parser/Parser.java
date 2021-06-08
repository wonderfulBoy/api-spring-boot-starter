package com.sun.tools.javac.parser;

import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCStatement;

public interface Parser {

    JCCompilationUnit parseCompilationUnit();

    JCExpression parseExpression();

    JCStatement parseStatement();

    JCExpression parseType();
}
