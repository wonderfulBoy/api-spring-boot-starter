package com.github.api.sun.tools.doclets.internal.toolkit.taglets;

import com.github.api.sun.javadoc.*;
import com.github.api.sun.tools.doclets.internal.toolkit.Configuration;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;

import java.util.StringTokenizer;

public class ValueTaglet extends BaseInlineTaglet {

    public ValueTaglet() {
        name = "value";
    }

    public boolean inMethod() {
        return true;
    }

    public boolean inConstructor() {
        return true;
    }

    public boolean inOverview() {
        return true;
    }

    public boolean inPackage() {
        return true;
    }

    public boolean inType() {
        return true;
    }

    private FieldDoc getFieldDoc(Configuration config, Tag tag, String name) {
        if (name == null || name.length() == 0) {

            if (tag.holder() instanceof FieldDoc) {
                return (FieldDoc) tag.holder();
            } else {


                return null;
            }
        }
        StringTokenizer st = new StringTokenizer(name, "#");
        String memberName = null;
        ClassDoc cd = null;
        if (st.countTokens() == 1) {

            Doc holder = tag.holder();
            if (holder instanceof MemberDoc) {
                cd = ((MemberDoc) holder).containingClass();
            } else if (holder instanceof ClassDoc) {
                cd = (ClassDoc) holder;
            }
            memberName = st.nextToken();
        } else {

            cd = config.root.classNamed(st.nextToken());
            memberName = st.nextToken();
        }
        if (cd == null) {
            return null;
        }
        FieldDoc[] fields = cd.fields();
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].name().equals(memberName)) {
                return fields[i];
            }
        }
        return null;
    }

    public Content getTagletOutput(Tag tag, TagletWriter writer) {
        FieldDoc field = getFieldDoc(
                writer.configuration(), tag, tag.text());
        if (field == null) {
            if (tag.text().isEmpty()) {

                writer.getMsgRetriever().warning(tag.holder().position(),
                        "doclet.value_tag_invalid_use");
            } else {

                writer.getMsgRetriever().warning(tag.holder().position(),
                        "doclet.value_tag_invalid_reference", tag.text());
            }
        } else if (field.constantValue() != null) {
            return writer.valueTagOutput(field,
                    field.constantValueExpression(),
                    !field.equals(tag.holder()));
        } else {

            writer.getMsgRetriever().warning(tag.holder().position(),
                    "doclet.value_tag_invalid_constant", field.name());
        }
        return writer.getOutputInstance();
    }
}
