package com.sun.tools.doclets.internal.toolkit.builders;

import com.sun.tools.doclets.internal.toolkit.Configuration;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.util.DocletAbortException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

public abstract class AbstractBuilder {
    protected static final boolean DEBUG = false;
    protected final Configuration configuration;

    protected final Set<String> containingPackagesSeen;
    protected final LayoutParser layoutParser;

    public AbstractBuilder(Context c) {
        this.configuration = c.configuration;
        this.containingPackagesSeen = c.containingPackagesSeen;
        this.layoutParser = c.layoutParser;
    }

    public abstract String getName();

    public abstract void build() throws IOException;

    protected void build(XMLNode node, Content contentTree) {
        String component = node.name;
        try {
            invokeMethod("build" + component,
                    new Class<?>[]{XMLNode.class, Content.class},
                    new Object[]{node, contentTree});
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            configuration.root.printError("Unknown element: " + component);
            throw new DocletAbortException(e);
        } catch (InvocationTargetException e) {
            throw new DocletAbortException(e.getCause());
        } catch (Exception e) {
            e.printStackTrace();
            configuration.root.printError("Exception " +
                    e.getClass().getName() +
                    " thrown while processing element: " + component);
            throw new DocletAbortException(e);
        }
    }

    protected void buildChildren(XMLNode node, Content contentTree) {
        for (XMLNode child : node.children)
            build(child, contentTree);
    }

    protected void invokeMethod(String methodName, Class<?>[] paramClasses,
                                Object[] params)
            throws Exception {
        if (DEBUG) {
            configuration.root.printError("DEBUG: " + this.getClass().getName() + "." + methodName);
        }
        Method method = this.getClass().getMethod(methodName, paramClasses);
        method.invoke(this, params);
    }

    public static class Context {

        final Configuration configuration;

        final Set<String> containingPackagesSeen;

        final LayoutParser layoutParser;

        Context(Configuration configuration,
                Set<String> containingPackagesSeen,
                LayoutParser layoutParser) {
            this.configuration = configuration;
            this.containingPackagesSeen = containingPackagesSeen;
            this.layoutParser = layoutParser;
        }
    }
}
