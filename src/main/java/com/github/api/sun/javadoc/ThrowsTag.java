package com.github.api.sun.javadoc;

public interface ThrowsTag extends Tag {
    String exceptionName();

    String exceptionComment();

    ClassDoc exception();

    Type exceptionType();
}
