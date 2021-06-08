package com.sun.source.tree;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

@jdk.Exported
public interface Scope {
    Scope getEnclosingScope();
    TypeElement getEnclosingClass();
    ExecutableElement getEnclosingMethod();
    Iterable<? extends Element> getLocalElements();
}
