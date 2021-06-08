package com.sun.tools.javadoc;

import com.sun.javadoc.ParamTag;
import com.sun.javadoc.Tag;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ParamTagImpl extends TagImpl implements ParamTag {
    private static final Pattern typeParamRE = Pattern.compile("<([^<>]+)>");
    private final String parameterName;
    private final String parameterComment;
    private final boolean isTypeParameter;
    private Tag[] inlineTags;

    ParamTagImpl(DocImpl holder, String name, String text) {
        super(holder, name, text);
        String[] sa = divideAtWhite();
        Matcher m = typeParamRE.matcher(sa[0]);
        isTypeParameter = m.matches();
        parameterName = isTypeParameter ? m.group(1) : sa[0];
        parameterComment = sa[1];
    }

    public String parameterName() {
        return parameterName;
    }

    public String parameterComment() {
        return parameterComment;
    }

    @Override
    public String kind() {
        return "@param";
    }

    public boolean isTypeParameter() {
        return isTypeParameter;
    }

    @Override
    public String toString() {
        return name + ":" + text;
    }

    @Override
    public Tag[] inlineTags() {
        if (inlineTags == null) {
            inlineTags = Comment.getInlineTags(holder, parameterComment);
        }
        return inlineTags;
    }
}
