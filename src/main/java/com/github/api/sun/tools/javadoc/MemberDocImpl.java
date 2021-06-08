package com.github.api.sun.tools.javadoc;

import com.github.api.sun.javadoc.MemberDoc;
import com.github.api.sun.source.util.TreePath;
import com.github.api.sun.tools.javac.code.Symbol;

public abstract class MemberDocImpl extends ProgramElementDocImpl implements MemberDoc {
    public MemberDocImpl(DocEnv env, Symbol sym, TreePath treePath) {
        super(env, sym, treePath);
    }

    public abstract boolean isSynthetic();
}
