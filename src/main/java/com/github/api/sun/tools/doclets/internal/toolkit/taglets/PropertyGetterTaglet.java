package com.github.api.sun.tools.doclets.internal.toolkit.taglets;

public class PropertyGetterTaglet extends BasePropertyTaglet {

    public PropertyGetterTaglet() {
        name = "propertyGetter";
    }

    @Override
    String getText(TagletWriter tagletWriter) {
        return tagletWriter.configuration().getText("doclet.PropertyGetter");
    }
}
