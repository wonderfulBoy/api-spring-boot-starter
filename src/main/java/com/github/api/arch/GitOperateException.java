package com.github.api.arch;

/**
 * GitOperateException
 *
 * @author echils
 * @since 2021-02-27 19:52:48
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
