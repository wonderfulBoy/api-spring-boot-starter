package com.sun.tools.doclets.internal.toolkit.taglets;

import com.sun.javadoc.Tag;
import com.sun.tools.doclets.internal.toolkit.Content;

public abstract class BasePropertyTaglet extends BaseTaglet {
    public BasePropertyTaglet() {
    }

    abstract String getText(TagletWriter tagletWriter);

    public Content getTagletOutput(Tag tag, TagletWriter tagletWriter) {
        return tagletWriter.propertyTagOutput(tag, getText(tagletWriter));
    }

    public boolean inConstructor() {
        return false;
    }

    public boolean inOverview() {
        return false;
    }

    public boolean inPackage() {
        return false;
    }

    public boolean inType() {
        return false;
    }

    public boolean isInlineTag() {
        return false;
    }
}
