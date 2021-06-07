package com.sun.tools.javac.api;

import java.util.Locale;

public interface Formattable {

    String toString(Locale locale, Messages messages);

    String getKind();

    class LocalizedString implements Formattable {
        String key;

        public LocalizedString(String key) {
            this.key = key;
        }

        public String toString(Locale l, Messages messages) {
            return messages.getLocalizedString(l, key);
        }

        public String getKind() {
            return "LocalizedString";
        }

        public String toString() {
            return key;
        }
    }
}
