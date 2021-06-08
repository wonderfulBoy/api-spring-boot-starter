package com.github.api.sun.tools.javadoc;

import com.github.api.sun.tools.javac.comp.AttrContext;
import com.github.api.sun.tools.javac.comp.Env;
import com.github.api.sun.tools.javac.comp.Todo;
import com.github.api.sun.tools.javac.util.Context;

public class JavadocTodo extends Todo {
    protected JavadocTodo(Context context) {
        super(context);
    }

    public static void preRegister(Context context) {
        context.put(todoKey, new Context.Factory<Todo>() {
            public Todo make(Context c) {
                return new JavadocTodo(c);
            }
        });
    }

    @Override
    public void append(Env<AttrContext> e) {
    }

    @Override
    public boolean offer(Env<AttrContext> e) {
        return false;
    }
}
