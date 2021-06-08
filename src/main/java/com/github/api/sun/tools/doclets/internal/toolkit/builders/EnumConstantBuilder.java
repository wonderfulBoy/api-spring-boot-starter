package com.github.api.sun.tools.doclets.internal.toolkit.builders;

import com.github.api.sun.javadoc.ClassDoc;
import com.github.api.sun.javadoc.FieldDoc;
import com.github.api.sun.javadoc.ProgramElementDoc;
import com.github.api.sun.tools.doclets.internal.toolkit.Content;
import com.github.api.sun.tools.doclets.internal.toolkit.EnumConstantWriter;
import com.github.api.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EnumConstantBuilder extends AbstractMemberBuilder {

    private final ClassDoc classDoc;

    private final VisibleMemberMap visibleMemberMap;

    private final EnumConstantWriter writer;

    private final List<ProgramElementDoc> enumConstants;

    private int currentEnumConstantsIndex;

    private EnumConstantBuilder(Context context,
                                ClassDoc classDoc, EnumConstantWriter writer) {
        super(context);
        this.classDoc = classDoc;
        this.writer = writer;
        visibleMemberMap =
                new VisibleMemberMap(
                        classDoc,
                        VisibleMemberMap.ENUM_CONSTANTS,
                        configuration);
        enumConstants =
                new ArrayList<ProgramElementDoc>(visibleMemberMap.getMembersFor(classDoc));
        if (configuration.getMemberComparator() != null) {
            Collections.sort(enumConstants, configuration.getMemberComparator());
        }
    }

    public static EnumConstantBuilder getInstance(Context context,
                                                  ClassDoc classDoc, EnumConstantWriter writer) {
        return new EnumConstantBuilder(context, classDoc, writer);
    }

    public String getName() {
        return "EnumConstantDetails";
    }

    public List<ProgramElementDoc> members(ClassDoc classDoc) {
        return visibleMemberMap.getMembersFor(classDoc);
    }

    public VisibleMemberMap getVisibleMemberMap() {
        return visibleMemberMap;
    }

    public boolean hasMembersToDocument() {
        return enumConstants.size() > 0;
    }

    public void buildEnumConstant(XMLNode node, Content memberDetailsTree) {
        if (writer == null) {
            return;
        }
        int size = enumConstants.size();
        if (size > 0) {
            Content enumConstantsDetailsTree = writer.getEnumConstantsDetailsTreeHeader(
                    classDoc, memberDetailsTree);
            for (currentEnumConstantsIndex = 0; currentEnumConstantsIndex < size;
                 currentEnumConstantsIndex++) {
                Content enumConstantsTree = writer.getEnumConstantsTreeHeader(
                        (FieldDoc) enumConstants.get(currentEnumConstantsIndex),
                        enumConstantsDetailsTree);
                buildChildren(node, enumConstantsTree);
                enumConstantsDetailsTree.addContent(writer.getEnumConstants(
                        enumConstantsTree, (currentEnumConstantsIndex == size - 1)));
            }
            memberDetailsTree.addContent(
                    writer.getEnumConstantsDetails(enumConstantsDetailsTree));
        }
    }

    public void buildSignature(XMLNode node, Content enumConstantsTree) {
        enumConstantsTree.addContent(writer.getSignature(
                (FieldDoc) enumConstants.get(currentEnumConstantsIndex)));
    }

    public void buildDeprecationInfo(XMLNode node, Content enumConstantsTree) {
        writer.addDeprecated(
                (FieldDoc) enumConstants.get(currentEnumConstantsIndex),
                enumConstantsTree);
    }

    public void buildEnumConstantComments(XMLNode node, Content enumConstantsTree) {
        if (!configuration.nocomment) {
            writer.addComments(
                    (FieldDoc) enumConstants.get(currentEnumConstantsIndex),
                    enumConstantsTree);
        }
    }

    public void buildTagInfo(XMLNode node, Content enumConstantsTree) {
        writer.addTags(
                (FieldDoc) enumConstants.get(currentEnumConstantsIndex),
                enumConstantsTree);
    }

    public EnumConstantWriter getWriter() {
        return writer;
    }
}
