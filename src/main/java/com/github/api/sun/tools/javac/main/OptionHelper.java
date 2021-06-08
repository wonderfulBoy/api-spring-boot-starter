package com.github.api.sun.tools.javac.main;

import com.github.api.sun.tools.javac.util.Log;
import com.github.api.sun.tools.javac.util.Log.PrefixKind;

import java.io.File;

public abstract class OptionHelper {

    public abstract String get(Option option);

    public abstract void put(String name, String value);

    public abstract void remove(String name);

    public abstract Log getLog();

    public abstract String getOwnName();

    abstract void error(String key, Object... args);

    abstract void addFile(File f);

    abstract void addClassName(String s);

    public static class GrumpyHelper extends OptionHelper {
        private final Log log;

        public GrumpyHelper(Log log) {
            this.log = log;
        }

        @Override
        public Log getLog() {
            return log;
        }

        @Override
        public String getOwnName() {
            throw new IllegalStateException();
        }

        @Override
        public String get(Option option) {
            throw new IllegalArgumentException();
        }

        @Override
        public void put(String name, String value) {
            throw new IllegalArgumentException();
        }

        @Override
        public void remove(String name) {
            throw new IllegalArgumentException();
        }

        @Override
        void error(String key, Object... args) {
            throw new IllegalArgumentException(log.localize(PrefixKind.JAVAC, key, args));
        }

        @Override
        public void addFile(File f) {
            throw new IllegalArgumentException(f.getPath());
        }

        @Override
        public void addClassName(String s) {
            throw new IllegalArgumentException(s);
        }
    }
}
