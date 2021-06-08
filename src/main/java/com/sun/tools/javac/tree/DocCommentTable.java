package com.sun.tools.javac.tree;

import com.sun.tools.javac.parser.Tokens.Comment;
import com.sun.tools.javac.tree.DCTree.DCDocComment;

public interface DocCommentTable {

    boolean hasComment(JCTree tree);

    Comment getComment(JCTree tree);

    String getCommentText(JCTree tree);

    DCDocComment getCommentTree(JCTree tree);

    void putComment(JCTree tree, Comment c);
}
