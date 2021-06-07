package com.sun.tools.javac.api;

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.util.Context;

import java.util.Arrays;
import java.util.Collection;

public class MultiTaskListener implements TaskListener {

    public static final Context.Key<MultiTaskListener> taskListenerKey = new Context.Key<MultiTaskListener>();
    TaskListener[] listeners = {};
    ClientCodeWrapper ccw;

    protected MultiTaskListener(Context context) {
        context.put(taskListenerKey, this);
        ccw = ClientCodeWrapper.instance(context);
    }

    public static MultiTaskListener instance(Context context) {
        MultiTaskListener instance = context.get(taskListenerKey);
        if (instance == null)
            instance = new MultiTaskListener(context);
        return instance;
    }

    public Collection<TaskListener> getTaskListeners() {
        return Arrays.asList(listeners);
    }

    public boolean isEmpty() {
        return (listeners.length == 0);
    }

    public void add(TaskListener listener) {
        for (TaskListener l : listeners) {
            if (ccw.unwrap(l) == listener)
                throw new IllegalStateException();
        }
        listeners = Arrays.copyOf(listeners, listeners.length + 1);
        listeners[listeners.length - 1] = ccw.wrap(listener);
    }

    public void remove(TaskListener listener) {
        for (int i = 0; i < listeners.length; i++) {
            if (ccw.unwrap(listeners[i]) == listener) {
                TaskListener[] newListeners = new TaskListener[listeners.length - 1];
                System.arraycopy(listeners, 0, newListeners, 0, i);
                System.arraycopy(listeners, i + 1, newListeners, i, newListeners.length - i);
                listeners = newListeners;
                break;
            }
        }
    }

    @Override
    public void started(TaskEvent e) {
        TaskListener[] ll = this.listeners;
        for (TaskListener l : ll)
            l.started(e);
    }

    @Override
    public void finished(TaskEvent e) {
        TaskListener[] ll = this.listeners;
        for (TaskListener l : ll)
            l.finished(e);
    }

    @Override
    public String toString() {
        return Arrays.toString(listeners);
    }
}
