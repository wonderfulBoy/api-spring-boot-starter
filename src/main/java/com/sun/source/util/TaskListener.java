package com.sun.source.util;

@jdk.Exported
public interface TaskListener {
    void started(TaskEvent e);
    void finished(TaskEvent e);
}
