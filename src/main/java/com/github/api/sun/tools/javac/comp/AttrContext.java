package com.github.api.sun.tools.javac.comp;

import com.github.api.sun.tools.javac.code.Lint;
import com.github.api.sun.tools.javac.code.Scope;
import com.github.api.sun.tools.javac.code.Symbol;
import com.github.api.sun.tools.javac.code.Type;
import com.github.api.sun.tools.javac.util.List;

public class AttrContext {

    Scope scope = null;

    int staticLevel = 0;

    boolean isSelfCall = false;

    boolean selectSuper = false;

    Resolve.MethodResolutionPhase pendingResolutionPhase = null;

    Lint lint;

    Symbol enclVar = null;

    Attr.ResultInfo returnResult = null;

    Type defaultSuperCallSite = null;

    AttrContext dup(Scope scope) {
        AttrContext info = new AttrContext();
        info.scope = scope;
        info.staticLevel = staticLevel;
        info.isSelfCall = isSelfCall;
        info.selectSuper = selectSuper;
        info.pendingResolutionPhase = pendingResolutionPhase;
        info.lint = lint;
        info.enclVar = enclVar;
        info.returnResult = returnResult;
        info.defaultSuperCallSite = defaultSuperCallSite;
        return info;
    }

    AttrContext dup() {
        return dup(scope);
    }

    public Iterable<Symbol> getLocalElements() {
        if (scope == null)
            return List.nil();
        return scope.getElements();
    }

    boolean lastResolveVarargs() {
        return pendingResolutionPhase != null &&
                pendingResolutionPhase.isVarargsRequired();
    }

    @Override
    public String toString() {
        return "AttrContext[" + scope.toString() + "]";
    }
}
