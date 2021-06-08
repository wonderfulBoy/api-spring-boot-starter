package com.sun.tools.doclets.internal.toolkit.taglets;

public class PropertySetterTaglet extends BasePropertyTaglet {

    public PropertySetterTaglet() {
        name = "propertySetter";
    }

    @Override
    String getText(TagletWriter tagletWriter) {
        return tagletWriter.configuration().getText("doclet.PropertySetter");
    }
}
