package com.github.api.sun.tools.javac.util;

import java.util.HashMap;
import java.util.Map;

public class Context {

    private Map<Key<?>, Object> ht = new HashMap<Key<?>, Object>();
    private Map<Key<?>, Factory<?>> ft = new HashMap<Key<?>, Factory<?>>();

    private Map<Class<?>, Key<?>> kt = new HashMap<Class<?>, Key<?>>();

    public Context() {
    }

    public Context(Context prev) {
        kt.putAll(prev.kt);
        ft.putAll(prev.ft);
        ht.putAll(prev.ft);
    }

    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast(Object o) {
        return (T) o;
    }

    private static void checkState(Map<?, ?> t) {
        if (t == null)
            throw new IllegalStateException();
    }

    public <T> void put(Key<T> key, Factory<T> fac) {
        checkState(ht);
        Object old = ht.put(key, fac);
        if (old != null)
            throw new AssertionError("duplicate context value");
        checkState(ft);
        ft.put(key, fac);
    }

    public <T> void put(Key<T> key, T data) {
        if (data instanceof Factory<?>)
            throw new AssertionError("T extends Context.Factory");
        checkState(ht);
        Object old = ht.put(key, data);
        if (old != null && !(old instanceof Factory<?>) && old != data && data != null)
            throw new AssertionError("duplicate context value");
    }

    public <T> T get(Key<T> key) {
        checkState(ht);
        Object o = ht.get(key);
        if (o instanceof Factory<?>) {
            Factory<?> fac = (Factory<?>) o;
            o = fac.make(this);
            if (o instanceof Factory<?>)
                throw new AssertionError("T extends Context.Factory");
            Assert.check(ht.get(key) == o);
        }

        return Context.uncheckedCast(o);
    }

    private <T> Key<T> key(Class<T> clss) {
        checkState(kt);
        Key<T> k = uncheckedCast(kt.get(clss));
        if (k == null) {
            k = new Key<T>();
            kt.put(clss, k);
        }
        return k;
    }

    public <T> T get(Class<T> clazz) {
        return get(key(clazz));
    }

    public <T> void put(Class<T> clazz, T data) {
        put(key(clazz), data);
    }

    public <T> void put(Class<T> clazz, Factory<T> fac) {
        put(key(clazz), fac);
    }

    public void dump() {
        for (Object value : ht.values())
            System.err.println(value == null ? null : value.getClass());
    }

    public void clear() {
        ht = null;
        kt = null;
        ft = null;
    }

    public interface Factory<T> {
        T make(Context c);
    }

    public static class Key<T> {

    }
}
