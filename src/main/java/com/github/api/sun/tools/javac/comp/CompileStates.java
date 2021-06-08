package com.github.api.sun.tools.javac.comp;

import com.github.api.sun.tools.javac.util.Context;

import java.util.HashMap;

public class CompileStates extends HashMap<Env<AttrContext>, CompileStates.CompileState> {

    protected static final Context.Key<CompileStates> compileStatesKey =
            new Context.Key<CompileStates>();
    private static final long serialVersionUID = 1812267524140424433L;
    protected Context context;

    public CompileStates(Context context) {
        this.context = context;
        context.put(compileStatesKey, this);
    }

    public static CompileStates instance(Context context) {
        CompileStates instance = context.get(compileStatesKey);
        if (instance == null) {
            instance = new CompileStates(context);
        }
        return instance;
    }

    public boolean isDone(Env<AttrContext> env, CompileState cs) {
        CompileState ecs = get(env);
        return (ecs != null) && !cs.isAfter(ecs);
    }

    public enum CompileState {
        INIT(0),
        PARSE(1),
        ENTER(2),
        PROCESS(3),
        ATTR(4),
        FLOW(5),
        TRANSTYPES(6),
        UNLAMBDA(7),
        LOWER(8),
        GENERATE(9);

        private final int value;

        CompileState(int value) {
            this.value = value;
        }

        public static CompileState max(CompileState a, CompileState b) {
            return a.value > b.value ? a : b;
        }

        public boolean isAfter(CompileState other) {
            return value > other.value;
        }
    }
}
