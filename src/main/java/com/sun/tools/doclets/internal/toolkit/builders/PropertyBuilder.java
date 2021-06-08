package com.sun.tools.doclets.internal.toolkit.builders;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.tools.doclets.internal.toolkit.Content;
import com.sun.tools.doclets.internal.toolkit.PropertyWriter;
import com.sun.tools.doclets.internal.toolkit.util.VisibleMemberMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PropertyBuilder extends AbstractMemberBuilder {

    private final ClassDoc classDoc;

    private final VisibleMemberMap visibleMemberMap;

    private final PropertyWriter writer;

    private final List<ProgramElementDoc> properties;

    private int currentPropertyIndex;

    private PropertyBuilder(Context context,
                            ClassDoc classDoc,
                            PropertyWriter writer) {
        super(context);
        this.classDoc = classDoc;
        this.writer = writer;
        visibleMemberMap =
                new VisibleMemberMap(
                        classDoc,
                        VisibleMemberMap.PROPERTIES,
                        configuration);
        properties =
                new ArrayList<ProgramElementDoc>(visibleMemberMap.getMembersFor(classDoc));
        if (configuration.getMemberComparator() != null) {
            Collections.sort(properties, configuration.getMemberComparator());
        }
    }

    public static PropertyBuilder getInstance(Context context,
                                              ClassDoc classDoc,
                                              PropertyWriter writer) {
        return new PropertyBuilder(context, classDoc, writer);
    }

    public String getName() {
        return "PropertyDetails";
    }

    public List<ProgramElementDoc> members(ClassDoc classDoc) {
        return visibleMemberMap.getMembersFor(classDoc);
    }

    public VisibleMemberMap getVisibleMemberMap() {
        return visibleMemberMap;
    }

    public boolean hasMembersToDocument() {
        return properties.size() > 0;
    }

    public void buildPropertyDoc(XMLNode node, Content memberDetailsTree) {
        if (writer == null) {
            return;
        }
        int size = properties.size();
        if (size > 0) {
            Content propertyDetailsTree = writer.getPropertyDetailsTreeHeader(
                    classDoc, memberDetailsTree);
            for (currentPropertyIndex = 0; currentPropertyIndex < size;
                 currentPropertyIndex++) {
                Content propertyDocTree = writer.getPropertyDocTreeHeader(
                        (MethodDoc) properties.get(currentPropertyIndex),
                        propertyDetailsTree);
                buildChildren(node, propertyDocTree);
                propertyDetailsTree.addContent(writer.getPropertyDoc(
                        propertyDocTree, (currentPropertyIndex == size - 1)));
            }
            memberDetailsTree.addContent(
                    writer.getPropertyDetails(propertyDetailsTree));
        }
    }

    public void buildSignature(XMLNode node, Content propertyDocTree) {
        propertyDocTree.addContent(
                writer.getSignature((MethodDoc) properties.get(currentPropertyIndex)));
    }

    public void buildDeprecationInfo(XMLNode node, Content propertyDocTree) {
        writer.addDeprecated(
                (MethodDoc) properties.get(currentPropertyIndex), propertyDocTree);
    }

    public void buildPropertyComments(XMLNode node, Content propertyDocTree) {
        if (!configuration.nocomment) {
            writer.addComments((MethodDoc) properties.get(currentPropertyIndex), propertyDocTree);
        }
    }

    public void buildTagInfo(XMLNode node, Content propertyDocTree) {
        writer.addTags((MethodDoc) properties.get(currentPropertyIndex), propertyDocTree);
    }

    public PropertyWriter getWriter() {
        return writer;
    }
}
