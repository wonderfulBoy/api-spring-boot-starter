package com.github.api.sun.tools.javac.util;

import com.github.api.sun.tools.javac.main.Option;

import java.util.LinkedHashMap;
import java.util.Set;

import static com.github.api.sun.tools.javac.main.Option.XLINT;
import static com.github.api.sun.tools.javac.main.Option.XLINT_CUSTOM;

public class Options {
    public static final Context.Key<Options> optionsKey =
            new Context.Key<Options>();
    private static final long serialVersionUID = 0;
    private LinkedHashMap<String, String> values;
    private List<Runnable> listeners = List.nil();

    protected Options(Context context) {
        values = new LinkedHashMap<String, String>();
        context.put(optionsKey, this);
    }

    public static Options instance(Context context) {
        Options instance = context.get(optionsKey);
        if (instance == null)
            instance = new Options(context);
        return instance;
    }

    public String get(String name) {
        return values.get(name);
    }

    public String get(Option option) {
        return values.get(option.text);
    }

    public boolean getBoolean(String name) {
        return getBoolean(name, false);
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        String value = get(name);
        return (value == null) ? defaultValue : Boolean.parseBoolean(value);
    }

    public boolean isSet(String name) {
        return (values.get(name) != null);
    }

    public boolean isSet(Option option) {
        return (values.get(option.text) != null);
    }

    public boolean isSet(Option option, String value) {
        return (values.get(option.text + value) != null);
    }

    public boolean isUnset(String name) {
        return (values.get(name) == null);
    }

    public boolean isUnset(Option option) {
        return (values.get(option.text) == null);
    }

    public boolean isUnset(Option option, String value) {
        return (values.get(option.text + value) == null);
    }

    public void put(String name, String value) {
        values.put(name, value);
    }

    public void put(Option option, String value) {
        values.put(option.text, value);
    }

    public void putAll(Options options) {
        values.putAll(options.values);
    }

    public void remove(String name) {
        values.remove(name);
    }

    public Set<String> keySet() {
        return values.keySet();
    }

    public int size() {
        return values.size();
    }

    public void addListener(Runnable listener) {
        listeners = listeners.prepend(listener);
    }

    public void notifyListeners() {
        for (Runnable r : listeners)
            r.run();
    }

    public boolean lint(String s) {


        return
                isSet(XLINT_CUSTOM, s) ||
                        (isSet(XLINT) || isSet(XLINT_CUSTOM, "all")) &&
                                isUnset(XLINT_CUSTOM, "-" + s);
    }
}
