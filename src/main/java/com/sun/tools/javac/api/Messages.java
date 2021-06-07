package com.sun.tools.javac.api;

import java.util.Locale;
import java.util.MissingResourceException;
public interface Messages {

    void add(String bundleName) throws MissingResourceException;

    String getLocalizedString(Locale l, String key, Object... args);
}
