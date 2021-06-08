package com.sun.source.tree;

@jdk.Exported
public interface LineMap {
    long getStartPosition(long line);

    long getPosition(long line, long column);

    long getLineNumber(long pos);

    long getColumnNumber(long pos);
}
