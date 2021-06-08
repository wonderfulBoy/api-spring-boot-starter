package com.github.api.sun.tools.doclets.internal.toolkit.taglets;

import com.github.api.sun.tools.doclets.internal.toolkit.util.DocFinder;

public interface InheritableTaglet extends Taglet {

    void inherit(DocFinder.Input input, DocFinder.Output output);
}
