package com.github.api.core.arch;

/**
 * GitOperateException
 *
 * @author echils
 */
public class GitOperateException extends RuntimeException {

    public GitOperateException() {
    }

    public GitOperateException(String message) {
        super(message);
    }

    public GitOperateException(String message, Throwable cause) {
        super(message, cause);
    }

    public GitOperateException(Throwable cause) {
        super(cause);
    }

    public GitOperateException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
