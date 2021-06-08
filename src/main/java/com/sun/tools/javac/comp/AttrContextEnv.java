package com.sun.tools.javac.comp;

import com.sun.tools.javac.tree.JCTree;

public class AttrContextEnv extends Env<AttrContext> {

    public AttrContextEnv(JCTree tree, AttrContext info) {
        super(tree, info);
    }
}
