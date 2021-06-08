package com.sun.tools.javac.parser;

import com.sun.tools.javac.parser.Tokens.Comment;
import com.sun.tools.javac.tree.DCTree.DCDocComment;
import com.sun.tools.javac.tree.DocCommentTable;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.DiagnosticSource;

import java.util.HashMap;
import java.util.Map;

public class LazyDocCommentTable implements DocCommentTable {
    ParserFactory fac;
    DiagnosticSource diagSource;
    Map<JCTree, Entry> table;
    LazyDocCommentTable(ParserFactory fac) {
        this.fac = fac;
        diagSource = fac.log.currentSource();
        table = new HashMap<JCTree, Entry>();
    }

    public boolean hasComment(JCTree tree) {
        return table.containsKey(tree);
    }

    public Comment getComment(JCTree tree) {
        Entry e = table.get(tree);
        return (e == null) ? null : e.comment;
    }

    public String getCommentText(JCTree tree) {
        Comment c = getComment(tree);
        return (c == null) ? null : c.getText();
    }

    public DCDocComment getCommentTree(JCTree tree) {
        Entry e = table.get(tree);
        if (e == null)
            return null;
        if (e.tree == null)
            e.tree = new DocCommentParser(fac, diagSource, e.comment).parse();
        return e.tree;
    }

    public void putComment(JCTree tree, Comment c) {
        table.put(tree, new Entry(c));
    }

    private static class Entry {
        final Comment comment;
        DCDocComment tree;

        Entry(Comment c) {
            comment = c;
        }
    }
}
