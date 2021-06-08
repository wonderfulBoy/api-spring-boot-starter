package com.sun.tools.doclets.internal.toolkit.builders;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.FieldWriter;
import com.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FieldBuilder extends AbstractMemberBuilder {

    private final ClassDoc classDoc;

    private final VisibleMemberMap visibleMemberMap;

    private final FieldWriter writer;

    private final List<ProgramElementDoc> fields;

    private int currentFieldIndex;

    private FieldBuilder(Context context,
                         ClassDoc classDoc,
                         FieldWriter writer) {
        super(context);
        this.classDoc = classDoc;
        this.writer = writer;
        visibleMemberMap =
                new VisibleMemberMap(
                        classDoc,
                        VisibleMemberMap.FIELDS,
                        configuration);
        fields =
                new ArrayList<ProgramElementDoc>(visibleMemberMap.getLeafClassMembers(
                        configuration));
        if (configuration.getMemberComparator() != null) {
            Collections.sort(fields, configuration.getMemberComparator());
        }
    }

    public static FieldBuilder getInstance(Context context,
                                           ClassDoc classDoc,
                                           FieldWriter writer) {
        return new FieldBuilder(context, classDoc, writer);
    }

    public String getName() {
        return "FieldDetails";
    }

    public List<ProgramElementDoc> members(ClassDoc classDoc) {
        return visibleMemberMap.getMembersFor(classDoc);
    }

    public VisibleMemberMap getVisibleMemberMap() {
        return visibleMemberMap;
    }

    public boolean hasMembersToDocument() {
        return fields.size() > 0;
    }

    public void buildFieldDoc(XMLNode node, Content memberDetailsTree) {
        if (writer == null) {
            return;
        }
        int size = fields.size();
        if (size > 0) {
            Content fieldDetailsTree = writer.getFieldDetailsTreeHeader(
                    classDoc, memberDetailsTree);
            for (currentFieldIndex = 0; currentFieldIndex < size;
                 currentFieldIndex++) {
                Content fieldDocTree = writer.getFieldDocTreeHeader(
                        (FieldDoc) fields.get(currentFieldIndex),
                        fieldDetailsTree);
                buildChildren(node, fieldDocTree);
                fieldDetailsTree.addContent(writer.getFieldDoc(
                        fieldDocTree, (currentFieldIndex == size - 1)));
            }
            memberDetailsTree.addContent(
                    writer.getFieldDetails(fieldDetailsTree));
        }
    }

    public void buildSignature(XMLNode node, Content fieldDocTree) {
        fieldDocTree.addContent(
                writer.getSignature((FieldDoc) fields.get(currentFieldIndex)));
    }

    public void buildDeprecationInfo(XMLNode node, Content fieldDocTree) {
        writer.addDeprecated(
                (FieldDoc) fields.get(currentFieldIndex), fieldDocTree);
    }

    public void buildFieldComments(XMLNode node, Content fieldDocTree) {
        if (!configuration.nocomment) {
            writer.addComments((FieldDoc) fields.get(currentFieldIndex), fieldDocTree);
        }
    }

    public void buildTagInfo(XMLNode node, Content fieldDocTree) {
        writer.addTags((FieldDoc) fields.get(currentFieldIndex), fieldDocTree);
    }

    public FieldWriter getWriter() {
        return writer;
    }
}
