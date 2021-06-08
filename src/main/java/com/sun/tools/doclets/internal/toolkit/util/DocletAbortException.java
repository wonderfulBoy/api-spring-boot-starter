package com.sun.tools.doclets.internal.toolkit.util;

public class DocletAbortException extends RuntimeException {
    private static final long serialVersionUID = -9131058909576418984L;

    public DocletAbortException(String message) {
        super(message);
    }

    public DocletAbortException(Throwable cause) {
        super(cause);
    }
}
