package com.sun.tools.javadoc;

import com.sun.javadoc.MemberDoc;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol;

public abstract class MemberDocImpl extends ProgramElementDocImpl implements MemberDoc {

    public MemberDocImpl(DocEnv env, Symbol sym, TreePath treePath) {
        super(env, sym, treePath);
    }

    public abstract boolean isSynthetic();

}
